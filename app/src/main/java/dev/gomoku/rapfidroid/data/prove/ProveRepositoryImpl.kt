package dev.gomoku.rapfidroid.data.prove

import dev.gomoku.rapfidroid.core.common.IoDispatcher
import dev.gomoku.rapfidroid.core.model.AnalysisSnapshot
import dev.gomoku.rapfidroid.core.model.AppSettings
import dev.gomoku.rapfidroid.core.model.EngineBusy
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.Position
import dev.gomoku.rapfidroid.core.model.ProveKind
import dev.gomoku.rapfidroid.core.model.ProveNode
import dev.gomoku.rapfidroid.core.model.ProveOptions
import dev.gomoku.rapfidroid.core.model.ProveOutcome
import dev.gomoku.rapfidroid.core.model.ProveOverlay
import dev.gomoku.rapfidroid.core.model.ProvePhase
import dev.gomoku.rapfidroid.core.model.ProveProgress
import dev.gomoku.rapfidroid.core.model.ProvePv
import dev.gomoku.rapfidroid.core.model.ProveResult
import dev.gomoku.rapfidroid.core.model.ProveState
import dev.gomoku.rapfidroid.core.model.ProveStep
import dev.gomoku.rapfidroid.core.model.ProveTree
import dev.gomoku.rapfidroid.domain.engine.DbEditRecord
import dev.gomoku.rapfidroid.domain.engine.DbQueryOne
import dev.gomoku.rapfidroid.domain.engine.EngineCommand
import dev.gomoku.rapfidroid.domain.engine.EngineResponse
import dev.gomoku.rapfidroid.domain.engine.SearchAggregator
import dev.gomoku.rapfidroid.domain.repository.DatabaseSaver
import dev.gomoku.rapfidroid.domain.repository.EngineRepository
import dev.gomoku.rapfidroid.domain.repository.GameRepository
import dev.gomoku.rapfidroid.domain.repository.ProveRepository
import dev.gomoku.rapfidroid.domain.repository.ProveStart
import dev.gomoku.rapfidroid.domain.repository.ReviewRepository
import dev.gomoku.rapfidroid.domain.repository.SettingsRepository
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * The prove pipeline's engine conversation (main.c:9435-9598, 9756-9859). The
 * tree, the cost function and every propagation rule live in [ProveTree]; this
 * class only talks to the engine, one pending command at a time:
 *
 * ```
 *  pick cheapest open node -> yxquerydatabaseone
 *      label w/l/d  -> conclusion straight from the database
 *      no record    -> yxnbest k (attack) / yxsearchdefend (defend)
 *  conclusion -> yxedittvddatabase 7 (one per queued mate) -> next node
 * ```
 *
 * Like the review it never walks the visible board: the root is the line that was
 * on the board when the run started, and every position goes to the engine as a
 * `YXBOARD` of its own.
 */
