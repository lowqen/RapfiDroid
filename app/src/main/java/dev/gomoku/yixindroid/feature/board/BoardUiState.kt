package dev.gomoku.yixindroid.feature.board

import dev.gomoku.yixindroid.core.designsystem.component.BoardRender
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.DbPositionValue
import dev.gomoku.yixindroid.core.model.DbState
import dev.gomoku.yixindroid.core.model.FontSpec
import dev.gomoku.yixindroid.core.model.FunctionScripts
import dev.gomoku.yixindroid.core.model.GameState
import dev.gomoku.yixindroid.core.model.LngTable
import dev.gomoku.yixindroid.core.model.SearchStats
import dev.gomoku.yixindroid.core.model.StoneColor

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
    // ---- game (P5) ----
    val game: GameState = GameState(),
    val sideToMove: StoneColor = StoneColor.BLACK,
    val showForbidden: Boolean = true,
    val isRenju: Boolean = true,
    /** settings.txt line 28 — the desktop hides the clock widget when off. */
    val showClock: Boolean = true,
    /** An opening protocol is selected but the board has no centre point. */
    val openingNeedsOddSize: Boolean = false,
    /**
     * A research run owns the engine right now: the badge painted on the board,
     * carrying `prove_badge_lines` while a proof runs.
     */
    val research: ResearchBanner? = null,
    /** The user's own toolbar (`function/toolbar*.txt`) and the labels for it. */
    val toolbar: List<FunctionScripts.ToolbarItem> = emptyList(),
    val language: LngTable = LngTable.EMPTY,
    /** settings.txt line 20 — icon only, or icon with words. */
    val toolbarStyle: Int = 0,
    /** settings.txt line 33 — 0 puts the toolbar beside the board when there is room. */
    val toolbarPos: Int = 0,
    /** settings.txt lines 46-47 — the two "Database Comment Font" entries. */
    val dbCommentFont: FontSpec = FontSpec.DEFAULT,
    val dbCommentEditFont: FontSpec = FontSpec.DEFAULT,
) {
    val canAnalyze: Boolean get() = connection.isLive
    val canUndo: Boolean get() = moveCount > 0
    val canRedo: Boolean get() = futureCount > 0
    val busy: Boolean get() = analyzing || balancing || game.thinking

    /** The engine owes the move that is due, so "엔진 착수" makes sense. */
    val engineOnMove: Boolean
        get() = game.engineOwns(sideToMove) && !game.thinking && !game.over && connection.isLive

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

/**
 * "Something is running" strip above the board. The desktop signals this by
 * painting counters over the win-rate graph and by refusing board edits; on a
 * phone the board is often the only thing on screen, so it says so plainly and
 * offers the stop button that would otherwise be a tab away.
 */
data class ResearchBanner(
    val title: String,
    val detail: String = "",
    val progress: Float? = null,
    val isProve: Boolean = false,
)
