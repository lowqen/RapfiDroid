package dev.gomoku.yixindroid.feature.board

import dev.gomoku.yixindroid.core.designsystem.component.BoardRender
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.DbPositionValue
import dev.gomoku.yixindroid.core.model.DbState
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
    // ---- database (P7) ----
    val db: DbState = DbState(),
    /** Position value read from the database, used while the engine is idle. */
    val dbValue: DbPositionValue? = null,
    /** Transient message (e.g. a write refused because the DB is read-only). */
    val notice: String? = null,
    // ---- toolbar ----
    /** Moves undone but still redoable (the desktop's `movepath` tail). */
    val futureCount: Int = 0,
    val balancing: Boolean = false,
    /** The line in the desktop's clipboard format ("h8i9…", `getpos`). */
    val positionString: String = "",
) {
    val canAnalyze: Boolean get() = connection.isLive
    val canUndo: Boolean get() = moveCount > 0
    val canRedo: Boolean get() = futureCount > 0
    val busy: Boolean get() = analyzing || balancing

    /**
     * Shape transforms need stones. An ordinary analysis simply restarts on the
     * new shape, but a balance search would come back with coordinates for the
     * old one, so those block the transform buttons.
     */
    val canTransform: Boolean get() = moveCount > 0 && !balancing

    /**
     * Eval bar source: the live search when there is one, otherwise the stored
     * database value — the desktop feeds the bar from both
     * (`evalbar_update_from_engine` / `evalbar_update_from_db`).
     */
    val blackWinRate: Double? get() = snapshot?.blackWinRate() ?: dbValue?.blackWinRate
    val blackMate: Int? get() = snapshot?.blackMate() ?: dbValue?.blackMate
    val dbActive: Boolean get() = db.enabled && connection.isLive
    val canEditDb: Boolean get() = db.canWrite(connection.isLive)
    val depth: Int get() = snapshot?.depth ?: 0
    val stats: SearchStats get() = snapshot?.stats ?: SearchStats()

    /** Board zoom (settings_dev line 8): 0.6..3.0 of the available width. Above
     *  1.0 the board is wider than the screen and scrolls horizontally. */
    val boardScale: Float get() = boardZoomPercent.coerceIn(60, 300) / 100f

    /** Main line of the best PV as board labels, for the BESTLINE status field. */
    fun bestLineLabels(size: Int): String =
        snapshot?.best?.line.orEmpty().joinToString(" ") { it.label(size) }
}
