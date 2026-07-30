package dev.gomoku.yixindroid.feature.board

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.yixindroid.core.designsystem.component.BoardRender
import dev.gomoku.yixindroid.core.designsystem.component.DbLabel
import dev.gomoku.yixindroid.core.designsystem.component.TagPalette
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
import dev.gomoku.yixindroid.core.model.GameState
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.MoveQuality
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.ProveOverlay
import dev.gomoku.yixindroid.core.model.ProveProgress
import dev.gomoku.yixindroid.core.model.Swap2Choice
import dev.gomoku.yixindroid.core.model.TapResult
import dev.gomoku.yixindroid.data.board.BoardImageIo
import dev.gomoku.yixindroid.domain.repository.DatabaseRepository
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.GameRepository
import dev.gomoku.yixindroid.domain.repository.ProveRepository
import dev.gomoku.yixindroid.domain.repository.ReviewRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val imageIo: BoardImageIo,
) : ViewModel() {

    private val settings = settingsRepository.settings

    /** The board itself lives in [GameRepository] so a game survives this screen. */
    private val position = game.position

    private val snapshot = MutableStateFlow<AnalysisSnapshot?>(null)
    private val analyzing = MutableStateFlow(false)
    private val previewPv = MutableStateFlow<Int?>(null)

    /** A balance search is running (desktop `balance1` / `balance2`). */
    private val balancing = MutableStateFlow(false)

    private var analyzeJob: Job? = null

    /** Black-perspective win rate per ply, for the win-rate graph. */
    private val winRateHistory = MutableStateFlow<List<Double?>>(emptyList())

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
    )

    private val panel = combine(
        repository.state, previewPv, analyzing, game.forbidden, database.state, _notice,
        game.future, balancing, game.state, review.report, prove.overlay, prove.progress,
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
        )
    }

    val uiState: StateFlow<BoardUiState> =
        combine(position, snapshot, panel, settings, winRateHistory) {
                pos, snap, p, config, history ->
            BoardUiState(
                render = buildRender(pos, snap, p, config),
                moveCount = pos.moves.size,
                connection = p.connection,
                analyzing = p.analyzing,
                snapshot = snap,
                multiPv = config.multiPv,
                previewPv = p.preview,
                winRateHistory = if (config.showWrGraph) history else emptyList(),
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
                proveBadge = if (p.proveProgress.running) p.proveProgress.badgeLines() else null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState())

    init {
        // The database follows the board: every position change re-queries it,
        // exactly like the desktop's show_database() call after each move.
        viewModelScope.launch {
            position.collect { pos ->
                database.setPosition(pos)
                snapshot.value = null
                if (analyzing.value) startAnalysis()
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
        winRateHistory.value = emptyList()
        game.newGame(resetClock = true)
    }

    fun onStartAnalyze() {
        if (analyzing.value) return
        researchBusy()?.let {
            _notice.value = it
            return
        }
        if (game.state.value.thinking) {
            _notice.value = "대국 착수를 계산하는 중입니다"
            return
        }
        startAnalysis()
    }

    /**
     * Stop button: ends whichever search is running — analysis, a balance search
     * or the engine's game turn. All three stop with `YXSTOP`, which makes the
     * engine report its current best move, exactly like the desktop's stop.
     */
    fun onStopAnalyze() {
        if (analyzing.value) stopAnalysis()
        if (balancing.value) viewModelScope.launch { runCatching { repository.stop() } }
        if (game.state.value.thinking) viewModelScope.launch { game.stopThinking() }
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
            _notice.value = "판 밖으로 나가는 수가 있어 이동할 수 없습니다"
            return
        }
        applyLine(shifted)
    }

    /** Replace the position with [moves] (transform result / pasted line). */
    private fun applyLine(moves: List<Move>) = inGame {
        previewPv.value = null
        winRateHistory.value = emptyList()
        game.replaceLine(moves)
    }

    /** `putpos`: load a line from the desktop's clipboard format ("h8i9…"). */
    fun onLoadPositionString(text: String) {
        val size = position.value.size
        val moves = BoardTransform.fromPositionString(text.trim(), size)
        if (moves.isEmpty()) {
            _notice.value = "국면 문자열을 읽을 수 없습니다"
            return
        }
        applyLine(moves)
        _notice.value = "${moves.size}수를 불러왔습니다"
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
                _notice.value = "균형점을 찾지 못했습니다"
                return@launch
            }
            game.replaceLine(target.moves + legal)
            _notice.value = "균형점: ${legal.joinToString(" ") { it.label(target.size) }}"
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
                onSuccess = { "보드 이미지를 저장했습니다" },
                onFailure = { e -> "이미지 저장 실패: ${e.message}" },
            )
        }
    }

    private fun startAnalysis() {
        analyzeJob?.cancel()
        snapshot.value = null
        analyzing.value = true
        // The game's forbidden refresh must not push a board mid-search.
        game.setAnalyzing(true)
        val target = position.value
        val ply = target.moves.size
        analyzeJob = viewModelScope.launch {
            repository.analyze(target, AnalyzeParams(settings.value.multiPv)).collect { snap ->
                snapshot.value = snap
                snap.blackWinRate()?.let { recordWinRate(ply, it) }
            }
        }
    }

    /** Keep one (latest) win rate per ply so the graph tracks the whole game. */
    private fun recordWinRate(ply: Int, rate: Double) {
        winRateHistory.update { current ->
            val out = current.toMutableList()
            while (out.size <= ply) out.add(null)
            out[ply] = rate
            out
        }
    }

    private fun stopAnalysis() {
        analyzeJob?.cancel()
        analyzeJob = null
        analyzing.value = false
        game.setAnalyzing(false)
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
        val overlay = config.showAnalysis
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
            badges = if (config.showMoveBadge) badgesFor(pos, panel.report) else emptyMap(),
            // Prove overlay: ghost stones of the line under search plus a status
            // marker on every root candidate (main.c:9061 `prove_cell_pixbuf`).
            prove = panel.prove.takeIf { it.active },
            showNumbers = config.showNumber,
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
    private fun badgesFor(pos: Position, report: GameReport?): Map<Move, MoveQuality> {
        if (report == null || pos.moves.isEmpty()) return emptyMap()
        val reviewed = report.data.moves
        if (reviewed.size < pos.moves.size) return emptyMap()
        for (i in pos.moves.indices) if (reviewed[i] != pos.moves[i]) return emptyMap()
        return report.moves.take(pos.moves.size)
            .filter { it.quality != MoveQuality.NONE }
            .associate { it.move to it.quality }
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
        review.progress.value.running -> "게임 리뷰가 진행 중입니다 — 먼저 중지하세요"
        prove.progress.value.running -> "국면 증명이 진행 중입니다 — 먼저 중지하세요"
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
    }
}
