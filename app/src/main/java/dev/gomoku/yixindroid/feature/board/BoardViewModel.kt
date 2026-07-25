package dev.gomoku.yixindroid.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gomoku.yixindroid.core.designsystem.component.BoardRender
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AnalyzeParams
import dev.gomoku.yixindroid.core.model.EngineParams
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {

    private val position = MutableStateFlow(Position())
    private val snapshot = MutableStateFlow<AnalysisSnapshot?>(null)
    private val analyzing = MutableStateFlow(false)
    // settings.txt line 20 ("default number of multi-pv") is 3; the desktop sends
    // `yxnbest <numpv>` with that value, so match it or the search differs.
    private val multiPv = MutableStateFlow(EngineParams().multiPv)
    private val previewPv = MutableStateFlow<Int?>(null)

    private var analyzeJob: Job? = null

    /** Black-perspective win rate per ply, for the win-rate graph. */
    private val winRateHistory = MutableStateFlow<List<Double?>>(emptyList())

    private val panel = combine(multiPv, previewPv, analyzing) { m, p, a -> Triple(m, p, a) }

    val uiState: StateFlow<BoardUiState> =
        combine(position, repository.state, snapshot, panel, winRateHistory) {
                pos, conn, snap, (mpv, preview, isAnalyzing), history ->
            BoardUiState(
                render = buildRender(pos, snap, preview),
                moveCount = pos.moves.size,
                connection = conn,
                analyzing = isAnalyzing,
                snapshot = snap,
                multiPv = mpv,
                previewPv = preview,
                winRateHistory = history,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState())

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
        position.value = Position()
        winRateHistory.value = emptyList()
        onPositionChanged()
    }

    fun onToggleAnalyze() {
        if (analyzing.value) stopAnalysis() else startAnalysis()
    }

    fun onMultiPvChange(value: Int) {
        multiPv.value = value.coerceIn(1, 8)
        if (analyzing.value) startAnalysis()
    }

    fun onPreviewPv(index: Int?) {
        previewPv.value = index
    }

    private fun onPositionChanged() {
        snapshot.value = null
        if (analyzing.value) startAnalysis()
    }

    private fun startAnalysis() {
        analyzeJob?.cancel()
        snapshot.value = null
        analyzing.value = true
        val target = position.value
        val ply = target.moves.size
        analyzeJob = viewModelScope.launch {
            repository.analyze(target, AnalyzeParams(multiPv.value)).collect { snap ->
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
    }

    private fun buildRender(pos: Position, snap: AnalysisSnapshot?, preview: Int?): BoardRender {
        val pv = when {
            preview != null -> snap?.pvs?.firstOrNull { it.index == preview }?.line
            else -> snap?.best?.line?.ifEmpty { snap.realtimeLine }
        }.orEmpty()
        val ghosts = pv.filterNot { pos.moves.contains(it) }.take(GHOST_LIMIT)
        val bestMark = snap?.realtimeBest ?: snap?.best?.head
        // While previewing one PV the per-cell tags would fight the ghosts for
        // space, so they are only drawn in the default (aggregate) view.
        val tags = if (preview == null) snap?.tags.orEmpty() else emptyMap()
        return BoardRender(
            size = pos.size,
            stones = pos.moves,
            lastMove = pos.moves.lastOrNull(),
            ghosts = ghosts,
            bestMark = if (bestMark != null && !pos.moves.contains(bestMark)) bestMark else null,
            tags = tags,
            candidates = snap?.candidates.orEmpty(),
            loseCells = snap?.loseCells.orEmpty(),
        )
    }

    private companion object {
        const val GHOST_LIMIT = 8
    }
}
