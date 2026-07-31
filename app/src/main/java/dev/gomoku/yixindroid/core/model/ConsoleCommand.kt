package dev.gomoku.yixindroid.core.model

/**
 * The desktop's console command language (main.c `execute_command`,
 * 10067-11500), as a pure parser.
 *
 * This one language drives four things on the desktop: the log window's input
 * line, the 36 custom toolbar buttons, the 6 hotkeys, and the callback scripts.
 * Porting it as a parser rather than as a pile of buttons means the app gets all
 * four for the price of one, and it is testable without an engine.
 *
 * **Prefix matching order is part of the semantics.** The desktop is a chain of
 * `yixin_strnicmp(command, "...", n)` tests, so `blockpath reset` must be tried
 * before `blockpath`, and every `blockpath*` before any `block*` — otherwise
 * `block` swallows `blockpath`. [parse] keeps the desktop's order exactly.
 */
sealed interface ConsoleCommand {

    // ---- console plumbing ---------------------------------------------------

    data object Help : ConsoleCommand
    data object ClearLog : ConsoleCommand

    /** `command on` / `command off` — raw passthrough to the engine. */
    data class CommandMode(val on: Boolean) : ConsoleCommand

    data class Echo(val text: String) : ConsoleCommand

    /** `sleep <ms>` — the rest of the script runs after the delay. */
    data class Sleep(val ms: Int) : ConsoleCommand

    /** A line sent verbatim because [CommandMode] is on. */
    data class RawLine(val line: String) : ConsoleCommand

    // ---- hash / transposition table ----------------------------------------

    data object HashClear : ConsoleCommand

    /** Clear the TT before every engine move (client-side flag). */
    data class HashAutoClear(val on: Boolean) : ConsoleCommand

    data object HashUsage : ConsoleCommand

    /** Paths are **server-side**: the engine writes them, not the phone. */
    data class HashDump(val path: String) : ConsoleCommand
    data class HashLoad(val path: String) : ConsoleCommand

    // ---- position stack (client-side, 10 slots) -----------------------------

    data class PushPos(val slot: Int) : ConsoleCommand
    data class PopPos(val slot: Int) : ConsoleCommand

    data object GetPos : ConsoleCommand
    data class PutPos(val text: String) : ConsoleCommand

    // ---- blocked points -----------------------------------------------------

    /** Forbid the engine from considering these points. */
    data class Block(val cells: List<Move>) : ConsoleCommand
    data class BlockUndo(val cell: Move) : ConsoleCommand
    data object BlockReset : ConsoleCommand

    /** Block everything **except** the listed points. */
    data class BlockCompare(val cells: List<Move>) : ConsoleCommand
    data class BlockAutoReset(val on: Boolean) : ConsoleCommand

    /** Block a continuation (this position + these follow-up points). */
    data class BlockPath(val cells: List<Move>) : ConsoleCommand
    data class BlockPathUndo(val cells: List<Move>) : ConsoleCommand
    data object BlockPathReset : ConsoleCommand
    data class BlockPathExcept(val cells: List<Move>) : ConsoleCommand
    data class BlockPathAutoReset(val on: Boolean) : ConsoleCommand

    // ---- forced forbidden points -------------------------------------------

    /** `forbid <side> <cell>` — side 0 = black, 1 = white. */
    data class ForbidAdd(val side: Int, val cell: Move) : ConsoleCommand
    data class ForbidDel(val side: Int, val cell: Move) : ConsoleCommand

    // ---- search tools -------------------------------------------------------

    data object SearchDefend : ConsoleCommand

    /** `nbest [k]` — k null means the configured default. */
    data class Nbest(val count: Int?) : ConsoleCommand

    /** `search from <depth>` → `info start_depth`. */
    data class SearchFrom(val depth: Int) : ConsoleCommand

    data class Balance(val two: Boolean, val bias: Int) : ConsoleCommand
    data object BestLine : ConsoleCommand

    // ---- engine maintenance -------------------------------------------------

    data object PrintFeatures : ConsoleCommand
    data object SendBoard : ConsoleCommand

    /** Re-push `usedatabase` / `database_readonly` and redraw. */
    data object DbRefresh : ConsoleCommand

    /*
     * NOTE: `bench`, `traceboard`, `tracesearch` and `reloadconfig` are NOT in
     * this list, because they are not desktop console commands — they are
     * **engine** commands, reached by wrapping them in `command on` / `command
     * off`. That is exactly what the desktop's own toolbar buttons do
     * (function/toolbar33-36.txt), and [ToolScripts] reproduces those scripts
     * verbatim. Accepting them as bare commands would make a script behave
     * differently here than on the PC.
     */

