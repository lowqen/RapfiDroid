package dev.gomoku.yixindroid.domain.engine

import dev.gomoku.yixindroid.core.model.DbDeleteScope
import dev.gomoku.yixindroid.core.model.Move

/**
 * yixindb commands, ported command-for-command from the desktop
 * (`execute_command` in main.c:10892-11232 and `show_database`/
 * `show_dialog_boardtext`). Three shapes exist:
 *
 *  1. **position-scoped** — head line, then one `y,x` line per move of the
 *     current path, then `done` ([DbPositionCommand]). Everything that reads or
 *     edits *this* position uses it;
 *  2. **file-scoped** — head line, then the engine-side path ([DbFileCommand]);
 *  3. **bare** — a single line ([DbSimpleCommand]).
 *
 * The move list is written through [CoordMapper], i.e. `row,col` — the same
 * encoding the desktop uses (`movepath[i] / boardsize, movepath[i] % boardsize`).
 */
sealed interface DbPositionCommand : EngineCommand {
    /** First line, including any arguments (coordinates go through [coord]). */
    fun head(coord: CoordMapper): String

    /** Path from the empty board to the position being addressed. */
    val moves: List<Move>

    override fun serialize(coord: CoordMapper): String = buildString {
        append(head(coord))
        for (move in moves) {
            append('\n')
            append(coord.toWire(move))
        }
        append("\ndone")
    }
}

// ---- queries ---------------------------------------------------------------

/** `show_database()`: all child values **and** texts for the position. */
data class DbQueryAll(override val moves: List<Move>) : DbPositionCommand {
    override fun head(coord: CoordMapper) = "yxquerydatabaseallt"
}

/** Console `dbval`: the single record stored for this position. */
data class DbQueryOne(override val moves: List<Move>) : DbPositionCommand {
    override fun head(coord: CoordMapper) = "yxquerydatabaseone"
}

/** Console `dbtext`: the position comment. */
data class DbQueryText(override val moves: List<Move>) : DbPositionCommand {
    override fun head(coord: CoordMapper) = "yxquerydatabasetext"
}

// ---- edits -----------------------------------------------------------------

/**
 * Position comment. The desktop wraps it in quotes and escapes `"` and `\`
 * (main.c:10913-10930); newlines are sent verbatim inside the quotes.
 */
data class DbEditComment(
    val comment: String,
    override val moves: List<Move>,
) : DbPositionCommand {
    override fun head(coord: CoordMapper) = "yxedittextdatabase \"${escape(comment)}\""

    private companion object {
        fun escape(text: String): String = buildString(text.length) {
            for (c in text) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> append(c)
            }
        }
    }
}

/**
 * Free-text label on one cell. An empty [label] deletes it — the desktop sends
 * the command with a trailing space and no text (main.c:2618).
 *
 * [moves] is the path of the position the label belongs to; [target] is the cell
 * being labelled and is **not** part of that path.
 */
data class DbEditLabel(
    val target: Move,
    val label: String,
    override val moves: List<Move>,
) : DbPositionCommand {
    override fun head(coord: CoordMapper) =
        "yxeditlabeldatabase ${coord.toWire(target)} ${wireLabel()}"

    /**
     * The engine reads the label with `%s`, so it must be a single token; the
     * desktop's entry also caps it at six characters.
     */
    private fun wireLabel(): String =
        label.trim().substringBefore(' ').take(MAX_LABEL)

    private companion object {
        /** `gtk_entry_set_max_length(entry, 6)` in the desktop dialog. */
        const val MAX_LABEL = 6
    }
}

/**
 * Edit the stored record: `yxedittvddatabase <mask> <tag> <value> <depth>`.
 * The desktop uses mask 1 for the tag, 2 for the value, 4 for the depth
 * (main.c:10997/11013/11027) and 7 for "all three at once" (the prove pipeline,
 * main.c:9455). [tag] is the raw character code, -1 meaning "clear".
 */
data class DbEditRecord(
    val mask: Int,
    val tag: Int = -1,
    val value: Int = 0,
    val depth: Int = 0,
    override val moves: List<Move>,
) : DbPositionCommand {
    override fun head(coord: CoordMapper) = "yxedittvddatabase $mask $tag $value $depth"

    companion object {
        const val MASK_TAG = 1
        const val MASK_VALUE = 2
        const val MASK_DEPTH = 4

        /** `dbedittag <c>` — an empty tag clears it (-1 on the wire). */
        fun tag(tag: Char?, moves: List<Move>) =
            DbEditRecord(MASK_TAG, tag = tag?.code ?: -1, moves = moves)

        /** `dbeditval <n>` — the desktop passes tag = -1 so only the value changes. */
        fun value(value: Int, moves: List<Move>) =
            DbEditRecord(MASK_VALUE, tag = -1, value = value, moves = moves)

        /** `dbeditdep <n>`. */
        fun depth(depth: Int, moves: List<Move>) =
            DbEditRecord(MASK_DEPTH, tag = -1, depth = depth, moves = moves)
    }
}

