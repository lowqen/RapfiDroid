package dev.gomoku.yixindroid.feature.board

import dev.gomoku.yixindroid.core.designsystem.component.BoardRender
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.ConnectionState

data class BoardUiState(
    val render: BoardRender = BoardRender(),
    val moveCount: Int = 0,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val analyzing: Boolean = false,
    val snapshot: AnalysisSnapshot? = null,
    val multiPv: Int = 1,
    val previewPv: Int? = null,
) {
    val canAnalyze: Boolean get() = connection.isLive
    val blackWinRate: Double? get() = snapshot?.blackWinRate()
    val blackMate: Int? get() = snapshot?.blackMate()
    val depth: Int get() = snapshot?.depth ?: 0
}
