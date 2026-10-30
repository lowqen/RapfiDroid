package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.gomoku.zenyth.core.model.CandidateState
import dev.gomoku.zenyth.core.model.CellTag
import dev.gomoku.zenyth.core.model.DbCellKind
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.MoveQuality
import dev.gomoku.zenyth.core.model.ProveOverlay

/**
 * Everything the board needs to draw one frame (pure data → easy to preview).
 *
 * Immutable, and declared so: compared by value, a render equal to the last
 * one — a clock tick, a card that changed — lets the board skip composing and
 * drawing again.
 */
@Immutable
data class BoardRender(
    val size: Int = Move.DEFAULT_SIZE,
    val stones: List<Move> = emptyList(),        // played order, black first
    val lastMove: Move? = null,
    val forbidden: List<Move> = emptyList(),
    val ghosts: List<Move> = emptyList(),         // PV preview from the current position
    val bestMark: Move? = null,                   // realtime / PV-head highlight
    val tags: Map<Move, CellTag> = emptyMap(),    // per-cell winrate / mate labels
    val candidates: Map<Move, CandidateState> = emptyMap(), // realtime POS/DONE
    val loseCells: Set<Move> = emptySet(),        // realtime LOSE
    val dbLabels: Map<Move, DbLabel> = emptyMap(), // yixindb values / board texts
    val showNumbers: Boolean = true,              // settings.txt line 14
    val palette: TagPalette = TagPalette(),       // saturation/value settings
    /** Review grades on played stones (settings_dev line 4, `mq_badge_pixbuf`). */
    val badges: Map<Move, MoveQuality> = emptyMap(),
    /** Prove overlay while a proof runs; null when none does. */
    val prove: ProveOverlay? = null,
    /** Half of the desktop's 500 ms prove heartbeat (`provepulse`, main.c:9212). */
    val provePulse: Boolean = false,
    /**
     * Size of the text drawn on points — analysis tags and database values —
     * from the desktop's "Board Text Font" (settings.txt line 44). The families
     * in that setting are PC fonts nobody has here; the size is the part that
     * transfers, and it is the part being adjusted.
     */
    val textScale: Float = 1f,
    /** Points the engine has been told to ignore (`block`, main.c:1899). */
    val blocked: Set<Move> = emptySet(),
    /** Short text marks on empty points: opening grades, joseki symbols. */
    val marks: Map<Move, BoardMark> = emptyMap(),
    /** 흑 5수 유불리 shapes on empty points (the opening explorer's next moves). */
    val gradeMarks: Map<Move, dev.gomoku.zenyth.core.model.OpeningEval.Grade> = emptyMap(),
)
/** A short coloured mark on an empty point (`+3`, `!`, `A`). */
data class BoardMark(val text: String, val color: Color)
/**
 * The colours a [BoardMark] is written in — the board's own words for «good»,
 * «wrong» and «just a label».
 *
 * One set for both woods: a mark is drawn with the dark [TextOutline] round it,
 * like every value on the board, so it reads on maple and on dark wood alike and
 * does not follow the theme. Named in UI overhaul P4; since 2026-10-29 the
 * same green and red as the value text ([TagPalette.textFor]) instead of the
 * neon `#6BE38A` / `#FF6B5E`.
 */
object BoardMarkColors {
    /** The book move, a right answer, a hint. */
    val Good = textInkAt(120f)

    /** A trap, a wrong answer, the refutation. */
    val Bad = textInkAt(0f)

    /** A branch letter, a game count. */
    val Plain = Color(0xFFF2E7D3)
}
/**
 * A database label on one point: the stored value (`W5`, `39%`, `D`) or a
 * free-form board text. Drawn only while the engine is idle, like the desktop
 * (main.c:1913 — analysis tags win during a search).
 */
data class DbLabel(val text: String, val kind: DbCellKind)
