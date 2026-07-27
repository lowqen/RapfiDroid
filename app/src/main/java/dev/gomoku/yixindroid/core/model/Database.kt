package dev.gomoku.yixindroid.core.model

/**
 * yixindb (the engine-side opening database) as the desktop models it.
 *
 * The desktop keeps two parallel per-cell arrays, both filled by
 * `MESSAGE DATABASE <y> <x> <tag> …` (main.c `iochannelout_watch`):
 *  - `boardtag[y][x]` — 1..4 characters **packed big-endian into one int**
 *    (`'w'<<16 | '3'<<8 | '9'` == "w39"), the stored value of the child move;
 *  - `boardtext[y][x]` — a free-form label (max 6 chars) the user can type.
 *
 * Drawing prefers the free text when "show board text" is on, otherwise the tag
 * (main.c:1918-2012); both are coloured by win rate when they look like a value.
 */

/** What a cell label means once parsed (`NN%` / `W<n>` / `L<n>` / `D`). */
enum class DbCellKind { RATE, WIN, LOSS, DRAW, NOTE }

/** One database cell: the stored value of playing that point in this position. */
data class DbCell(
    val move: Move,
    /** Decoded `boardtag` text, "" when the cell has no stored value. */
    val tagLabel: String = "",
    /** Free-form `boardtext` label, "" when unset. */
    val text: String = "",
    /** Raw trailing fields of the message (value, depth, bound, …), kept verbatim. */
    val fields: List<Int> = emptyList(),
) {
    /** What the board shows: the free text when present, else the tag. */
    fun display(showBoardText: Boolean): String =
        if (showBoardText && text.isNotEmpty()) text else tagLabel

    /** Value-ish reading of [display]-worthy content, used for colour and eval. */
    fun kindOf(label: String = valueLabel()): DbCellKind = when {
        label.isEmpty() -> DbCellKind.NOTE
        label.endsWith('%') && label.dropLast(1).all { it.isDigit() } -> DbCellKind.RATE
        label[0] == 'W' || label[0] == 'w' -> DbCellKind.WIN
        label[0] == 'L' || label[0] == 'l' -> DbCellKind.LOSS
        label[0] == 'D' || label[0] == 'd' -> DbCellKind.DRAW
        else -> DbCellKind.NOTE
    }

    /**
     * The label the *value* logic reads. main.c prefers the packed tag and falls
     * back to the free text (`evalbar_update_from_db`), independent of the
     * "show board text" toggle.
     */
    fun valueLabel(): String = if (tagLabel.isNotEmpty()) tagLabel else text

    /** Win rate percent for a `NN%` label, else null. */
    fun winRatePct(): Int? {
        val label = valueLabel()
        if (kindOf(label) != DbCellKind.RATE) return null
        return label.dropLast(1).toIntOrNull()?.takeIf { it in 0..100 }
    }

    /** Mate distance for `W<n>` / `L<n>` (0 when the engine stored no step). */
    fun mateStep(): Int? {
        val label = valueLabel()
        return when (kindOf(label)) {
            DbCellKind.WIN, DbCellKind.LOSS -> leadingInt(label.drop(1))
            else -> null
        }
    }

    private fun leadingInt(s: String): Int =
        s.trimStart().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
}

/** Bound of a stored value (`MESSAGE DATABASE ONE … <bound>`, main.c:13569). */
enum class DbBound(val label: String) {
    NONE("none"), UPPER("upper"), LOWER("lower"), EXACT("exact");

    companion object {
        fun of(code: Int): DbBound = when (code) {
            3 -> EXACT
            2 -> LOWER
            1 -> UPPER
            else -> NONE
        }
    }
}

/** The single-record reply to `yxquerydatabaseone` (the desktop's `dbval`). */
data class DbEntry(
    val tag: Char?,      // null when the record has no label
    val value: Int,
    val depth: Int,
    val bound: DbBound,
    val label: String = "",
) {
    fun summary(): String = buildString {
        if (label.isNotEmpty()) append("$label: ")
        append("tag=${tag ?: "(없음)"}  값=$value  깊이=$depth  범위=${bound.label}")
    }
}