@Singleton
class ProveRepositoryImpl @Inject constructor(
    private val engine: EngineRepository,
    private val game: GameRepository,
    private val review: ReviewRepository,
    private val settingsRepository: SettingsRepository,
    private val databaseSaver: DatabaseSaver,
    private val busy: EngineBusy,
    @IoDispatcher io: CoroutineDispatcher,
) : ProveRepository {

    private val scope = CoroutineScope(SupervisorJob() + io)

    private val _progress = MutableStateFlow(ProveProgress())
    override val progress: StateFlow<ProveProgress> = _progress.asStateFlow()

    private val _overlay = MutableStateFlow(ProveOverlay.EMPTY)
    override val overlay: StateFlow<ProveOverlay> = _overlay.asStateFlow()

    private val _outcome = MutableStateFlow<ProveOutcome?>(null)
    override val outcome: StateFlow<ProveOutcome?> = _outcome.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    override val log: StateFlow<List<String>> = _log.asStateFlow()

    private val settings: AppSettings get() = settingsRepository.settings.value

    // ---- run state (touched only under [lock]) ------------------------------

    private val lock = Mutex()
    private var tree: ProveTree? = null
    private var options = ProveOptions()

    /** The line on the board when the run started (`movepath` up to `piecenum`). */
    private var rootMoves: List<Move> = emptyList()
    private var size = Move.DEFAULT_SIZE
    private var phase = ProvePhase.IDLE
    private var current = -1
    private var searches = 0
    private var dbWrites = 0

    /** Budget of the running search: ms, or a depth (`provecurbudget`). */
    private var currentBudget = 0
    private var searchStartedAt = 0L

    /**
     * `isneedomit`: a `yxstop` still produces one best move. Unlike the desktop's
     * global counter this only has to cover the watchdog's retry, where the stale
     * move would otherwise be read as the answer to the *new* search — a stray
     * move after the run has ended is ignored by every repository anyway.
     */
    private var omit = 0

    private var aggregator: SearchAggregator? = null
    private var latest: AnalysisSnapshot? = null
    private var watchdog: Job? = null
    private var ticker: Job? = null

    init {
        scope.launch { engine.responses.collect { onResponse(it) } }
    }

    /** Tests only; the app keeps the singleton for the process's lifetime. */
    fun shutdown() {
        scope.cancel()
    }

    // ---- starting -----------------------------------------------------------

    override suspend fun start(options: ProveOptions): ProveStart = lock.withLock {
        guard()?.let { return@withLock ProveStart.Refused(it) }
        this.options = options.sanitized()
        rootMoves = game.position.value.moves
        size = game.position.value.size
        val tree = ProveTree(this.options, size) { note(it) }
        this.tree = tree
        phase = ProvePhase.IDLE
        current = -1
        searches = 0
        dbWrites = 0
        omit = 0
        _outcome.value = null
        _progress.value = ProveProgress(
            running = true, byDepth = this.options.byDepth, nodes = 1, open = 1,
        )
        // The engine is ours until finish(): a database save now would rewrite
        // the file this run is still adding records to.
        busy.acquire(EngineBusy.PROVE)
        _overlay.value = tree.overlay(null, rootMoves.size)

        // The run owns the engine's limits until it finishes (main.c:9964).
        engine.send(EngineCommand.Info("timeout_match", UNLIMITED_MATCH_MS.toString()))
        engine.send(EngineCommand.Info("max_node", "-1"))
        engine.send(EngineCommand.Info("max_depth", (size * size).toString()))
        note(
            "증명: ${if (rootMoves.size % 2 == 0) "흑" else "백"} 차례, " +
                "노드당 예산 ${this.options.label}, nbest ${this.options.nbest}",
        )
        startTicker()
        continueRun()
        ProveStart.Started
    }

    /** The desktop's four refusals (main.c:9867-9882). */
    private fun guard(): String? = when {
        _progress.value.running -> "이미 증명이 진행 중입니다"
        review.progress.value.running -> "게임 리뷰가 진행 중입니다 — 먼저 중지하세요"
        !engine.state.value.isLive -> "엔진에 연결되어 있지 않습니다"
        game.state.value.thinking -> "증명: 진행 중인 계산을 먼저 중지하세요"
        game.state.value.over -> "증명: 이미 승부가 결정된 국면입니다"
        !settings.useDatabase || settings.databaseReadonly ->
            "증명: 데이터베이스를 켜고 읽기 전용을 해제하세요"
        else -> null
    }

    override suspend fun cancel() = lock.withLock {
        if (!_progress.value.running) return@withLock
        finish(cancelled = true)
    }

    override fun clearOutcome() {
        _outcome.value = null
    }

    // ---- the schedule -------------------------------------------------------

    /** `prove_continue` (main.c:9567). */
    private suspend fun continueRun() {
        val tree = tree ?: return
        if (tree.root.state == ProveState.RESOLVED) {
            // Flush the remaining conclusions before finishing.
            val pending = tree.popWrite()
            if (pending != null) sendEdit(pending) else finish(cancelled = false)
            return
        }
        val next = tree.pickNext()
        if (next == null) {
            finish(cancelled = false)
            return
        }
        current = next
        pushOverlay()
        sendQuery(next)
    }

    /** `prove_flush_writes` (main.c:9505). */
    private suspend fun flushWrites() {
        val pending = tree?.popWrite()
        if (pending != null) sendEdit(pending) else continueRun()
    }

    // ---- engine conversation ------------------------------------------------

    private fun positionOf(node: ProveNode) = Position(size = size, moves = rootMoves + node.moves)

    /** `prove_send_query` (main.c:9435). */
    private suspend fun sendQuery(i: Int) {
        val tree = tree ?: return
        phase = ProvePhase.QUERY
        engine.send(DbQueryOne(positionOf(tree[i]).moves))
        armWatchdog(REPLY_TIMEOUT_SEC)
        pushProgress()
    }

    /**
     * `prove_send_edit` (main.c:9443). yixindb labels are **mover perspective**:
     * the side that moved *into* the position. So a node the side to move wins is
     * stored as `L` (the previous mover lost), and vice versa — getting this
     * backwards would fill the shared database with inverted mate records.
     */
    private suspend fun sendEdit(i: Int) {
        val tree = tree ?: return
        val n = tree[i]
        val winForStm = (n.result == ProveResult.WIN) == n.isOr
        val value = n.value.coerceIn(-30000, 30000)
        phase = ProvePhase.EDIT
        engine.send(
            DbEditRecord(
                mask = 7,
                tag = (if (winForStm) 'L' else 'W').code,
                value = value,
                depth = if (n.recDepth > 0) n.recDepth else 1,
                moves = positionOf(n).moves,
            ),
        )
        armWatchdog(REPLY_TIMEOUT_SEC)
        dbWrites++
        pushProgress()
    }

    /** `prove_send_search` (main.c:9463). */
    private suspend fun sendSearch(i: Int) {
        val tree = tree ?: return
        val n = tree[i]
        val budget = n.budget.coerceAtMost(options.maxBudget)
        phase = ProvePhase.SEARCH
        currentBudget = budget
        searchStartedAt = now()
        val position = positionOf(n)
        aggregator = SearchAggregator(position.sideToMove)
        latest = null
        engine.send(EngineCommand.Start(size))
        engine.send(EngineCommand.YxBoard(position.placements()))
        engine.send(EngineCommand.InfoTimeLeft(UNLIMITED_MATCH_MS))
        // Time mode's budget is its own leash; depth mode borrows the seconds
        // cap for one, because a depth alone does not bound an AND node:
        // `yxsearchdefend` evaluates *every* defense to max_depth, so its cost
        // is the number of defenses times one depth-N search, while an OR node's
        // k is capped at 8. With the clock open the defense step ran until the
        // watchdog — and the watchdog throws the node away (stop, retry,
        // exhausted after three) where a `timeout_turn` makes the engine hand
        // back the best it has (main.c:9484).
        val leash = if (options.byDepth) options.depthLeashMs else budget
        if (options.byDepth) {
            engine.send(EngineCommand.Info("timeout_turn", leash.toString()))
            engine.send(EngineCommand.Info("max_depth", budget.toString()))
        } else {
            engine.send(EngineCommand.Info("timeout_turn", budget.toString()))
        }
        if (n.isOr) {
            engine.send(EngineCommand.YxNbest(if (n.k > 0) n.k else options.nbest))
        } else {
            engine.send(EngineCommand.YxSearchDefend)
        }
        searches++
        // The watchdog sits above the leash in both modes: it answers a silent
        // engine, not a slow one.
        armWatchdog(leash / 1000 * 2 + 60)
        pushProgress()
    }

    private fun armWatchdog(seconds: Int) {
        watchdog?.cancel()
        val armedFor = phase
        watchdog = scope.launch {
            delay(seconds * 1000L)
            lock.withLock {
                if (!_progress.value.running || phase != armedFor) return@withLock
                onTimeout()
            }
        }
    }

    /** `prove_watchdog_fire` (main.c:9832). */
    private suspend fun onTimeout() {
        val tree = tree ?: return
        note("증명: 엔진 응답 없음 (단계 ${phase.name}), 다시 시도")
        when (phase) {
            ProvePhase.SEARCH -> {
                omit++
                runCatching { engine.send(EngineCommand.YxStop) }
                phase = ProvePhase.IDLE
                when (tree.onTimeout(current)) {
                    ProveStep.RETRY -> sendSearch(current)
                    else -> continueRun()
                }
            }
            ProvePhase.QUERY -> sendQuery(current)
            ProvePhase.EDIT -> {
                phase = ProvePhase.IDLE
                flushWrites() // the edit is idempotent; move on
            }
            ProvePhase.IDLE -> Unit
        }
    }

    /**
     * One deliberate difference from main.c: the desktop stops the watchdog and
     * clears `isthinking` *before* it checks the phase, so a stray best move
     * arriving during a query or an edit disarms the watchdog and the run stalls.
     * Here a response is only ever handled by the phase that expects it.
     */
    private suspend fun onResponse(response: EngineResponse) {
        if (!_progress.value.running) return
        lock.withLock {
            if (phase == ProvePhase.SEARCH) {
                aggregator?.consume(response)?.let {
                    latest = it
                    pushProgress()
                }
                if (response is EngineResponse.BestMove) onBestMove()
                return@withLock
            }
            if (response is EngineResponse.DbOne) onDbOne(response)
        }
    }

    /**
     * `prove_on_dbone` (main.c:9601). The same message acknowledges an edit and
     * answers a query, so the phase decides what it means.
     *
     * yixindb reports its labels in **lower case** while an edit writes them upper
     * case; main.c matches only the lower-case forms and treats anything else as
     * "no record", which just means one more search. Ported as it stands.
     */
    private suspend fun onDbOne(response: EngineResponse.DbOne) {
        val tree = tree ?: return
        if (phase == ProvePhase.EDIT) {
            watchdog?.cancel()
            phase = ProvePhase.IDLE
            flushWrites()
            return
        }
        if (phase != ProvePhase.QUERY || current < 0) return
        watchdog?.cancel()
        phase = ProvePhase.IDLE
        val isOr = tree[current].isOr
        val resolved = when (response.tag) {
            'w'.code -> if (isOr) ProveResult.NOWIN else ProveResult.WIN
            'l'.code -> if (isOr) ProveResult.WIN else ProveResult.NOWIN
            'd'.code -> ProveResult.NOWIN // a draw record refutes a win claim
            else -> null
        }
        if (resolved == null) {
            sendSearch(current)
            return
        }
        val note = if (response.tag == 'd'.code) "db draw" else "db"
        tree.resolve(current, resolved, ProveKind.DB, response.value, response.depth, note)
        flushWrites()
    }

    /** `prove_on_bestmove` (main.c:9756). */
    private suspend fun onBestMove() {
        if (omit > 0) {
            omit--
            return
        }
        val tree = tree ?: return
        watchdog?.cancel()
        watchdog = null
        phase = ProvePhase.IDLE
        val captured = capturedPvs()
        val step = tree.onSearchResult(current, captured.pvs, captured.complete)
        pushOverlay()
        when (step) {
            ProveStep.RESOLVED -> flushWrites()
            ProveStep.RETRY -> sendSearch(current)
            ProveStep.CONTINUE -> continueRun()
        }
    }

    /**
     * What a finished search actually established, and how much of it we can
     * read: the PVs of the **last deepening round** in engine index order, plus
     * whether that round arrived intact.
     *
     * Both halves matter to the conclusion. Reading the last round only keeps a
     * wider earlier round's leftovers from passing as this round's answers; and
     * a PV block that came back without a `BESTLINE` is a move the engine told
     * us about and we cannot place, so the set we hold is *smaller* than the set
     * that exists. That has to reach [ProveTree], because "every defence loses"
     * counts the set — dropping the unplaceable ones silently satisfies it with
     * an empty seat.
     */
    private data class Captured(val pvs: List<ProvePv>, val complete: Boolean)

    private fun capturedPvs(): Captured {
        val pvs = aggregator?.finalPvs().orEmpty()
        // main.c retries the search when PV 0 carries no move, however many
        // other PVs came back, so an unusable first PV empties the list here too.
        if (pvs.firstOrNull { it.index == 0 }?.head == null) return Captured(emptyList(), false)
        val usable = pvs.mapNotNull { pv ->
            val head = pv.head ?: return@mapNotNull null
            // The win rate stays nullable all the way in: a block with no
            // `INFO WINRATE` reported nothing, which is not the same as 0 %.
            ProvePv(move = head, winRate = pv.winRate, mate = pv.mate ?: 0, depth = pv.depth)
        }
        return Captured(usable, usable.size == pvs.size)
    }

    // ---- finishing ----------------------------------------------------------

    /** `prove_finish` (main.c:9514). */
    private suspend fun finish(cancelled: Boolean) {
        val tree = tree
        watchdog?.cancel()
        watchdog = null
        ticker?.cancel()
        ticker = null
        if (phase == ProvePhase.SEARCH) {
            runCatching { engine.send(EngineCommand.YxStop) }
            // Exactly one move arrives from this stop. An absolute value (not ++)
            // keeps it deterministic, as main.c's comment demands.
            omit = 1
        } else {
            omit = 0
        }
        phase = ProvePhase.IDLE
        aggregator = null
        latest = null
        _progress.value = ProveProgress(byDepth = options.byDepth)
        _overlay.value = ProveOverlay.EMPTY
        busy.release(EngineBusy.PROVE)
        runCatching { restoreLevel() }
        // Through the database repository, not straight at the engine: a save
        // writes the engine's whole in-memory database over the file, and that
        // is refused while a load is unfinished or the database is read-only.
        // A proof ending is no reason to skip either check.
        runCatching { databaseSaver.saveDatabase() }

        val root = tree?.root
        val outcome = ProveOutcome(
            cancelled = cancelled,
            resolved = root?.state == ProveState.RESOLVED,
            win = root?.result == ProveResult.WIN,
            kind = root?.kind ?: ProveKind.NONE,
            blackToMove = rootMoves.size % 2 == 0,
            searches = searches,
            conclusions = tree?.resolvedCount ?: 0,
            dbWrites = dbWrites,
            attackerWinRatePct = ((root?.wratt ?: 0.0) * 100).roundToInt(),
        )
        _outcome.value = outcome
        note("증명 ${if (cancelled) "취소" else "완료"}: ${outcome.message.replace('\n', ' ')}")
    }

    /** `set_level(levelchoice)` — put the user's limits back (main.c:9539). */
    private suspend fun restoreLevel() {
        settings.toEngineParams().levelPairs().forEach { (key, value) ->
            runCatching { engine.send(EngineCommand.Info(key, value)) }
        }
    }

    // ---- progress / overlay -------------------------------------------------

    /** `prove_pulse_tick` (main.c:9206): keeps the live badge ticking. */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(PULSE_MS)
                if (!_progress.value.running) break
                lock.withLock { pushProgress() }
            }
        }
    }

    private fun pushOverlay() {
        val tree = tree ?: return
        _overlay.value = tree.overlay(current.takeIf { it >= 0 }, rootMoves.size)
        pushProgress()
    }

    private fun pushProgress() {
        val tree = tree ?: return
        // The ticker can wake up while [finish] holds the lock; without this it
        // would resurrect a finished run's progress.
        if (!_progress.value.running) return
        val node = tree[current.coerceIn(0, tree.count - 1)]
        val parent = node.parent.takeIf { it >= 0 }?.let { tree[it] }
        val stats = latest?.stats
        val searching = phase == ProvePhase.SEARCH
        _progress.update {
            it.copy(
                running = true,
                phase = phase,
                resolved = tree.resolvedCount,
                searches = searches,
                dbWrites = dbWrites,
                open = tree.openCount(),
                nodes = tree.count,
                path = tree.path(if (current >= 0) current else 0),
                attack = node.isOr,
                budget = currentBudget,
                byDepth = options.byDepth,
                elapsedSec = if (searching) ((now() - searchStartedAt) / 1000).toInt() else 0,
                depth = stats?.depth ?: 0,
                mate = stats?.mate ?: 0,
                winRatePct = stats?.winRatePct ?: 0,
                candIndex = if (parent?.isOr == true && parent.alt.isNotEmpty()) parent.altNext + 1 else 0,
                candTotal = if (parent?.isOr == true && parent.alt.isNotEmpty()) parent.alt.size + 1 else 0,
            )
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun note(text: String) {
        _log.update { (it + text).takeLast(LOG_LIMIT) }
    }

    private companion object {
        const val UNLIMITED_MATCH_MS = 2_000_000_000L

        /** main.c arms 60 s on a query or an edit (main.c:9440). */
        const val REPLY_TIMEOUT_SEC = 60
        const val PULSE_MS = 500L
        const val LOG_LIMIT = 80
    }
}
