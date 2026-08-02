package dev.gomoku.yixindroid.feature.board

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.yixindroid.core.designsystem.component.BoardRender
import dev.gomoku.yixindroid.core.designsystem.component.DbLabel
import dev.gomoku.yixindroid.core.designsystem.component.TagPalette
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AnalyzeParams
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.BoardShift
import dev.gomoku.yixindroid.core.model.BoardSymmetry
import dev.gomoku.yixindroid.core.model.BoardTransform
import dev.gomoku.yixindroid.core.model.ComputerSide
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.DbOpResult
import dev.gomoku.yixindroid.core.model.DbState
import dev.gomoku.yixindroid.core.model.GameReport
import dev.gomoku.yixindroid.core.model.FontSpec
import dev.gomoku.yixindroid.core.model.FunctionScripts
import dev.gomoku.yixindroid.core.model.GameState
import dev.gomoku.yixindroid.core.model.GradingPreset
import dev.gomoku.yixindroid.core.model.LngTable
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.MoveGrader
import dev.gomoku.yixindroid.core.model.MoveQuality
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.PositionRecord
import dev.gomoku.yixindroid.core.model.ReviewData
import dev.gomoku.yixindroid.core.model.ReviewProgress
import dev.gomoku.yixindroid.core.model.ProveOverlay
import dev.gomoku.yixindroid.core.model.ProveProgress
import dev.gomoku.yixindroid.core.model.Swap2Choice
import dev.gomoku.yixindroid.core.model.TapResult
import dev.gomoku.yixindroid.data.board.BoardImageIo
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.repository.AppearanceRepository
import dev.gomoku.yixindroid.domain.repository.DatabaseRepository
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.GameRepository
import dev.gomoku.yixindroid.core.model.ToolsState
import dev.gomoku.yixindroid.domain.repository.EngineToolsRepository
import dev.gomoku.yixindroid.domain.repository.ProveRepository
import dev.gomoku.yixindroid.domain.repository.ReviewRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
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
) : ViewModel() {

    private val settings = settingsRepository.settings

    /** The board itself lives in [GameRepository] so a game survives this screen. */
    private val position = game.position

    private val snapshot = MutableStateFlow<AnalysisSnapshot?>(null)
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
     * the move. Here that is the restart in [startAnalysis] — the old search's
     * reply lands while the new one is already running.
     */
    private var omitSettles = 0

    /** Gives up waiting for a stop the engine never acknowledged. */
    private var stopFallbackJob: Job? = null

    /** Black-perspective win rate per ply, for the win-rate graph. */
    /**
     * Per-ply evaluation history — the desktop's `wrhistory`/`wrmate`/`wrvalid`
     * triple (main.c:1215). **Any** value fills it in, engine or database
     * (`evalbar_set_black_winrate` has exactly those two callers), and that is
     * what lets the current move carry a grade badge without a review.
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
        /** Points the engine has been told to ignore (P10 `block`). */
        val blocked: Set<Move>,
        /** The user's own toolbar (`function/toolbar*.txt`) and its labels. */
        val toolbar: List<FunctionScripts.ToolbarItem>,
        val language: LngTable,
        /** The running search enumerates defenses (`searchdefend`). */
        val defending: Boolean,
    )

    private val panel = combine(
        repository.state, previewPv, analyzing, game.forbidden, database.state, _notice,
        game.future, balancing, game.state, review.report, prove.overlay, prove.progress,
        tools.state, review.progress, appearance.toolbar, appearance.language, defending,
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
        )
    }

    val uiState: StateFlow<BoardUiState> =
        combine(position, snapshot, panel, settings, records) {
                pos, snap, p, config, history ->
            BoardUiState(
                render = buildRender(pos, snap, p, config),
                moveCount = pos.moves.size,
                connection = p.connection,
                analyzing = p.analyzing,
                defending = p.defending,
                snapshot = snap,
                multiPv = config.multiPv,
                previewPv = p.preview,
                winRateHistory =
                    if (config.showWrGraph) history.map { it.blackWinRate } else emptyList(),
                showEvalBar = config.showEvalBar,
                showWrGraph = config.showWrGraph,
                showWarning = config.showWarning,
                boardZoomPercent = config.boardZoomPercent,
                db = p.db,
                dbValue = p.db.value,
                notice = p.notice,
                futureCount = p.future.size,
                balancing = p.balancing,
                positionString = BoardTransform.toPositionString(pos.moves, pos.size),
                game = p.game,
                sideToMove = pos.sideToMove,
                showForbidden = config.showForbidden,
                isRenju = config.isRenju,
                showClock = config.showClock,
                openingNeedsOddSize = config.openingNeedsOddSize,
                research = researchBanner(p),
                // Only the buttons the board row does not already have. With the
                // desktop defaults that is none of them, so no strip appears.
                toolbar = p.toolbar.filterNot { FunctionScripts.isBoardRowDuplicate(it.script) },
                language = p.language,
                toolbarStyle = config.toolbarStyle,
                toolbarPos = config.toolbarPos,
                dbCommentFont = FontSpec.parse(config.dbCommentFont),
                dbCommentEditFont = FontSpec.parse(config.dbCommentFont2),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState())

    init {
        // The database reply is the desktop's *other* evaluation source
        // (`evalbar_update_from_database`, main.c:1750). Without it a position
        // that is only ever looked up, never searched, would stay ungraded.
        database.state
            .map { it.value }
            .distinctUntilChanged()
            .onEach { v ->
                if (v != null) {
                    recordValue(position.value.moves.size, v.blackWinRate, v.blackMate ?: 0)
                }
            }
            .launchIn(viewModelScope)

        // main.c:13930 — a bare `y,x` on its own is the end of a search: the
        // engine reports the move it settled on. The desktop plays that move and
        // re-reads the database (main.c:13957-13961); the only replies it throws
        // away are the ones it asked for while rearranging the board.
        repository.responses
            .filterIsInstance<EngineResponse.BestMove>()
            .onEach { onSearchSettled(it.moves) }
            .launchIn(viewModelScope)

        // The database follows the board: every position change re-queries it,
        // exactly like the desktop's show_database() call after each move.
        viewModelScope.launch {
            position.collect { pos ->
                database.setPosition(pos)
                snapshot.value = null
                // Restart in whichever mode was running: someone stepping through
                // a line with `searchdefend` on wants the defenses for the new
                // position too, not a sudden switch back to the best moves.
                if (analyzing.value) startAnalysis(defending.value)
            }
        }
    }

    // ---- board -------------------------------------------------------------

    /**
     * A tap goes to the game: depending on the rule and whose turn it is it
     * places a stone, answers an opening step, or starts the engine's turn.
     */
    fun onTap(move: Move) {
        researchBusy()?.let {
            _notice.value = it
            return
        }
        previewPv.value = null
        viewModelScope.launch {
            when (val result = game.tap(move)) {
                is TapResult.Rejected -> _notice.value = result.reason
                else -> Unit
            }
        }
    }

    /** One move back (`undo one`), remembering it for redo. */
    fun onUndo() = inGame { game.undo() }

    /** One move forward along the remembered line (`redo one`). */
    fun onRedo() = inGame { game.redo() }

    /** Back to the empty board without losing the line (`undo all`). */
    fun onFirst() = inGame { game.toStart() }

    /** Forward to the end of the line (`redo all`). */
    fun onLast() = inGame { game.toEnd() }

    /** Discard the game (`clear`), clocks and opening protocol included. */
    fun onReset() = inGame {
        records.value = emptyList()
        game.newGame(resetClock = true)
    }

    fun onStartAnalyze() {
        if (analyzing.value) return
        researchBusy()?.let {
            _notice.value = it
            return
        }
        if (game.state.value.thinking) {
            _notice.value = tr("대국 착수를 계산하는 중입니다", "The engine is working on its game move")
            return
        }
        startAnalysis()
    }

    /**
     * `searchdefend` (main.c:10883): instead of the k best moves, every move that
     * still defends. It is an analysis like any other — same replies, same tags,
     * same stop button — so it runs through the same path with one flag.
     */
    fun onSearchDefend() {
        researchBusy()?.let {
            _notice.value = it
            return
        }
        if (!repository.state.value.isLive) return
        if (game.state.value.thinking) {
            _notice.value = tr("대국 착수를 계산하는 중입니다", "The engine is working on its game move")
            return
        }
        startAnalysis(defend = true)
    }

    /**
     * Stop button: ends whichever search is running — analysis, a balance search
     * or the engine's game turn. All three stop with `YXSTOP`, which makes the
     * engine report the best move it has found so far.
     *
     * The analysis is **not** torn down here. `YXSTOP` is a request, and the
     * search is over only when the engine answers it with that move; ending the
     * flow first would throw the answer away, which is why stopping used to leave
     * the board exactly as it was. [onSearchSettled] does the rest when it lands.
     */
    fun onStopAnalyze() {
        if (analyzing.value) {
            viewModelScope.launch { runCatching { repository.stop() } }
            stopFallbackJob?.cancel()
            stopFallbackJob = viewModelScope.launch {
                // A stop the engine never acknowledges (a dropped link, mostly)
                // must not leave the UI searching forever.
                kotlinx.coroutines.delay(STOP_REPLY_TIMEOUT_MS)
                if (analyzing.value) {
                    stopAnalysis()
                    runCatching { database.refresh() }
                }
            }
        }
        if (balancing.value) viewModelScope.launch { runCatching { repository.stop() } }
        if (game.state.value.thinking) viewModelScope.launch { game.stopThinking() }
    }

    /**
     * The engine settled on a move — a search ended, whether the user stopped it
     * or its own budget ran out. The desktop plays that move onto the board and
     * re-reads the database for the new position (main.c:13957-13961).
     *
     * Only an analysis of ours is claimed here: a game turn belongs to
     * [GameRepository] (which gates on its own `thinking`), a balance search to
     * the call that is awaiting it, and a review or proof to its run.
     */
    private fun onSearchSettled(moves: List<Move>) {
        if (!analyzing.value || balancing.value || game.state.value.thinking) return
        // Checked after the ownership guard, so a reply that was never ours —
        // a review's, a proof's — cannot spend the count meant for our own.
        if (omitSettles > 0) {
            omitSettles--
            return
        }
        stopAnalysis()
        val target = position.value
        // The engine may answer with a point that is already occupied when the
        // board moved on under a late reply; the desktop's `is_legal_move` guard
        // covers the same case.
        val legal = moves.filter { it.isInside(target.size) && it !in target.moves }
        viewModelScope.launch {
            if (legal.isEmpty()) {
                // Nothing to play, but the search may well have written to the
                // database — the values on the board have to be re-read either way.
                runCatching { database.refresh() }
                return@launch
            }
            game.replaceLine(target.moves + legal)
            val played = legal.joinToString(" ") { it.label(target.size) }
            _notice.value = tr("탐색 종료 · $played 착수", "Search over · played $played")
        }
    }

    /**
     * `rotate` / `flip`: transform the whole shape. The desktop replays the
     * transformed path from an empty board, so colours and numbering survive and
     * the redo tail is dropped (main.c:10194-10266).
     */
    fun onSymmetry(symmetry: BoardSymmetry) {
        val pos = position.value
        if (pos.moves.isEmpty()) return
        applyLine(BoardTransform.symmetry(pos.moves, pos.size, symmetry))
    }

    /**
     * `move [^,v,<,>]`: shift every stone one point. The desktop refuses the
     * whole shift if any stone would leave the board (main.c:10304) rather than
     * clipping the shape.
     */
    fun onShift(direction: BoardShift) {
        val pos = position.value
        if (pos.moves.isEmpty()) return
        val shifted = BoardTransform.shift(pos.moves, pos.size, direction)
        if (shifted == null) {
            _notice.value = tr("판 밖으로 나가는 수가 있어 이동할 수 없습니다", "A stone would leave the board, so nothing was moved")
            return
        }
        applyLine(shifted)
    }

    /** Replace the position with [moves] (transform result / pasted line). */
    private fun applyLine(moves: List<Move>) = inGame {
        previewPv.value = null
        records.value = emptyList()
        game.replaceLine(moves)
    }

    /** `putpos`: load a line from the desktop's clipboard format ("h8i9…"). */
    fun onLoadPositionString(text: String) {
        val size = position.value.size
        val moves = BoardTransform.fromPositionString(text.trim(), size)
        if (moves.isEmpty()) {
            _notice.value = tr("국면 문자열을 읽을 수 없습니다", "That position string could not be read")
            return
        }
        applyLine(moves)
        _notice.value = tr("${moves.size}수를 불러왔습니다", "Loaded ${moves.size} moves")
    }

    /**
     * Balance search (`balance1` / `balance2`). The desktop plays the answer onto
     * the board — one move, or both moves of a pair — so this does the same.
     */
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
            // The board may have moved on while the engine was thinking; only a
            // still-current position may be played onto (the desktop swallows a
            // late reply through `isneedomit`, main.c:4538).
            if (position.value != target) return@launch
            val legal = moves.filter { it.isInside(target.size) && it !in target.moves }
            if (legal.isEmpty()) {
                _notice.value = tr("균형점을 찾지 못했습니다", "No balancing move was found")
                return@launch
            }
            game.replaceLine(target.moves + legal)
            _notice.value = tr("균형점: ", "Balance: ") +
                legal.joinToString(" ") { it.label(target.size) }
        }
    }

    // ---- game (P5) ---------------------------------------------------------

    /** Which colours the engine plays (settings.txt lines 4-5). */
    fun onComputerSide(side: ComputerSide) = inGame { game.setComputerSide(side) }

    /** "엔진 착수" — the desktop's `thinking start` when a game is on. */
    fun onEngineMove() = inGame {
        when (val result = game.engineMove()) {
            is TapResult.Rejected -> _notice.value = result.reason
            else -> Unit
        }
    }

    fun onNewGame() = onReset()

    fun onOfferDraw() = inGame { game.offerDraw() }

    fun onResign() = inGame { game.resign() }

    fun onSwapAnswer(yes: Boolean) = inGame { game.answerSwap(yes) }

    fun onSwap2Answer(choice: Swap2Choice) = inGame { game.answerSwap2(choice) }

    fun onFifthCount(count: Int) = inGame { game.answerFifthCount(count) }

    fun onDismissPrompt() = game.dismissPrompt()

    /** Forbidden point display (settings.txt line 30), toggled from the board. */
    fun onToggleForbidden() {
        val next = !settings.value.showForbidden
        viewModelScope.launch { settingsRepository.set("showForbidden", if (next) "1" else "0") }
    }

    /** Feedback from the screen (image export result, copied position, …). */
    fun onNotice(text: String) {
        _notice.value = text
    }

    /** The stepper writes settings.txt line 20, so the choice survives restarts. */
    fun onMultiPvChange(value: Int) {
        viewModelScope.launch {
            settingsRepository.set("multiPv", value.coerceIn(1, MULTI_PV_LIMIT).toString())
            if (analyzing.value) startAnalysis()
        }
    }

    fun onPreviewPv(index: Int?) {
        previewPv.value = index
    }

    /**
     * Write the current board as a PNG to a location the user picked. The bytes
     * are rendered by the screen (it owns the Compose density) and only stored
     * here, next to the other one-shot feedback.
     */
    fun onSaveImage(uri: Uri, bytes: ByteArray) {
        viewModelScope.launch {
            _notice.value = runCatching { imageIo.write(uri, bytes) }.fold(
                onSuccess = { tr("보드 이미지를 저장했습니다", "Board image saved") },
                onFailure = { e -> tr("이미지 저장 실패: ${e.message}", "Could not save the image: ${e.message}") },
            )
        }
    }

    private fun startAnalysis(defend: Boolean = false) {
        // Restarting on a new position cancels the old search, and the engine
        // answers that cancel with the move it had — for the position the user
        // has already left. That reply is the desktop's `isneedomit` case.
        if (analyzing.value) omitSettles++
        stopFallbackJob?.cancel()
        analyzeJob?.cancel()
        snapshot.value = null
        analyzing.value = true
        defending.value = defend
        // The game's forbidden refresh must not push a board mid-search.
        game.setAnalyzing(true)
        val target = position.value
        val ply = target.moves.size
        analyzeJob = viewModelScope.launch {
            repository.analyze(target, AnalyzeParams(settings.value.multiPv, defend)).collect { snap ->
                snapshot.value = snap
                snap.blackWinRate()?.let {
                    recordValue(ply, it, snap.blackMate() ?: 0, snap.best?.head, gapOf(snap))
                }
            }
        }
    }

    /**
     * Record one position's evaluation, the desktop's `evalbar_set_black_winrate`
     * (main.c:1642). A mate pins the win rate to 0/1 exactly as it does there.
     */
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
            val out = current.toMutableList()
            while (out.size <= ply) out.add(PositionRecord())
            val previous = out[ply]
            out[ply] = PositionRecord(
                blackWinRate = rate,
                blackMate = blackMate,
                // The engine's own best/gap survive a later database reply, which
                // carries neither — losing them would drop Brilliant/Great.
                best = best ?: previous.best,
                gap = gap ?: previous.gap,
            )
            out
        }
    }

    /** Best-vs-second win-rate spread, the "only move" signal (`reviewgap`). */
    private fun gapOf(snap: AnalysisSnapshot): Double? {
        val sorted = snap.pvs.sortedBy { it.index }
        val first = sorted.getOrNull(0)?.winRate ?: return null
        val second = sorted.getOrNull(1)?.winRate ?: return null
        return (first - second).coerceAtLeast(0.0)
    }

    private fun stopAnalysis() {
        stopFallbackJob?.cancel()
        stopFallbackJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        analyzing.value = false
        defending.value = false
        game.setAnalyzing(false)
    }

    /**
     * The badge painted on the board while a research run holds the engine —
     * `prove_badge_lines` verbatim for a proof (totals on top, the live search
     * below), and the review's own counter (main.c:6864) otherwise.
     */
    private fun researchBanner(panel: Panel): ResearchBanner? = when {
        panel.proveProgress.running -> {
            val (first, second) = panel.proveProgress.badgeLines()
            ResearchBanner(first, second, isProve = true)
        }
        panel.reviewProgress.running -> ResearchBanner(
            tr("리뷰 ${panel.reviewProgress.index}/${panel.reviewProgress.total}", "Review ${panel.reviewProgress.index}/${panel.reviewProgress.total}"),
            progress = panel.reviewProgress.fraction.takeIf { panel.reviewProgress.total > 0 },
        )
        else -> null
    }

    /**
     * Run a user toolbar button or hotkey. It is the same console script the
     * desktop hands to `custom_function` (main.c:10064), so it goes to the same
     * interpreter the log input line uses — no second code path for buttons.
     */
    fun onRunScript(script: String) {
        viewModelScope.launch { runCatching { tools.run(script) } }
    }

    fun onStopResearch() {
        viewModelScope.launch {
            if (prove.progress.value.running) prove.cancel() else review.cancel()
        }
    }

    private fun buildRender(
        pos: Position,
        snap: AnalysisSnapshot?,
        panel: Panel,
        config: AppSettings,
    ): BoardRender {
        val pv = when {
            panel.preview != null -> snap?.pvs?.firstOrNull { it.index == panel.preview }?.line
            else -> snap?.best?.line?.ifEmpty { snap.realtimeLine }
        }.orEmpty()
        val ghosts = pv.filterNot { pos.moves.contains(it) }.take(GHOST_LIMIT)
        val bestMark = snap?.realtimeBest ?: snap?.best?.head
        // main.c only records tags while `showanalysiswinrate` is on, and draws no
        // analysis overlay at all with `showanalysis` off. While previewing one PV
        // the tags would fight the ghosts for space, so they are hidden there too.
        //
        // **Only while a search runs** (main.c:1901 `showanalysis && isthinking`).
        // The desktop keeps one array for both, so when a search ends
        // `show_database()` writes the stored values straight over the analysis
        // percentages; here they are two maps and the analysis one won, which is
        // why database values stayed hidden on exactly the points that had just
        // been analysed.
        val overlay = config.showAnalysis && panel.analyzing
        val tags = if (overlay && config.showAnalysisWinrate && panel.preview == null) {
            snap?.tags.orEmpty()
        } else {
            emptyMap()
        }
        // yixindb labels: the desktop draws them only while the engine is idle
        // (main.c:1913 `f == 0 && usedatabase && isthinking == 0`), preferring the
        // free-form board text over the stored value when that toggle is on.
        val dbLabels = if (config.useDatabase && !panel.analyzing && !panel.game.thinking) {
            panel.db.snapshot.cells.mapNotNull { (move, cell) ->
                val text = cell.display(config.showBoardText)
                if (text.isEmpty()) null else move to DbLabel(text, cell.kindOf())
            }.toMap()
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
            candidates = if (overlay) snap?.candidates.orEmpty() else emptyMap(),
            loseCells = if (overlay) snap?.loseCells.orEmpty() else emptySet(),
            dbLabels = dbLabels,
            // Review grades, only for the stones actually on the board and only
            // while the line still matches the reviewed one (main.c re-grades on
            // every board refresh; here the report is the source of truth).
            badges = if (config.showMoveBadge) badgeFor(pos, panel.report, config) else emptyMap(),
            // Prove overlay: ghost stones of the line under search plus a status
            // marker on every root candidate (main.c:9061 `prove_cell_pixbuf`).
            prove = panel.prove.takeIf { it.active },
            blocked = panel.blocked,
            showNumbers = config.showNumber,
            // settings.txt line 44 "Board Text Font": the PC families are not
            // here, but the size the user chose is what they were adjusting.
            textScale = FontSpec.parse(config.boardTextFont).scale,
            palette = TagPalette(
                losingSaturation = config.lossSaturation,
                winningSaturation = config.winSaturation,
                minRateSaturation = config.minSaturation,
                maxRateSaturation = config.maxSaturation,
                value = config.colorValue,
            ),
        )
    }

    /**
     * Grades for the stones on the board. The report holds the whole reviewed
     * line, so a prefix of it still matches while the user walks the game; a
     * different line means the badges no longer belong to these stones.
     */
    /**
     * The badge on the **current move only** — main.c:2114 grades every move but
     * paints just `bn == piecenum`, because past moves already have their markers
     * in the win-rate graph.
     *
     * Two sources feed one record list: the live evaluations collected above
     * (engine or database), and a review report when the reviewed line still
     * matches the board. That is why the PC shows a badge the moment a value
     * arrives, with or without a review.
     */
    private fun badgeFor(
        pos: Position,
        report: GameReport?,
        config: AppSettings,
    ): Map<Move, MoveQuality> {
        if (pos.moves.isEmpty()) return emptyMap()
        val ply = pos.moves.size
        val live = records.value
        val fromReport = report?.data?.takeIf { it.matchesPrefix(pos.moves) }?.records.orEmpty()
        val merged = (0..ply).map { i ->
            // A review searched every position deliberately; prefer its record and
            // fall back to whatever the board picked up on its own.
            fromReport.getOrNull(i)?.takeIf { it.recorded } ?: live.getOrNull(i) ?: PositionRecord()
        }
        val quality = MoveGrader.currentBadge(
            moves = pos.moves,
            size = pos.size,
            records = merged,
            preset = GradingPreset.of(config.mqPreset),
            skipOpening = config.skipOpening,
        ) ?: return emptyMap()
        return mapOf(pos.moves.last() to quality)
    }

    // ---- database actions (P7) ---------------------------------------------

    /**
     * Long press on a point = the desktop's board-text dialog (Ctrl/middle click,
     * main.c:2677). It only makes sense on an empty point, and never while the
     * database is read-only — the desktop returns early in that case too.
     */
    fun onCellLabel(cell: Move, label: String) = runDb { database.editCellLabel(cell, label) }

    fun onSaveComment(comment: String) = runDb { database.editComment(comment) }

    /** `dbval` — logs the stored record for this position. */
    fun onQueryDbValue() = runDb { database.queryValue() }

    fun onQueryDbComment() = runDb { database.queryComment() }

    fun onDbDeleteOne() = runDb { database.deleteOne() }

    fun onDbSetBestMove() = runDb { database.setBestMove() }

    fun onDbClearBestMove() = runDb { database.clearBestMove() }

    fun onDbSave() = runDb { database.save() }

    fun onNoticeShown() {
        _notice.value = null
    }

    /** Runs a database call and surfaces a refusal as a notice instead of failing silently. */
    private fun runDb(block: suspend () -> DbOpResult) {
        viewModelScope.launch {
            when (val result = block()) {
                is DbOpResult.Refused -> _notice.value = result.reason
                DbOpResult.Sent -> Unit
            }
        }
    }

    /** Runs a game call on the view-model scope (named to avoid shadowing `launch`). */
    /**
     * Board actions are locked while a review or a proof runs — main.c returns
     * early from the board click and from every navigation callback while
     * `reviewactive || proveactive` (main.c:2661, 4524). Without this a tap would
     * push a `TURN` into the middle of the run's own conversation.
     */
    private fun researchBusy(): String? = when {
        review.progress.value.running -> tr("게임 리뷰가 진행 중입니다 — 먼저 중지하세요", "A game review is running — stop it first")
        prove.progress.value.running -> tr("국면 증명이 진행 중입니다 — 먼저 중지하세요", "A proof is running — stop it first")
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

        /** How long a `YXSTOP` may go unanswered before the UI stops waiting. */
        const val STOP_REPLY_TIMEOUT_MS = 5_000L
    }
}
