package dev.gomoku.yixindroid.feature.board

import dev.gomoku.yixindroid.core.designsystem.component.BoardRender
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.SearchStats

data class BoardUiState(
    val render: BoardRender = BoardRender(),
    val moveCount: Int = 0,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val analyzing: Boolean = false,
    val snapshot: AnalysisSnapshot? = null,
    val multiPv: Int = 1,
    val previewPv: Int? = null,
    val winRateHistory: List<Double?> = emptyList(),
) {
    val canAnalyze: Boolean get() = connection.isLive
    val blackWinRate: Double? get() = snapshot?.blackWinRate()
    val blackMate: Int? get() = snapshot?.blackMate()
    val depth: Int get() = snapshot?.depth ?: 0
    val stats: SearchStats get() = snapshot?.stats ?: SearchStats()

    /** Main line of the best PV as board labels, for the BESTLINE status field. */
    fun bestLineLabels(size: Int): String =
        snapshot?.best?.line.orEmpty().joinToString(" ") { it.label(size) }
}
