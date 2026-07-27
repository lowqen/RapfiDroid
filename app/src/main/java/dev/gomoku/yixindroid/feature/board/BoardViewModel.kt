package dev.gomoku.yixindroid.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gomoku.yixindroid.core.designsystem.component.BoardRender
import dev.gomoku.yixindroid.core.designsystem.component.DbLabel
import dev.gomoku.yixindroid.core.designsystem.component.TagPalette
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AnalyzeParams
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.DbOpResult
import dev.gomoku.yixindroid.core.model.DbState
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.StoneColor
import dev.gomoku.yixindroid.domain.repository.DatabaseRepository
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BoardViewModel @Inject constructor(
    private val repository: EngineRepository,
    private val settingsRepository: SettingsRepository,
    private val database: DatabaseRepository,
) : ViewModel() {

    private val settings = settingsRepository.settings

    private val position = MutableStateFlow(Position(size = settings.value.boardSize))
    private val snapshot = MutableStateFlow<AnalysisSnapshot?>(null)
    private val analyzing = MutableStateFlow(false)
    private val previewPv = MutableStateFlow<Int?>(null)

    /** Renju forbidden points for the current position (settings.txt line 30). */
    private val forbidden = MutableStateFlow<List<Move>>(emptyList())

    private var analyzeJob: Job? = null

    /** Black-perspective win rate per ply, for the win-rate graph. */
    private val winRateHistory = MutableStateFlow<List<Double?>>(emptyList())

    /** One-shot user feedback (a refused database write, mostly). */
    private val _notice = MutableStateFlow<String?>(null)

    private data class Panel(
        val connection: ConnectionState,
        val preview: Int?,
        val analyzing: Boolean,
        val forbidden: List<Move>,
        val db: DbState,
        val notice: String?,
    )

    private val panel = combine(
        repository.state, previewPv, analyzing, forbidden, database.state, _notice,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        Panel(
            connection = values[0] as ConnectionState,
            preview = values[1] as Int?,
            analyzing = values[2] as Boolean,
            forbidden = values[3] as List<Move>,
            db = values[4] as DbState,
            notice = values[5] as String?,
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
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState())

    init {
        // Board size is a setting; changing it starts a fresh board (the engine is
        // re-STARTed by the repository for the same reason).
        viewModelScope.launch {
            settings.map { it.boardSize }.distinctUntilChanged().collect { size ->
                if (size != position.value.size) {
                    position.value = Position(size = size)
                    winRateHistory.value = emptyList()
                    onPositionChanged()
                }
            }
        }
        // The database follows the board: every position change re-queries it,
        // exactly like the desktop's show_database() call after each move.
        viewModelScope.launch {
            position.collect { database.setPosition(it) }
        }
        // Forbidden points depend on the rule, the toggle and whose turn it is.
        viewModelScope.launch {
            settings.map { it.showForbidden to it.isRenju }.distinctUntilChanged().collect {
                refreshForbidden()
            }
        }
    }

    fun onTap(move: Move) {
        if (position.value.moves.contains(move)) return
        previewPv.value = null
        position.value = position.value.play(move)
        onPositionChanged()
    }

    fun onUndo() {
        if (position.value.moves.isEmpty()) return
        previewPv.value = null
        position.value = position.value.undo()
        onPositionChanged()
    }

    fun onReset() {
        previewPv.value = null
        position.value = Position(size = settings.value.boardSize)
        winRateHistory.value = emptyList()
        onPositionChanged()
    }

    fun onToggleAnalyze() {
        if (analyzing.value) stopAnalysis() else startAnalysis()
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

    private fun onPositionChanged() {
        snapshot.value = null
        refreshForbidden()
        if (analyzing.value) startAnalysis()
    }

    private fun startAnalysis() {
        analyzeJob?.cancel()
        snapshot.value = null
        analyzing.value = true
        val target = position.value
        val ply = target.moves.size
        analyzeJob = viewModelScope.launch {
            repository.analyze(target, AnalyzeParams(settings.value.multiPv)).collect { snap ->
                snapshot.value = snap
                snap.blackWinRate()?.let { recordWinRate(ply, it) }
            }
        }
    }

    /**
     * Ask the engine for forbidden points, like the desktop does: renju base rule,
     * the toggle on, Black to move (only Black has forbidden points) and **not
     * while searching** — the desktop hides them then, and a YXBOARD mid-search
     * would disturb it.
     */
    private fun refreshForbidden() {
        val config = settings.value
        val pos = position.value
        val wanted = config.showForbidden && config.isRenju && !analyzing.value &&
            pos.sideToMove == StoneColor.BLACK && repository.state.value.isLive
        if (!wanted) {
            forbidden.value = emptyList()
            return
        }
        viewModelScope.launch {
            forbidden.value = runCatching { repository.forbidden(pos) }.getOrDefault(emptyList())
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
        val dbLabels = if (config.useDatabase && !panel.analyzing) {
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

    private companion object {
        const val GHOST_LIMIT = 8
        const val MULTI_PV_LIMIT = 8
    }
}