    // ---- game actions (handled by the game repository) ----------------------

    enum class Thinking { START, STOP, TOGGLE }
    data class ThinkingCmd(val action: Thinking) : ConsoleCommand

    data class Undo(val all: Boolean) : ConsoleCommand
    data class Redo(val all: Boolean) : ConsoleCommand
    data object Draw : ConsoleCommand
    data object Resign : ConsoleCommand

    data class Symmetry(val symmetry: BoardSymmetry) : ConsoleCommand
    data class Shift(val direction: BoardShift) : ConsoleCommand

    // ---- callbacks ----------------------------------------------------------

    /** `callback on` / `callback off` — suspend scripts without unconfiguring. */
    data class CallbackEnabled(val on: Boolean) : ConsoleCommand

    /** Not a command we know; the desktop prints its "unknown command" line. */
    data class Unknown(val line: String) : ConsoleCommand

    companion object {

        /**
         * Parse one line. [commandMode] on makes every line a [RawLine] except
         * `command off`, exactly as the desktop's dispatcher does before any
         * other test.
         */
        fun parse(raw: String, size: Int = Move.DEFAULT_SIZE, commandMode: Boolean = false):
            ConsoleCommand {
            val line = raw.trim()
            if (line.isEmpty()) return Unknown("")
            // `command on/off` wins over passthrough — otherwise there would be
            // no way back out of command mode.
            if (line.startsWithIc("command on")) return CommandMode(true)
            if (line.startsWithIc("command off")) return CommandMode(false)
            if (commandMode) return RawLine(line)

            return when {
                line.startsWithIc("echo") -> Echo(line.drop(4).trim())
                line.startsWithIc("help") -> Help
                line.startsWithIc("clear") -> ClearLog
                line.startsWithIc("sleep") -> Sleep(line.arg(5)?.toIntOrNull() ?: 0)

                // rotate / flip / move
                line.startsWithIc("rotate") -> when (line.arg(6)?.firstOrNull()) {
                    '1' -> Symmetry(BoardSymmetry.ROTATE_180)
                    '2' -> Symmetry(BoardSymmetry.ROTATE_270)
                    else -> Symmetry(BoardSymmetry.ROTATE_90)   // desktop default
                }
                line.startsWithIc("flip") -> when (line.arg(4)?.firstOrNull()) {
                    '|' -> Symmetry(BoardSymmetry.MIRROR_LEFT_RIGHT)
                    '\\' -> Symmetry(BoardSymmetry.MIRROR_DIAGONAL)
                    '/' -> Symmetry(BoardSymmetry.MIRROR_ANTI_DIAGONAL)
                    else -> Symmetry(BoardSymmetry.MIRROR_UP_DOWN)
                }
                line.startsWithIc("move") -> when (line.arg(4)?.firstOrNull()) {
                    'v' -> Shift(BoardShift.DOWN)
                    '<' -> Shift(BoardShift.LEFT)
                    '>' -> Shift(BoardShift.RIGHT)
                    else -> Shift(BoardShift.UP)
                }

                // position strings and the 10-slot stack
                line.startsWithIc("pushpos") -> PushPos(line.arg(7)?.toIntOrNull() ?: 0)
                line.startsWithIc("poppos") -> PopPos(line.arg(6)?.toIntOrNull() ?: 0)
                line.startsWithIc("putpos") -> PutPos(line.drop(6).trim())
                line.startsWithIc("getpos") -> GetPos

                // blockpath BEFORE block — `block` is a prefix of `blockpath`
                line.startsWithIc("blockpath reset") -> BlockPathReset
                line.startsWithIc("blockpath autoreset") ->
                    BlockPathAutoReset(line.onOff(19))
                line.startsWithIc("blockpath undo") -> BlockPathUndo(cells(line.drop(14), size))
                line.startsWithIc("blockpath except") ->
                    BlockPathExcept(cells(line.drop(16), size))
                line.startsWithIc("blockpath") -> BlockPath(cells(line.drop(9), size))

                line.startsWithIc("block reset") -> BlockReset
                line.startsWithIc("block autoreset") -> BlockAutoReset(line.onOff(15))
                line.startsWithIc("block undo") ->
                    cells(line.drop(10), size).firstOrNull()?.let { BlockUndo(it) }
                        ?: Unknown(line)
                line.startsWithIc("block compare") -> BlockCompare(cells(line.drop(13), size))
                line.startsWithIc("block") -> Block(cells(line.drop(5), size))

                // forbid undo BEFORE forbid
                line.startsWithIc("forbid undo") -> sideCell(line.drop(11), size)
                    ?.let { (s, c) -> ForbidDel(s, c) } ?: Unknown(line)
                line.startsWithIc("forbid") -> sideCell(line.drop(6), size)
                    ?.let { (s, c) -> ForbidAdd(s, c) } ?: Unknown(line)

                // hash
                line.startsWithIc("hash autoclear") -> HashAutoClear(line.onOff(14))
                line.startsWithIc("hash clear") -> HashClear
                line.startsWithIc("hash usage") -> HashUsage
                line.startsWithIc("hash dump") -> HashDump(line.drop(9).trim())
                line.startsWithIc("hash load") -> HashLoad(line.drop(9).trim())

                // search
                line.startsWithIc("search from") ->
                    SearchFrom(line.arg(11)?.toIntOrNull() ?: 1)
                line.startsWithIc("searchdefend") -> SearchDefend
                line.startsWithIc("nbest") -> Nbest(line.arg(5)?.toIntOrNull()?.coerceAtLeast(1))
                line.startsWithIc("bestline") -> BestLine
                line.startsWithIc("balance1") -> Balance(false, line.arg(8)?.toIntOrNull() ?: 0)
                line.startsWithIc("balance2") -> Balance(true, line.arg(8)?.toIntOrNull() ?: 0)

                // engine maintenance
                line.startsWithIc("print features") -> PrintFeatures
                line.startsWithIc("send board") -> SendBoard
                line.startsWithIc("dbrefresh") -> DbRefresh

                // game
                line.startsWithIc("thinking start") -> ThinkingCmd(Thinking.START)
                line.startsWithIc("thinking stop") -> ThinkingCmd(Thinking.STOP)
                line.startsWithIc("thinking toggle") -> ThinkingCmd(Thinking.TOGGLE)
                line.startsWithIc("undo all") -> Undo(all = true)
                line.startsWithIc("undo one") -> Undo(all = false)
                line.startsWithIc("redo all") -> Redo(all = true)
                line.startsWithIc("redo one") -> Redo(all = false)
                line.startsWithIc("draw") -> Draw
                line.startsWithIc("resign") -> Resign

                line.startsWithIc("callback on") -> CallbackEnabled(true)
                line.startsWithIc("callback off") -> CallbackEnabled(false)

                else -> Unknown(line)
            }
        }

        /**
         * Split a script into lines the way `custom_function` does. A `sleep`
         * suspends the rest, so the caller gets the delay and the remaining
         * script instead of a flat list.
         */
        fun script(text: String): List<String> =
            text.split('\n').map { it.trim('\r', ' ', '\t') }.filter { it.isNotEmpty() }

        /**
         * Consume `<letter><digits>` pairs packed without separators, as
         * `block h8i8j7` does. Stops at the first token that does not parse.
         */
        fun cells(text: String, size: Int = Move.DEFAULT_SIZE): List<Move> {
            val s = text.trim()
            val out = ArrayList<Move>()
            var i = 0
            while (i < s.length) {
                if (s[i] == ' ' || s[i] == ',') { i++; continue }
                if (!s[i].isLetter()) break
                val x = s[i].uppercaseChar() - 'A'
                i++
                var row = 0
                var digits = 0
                while (i < s.length && s[i].isDigit() && digits < 2) {
                    row = row * 10 + (s[i] - '0'); i++; digits++
                }
                if (digits == 0) break
                val m = Move(x, size - row)
                if (!m.isInside(size)) break
                out.add(m)
            }
            return out
        }

        /** `<side> <cell>` as `forbid 0 h8` writes it. */
        private fun sideCell(text: String, size: Int): Pair<Int, Move>? {
            val s = text.trim()
            val side = s.firstOrNull()?.digitToIntOrNull() ?: return null
            if (side !in 0..1) return null
            val cell = cells(s.drop(1), size).firstOrNull() ?: return null
            return side to cell
        }

        private fun String.startsWithIc(prefix: String) = startsWith(prefix, ignoreCase = true)

        /** The token after a fixed-length prefix, or null when absent. */
        private fun String.arg(prefixLength: Int): String? =
            drop(prefixLength).trim().takeWhile { !it.isWhitespace() }.ifEmpty { null }

        /**
         * The desktop tests the **second** character of the argument
         * (`command[16] == 'n'` for "hash autoclear on"), so "on" turns it on
         * and anything else — including a missing argument — turns it off.
         */
        private fun String.onOff(prefixLength: Int): Boolean =
            drop(prefixLength).trim().startsWith("on", ignoreCase = true)
    }
}
