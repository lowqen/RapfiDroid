package dev.gomoku.yixindroid.data.review

import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.GameFile
import dev.gomoku.yixindroid.core.model.EngineBusy
import dev.gomoku.yixindroid.core.model.GameFileContent
import dev.gomoku.yixindroid.core.model.GameFileFormat
import dev.gomoku.yixindroid.core.model.GameReport
import dev.gomoku.yixindroid.core.model.GradingPreset
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.MoveGrader
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.PositionRecord
import dev.gomoku.yixindroid.core.model.PvSnapshot
import dev.gomoku.yixindroid.core.model.QueueEntry
import dev.gomoku.yixindroid.core.model.QueueProgress
import dev.gomoku.yixindroid.core.model.QueueStatus
import dev.gomoku.yixindroid.core.model.Referee
import dev.gomoku.yixindroid.core.model.ReviewBudget
import dev.gomoku.yixindroid.core.model.ReviewData
import dev.gomoku.yixindroid.core.model.ReviewProgress
import dev.gomoku.yixindroid.core.model.StoneColor
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.engine.SearchAggregator
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.GameFileReader
import dev.gomoku.yixindroid.domain.repository.GameRepository
import dev.gomoku.yixindroid.domain.repository.ProveRepository
import dev.gomoku.yixindroid.domain.repository.ReviewRepository
import dev.gomoku.yixindroid.domain.repository.ReviewStart
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The review pipeline, ported from main.c:6964-7260 (`game_review`,
 * `review_analyze_current`, `review_on_bestmove`, `review_finish`) and the
 * queue that drives it over many files (main.c:7263-7560).
 *
 * Each position is searched with `yxnbest 2`: PV0 gives the position value and
 * the engine's best move, and the spread to PV1 is the "only move" signal the
 * grader needs. The desktop's four arrays (`wrhistory`/`wrmate`/`wrvalid`,
 * `reviewbestmove`, `reviewgap`) become one [PositionRecord] per ply.
 *
 * Unlike the desktop this never walks the visible board: the review has its own
 * copy of the line and only pushes positions to the engine, so a review can run
 * while the user looks at something else. Tapping a row of the report is what
 * moves the board (`game_report_row`).
 */
