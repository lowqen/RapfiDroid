package dev.gomoku.yixindroid.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gomoku.yixindroid.core.designsystem.component.BoardRender
import dev.gomoku.yixindroid.core.designsystem.component.TagPalette
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AnalyzeParams
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.StoneColor
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

    private data class Panel(
        val connection: ConnectionState,
        val preview: Int?,
        val analyzing: Boolean,
        val forbidden: List<Move>,
    )

    private val panel = combine(repository.state, previewPv, analyzing, forbidden) { c, p, a, f ->
        Panel(c, p, a, f)
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

    private companion object {
        const val GHOST_LIMIT = 8
        const val MULTI_PV_LIMIT = 8
    }
}
