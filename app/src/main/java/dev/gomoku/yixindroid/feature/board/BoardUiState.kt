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
    // ---- display settings (settings.txt / settings_dev.txt) ----
    val showEvalBar: Boolean = true,
    val showWrGraph: Boolean = true,
    val showWarning: Boolean = true,
    val boardZoomPercent: Int = 100,
) {
    val canAnalyze: Boolean get() = connection.isLive
    val blackWinRate: Double? get() = snapshot?.blackWinRate()
    val blackMate: Int? get() = snapshot?.blackMate()
    val depth: Int get() = snapshot?.depth ?: 0
    val stats: SearchStats get() = snapshot?.stats ?: SearchStats()

    /** Board zoom (settings_dev line 8): 0.6..3.0 of the available width. Above
     *  1.0 the board is wider than the screen and scrolls horizontally. */
    val boardScale: Float get() = boardZoomPercent.coerceIn(60, 300) / 100f

    /** Main line of the best PV as board labels, for the BESTLINE status field. */
    fun bestLineLabels(size: Int): String =
        snapshot?.best?.line.orEmpty().joinToString(" ") { it.label(size) }
}