@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val engine: EngineRepository,
    private val game: GameRepository,
    private val settingsRepository: SettingsRepository,
    private val files: GameFileReader,
    /**
     * A prove run owns the engine just as exclusively as a review does, and each
     * refuses to start while the other runs (main.c's `reviewactive` /
     * `proveactive` pair). Injected lazily because the prove repository takes
     * *this* one — the guard is the only thing that needs it.
     */
    private val prove: Provider<ProveRepository>,
    private val busy: EngineBusy,
    @IoDispatcher io: CoroutineDispatcher,
) : ReviewRepository {

    private val scope = CoroutineScope(SupervisorJob() + io)

    private val _progress = MutableStateFlow(ReviewProgress())
    override val progress: StateFlow<ReviewProgress> = _progress.asStateFlow()

    private val _report = MutableStateFlow<GameReport?>(null)
    override val report: StateFlow<GameReport?> = _report.asStateFlow()

    private val _queueReports = MutableStateFlow<List<GameReport>>(emptyList())
    override val queueReports: StateFlow<List<GameReport>> = _queueReports.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    override val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    override val log: StateFlow<List<String>> = _log.asStateFlow()

    private val settings: AppSettings get() = settingsRepository.settings.value

    // ---- run state (touched only under [lock]) ------------------------------

    private val lock = Mutex()
    private var line: List<Move> = emptyList()
    private var size: Int = Move.DEFAULT_SIZE
    private var records: MutableList<PositionRecord> = mutableListOf()
    private var index = 0
    private var total = 0
    private var budget = ReviewBudget()
    private var title = ""
    private var searching = false

    /** `isneedomit`: best moves a `yxstop` will produce that must be ignored. */
    private var omit = 0

    private var aggregator: SearchAggregator? = null

    /** Last snapshot the aggregator produced for the position being searched. */
    private var latest: AnalysisSnapshot? = null
    private var watchdog: Job? = null

    // queue
    private var queueRunning = false
    private var queueIndex = 0
    private var queueOk = 0
    private var queueFailed = 0

    init {
        scope.launch { engine.responses.collect { onResponse(it) } }
    }

    /** Tests only; the app keeps the singleton for the process's lifetime. */
    fun shutdown() {
        scope.cancel()
    }

    // ---- starting -----------------------------------------------------------

    override suspend fun start(budget: ReviewBudget): ReviewStart = lock.withLock {
        val refusal = guard()
        if (refusal != null) return@withLock ReviewStart.Refused(refusal)
        val whole = game.position.value.moves + game.future.value
        if (whole.isEmpty()) return@withLock ReviewStart.Refused("분석할 수가 없습니다")
        queueRunning = false
        beginRun(whole, game.position.value.size, budget.sanitized(), title = boardTitle(whole))
        ReviewStart.Started
    }

    override suspend fun startQueue(budget: ReviewBudget): ReviewStart = lock.withLock {
        val refusal = guard()
        if (refusal != null) return@withLock ReviewStart.Refused(refusal)
        if (_queue.value.isEmpty()) return@withLock ReviewStart.Refused("큐가 비어 있습니다")
        queueRunning = true
        queueIndex = 0
        queueOk = 0
        queueFailed = 0
        _queueReports.value = emptyList()
        _queue.update { list -> list.map { it.copy(status = QueueStatus.PENDING, result = "") } }
        this.budget = budget.sanitized()
        if (!queueNext()) return@withLock ReviewStart.Refused("읽을 수 있는 기보가 없습니다")
        ReviewStart.Started
    }

    /** The desktop's refusals (main.c:7189, 7195, 7440). */
    private fun guard(): String? = when {
        _progress.value.running -> "이미 리뷰가 진행 중입니다"
        prove.get().progress.value.running -> "국면 증명이 진행 중입니다 — 먼저 중지하세요"
        !engine.state.value.isLive -> "엔진에 연결되어 있지 않습니다"
        game.state.value.thinking -> "엔진이 대국 수를 계산 중입니다 — 먼저 중지하세요"
        else -> null
    }

    private suspend fun beginRun(whole: List<Move>, boardSize: Int, budget: ReviewBudget, title: String) {
        line = whole
        size = boardSize
        total = whole.size
        index = 0
        records = MutableList(total + 1) { PositionRecord() }
        this.budget = budget
        this.title = title
        omit = 0
        _progress.value = ReviewProgress(
            running = true, index = 0, total = total, budget = budget,
            queue = queueProgress(),
        )
        // The engine is ours until finish(): a database save now would rewrite
        // the file out from under a search that is still adding to it.
        busy.acquire(EngineBusy.REVIEW)
        note("게임 리뷰 시작: ${total + 1}개 국면, 수당 ${budget.label}")
        sendBudget()
        analyzeCurrent()
    }

    private fun queueProgress(): QueueProgress? {
        if (!queueRunning) return null
        val entry = _queue.value.getOrNull(queueIndex) ?: return null
        return QueueProgress(queueIndex + 1, _queue.value.size, entry.name)
    }

    private fun boardTitle(whole: List<Move>): String {
        val head = whole.take(3).joinToString("-") { MoveGrader.coord(it, size) }
        return if (head.isEmpty()) "game" else head
    }

    /** `review_send_budget` (main.c:7073). */
    private suspend fun sendBudget() {
        val info = mutableListOf<Pair<String, String>>()
        if (budget.byDepth) {
            info += "timeout_turn" to UNLIMITED_TURN_MS.toString()
        } else {
            info += "timeout_turn" to (budget.seconds * 1000).toString()
        }
        info += "timeout_match" to UNLIMITED_MATCH_MS.toString()
        info += "max_node" to "-1"
        info += "max_depth" to (if (budget.byDepth) budget.depth else size * size).toString()
        info.forEach { (key, value) -> engine.send(EngineCommand.Info(key, value)) }
    }

    /** `set_level(levelchoice)` — put the user's limits back (main.c:7027). */
    private suspend fun restoreLevel() {
        val params = settings.toEngineParams()
        params.levelPairs().forEach { (key, value) ->
            runCatching { engine.send(EngineCommand.Info(key, value)) }
        }
    }

    // ---- the loop -----------------------------------------------------------

    /** `review_analyze_current` (main.c:7098). */
    private suspend fun analyzeCurrent() {
        val skipOpening = settings.skipOpening
        while (index <= total) {
            if (skipOpening && index < MoveGrader.SKIP_OPENING_N) {
                index++          // opening positions are not searched at all
                continue
            }
            val prefix = line.take(index)
            val decided = Referee.result(prefix, size, settings.allowsOverlineWin) != null
            if (!decided) break
            // The game is over here: the last mover won, no search needed.
            records[index] = PositionRecord(
                blackWinRate = if (index % 2 == 1) 1.0 else 0.0,
                blackMate = 0,
            )
            index++
        }
        if (index > total) {
            finish(cancelled = false)
            return
        }
        _progress.update { it.copy(index = index) }
        val position = Position(size = size, moves = line.take(index))
        aggregator = SearchAggregator(position.sideToMove)
        latest = null
        searching = true
        engine.send(EngineCommand.InfoTimeLeft(UNLIMITED_MATCH_MS))
        engine.send(EngineCommand.Start(size))
        engine.send(EngineCommand.YxBoard(position.placements()))
        engine.send(EngineCommand.YxNbest(REVIEW_PV))
        armWatchdog()
    }

    /** `review_watchdog_fire`: a stalled engine gets the position again. */
    private fun armWatchdog() {
        watchdog?.cancel()
        val seconds = budget.watchdogSeconds
        watchdog = scope.launch {
            delay(seconds * 1000L)
            lock.withLock {
                if (!_progress.value.running || !searching) return@withLock
                note("리뷰: 엔진 응답이 없어 ${index}번 국면을 다시 보냅니다")
                omit++
                runCatching { engine.send(EngineCommand.YxStop) }
                searching = false
                analyzeCurrent()
            }
        }
    }

    private suspend fun onResponse(response: EngineResponse) {
        if (!_progress.value.running) return
        lock.withLock {
            if (!searching) return@withLock
            aggregator?.consume(response)?.let { latest = it }
            if (response is EngineResponse.BestMove) onBestMove(response.moves.firstOrNull())
        }
    }

    /** `review_on_bestmove` (main.c:7167). */
    private suspend fun onBestMove(move: Move?) {
        if (omit > 0) {
            omit--
            return
        }
        watchdog?.cancel()
        watchdog = null
        searching = false
        val pvs = latest?.pvs.orEmpty()
        val stm = if (index % 2 == 0) StoneColor.BLACK else StoneColor.WHITE
        // The desktop's eval bar clamps a mate to 1.0 / 0.0 before it lands in
        // `wrhistory` (`evalbar_update_from_engine`, main.c:1766).
        val best = pvs.firstOrNull { it.index == 0 } ?: pvs.firstOrNull()
        val mate = best?.mate ?: 0
        val blackMate = if (stm == StoneColor.BLACK) mate else -mate
        val rate = best?.winRate
        val blackRate = when {
            blackMate > 0 -> 1.0
            blackMate < 0 -> 0.0
            rate == null -> null
            stm == StoneColor.BLACK -> rate
            else -> 1.0 - rate
        }
        // Both PVs seen: keep the best-vs-second spread, mover's view, mates
        // clamped exactly as `review_on_pvdone` does.
        val gap = if (pvs.size >= 2) {
            val a = pvWinRate(pvs[0])
            val b = pvWinRate(pvs[1])
            if (a == null || b == null) null else (a - b).coerceAtLeast(0.0)
        } else {
            null
        }
        records[index] = records[index].copy(
            blackWinRate = blackRate,
            blackMate = blackMate,
            best = move,
            gap = gap,
        )
        index++
        _progress.update { it.copy(index = index) }
        if (index <= total) analyzeCurrent() else finish(cancelled = false)
    }

    private fun pvWinRate(pv: PvSnapshot): Double? = when {
        (pv.mate ?: 0) > 0 -> 1.0
        (pv.mate ?: 0) < 0 -> 0.0
        else -> pv.winRate
    }

    /** `review_finish` (main.c:7019). */
    private suspend fun finish(cancelled: Boolean) {
        watchdog?.cancel()
        watchdog = null
        if (cancelled && searching) {
            omit++
            runCatching { engine.send(EngineCommand.YxStop) }
        }
        searching = false
        aggregator = null
        _progress.value = ReviewProgress(budget = budget)
        busy.release(EngineBusy.REVIEW)
        runCatching { restoreLevel() }

        if (cancelled) {
            note("게임 리뷰 취소 (${index}/${total + 1})")
        } else {
            val report = buildReport()
            _report.value = report
            note("게임 리뷰 완료: ${total + 1}개 국면")
            if (queueRunning) {
                _queueReports.update { it + report }
                markQueue(
                    QueueStatus.DONE,
                    "완료 — 정확도 흑 ${percent(report.tally.blackAccuracy)} / " +
                        "백 ${percent(report.tally.whiteAccuracy)}, ${report.moveCount}수",
                )
                queueOk++
            }
        }
        if (queueRunning) {
            if (cancelled) {
                finishQueue(cancelled = true)
            } else {
                queueIndex++
                if (!queueNext()) finishQueue(cancelled = false)
            }
        }
    }

    private fun buildReport(): GameReport = GameReport.of(
        title = title,
        data = ReviewData(moves = line, size = size, records = records.toList()),
        budget = budget,
        preset = GradingPreset.of(settings.mqPreset),
        skipOpening = settings.skipOpening,
        createdAt = System.currentTimeMillis(),
        ruleName = GameReport.ruleNameOf(settings),
    )

    override suspend fun cancel() = lock.withLock {
        if (!_progress.value.running) return@withLock
        finish(cancelled = true)
    }

    // ---- queue --------------------------------------------------------------

    override fun enqueue(entries: List<QueueEntry>) {
        if (_progress.value.running) return
        _queue.update { current ->
            val known = current.map { it.uri }.toHashSet()
            current + entries.filter { it.uri !in known }
        }
    }

    override fun removeQueued(uri: String) {
        if (_progress.value.running) return
        _queue.update { list -> list.filterNot { it.uri == uri } }
    }

    override fun clearQueue() {
        if (_progress.value.running) return
        _queue.value = emptyList()
        _queueReports.value = emptyList()
    }

    /** `queue_next` (main.c:7482): skip what cannot be read or has no moves. */
    private suspend fun queueNext(): Boolean {
        while (queueIndex < _queue.value.size) {
            val entry = _queue.value[queueIndex]
            val format = GameFileFormat.of(entry.name)
            val bytes = if (format == null) null else runCatching { files.read(entry.uri) }.getOrNull()
            val moves = if (bytes == null || format == null) {
                null
            } else {
                GameFile.parse(bytes, format, settings.boardSize)
            }
            if (moves.isNullOrEmpty()) {
                note("큐: ${entry.name} 을(를) 읽을 수 없어 건너뜁니다")
                markQueue(QueueStatus.FAILED, if (moves == null) "실패: 파일을 읽을 수 없음" else "실패: 수가 없음")
                queueFailed++
                queueIndex++
                continue
            }
            markQueue(QueueStatus.RUNNING, "")
            game.replaceLine(moves)
            note("큐 ${queueIndex + 1}/${_queue.value.size}: ${entry.name}")
            beginRun(moves, settings.boardSize, budget, GameFile.baseName(entry.name))
            return true
        }
        return false
    }

    private fun markQueue(status: QueueStatus, result: String) {
        _queue.update { list ->
            list.mapIndexed { i, entry ->
                if (i == queueIndex) entry.copy(status = status, result = result) else entry
            }
        }
    }

    private fun finishQueue(cancelled: Boolean) {
        queueRunning = false
        note(
            "분석 큐 ${if (cancelled) "취소" else "완료"}: " +
                "리뷰 $queueOk, 실패 $queueFailed / 총 ${_queue.value.size}",
        )
    }

    // ---- misc ---------------------------------------------------------------

    override suspend fun loadGame(content: GameFileContent) {
        game.newGame(resetClock = true)
        game.replaceLine(content.moves)
        title = content.name
        note("기보 불러오기: ${content.name} (${content.moves.size}수)")
    }

    override fun setPreset(preset: GradingPreset) {
        _report.update { current ->
            current?.let {
                GameReport.of(
                    title = it.title, data = it.data, budget = it.budget, preset = preset,
                    skipOpening = it.skipOpening, createdAt = it.createdAt, ruleName = it.ruleName,
                )
            }
        }
        _queueReports.update { list ->
            list.map {
                GameReport.of(
                    title = it.title, data = it.data, budget = it.budget, preset = preset,
                    skipOpening = it.skipOpening, createdAt = it.createdAt, ruleName = it.ruleName,
                )
            }
        }
    }

    override fun clearReport() {
        _report.value = null
    }

    private fun percent(value: Double?) = if (value == null) "0.0%" else "%.1f%%".format(value)

    private fun note(text: String) {
        _log.update { (it + text).takeLast(LOG_LIMIT) }
    }

    private companion object {
        /** `yxnbest 2` — the second PV is what the "only move" grades need. */
        const val REVIEW_PV = 2
        const val UNLIMITED_TURN_MS = 1_000_000_000L
        const val UNLIMITED_MATCH_MS = 2_000_000_000L
        const val LOG_LIMIT = 60
    }
}