/** Everything known about the database at the position currently on the board. */
data class DbSnapshot(
    val cells: Map<Move, DbCell> = emptyMap(),
    /** Position comment (`yxquerydatabasetext`), already unescaped. */
    val comment: String = "",
    /** Last `dbval` reply, if one was requested. */
    val entry: DbEntry? = null,
) {
    val hasValues: Boolean get() = cells.values.any { it.valueLabel().isNotEmpty() }

    /**
     * Position value derived from the stored child values — a direct port of
     * main.c `evalbar_update_from_db`: the side to move picks its best stored
     * child, so *best child value == position value*. Returns null when the
     * database holds no value for any child.
     */
    fun positionValue(blackToMove: Boolean): DbPositionValue? {
        var bestRate = -1
        var hasWin = false
        var winStep = 0
        var hasLoss = false
        var loseStep = 0

        for (cell in cells.values) {
            val label = cell.valueLabel()
            when (cell.kindOf(label)) {
                DbCellKind.RATE -> cell.winRatePct()?.let { if (it > bestRate) bestRate = it }
                DbCellKind.WIN -> {
                    val step = cell.mateStep() ?: 0
                    // first win wins outright; later ones only if strictly shorter
                    if (!hasWin || (step > 0 && step < winStep)) winStep = step
                    hasWin = true
                }
                DbCellKind.LOSS -> {
                    val step = cell.mateStep() ?: 0
                    if (step > loseStep) loseStep = step
                    hasLoss = true
                }
                DbCellKind.DRAW -> if (bestRate < DRAW_RATE) bestRate = DRAW_RATE
                DbCellKind.NOTE -> Unit
            }
        }

        val stmRate: Double
        var stmMate = 0
        when {
            hasWin -> {
                stmRate = 1.0
                stmMate = winStep
            }
            bestRate >= 0 -> stmRate = bestRate / 100.0
            hasLoss -> {
                stmRate = 0.0
                stmMate = -loseStep
            }
            else -> return null
        }

        var blackRate = if (blackToMove) stmRate else 1.0 - stmRate
        val blackMate = if (stmMate != 0) (if (blackToMove) stmMate else -stmMate) else 0
        if (blackMate != 0) blackRate = if (blackMate > 0) 1.0 else 0.0
        return DbPositionValue(
            blackWinRate = blackRate,
            blackMate = blackMate.takeIf { it != 0 },
            stmWinRate = stmRate,
            stmMate = stmMate.takeIf { it != 0 },
        )
    }

    private companion object {
        const val DRAW_RATE = 50
    }
}

/** Position evaluation read out of the database (black perspective + side to move). */
data class DbPositionValue(
    val blackWinRate: Double,
    val blackMate: Int?,
    val stmWinRate: Double,
    val stmMate: Int?,
)

/** Which record set a bulk delete touches (`yxdeletedatabaseall <filter>`). */
enum class DbDeleteFilter(val wire: String, val title: String) {
    ALL("", "모든 분기"),
    NON_WL("nonwl", "승/패가 아닌 기록"),
    WL("wl", "승/패 기록"),
    WIN("w", "승 기록"),
    LOSE("l", "패 기록"),
    WL_NO_STEP("wlnostep", "수순 없는 승/패"),
    WL_IN_STEP("wlinstep", "N수 이내 승/패"),
}

/**
 * One bulk-delete variant. The desktop exposes the same matrix through console
 * commands (`dbdel all …`, main.c:11251-11344): six filters × plain/recursive,
 * with `wlinstep` taking a mate distance. Plain "delete all" has no recursive
 * form there, so neither do we.
 */
data class DbDeleteScope(
    val filter: DbDeleteFilter = DbDeleteFilter.ALL,
    val recursive: Boolean = false,
    val step: Int = 1,
) {
    /** Argument after `yxdeletedatabaseall` ("" for the plain form). */
    fun wire(): String = when {
        filter == DbDeleteFilter.ALL -> ""
        filter == DbDeleteFilter.WL_IN_STEP ->
            (filter.wire + if (recursive) "recursive" else "") + " $step"
        else -> filter.wire + if (recursive) "recursive" else ""
    }

    fun title(): String = buildString {
        append(if (filter == DbDeleteFilter.WL_IN_STEP) "${step}수 이내 승/패" else filter.title)
        if (recursive && filter != DbDeleteFilter.ALL) append(" (하위 분기까지)")
    }
}

/** Progress of a database file operation (`MESSAGE DATABASE LOAD/SAVE START|DONE`). */
data class DbFileProgress(val saving: Boolean, val file: String)

/** Everything the database UI observes. */
data class DbState(
    /** `info usedatabase` (settings.txt line 32). */
    val enabled: Boolean = true,
    /** `info database_readonly` (line 33) — blocks every write path. */
    val readOnly: Boolean = false,
    /** Bulk deletes and split stay locked until the user opts in (plan §7-1). */
    val destructiveUnlocked: Boolean = false,
    val snapshot: DbSnapshot = DbSnapshot(),
    /** Position value derived from [snapshot], only for a paired query reply. */
    val value: DbPositionValue? = null,
    /** Non-null while the engine is loading/saving a file. */
    val progress: DbFileProgress? = null,
    /** Engine feedback for database operations, newest last. */
    val log: List<String> = emptyList(),
    val lastSaveAt: Long? = null,
) {
    /** Writes are possible only with the database on, writable and connected. */
    fun canWrite(connected: Boolean): Boolean = enabled && !readOnly && connected
}

/** Outcome of a database operation request. */
sealed interface DbOpResult {
    /** Command handed to the engine (the engine reports the result asynchronously). */
    data object Sent : DbOpResult

    /** Blocked by a guard — read-only, database off, locked, or not connected. */
    data class Refused(val reason: String) : DbOpResult
}