/** `dbsetbestmove`: mark the last move of [moves] as this position's best move. */
data class DbSetBestMove(override val moves: List<Move>) : DbPositionCommand {
    override fun head(coord: CoordMapper) = "yxsetbestmovedatabase"
}

/** `dbclearbestmove`. */
data class DbClearBestMove(override val moves: List<Move>) : DbPositionCommand {
    override fun head(coord: CoordMapper) = "yxclearbestmovedatabase"
}

// ---- deletes ---------------------------------------------------------------

/** `dbdel one`: drop the record of this position only. */
data class DbDeleteOne(override val moves: List<Move>) : DbPositionCommand {
    override fun head(coord: CoordMapper) = "yxdeletedatabaseone"
}

/**
 * `dbdel all […]`: drop every child branch matching [scope]. Destructive — the
 * repository refuses these unless the user unlocked them, and the UI confirms
 * first (the desktop's `show_dbdelall_query`).
 */
data class DbDeleteAll(
    val scope: DbDeleteScope,
    override val moves: List<Move>,
) : DbPositionCommand {
    override fun head(coord: CoordMapper) =
        "yxdeletedatabaseall" + scope.wire().let { if (it.isEmpty()) "" else " $it" }
}

// ---- file operations -------------------------------------------------------

/**
 * Head line plus an engine-side path. Paths are resolved **on the server**, not
 * on the phone: the database lives next to the remote engine.
 */
sealed interface DbFileCommand : EngineCommand {
    val head: String
    val path: String

    override fun serialize(coord: CoordMapper): String = "$head\n${path.trim()}"
}

/** `dbset [file]`: open an existing database or create a new one. */
data class DbSetFile(override val path: String) : DbFileCommand {
    override val head: String get() = "yxsetdatabase"
}

/** `dbmerge [file]`: merge another database into the current one. */
data class DbMerge(override val path: String) : DbFileCommand {
    override val head: String get() = "yxdbmerge"
}

/**
 * `dbsplit [file]`: write the subtree below the current position to a new file.
 * The desktop sends the board first (main.c:11377) — the repository does too.
 */
data class DbSplit(override val path: String) : DbFileCommand {
    override val head: String get() = "yxdbsplit"
}

/** `libtodb [file]`: import a Yixin .lib opening book. */
data class DbLibImport(override val path: String) : DbFileCommand {
    override val head: String get() = "yxlibtodb"
}

/** `dbtolib [file]`: export to .lib. */
data class DbLibExport(override val path: String) : DbFileCommand {
    override val head: String get() = "yxdbtolib"
}

/** `dbtotxt [file]`: export the subtree below the current position as text. */
data class DbTextExport(override val path: String) : DbFileCommand {
    override val head: String get() = "yxdbtotxt"
}

/** `dbtotxt all [file]`: export the whole database as CSV/text. */
data class DbTextExportAll(override val path: String) : DbFileCommand {
    override val head: String get() = "yxdbtotxtall"
}

/** `txttodb [file]`: import records from a text/CSV dump. */
data class DbTextImport(override val path: String) : DbFileCommand {
    override val head: String get() = "yxtxttodb"
}

/** `dbtopos [file]`: dump database positions to a .pos file. */
data class DbToPos(override val path: String) : DbFileCommand {
    override val head: String get() = "yxdbtopos"
}

// ---- bare commands ---------------------------------------------------------

sealed interface DbSimpleCommand : EngineCommand {
    val line: String
    override fun serialize(coord: CoordMapper): String = line
}

/** `dbsave` — also what the auto-save timer sends. */
data object DbSave : DbSimpleCommand {
    override val line: String get() = "yxsavedatabase"
}

/** `dbcheck` — integrity check. */
data object DbCheck : DbSimpleCommand {
    override val line: String get() = "yxdbcheck"
}

/** `dbfix` — repair what `dbcheck` found. */
data object DbFix : DbSimpleCommand {
    override val line: String get() = "yxdbfix"
}

/**
 * `info usedatabase <0|1>` (main.c `use_database`).
 *
 * In this app the flag lives in the settings model, so `EngineParams` is what
 * actually sends it — keeping one sender. This type exists for parity and for
 * direct use from the console.
 */
data class DbUse(val on: Boolean) : DbSimpleCommand {
    override val line: String get() = "info usedatabase ${if (on) 1 else 0}"
}
