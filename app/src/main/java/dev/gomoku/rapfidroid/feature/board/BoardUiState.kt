package dev.gomoku.rapfidroid.feature.board

import dev.gomoku.rapfidroid.core.designsystem.component.BoardRender
import dev.gomoku.rapfidroid.core.model.AnalysisSnapshot
import dev.gomoku.rapfidroid.core.model.BoardMode
import dev.gomoku.rapfidroid.core.model.ConnectionState
import dev.gomoku.rapfidroid.core.model.DbPositionValue
import dev.gomoku.rapfidroid.core.model.DbState
import dev.gomoku.rapfidroid.core.model.FontSpec
import dev.gomoku.rapfidroid.core.model.FunctionScripts
import dev.gomoku.rapfidroid.core.model.GameState
import dev.gomoku.rapfidroid.core.model.LngTable
import dev.gomoku.rapfidroid.core.model.MoveQuality
import dev.gomoku.rapfidroid.core.model.OpeningEval
import dev.gomoku.rapfidroid.core.model.SearchStats
import dev.gomoku.rapfidroid.core.model.StoneColor

data class BoardUiState(
    val render: BoardRender = BoardRender(),
    val moveCount: Int = 0,
    val connection: ConnectionState = ConnectionState.Disconnected,
    /** [분석] or [대국] — each tab has its own board. */
    val mode: BoardMode = BoardMode.ANALYSIS,
    val analyzing: Boolean = false,
    /** The running search is `searchdefend` — every defense, not the k best. */
    val defending: Boolean = false,
    /**
     * What the engine card shows: this position's analysis, or the last one
     * read when the board has moved on since ([stale], RapfiWeb's frozen card).
     */
    val shown: AnalysisSnapshot? = null,
    val stale: Boolean = false,
    /** Moves in the position [shown] belongs to, when [stale]. */
    val staleAt: Int = 0,
    /** Black-perspective value for the eval bar, or null. */
    val eval: EvalView? = null,
    val multiPv: Int = 1,
    val previewPv: Int? = null,
    val winRateHistory: List<Double?> = emptyList(),
    /** Analysis time in seconds; 0 reads until stopped. */
    val analysisSec: Int = 0,
    /** «정지하면 최선수 두기». */
    val playBestOnStop: Boolean = true,
    // ---- display settings (settings.txt / settings_dev.txt) ----
    val showEvalBar: Boolean = true,
    val showWrGraph: Boolean = true,
    val showWarning: Boolean = true,
    val boardZoomPercent: Int = 100,
    // ---- position card ----
    /** Opening name chain, moves 1 to 4 (천원 › 직접 › 소성 › 소기). */
    val openingNames: List<String> = emptyList(),
    /** 흑 5수 유불리 of this position. */
    val openingGrade: OpeningEval.Grade? = null,
    /** Grade of the last move, when a matching review or live values say so. */
    val moveGrade: MoveQuality? = null,
    val gameName: String? = null,
    /** A link replaced the board; the board it replaced is kept until decided. */
    val linkBackup: Boolean = false,
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
    /** Engine move time in ms for the game; 0 thinks until «지금 두기». */
    val moveTimeMs: Long = 0,
    /** Game strength 1..5 ([GameLevels]); 5 is full strength. */
    val strength: Int = GameLevels.MAX,
    val sideToMove: StoneColor = StoneColor.BLACK,
    val showForbidden: Boolean = true,
    val isRenju: Boolean = true,
    /** settings.txt line 3 as stored: 0 freestyle … 6 swap-2. */
    val rule: Int = 2,
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
    /** settings.txt lines 46-47 — the two "Database Comment Font" entries. */
    val dbCommentFont: FontSpec = FontSpec.DEFAULT,
) {
    val canAnalyze: Boolean get() = connection.isLive
    val canUndo: Boolean get() = moveCount > 0
    val canRedo: Boolean get() = futureCount > 0
    val busy: Boolean get() = analyzing || balancing || game.thinking
    val analysisTab: Boolean get() = mode == BoardMode.ANALYSIS

    /** The engine owes the move that is due, so "엔진 착수" makes sense. */
    val engineOnMove: Boolean
        get() = game.engineOwns(sideToMove) && !game.thinking && !game.over && connection.isLive

    /** The engine thinks until told to move — unlimited time at full strength. */
    val unlimitedTurn: Boolean get() = moveTimeMs <= 0 && strength >= GameLevels.MAX

    /**
     * Shape transforms need stones. A balance search would come back with
     * coordinates for the old shape, so it blocks the transform buttons.
     */
    val canTransform: Boolean get() = moveCount > 0 && !balancing

    val dbActive: Boolean get() = db.enabled && connection.isLive
    val canEditDb: Boolean get() = db.canWrite(connection.isLive)
    val stats: SearchStats get() = shown?.stats ?: SearchStats()

    /** Board zoom (settings_dev line 8): 0.6..3.0 of the available width. */
    val boardScale: Float get() = boardZoomPercent.coerceIn(60, 300) / 100f
}

/**
 * The eval bar's reading, always from Black's side. [mate] is plies to mate,
 * positive for Black. [fromDb] marks a value read from the database rather
 * than searched.
 */
data class EvalView(
    val blackPct: Double,
    val evalCp: Int? = null,
    val mate: Int? = null,
    val fromDb: Boolean = false,
)

/**
 * Game strength by nodes, RapfiWeb's five levels. The engine has one level
 * setting (settings.txt line 6); these sit on its «custom» level, with the node
 * cap in line 10 and the move time in line 7.
 */
object GameLevels {
    const val MAX = 5

    data class Level(val level: Int, val ko: String, val en: String, val maxNode: Long?)

    val all = listOf(
        Level(1, "입문", "Novice", 20_000),
        Level(2, "초급", "Beginner", 100_000),
        Level(3, "중급", "Intermediate", 500_000),
        Level(4, "상급", "Advanced", 2_000_000),
        Level(5, "최강", "Strongest", null),
    )

    /** Desktop line 10's value for full strength — effectively no cap. */
    const val NO_CAP = 1_000_000_000L

    /** Presets for the move time, ms. 0 thinks until «지금 두기». */
    val MOVE_TIMES = listOf(0L, 1_000L, 3_000L, 5_000L, 10_000L, 30_000L)

    /** Analysis time presets, seconds. */
    val ANALYSIS_SECS = listOf(0, 5, 10, 30, 60)

    /** Read the two controls back off the desktop fields. */
    fun strengthOf(level: Int, maxNode: Long): Int = when (level) {
        1 -> all.firstOrNull { it.maxNode != null && maxNode <= it.maxNode }?.level ?: MAX
        0 -> MAX
        else -> MAX
    }

    fun moveTimeOf(level: Int, timeoutTurnSec: Int): Long =
        if (level == 1 && timeoutTurnSec in 1 until UNLIMITED_TURN_SEC) timeoutTurnSec * 1000L else 0L

    /** The desktop's own «unlimited» turn: 2000 s. */
    const val UNLIMITED_TURN_SEC = 2000
}

/**
 * "Something is running" badge on the board. The desktop signals this by
 * painting counters over the win-rate graph; on a phone the board is often the
 * only thing on screen, so it says so plainly and offers the stop button.
 */
data class ResearchBanner(
    val title: String,
    val detail: String = "",
    val progress: Float? = null,
    val isProve: Boolean = false,
)
