package dev.gomoku.zenyth.feature.board

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.zenyth.core.common.IncomingLinks
import dev.gomoku.zenyth.core.designsystem.component.BoardRender
import dev.gomoku.zenyth.core.designsystem.component.DbLabel
import dev.gomoku.zenyth.core.designsystem.component.TagPalette
import dev.gomoku.zenyth.core.i18n.AppLanguage
import dev.gomoku.zenyth.core.i18n.appLanguage
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.AnalysisSnapshot
import dev.gomoku.zenyth.core.model.AnalyzeParams
import dev.gomoku.zenyth.core.model.AppSettings
import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.BoardShift
import dev.gomoku.zenyth.core.model.BoardSymmetry
import dev.gomoku.zenyth.core.model.BoardTransform
import dev.gomoku.zenyth.core.model.ComputerSide
import dev.gomoku.zenyth.core.model.ConnectionState
import dev.gomoku.zenyth.core.model.DbCell
import dev.gomoku.zenyth.core.model.DbOpResult
import dev.gomoku.zenyth.core.model.DbState
import dev.gomoku.zenyth.core.model.FontSpec
import dev.gomoku.zenyth.core.model.FunctionScripts
import dev.gomoku.zenyth.core.model.GameFile
import dev.gomoku.zenyth.core.model.GameFileFormat
import dev.gomoku.zenyth.core.model.GameReport
import dev.gomoku.zenyth.core.model.GameState
import dev.gomoku.zenyth.core.model.GradingPreset
import dev.gomoku.zenyth.core.model.LngTable
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.MoveGrader
import dev.gomoku.zenyth.core.model.MoveQuality
import dev.gomoku.zenyth.core.model.OpeningEval
import dev.gomoku.zenyth.core.model.OpeningName
import dev.gomoku.zenyth.core.model.OpeningProtocol
import dev.gomoku.zenyth.core.model.OpeningTables
import dev.gomoku.zenyth.core.model.Position
import dev.gomoku.zenyth.core.model.PositionLink
import dev.gomoku.zenyth.core.model.PositionRecord
import dev.gomoku.zenyth.core.model.ProveOverlay
import dev.gomoku.zenyth.core.model.ProveProgress
import dev.gomoku.zenyth.core.model.ReviewProgress
import dev.gomoku.zenyth.core.model.StoneColor
import dev.gomoku.zenyth.core.model.Swap2Choice
import dev.gomoku.zenyth.core.model.TapResult
import dev.gomoku.zenyth.core.model.ToolsState
import dev.gomoku.zenyth.data.board.BoardImageIo
import dev.gomoku.zenyth.data.game.GameFileIo
import dev.gomoku.zenyth.data.prefs.UiPrefs
import dev.gomoku.zenyth.domain.engine.EngineResponse
import dev.gomoku.zenyth.domain.repository.AppearanceRepository
import dev.gomoku.zenyth.domain.repository.DatabaseRepository
import dev.gomoku.zenyth.domain.repository.EngineRepository
import dev.gomoku.zenyth.domain.repository.EngineToolsRepository
import dev.gomoku.zenyth.domain.repository.GameRepository
import dev.gomoku.zenyth.domain.repository.ProveRepository
import dev.gomoku.zenyth.domain.repository.ReviewRepository
import dev.gomoku.zenyth.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BoardViewModel @Inject constructor(
    private val repository: EngineRepository,
    private val settingsRepository: SettingsRepository,
    private val database: DatabaseRepository,
    private val game: GameRepository,
    private val review: ReviewRepository,
    private val prove: ProveRepository,
    private val tools: EngineToolsRepository,
    private val appearance: AppearanceRepository,
    private val imageIo: BoardImageIo,
    private val fileIo: GameFileIo,
    private val links: IncomingLinks,
    uiPrefs: UiPrefs,
) : ViewModel() {

    private val settings = settingsRepository.settings

    /** The board itself lives in [GameRepository] so a game survives this screen. */
    private val position = game.position

    /** An analysis and the moves of the position it read. */
    private data class Read(val moves: List<Move>, val snap: AnalysisSnapshot)

    /**
     * The last analysis — kept when the board moves on, so the engine card can
     * go on showing it as «직전 국면의 분석» (Zenyth Web, freeze after stop). The
     * board's overlays only ever use it while it is this position's.
     */
    private val reading = MutableStateFlow<Read?>(null)
    private val analyzing = MutableStateFlow(false)
    private val previewPv = MutableStateFlow<Int?>(null)

    /** The running search is `searchdefend`, not `nbest` (main.c:10883). */
    private val defending = MutableStateFlow(false)

    /** A balance search is running (desktop `balance1` / `balance2`). */
    private val balancing = MutableStateFlow(false)

    private var analyzeJob: Job? = null

    /**
     * The desktop's `isneedomit` (main.c:4557): the move a stopped search reports
     * is swallowed when the stop was made to change the board rather than to take
     * the move — the restart in [startAnalysis].
     */
    private var omitSettles = 0

    /** The user pressed stop; only then may the settled move be played. */
    private var stopRequested = false

    /** A search was started and has not reported its move yet. */
    private var searchLive = false

    /** Gives up waiting for a stop the engine never acknowledged. */
    private var stopFallbackJob: Job? = null

    private val analysisSecPref = uiPrefs.int("analysis.sec", 0)
    private val playBestPref = uiPrefs.bool("analysis.playBestOnStop", true)

    /** The board a link replaced, kept until the user keeps or drops it. */
    private val linkBackup = MutableStateFlow<List<Move>?>(null)

    /**
     * Per-ply evaluation history — the desktop's `wrhistory`/`wrmate`/`wrvalid`
     * triple (main.c:1215). Engine or database values both fill it.
     */
    private val records = MutableStateFlow<List<PositionRecord>>(emptyList())

    /** One-shot user feedback (a refused move or database write, mostly). */
    private val _notice = MutableStateFlow<String?>(null)

    private data class Panel(
        val connection: ConnectionState,
        val preview: Int?,
        val analyzing: Boolean,
        val forbidden: List<Move>,
        val db: DbState,
        val notice: String?,
        val future: List<Move>,
        val balancing: Boolean,
        val game: GameState,
        val report: GameReport?,
        val prove: ProveOverlay,
        val proveProgress: ProveProgress,
        val reviewProgress: ReviewProgress,
        val blocked: Set<Move>,
        val toolbar: List<FunctionScripts.ToolbarItem>,
        val language: LngTable,
        val defending: Boolean,
        val mode: BoardMode,
        val gameName: String?,
        val analysisSec: Int,
        val playBest: Boolean,
        val linkBackup: Boolean,
    )

    private val panel = combine(
        listOf(
            repository.state, previewPv, analyzing, game.forbidden, database.state, _notice,
            game.future, balancing, game.state, review.report, prove.overlay, prove.progress,
            tools.state, review.progress, appearance.toolbar, appearance.language, defending,
            game.mode, game.gameName, analysisSecPref.state, playBestPref.state, linkBackup,
        ),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        Panel(
            connection = values[0] as ConnectionState,
            preview = values[1] as Int?,
            analyzing = values[2] as Boolean,
            forbidden = values[3] as List<Move>,
            db = values[4] as DbState,
            notice = values[5] as String?,
            future = values[6] as List<Move>,
            balancing = values[7] as Boolean,
            game = values[8] as GameState,
            report = values[9] as GameReport?,
            prove = values[10] as ProveOverlay,
            proveProgress = values[11] as ProveProgress,
            blocked = (values[12] as ToolsState).blocked,
            reviewProgress = values[13] as ReviewProgress,
            toolbar = values[14] as List<FunctionScripts.ToolbarItem>,
            language = values[15] as LngTable,
            defending = values[16] as Boolean,
            mode = values[17] as BoardMode,
            gameName = values[18] as String?,
            analysisSec = values[19] as Int,
            playBest = values[20] as Boolean,
            linkBackup = values[21] != null,
        )
    }

    val uiState: StateFlow<BoardUiState> =
        combine(position, reading, panel, settings, records) { pos, read, p, config, history ->
            val current = read?.takeIf { it.moves == pos.moves }?.snap
            val hasData = read != null && (read.snap.pvs.isNotEmpty() || read.snap.stats.depth > 0)
            val stale = current == null && hasData
            val analysisTab = p.mode == BoardMode.ANALYSIS
            val quality = if (analysisTab) badgeFor(pos, p.report, config) else null
            BoardUiState(
                render = buildRender(pos, current, p, config, quality),
                moveCount = pos.moves.size,
                connection = p.connection,
                mode = p.mode,
                analyzing = p.analyzing,
                defending = p.defending,
                shown = current ?: read?.snap?.takeIf { stale },
                stale = stale,
                staleAt = if (stale) read.moves.size else 0,
                eval = if (analysisTab) evalFor(pos, read, p) else null,
                multiPv = config.multiPv,
                previewPv = p.preview,
                winRateHistory = if (config.showWrGraph) winRates(history) else emptyList(),
                analysisSec = p.analysisSec,
                playBestOnStop = p.playBest,
                showEvalBar = config.showEvalBar,
                showWrGraph = config.showWrGraph,
                showWarning = config.showWarning,
                boardZoomPercent = config.boardZoomPercent,
                openingNames = openingNames(NamesKey(pos.moves, pos.size, appLanguage(), OpeningTables.names)),
                openingGrade = openingGrade(GradeKey(pos.moves, pos.size, OpeningTables.evals)),
                moveGrade = quality?.takeIf { it != MoveQuality.NONE },
                gameName = p.gameName,
                linkBackup = p.linkBackup,
                db = p.db,
                dbValue = p.db.value,
                notice = p.notice,
                futureCount = p.future.size,
                balancing = p.balancing,
                positionString = positionString(BoardKey(pos.moves, pos.size)),
                game = p.game,
                moveTimeMs = GameLevels.moveTimeOf(config.level, config.timeoutTurnSec),
                strength = GameLevels.strengthOf(config.level, config.maxNode),
                sideToMove = pos.sideToMove,
                showForbidden = config.showForbidden,
                isRenju = config.isRenju,
                rule = config.rule,
                showClock = config.showClock,
                openingNeedsOddSize = config.openingNeedsOddSize,
                research = researchBanner(p),
                toolbar = toolbar(p.toolbar),
                language = p.language,
                toolbarStyle = config.toolbarStyle,
                dbCommentFont = fromSettings(config).dbCommentFont,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState())

    init {
        // The database reply is the desktop's *other* evaluation source
        // (`evalbar_update_from_database`, main.c:1750).
        database.state
            .map { it.value }
            .distinctUntilChanged()
            .onEach { v ->
                if (v != null) {
                    recordValue(position.value.moves.size, v.blackWinRate, v.blackMate ?: 0)
                }
            }
            .launchIn(viewModelScope)

        // main.c:13930 — a bare `y,x` on its own is the end of a search.
        repository.responses
            .filterIsInstance<EngineResponse.BestMove>()
            .onEach { onSearchSettled(it.moves) }
            .launchIn(viewModelScope)

        // The database follows the board: every position change re-queries it.
        viewModelScope.launch {
            position.collect { pos ->
                database.setPosition(pos)
                if (analyzing.value) startAnalysis(defending.value)
            }
        }

        // A Zenyth Web link the app was opened with.
        links.pending.filterNotNull().onEach { onOpenLink(it) }.launchIn(viewModelScope)
    }

    // ---- tabs --------------------------------------------------------------

    /**
     * Switch [분석] / [대국]. Analysis → game only stops the analysis (it does not
     * play the best move); game → analysis abandons the engine's pending move —
     * [GameRepository.setMode] does that half.
     */
    fun onMode(mode: BoardMode) {
        if (researchBusy() != null && mode == BoardMode.GAME) {
            _notice.value = researchBusy()
            return
        }
        if (analyzing.value) stopAnalysis()
        previewPv.value = null
        viewModelScope.launch { game.setMode(mode) }
    }

    /** «새 판»: the current tab's board starts over; colours and opening stay. */
    fun onNewBoard() = inGame {
        if (analyzing.value) stopAnalysis()
        records.value = emptyList()
        reading.value = null
        game.newGame(resetClock = true)
    }

    /** «진영 전환»: the engine takes the other colour. */
    fun onSwapSides() = inGame {
        val side = game.state.value.computerSide
        if (side == ComputerSide.BLACK || side == ComputerSide.WHITE) game.setComputerSide(side.swapped())
    }

    // ---- board -------------------------------------------------------------

    /**
     * A tap goes to the game: depending on the rule and whose turn it is it
     * places a stone, answers an opening step, or starts the engine's turn.
     *
     * One shortcut from Zenyth Web: when the engine plays Black on an empty board
     * of a free opening, its first move is the centre — it is placed at once
     * rather than searched for.
     */
    fun onTap(move: Move) {
        researchBusy()?.let {
            _notice.value = it
            return
        }
        previewPv.value = null
        viewModelScope.launch {
            if (centreFirstMove()) return@launch
            when (val result = game.tap(move)) {
                is TapResult.Rejected -> _notice.value = result.reason
                else -> Unit
            }
        }
    }

    /** Places the engine's first black stone on the centre. True when it did. */
    private suspend fun centreFirstMove(): Boolean {
        val pos = position.value
        val st = game.state.value
        if (game.mode.value != BoardMode.GAME || pos.moves.isNotEmpty()) return false
        if (!st.computerSide.plays(StoneColor.BLACK) || st.thinking || st.over) return false
        if (st.opening != OpeningProtocol.NONE) return false
        game.replaceLine(listOf(Move(pos.size / 2, pos.size / 2)))
        if (st.computerSide.plays(StoneColor.WHITE)) game.engineMove()
        return true
    }

    fun onUndo() = inGame { game.undo() }
    fun onRedo() = inGame { game.redo() }
    fun onFirst() = inGame { game.toStart() }
    fun onLast() = inGame { game.toEnd() }

    /** Discard the board (`clear`), clocks and opening protocol included. */
    fun onReset() = onNewBoard()

    fun onStartAnalyze() {
        if (analyzing.value) return
        researchBusy()?.let {
            _notice.value = it
            return
        }
        if (game.state.value.thinking) {
            _notice.value = tr("대국 착수를 계산하는 중입니다", "The engine is working on its game move", "引擎正在计算对局着法")
            return
        }
        startAnalysis()
    }

    /** `searchdefend` (main.c:10883): every move that still defends. */
    fun onSearchDefend() {
        researchBusy()?.let {
            _notice.value = it
            return
        }
        if (!repository.state.value.isLive) return
        if (game.state.value.thinking) {
            _notice.value = tr("대국 착수를 계산하는 중입니다", "The engine is working on its game move", "引擎正在计算对局着法")
            return
        }
        startAnalysis(defend = true)
    }

    /**
     * Stop. An analysis stops with `YXSTOP`, which makes the engine report its
     * best move; with «정지하면 최선수 두기» on, that move is played when it
     * lands ([onSearchSettled]). On an empty board the move is the centre and
     * nothing waits for the engine.
     */
    fun onStopAnalyze() {
        if (analyzing.value) {
            val pos = position.value
            if (pos.moves.isEmpty() && playBestPref.value) {
                stopAnalysis()
                viewModelScope.launch { game.replaceLine(listOf(Move(pos.size / 2, pos.size / 2))) }
                return
            }
            stopRequested = true
            viewModelScope.launch { runCatching { repository.stop() } }
            stopFallbackJob?.cancel()
            stopFallbackJob = viewModelScope.launch {
                kotlinx.coroutines.delay(STOP_REPLY_TIMEOUT_MS)
                if (analyzing.value) {
                    stopRequested = false
                    stopAnalysis()
                    runCatching { database.refresh() }
                }
            }
        }
        if (balancing.value) viewModelScope.launch { runCatching { repository.stop() } }
        if (game.state.value.thinking) viewModelScope.launch { game.stopThinking() }
    }

    /** «지금 두기»: the engine plays what it has found so far. */
    fun onMoveNow() {
        if (game.state.value.thinking) viewModelScope.launch { game.stopThinking() }
    }

    /**
     * The engine settled on a move. A search the user stopped plays it (when
     * that is switched on); one whose time ran out just ends, and its result
     * stays on the card.
     */
    private fun onSearchSettled(moves: List<Move>) {
        if (!analyzing.value || balancing.value || game.state.value.thinking) return
        searchLive = false
        if (omitSettles > 0) {
            omitSettles--
            return
        }
        val target = position.value
        val legal = moves.filter { it.isInside(target.size) && it !in target.moves }
        // An answer with no line behind it is not the end of a search but a
        // move Rapfi plays without searching: on an empty board it answers 7,7
        // (H8) at once. That move is played and the analysis ends there.
        val read = reading.value?.takeIf { it.moves == target.moves }?.snap
        val instant = read == null || read.pvs.isEmpty()
        if (!stopRequested && instant && legal.isNotEmpty()) {
            stopAnalysis()
            viewModelScope.launch { game.replaceLine(target.moves + legal.first()) }
            return
        }
        val play = stopRequested && playBestPref.value
        stopRequested = false
        stopAnalysis()
        viewModelScope.launch {
            if (!play || legal.isEmpty()) {
                runCatching { database.refresh() }
                return@launch
            }
            game.replaceLine(target.moves + legal.first())
        }
    }

    fun onSymmetry(symmetry: BoardSymmetry) {
        val pos = position.value
        if (pos.moves.isEmpty()) return
        applyLine(BoardTransform.symmetry(pos.moves, pos.size, symmetry))
    }

    fun onShift(direction: BoardShift) {
        val pos = position.value
        if (pos.moves.isEmpty()) return
        val shifted = BoardTransform.shift(pos.moves, pos.size, direction)
        if (shifted == null) {
            _notice.value = tr("판 밖으로 나가는 수가 있어 이동할 수 없습니다", "A stone would leave the board, so nothing was moved", "有棋子会移出棋盘，无法平移")
            return
        }
        applyLine(shifted)
    }

    private fun applyLine(moves: List<Move>) = inGame {
        previewPv.value = null
        records.value = emptyList()
        game.replaceLine(moves)
    }

    /** `putpos`: load a line from the desktop's clipboard format or a link. */
    fun onLoadPositionString(text: String) {
        val size = position.value.size
        val moves = PositionLink.fromText(text, size)
            ?: BoardTransform.fromPositionString(text.trim(), size).takeIf { it.isNotEmpty() }
        if (moves == null) {
            _notice.value = tr("국면 문자열을 읽을 수 없습니다", "That position string could not be read", "无法读取局面代码")
            return
        }
        applyLine(moves)
        _notice.value = tr("${moves.size}수를 불러왔습니다", "Loaded ${moves.size} moves", "已载入 ${moves.size} 手")
    }

    // ---- links and files ---------------------------------------------------

    /** The Zenyth Web link of this position. */
    fun linkText(): String = position.value.let { PositionLink.url(it.moves, it.size) }

    /**
     * Open a Zenyth Web link. If it would replace a board with stones on it, that
     * board is kept aside with «두던 판으로 돌아가기 · 버리기».
     */
    fun onOpenLink(text: String) {
        links.consume()
        val pos = position.value
        val moves = PositionLink.fromText(text, pos.size)
        if (moves == null) {
            _notice.value = tr("링크의 국면을 읽을 수 없습니다", "The link's position could not be read", "无法读取链接中的局面")
            return
        }
        if (pos.moves.isNotEmpty() && pos.moves != moves) linkBackup.value = pos.moves
        applyLine(moves)
        _notice.value = tr("링크의 국면을 열었습니다", "Opened the linked position", "已打开链接中的局面")
    }

    fun onRestoreLinkBackup() {
        val backup = linkBackup.value ?: return
        linkBackup.value = null
        applyLine(backup)
    }

    fun onDropLinkBackup() {
        linkBackup.value = null
    }

    fun onOpenFile(uri: Uri) {
        viewModelScope.launch {
            val bytes = fileIo.read(uri.toString())
            val name = fileIo.displayName(uri)
            val size = position.value.size
            val format = GameFileFormat.of(name)
            val moves = bytes?.let { b ->
                if (format != null) GameFile.parse(b, format, size)
                else GameFileFormat.entries.firstNotNullOfOrNull { GameFile.parse(b, it, size)?.takeIf { m -> m.isNotEmpty() } }
            }
            if (moves.isNullOrEmpty()) {
                _notice.value = tr("기보를 읽을 수 없습니다", "That game file could not be read", "无法读取棋谱")
                return@launch
            }
            researchBusy()?.let {
                _notice.value = it
                return@launch
            }
            records.value = emptyList()
            game.loadLine(moves, GameFile.baseName(name))
            _notice.value = tr("${moves.size}수를 열었습니다", "Opened ${moves.size} moves", "已打开 ${moves.size} 手")
        }
    }

    fun onSaveFile(uri: Uri, format: GameFileFormat) {
        viewModelScope.launch {
            val pos = position.value
            val bytes = when (format) {
                GameFileFormat.PSQ -> GameFile.writePsq(pos.moves, pos.size)
                else -> GameFile.writeSav(pos.moves, pos.size)
            }
            _notice.value = runCatching { fileIo.write(uri, bytes) }.fold(
                onSuccess = { tr("기보를 저장했습니다", "Game saved", "棋谱已保存") },
                onFailure = { e -> tr("저장 실패: ${e.message}", "Could not save: ${e.message}", "保存失败: ${e.message}") },
            )
        }
    }

    fun saveName(format: GameFileFormat): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
        return "rapfi_${position.value.moves.size}_$stamp.${format.extension}"
    }

    /** Balance search (`balance1` / `balance2`), played onto the board. */
    fun onBalance(two: Boolean, bias: Int) {
        if (balancing.value || !repository.state.value.isLive) return
        researchBusy()?.let {
            _notice.value = it
            return
        }
        if (analyzing.value) stopAnalysis()
        balancing.value = true
        val target = position.value
        viewModelScope.launch {
            val moves = runCatching { repository.balance(target, two, bias) }.getOrDefault(emptyList())
            balancing.value = false
            if (position.value != target) return@launch
            val legal = moves.filter { it.isInside(target.size) && it !in target.moves }
            if (legal.isEmpty()) {
                _notice.value = tr("균형점을 찾지 못했습니다", "No balancing move was found", "未找到平衡点")
                return@launch
            }
            game.replaceLine(target.moves + legal)
            _notice.value = tr("균형점: ", "Balance: ", "平衡点:") + legal.joinToString(" ") { it.label(target.size) }
        }
    }

    // ---- game --------------------------------------------------------------

    fun onComputerSide(side: ComputerSide) = inGame { game.setComputerSide(side) }

    /** "엔진 착수" — the desktop's `thinking start` when a game is on. */
    fun onEngineMove() = inGame {
        if (centreFirstMove()) return@inGame
        when (val result = game.engineMove()) {
            is TapResult.Rejected -> _notice.value = result.reason
            else -> Unit
        }
    }

    fun onOfferDraw() = inGame { game.offerDraw() }
    fun onResign() = inGame { game.resign() }
    fun onSwapAnswer(yes: Boolean) = inGame { game.answerSwap(yes) }
    fun onSwap2Answer(choice: Swap2Choice) = inGame { game.answerSwap2(choice) }
    fun onFifthCount(count: Int) = inGame { game.answerFifthCount(count) }
    fun onDismissPrompt() = game.dismissPrompt()

    /**
     * Engine move time (settings.txt line 7) on the desktop's custom level, or
     * the unlimited level 0 when both time and strength are open.
     */
    fun onMoveTime(ms: Long) = writeLevel(ms, uiState.value.strength)

    fun onStrength(level: Int) = writeLevel(uiState.value.moveTimeMs, level)

    private fun writeLevel(moveTimeMs: Long, strength: Int) {
        viewModelScope.launch {
            val nodes = GameLevels.all.firstOrNull { it.level == strength }?.maxNode
            val unlimited = moveTimeMs <= 0 && nodes == null
            settingsRepository.update {
                if (unlimited) {
                    it.copy(level = 0)
                } else {
                    it.copy(
                        level = 1,
                        timeoutTurnSec = if (moveTimeMs <= 0) GameLevels.UNLIMITED_TURN_SEC
                        else ((moveTimeMs + 500) / 1000).toInt().coerceAtLeast(1),
                        maxNode = nodes ?: GameLevels.NO_CAP,
                    )
                }
            }
        }
    }

    /** settings.txt line 3 — rule and opening; the desktop starts a new game. */
    fun onRule(rule: Int) {
        viewModelScope.launch {
            settingsRepository.set("rule", rule.toString())
            game.newGame(resetClock = true)
        }
    }

    fun onToggleForbidden() {
        val next = !settings.value.showForbidden
        viewModelScope.launch { settingsRepository.set("showForbidden", if (next) "1" else "0") }
    }

    fun onNotice(text: String) {
        _notice.value = text
    }

    fun onMultiPvChange(value: Int) {
        viewModelScope.launch {
            settingsRepository.set("multiPv", value.coerceIn(1, MULTI_PV_LIMIT).toString())
            if (analyzing.value) startAnalysis()
        }
    }

    fun onAnalysisSec(sec: Int) {
        analysisSecPref.set(sec.coerceAtLeast(0))
        if (analyzing.value) startAnalysis(defending.value)
    }

    fun onPlayBestOnStop(on: Boolean) = playBestPref.set(on)

    fun onPreviewPv(index: Int?) {
        previewPv.value = index
    }

    fun onSaveImage(uri: Uri, bytes: ByteArray) {
        viewModelScope.launch {
            _notice.value = runCatching { imageIo.write(uri, bytes) }.fold(
                onSuccess = { tr("보드 이미지를 저장했습니다", "Board image saved", "棋盘图片已保存") },
                onFailure = { e -> tr("이미지 저장 실패: ${e.message}", "Could not save the image: ${e.message}", "图片保存失败: ${e.message}") },
            )
        }
    }

    private fun startAnalysis(defend: Boolean = false) {
        // Only a search still running answers its cancellation with a move; one
        // that already settled would leave the count to swallow the next real reply.
        if (analyzing.value && searchLive) omitSettles++
        searchLive = true
        stopFallbackJob?.cancel()
        analyzeJob?.cancel()
        stopRequested = false
        analyzing.value = true
        defending.value = defend
        game.setAnalyzing(true)
        val target = position.value
        val ply = target.moves.size
        val limit = analysisSecPref.value * 1000L
        analyzeJob = viewModelScope.launch {
            repository.analyze(target, AnalyzeParams(settings.value.multiPv, defend, timeLimitMs = limit))
                .collect { snap ->
                    reading.value = Read(target.moves, snap)
                    snap.blackWinRate()?.let {
                        recordValue(ply, it, snap.blackMate() ?: 0, snap.best?.head, gapOf(snap))
                    }
                }
        }
    }

    private fun recordValue(
        ply: Int,
        blackRate: Double,
        blackMate: Int,
        best: Move? = null,
        gap: Double? = null,
    ) {
        val rate = when {
            blackMate > 0 -> 1.0
            blackMate < 0 -> 0.0
            else -> blackRate.coerceIn(0.0, 1.0)
        }
        records.update { current ->
            val previous = current.getOrNull(ply) ?: PositionRecord()
            val next = PositionRecord(
                blackWinRate = rate,
                blackMate = blackMate,
                best = best ?: previous.best,
                gap = gap ?: previous.gap,
            )
            // Most lines of a search leave this ply's value as it was; copying
            // the whole history to store it again made the same list, slower.
            if (ply < current.size && current[ply] == next) return@update current
            val out = current.toMutableList()
            while (out.size <= ply) out.add(PositionRecord())
            out[ply] = next
            out
        }
    }

    private fun gapOf(snap: AnalysisSnapshot): Double? {
        val sorted = snap.pvs.sortedBy { it.index }
        val first = sorted.getOrNull(0)?.winRate ?: return null
        val second = sorted.getOrNull(1)?.winRate ?: return null
        return (first - second).coerceAtLeast(0.0)
    }

    private fun stopAnalysis() {
        searchLive = false
        stopFallbackJob?.cancel()
        stopFallbackJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        analyzing.value = false
        defending.value = false
        game.setAnalyzing(false)
    }

    /**
     * The eval bar: this position's search, or one of the positions before it —
     * after a move the bar keeps the last value until a new one arrives, but a
     * value from *after* an undone move is never shown. Without a search, the
     * database's value for this position.
     */
    private fun evalFor(pos: Position, read: Read?, p: Panel): EvalView? {
        val snap = read?.takeIf { it.moves.size <= pos.moves.size && pos.moves.subList(0, it.moves.size) == it.moves }?.snap
        val rate = snap?.blackWinRate()
        val live = p.analyzing && rate != null
        val db = p.db.value
        if (!live && db != null && p.db.enabled) {
            return EvalView(db.blackWinRate * 100, mate = db.blackMate, fromDb = true)
        }
        if (snap == null || rate == null) return null
        if (pos.moves.isEmpty() && !p.analyzing) return null
        val mate = snap.blackMate()?.takeIf { it != 0 }
        val black = snap.sideToMove == StoneColor.BLACK
        val cp = snap.stats.evalCp?.let { if (black) it else -it }
        return EvalView(rate * 100, evalCp = cp, mate = mate)
    }

    private fun researchBanner(panel: Panel): ResearchBanner? = when {
        panel.proveProgress.running -> {
            val (first, second) = panel.proveProgress.badgeLines()
            ResearchBanner(first, second, isProve = true)
        }
        panel.reviewProgress.running -> ResearchBanner(
            tr("리뷰 ${panel.reviewProgress.index}/${panel.reviewProgress.total}", "Review ${panel.reviewProgress.index}/${panel.reviewProgress.total}", "复盘 ${panel.reviewProgress.index}/${panel.reviewProgress.total}"),
            progress = panel.reviewProgress.fraction.takeIf { panel.reviewProgress.total > 0 },
        )
        else -> null
    }

    fun onRunScript(script: String) {
        viewModelScope.launch { runCatching { tools.run(script) } }
    }

    fun onStopResearch() {
        viewModelScope.launch {
            if (prove.progress.value.running) prove.cancel() else review.cancel()
        }
    }

    /**
     * The board as drawn. The analysis tab shows the engine's overlay for this
     * position (and keeps it after a stop that left the board alone); the game
     * tab shows none of it — no values, no best-move ring, no line — because
     * there it would be coaching. Database values come before engine values on
     * the same point: a proven or hand-written record outranks an estimate.
     */
    private fun buildRender(
        pos: Position,
        snap: AnalysisSnapshot?,
        panel: Panel,
        config: AppSettings,
        quality: MoveQuality?,
    ): BoardRender {
        val analysisTab = panel.mode == BoardMode.ANALYSIS
        val shown = if (analysisTab) snap else null
        val pv = when {
            panel.preview != null -> shown?.pvs?.firstOrNull { it.index == panel.preview }?.line
            else -> shown?.best?.line?.ifEmpty { shown.realtimeLine }
        }.orEmpty()
        val ghosts = pv.filterNot { pos.moves.contains(it) }.take(GHOST_LIMIT)
        val bestMark = shown?.realtimeBest ?: shown?.best?.head
        val overlay = config.showAnalysis && shown != null
        val dbLabels = if (analysisTab && config.useDatabase && !panel.game.thinking) {
            dbLabelsOf(DbLabelsKey(panel.db.snapshot.cells, config.showBoardText))
        } else {
            emptyMap()
        }
        val tags = if (overlay && config.showAnalysisWinrate && panel.preview == null) {
            shown.tags.filterKeys { it !in dbLabels }
        } else {
            emptyMap()
        }
        return BoardRender(
            size = pos.size,
            stones = pos.moves,
            lastMove = pos.moves.lastOrNull(),
            forbidden = panel.forbidden,
            ghosts = ghosts,
            bestMark = if (bestMark != null && !pos.moves.contains(bestMark)) bestMark else null,
            tags = tags,
            candidates = if (overlay && panel.analyzing) shown.candidates else emptyMap(),
            loseCells = if (overlay && panel.analyzing) shown.loseCells else emptySet(),
            dbLabels = dbLabels,
            badges = if (config.showMoveBadge && quality != null && quality != MoveQuality.NONE && pos.moves.isNotEmpty()) {
                mapOf(pos.moves.last() to quality)
            } else {
                emptyMap()
            },
            prove = panel.prove.takeIf { it.active },
            blocked = panel.blocked,
            showNumbers = config.showNumber,
            textScale = fromSettings(config).textScale,
            palette = fromSettings(config).palette,
        )
    }

    /**
     * The grade of the **current move only** — main.c:2114 grades every move but
     * paints just `bn == piecenum`. Live values (engine or database) and a review
     * report whose line still matches the board feed one record list.
     */
    private fun badgeFor(pos: Position, report: GameReport?, config: AppSettings): MoveQuality? {
        if (pos.moves.isEmpty()) return null
        return badge(BadgeKey(pos.moves, pos.size, report, records.value, config.mqPreset, config.skipOpening))
    }

    // ---- derived values, remembered -----------------------------------------
    //
    // `uiState` is rebuilt for every engine line that moves the reading - dozens
    // a second while a search streams - yet most of what goes into it follows
    // the board, the settings or the review, not the reading. Each value below
    // is recomputed only when one of its inputs changed. A key holds every
    // input, the language `tr()` reads and the opening tables a user can replace
    // at run time included, so a remembered value is exactly what computing it
    // again would give. Only the `uiState` upstream uses them, one emission at
    // a time.

    private class Memo<K, V>(private val compute: (K) -> V) {
        private var key: Any? = Unset
        private var value: Any? = null

        @Suppress("UNCHECKED_CAST")
        operator fun invoke(key: K): V {
            if (this.key != key) {
                value = compute(key)
                this.key = key
            }
            return value as V
        }

        private object Unset
    }

    private data class BoardKey(val moves: List<Move>, val size: Int)

    private data class NamesKey(
        val moves: List<Move>,
        val size: Int,
        val language: AppLanguage,
        val names: Map<String, OpeningTables.Name>,
    )

    private data class GradeKey(val moves: List<Move>, val size: Int, val evals: Map<String, Int>)

    private data class BadgeKey(
        val moves: List<Move>,
        val size: Int,
        val report: GameReport?,
        val live: List<PositionRecord>,
        val preset: Int,
        val skipOpening: Boolean,
    )

    private data class DbLabelsKey(val cells: Map<Move, DbCell>, val showBoardText: Boolean)

    /** What the settings alone decide about the board's drawing. */
    private class SettingsParts(val palette: TagPalette, val textScale: Float, val dbCommentFont: FontSpec)

    private val openingNames = Memo<NamesKey, List<String>> { k ->
        (1..minOf(OpeningName.MAX_PLY, k.moves.size)).mapNotNull { OpeningName.nameAt(it, k.moves, k.size) }
    }

    private val openingGrade = Memo<GradeKey, OpeningEval.Grade?> { k -> OpeningName.gradeAt(k.moves, k.size) }

    private val positionString = Memo<BoardKey, String> { k -> BoardTransform.toPositionString(k.moves, k.size) }

    private val winRates = Memo<List<PositionRecord>, List<Double?>> { history -> history.map { it.blackWinRate } }

    private val toolbar = Memo<List<FunctionScripts.ToolbarItem>, List<FunctionScripts.ToolbarItem>> { items ->
        items.filterNot { FunctionScripts.isBoardRowDuplicate(it.script) }
    }

    private val dbLabelsOf = Memo<DbLabelsKey, Map<Move, DbLabel>> { k ->
        k.cells.mapNotNull { (move, cell) ->
            val text = cell.display(k.showBoardText)
            if (text.isEmpty()) null else move to DbLabel(text, cell.kindOf())
        }.toMap()
    }

    private val fromSettings = Memo<AppSettings, SettingsParts> { config ->
        SettingsParts(
            palette = TagPalette(
                losingSaturation = config.lossSaturation,
                winningSaturation = config.winSaturation,
                minRateSaturation = config.minSaturation,
                maxRateSaturation = config.maxSaturation,
                value = config.colorValue,
            ),
            textScale = FontSpec.parse(config.boardTextFont).scale,
            dbCommentFont = FontSpec.parse(config.dbCommentFont),
        )
    }

    private val badge = Memo<BadgeKey, MoveQuality?> { k ->
        val ply = k.moves.size
        val fromReport = k.report?.data?.takeIf { it.matchesPrefix(k.moves) }?.records.orEmpty()
        val merged = (0..ply).map { i ->
            fromReport.getOrNull(i)?.takeIf { it.recorded } ?: k.live.getOrNull(i) ?: PositionRecord()
        }
        MoveGrader.currentBadge(
            moves = k.moves,
            size = k.size,
            records = merged,
            preset = GradingPreset.of(k.preset),
            skipOpening = k.skipOpening,
        )
    }

    // ---- database actions (P7) ---------------------------------------------

    /** Long press on an empty point: the cell label. Everything else of the database is in the DB tab. */
    fun onCellLabel(cell: Move, label: String) = runDb { database.editCellLabel(cell, label) }

    fun onNoticeShown() {
        _notice.value = null
    }

    private fun runDb(block: suspend () -> DbOpResult) {
        viewModelScope.launch {
            when (val result = block()) {
                is DbOpResult.Refused -> _notice.value = result.reason
                DbOpResult.Sent -> Unit
            }
        }
    }

    /**
     * Board actions are locked while a review or a proof runs — main.c returns
     * early from the board click and from every navigation callback while
     * `reviewactive || proveactive` (main.c:2661, 4524).
     */
    private fun researchBusy(): String? = when {
        review.progress.value.running -> tr("게임 리뷰가 진행 중입니다. 먼저 중지하세요", "A game review is running. Stop it first", "正在进行对局复盘，请先停止")
        prove.progress.value.running -> tr("국면 증명이 진행 중입니다. 먼저 중지하세요", "A proof is running. Stop it first", "正在进行局面证明，请先停止")
        else -> null
    }

    private fun inGame(block: suspend () -> Unit) {
        researchBusy()?.let {
            _notice.value = it
            return
        }
        viewModelScope.launch { block() }
    }

    private companion object {
        const val GHOST_LIMIT = 8
        const val MULTI_PV_LIMIT = 8
        const val STOP_REPLY_TIMEOUT_MS = 5_000L
    }
}
