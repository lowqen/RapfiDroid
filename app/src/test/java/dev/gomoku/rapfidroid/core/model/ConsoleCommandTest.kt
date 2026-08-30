package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The desktop console command language (main.c `execute_command`).
 *
 * The chain of `yixin_strnicmp` prefix tests means **order is semantics**: the
 * cases below pin the ones where a shorter command would otherwise swallow a
 * longer one, since that failure mode is silent — `blockpath h8h7` would parse
 * as `block` and quietly block the wrong thing.
 */
class ConsoleCommandTest {

    private val size = 15
    private fun parse(line: String, commandMode: Boolean = false) =
        ConsoleCommand.parse(line, size, commandMode)

    private fun move(label: String) = Move.fromLabel(label, size)!!

    // ---- prefix ordering ----------------------------------------------------

    @Test
    fun blockPathIsNotSwallowedByBlock() {
        assertThat(parse("blockpath h8h7"))
            .isEqualTo(ConsoleCommand.BlockPath(listOf(move("h8"), move("h7"))))
        assertThat(parse("blockpath reset")).isEqualTo(ConsoleCommand.BlockPathReset)
        assertThat(parse("blockpath undo h8h7"))
            .isEqualTo(ConsoleCommand.BlockPathUndo(listOf(move("h8"), move("h7"))))
        assertThat(parse("blockpath except h8i8j7"))
            .isEqualTo(
                ConsoleCommand.BlockPathExcept(listOf(move("h8"), move("i8"), move("j7"))),
            )
        assertThat(parse("blockpath autoreset on"))
            .isEqualTo(ConsoleCommand.BlockPathAutoReset(true))
    }

    @Test
    fun blockSubcommandsComeBeforeBareBlock() {
        assertThat(parse("block reset")).isEqualTo(ConsoleCommand.BlockReset)
        assertThat(parse("block undo h8")).isEqualTo(ConsoleCommand.BlockUndo(move("h8")))
        assertThat(parse("block compare h8i8j7"))
            .isEqualTo(ConsoleCommand.BlockCompare(listOf(move("h8"), move("i8"), move("j7"))))
        assertThat(parse("block autoreset off")).isEqualTo(ConsoleCommand.BlockAutoReset(false))
        assertThat(parse("block h8")).isEqualTo(ConsoleCommand.Block(listOf(move("h8"))))
    }

    @Test
    fun forbidUndoComesBeforeForbid() {
        assertThat(parse("forbid 0 h8")).isEqualTo(ConsoleCommand.ForbidAdd(0, move("h8")))
        assertThat(parse("forbid undo 1 h8")).isEqualTo(ConsoleCommand.ForbidDel(1, move("h8")))
    }

    @Test
    fun hashSubcommandsAreDistinct() {
        assertThat(parse("hash clear")).isEqualTo(ConsoleCommand.HashClear)
        assertThat(parse("hash usage")).isEqualTo(ConsoleCommand.HashUsage)
        assertThat(parse("hash autoclear on")).isEqualTo(ConsoleCommand.HashAutoClear(true))
        assertThat(parse("hash autoclear off")).isEqualTo(ConsoleCommand.HashAutoClear(false))
        assertThat(parse("hash dump /srv/rapfi.hash"))
            .isEqualTo(ConsoleCommand.HashDump("/srv/rapfi.hash"))
        assertThat(parse("hash load /srv/rapfi.hash"))
            .isEqualTo(ConsoleCommand.HashLoad("/srv/rapfi.hash"))
    }

    @Test
    fun searchFromIsNotConfusedWithSearchDefend() {
        assertThat(parse("search from 12")).isEqualTo(ConsoleCommand.SearchFrom(12))
        assertThat(parse("searchdefend")).isEqualTo(ConsoleCommand.SearchDefend)
    }

    // ---- command mode -------------------------------------------------------

    @Test
    fun commandModePassesEverythingThroughButStillTurnsOff() {
        assertThat(parse("bench", commandMode = true))
            .isEqualTo(ConsoleCommand.RawLine("bench"))
        assertThat(parse("hash clear", commandMode = true))
            .isEqualTo(ConsoleCommand.RawLine("hash clear"))
        // …otherwise there would be no way back out
        assertThat(parse("command off", commandMode = true))
            .isEqualTo(ConsoleCommand.CommandMode(false))
        assertThat(parse("command on")).isEqualTo(ConsoleCommand.CommandMode(true))
    }

    /**
     * `bench` / `traceboard` / `tracesearch` / `reloadconfig` are engine
     * commands, not GUI ones — outside command mode the desktop rejects them,
     * and so must this.
     */
    @Test
    fun engineOnlyCommandsAreUnknownOutsideCommandMode() {
        assertThat(parse("bench")).isInstanceOf(ConsoleCommand.Unknown::class.java)
        assertThat(parse("traceboard")).isInstanceOf(ConsoleCommand.Unknown::class.java)
        assertThat(parse("reloadconfig config.toml"))
            .isInstanceOf(ConsoleCommand.Unknown::class.java)
    }

    // ---- the shipped toolbar scripts ---------------------------------------

    @Test
    fun theBenchToolbarScriptRoundTrips() {
        val lines = ConsoleCommand.script(ToolScripts.BENCH)
        assertThat(lines).containsExactly("command on", "bench", "command off").inOrder()
        var mode = false
        val parsed = lines.map { line ->
            val c = parse(line, mode)
            if (c is ConsoleCommand.CommandMode) mode = c.on
            c
        }
        assertThat(parsed[1]).isEqualTo(ConsoleCommand.RawLine("bench"))
        assertThat(mode).isFalse()
    }

    @Test
    fun theTraceToolbarScriptRoundTrips() {
        val lines = ConsoleCommand.script(ToolScripts.TRACE)
        var mode = false
        val parsed = lines.map { line ->
            val c = parse(line, mode)
            if (c is ConsoleCommand.CommandMode) mode = c.on
            c
        }
        assertThat(parsed.first()).isEqualTo(ConsoleCommand.SendBoard)
        assertThat(parsed).contains(ConsoleCommand.RawLine("traceboard"))
        assertThat(parsed).contains(ConsoleCommand.RawLine("tracesearch"))
    }

    @Test
    fun theReloadScriptEndsByRepushingTheDatabaseFlags() {
        val lines = ConsoleCommand.script(ToolScripts.reload(ToolScripts.CONFIG_CLASSIC))
        var mode = false
        val parsed = lines.map { line ->
            val c = parse(line, mode)
            if (c is ConsoleCommand.CommandMode) mode = c.on
            c
        }
        assertThat(parsed).contains(ConsoleCommand.RawLine("reloadconfig config_classical.toml"))
        assertThat(parsed.last()).isEqualTo(ConsoleCommand.DbRefresh)
    }

    // ---- cell lists ---------------------------------------------------------

    @Test
    fun cellsArePackedWithoutSeparators() {
        assertThat(ConsoleCommand.cells("h8i8j7", size))
            .containsExactly(move("h8"), move("i8"), move("j7")).inOrder()
        assertThat(ConsoleCommand.cells("a1o15", size))
            .containsExactly(move("a1"), move("o15")).inOrder()
    }

    @Test
    fun cellParsingStopsAtTheFirstThingItCannotRead() {
        assertThat(ConsoleCommand.cells("h8zz", size)).containsExactly(move("h8"))
        assertThat(ConsoleCommand.cells("h8p9", size)).containsExactly(move("h8"))
        assertThat(ConsoleCommand.cells("", size)).isEmpty()
        // two digits at most, then the row must be on the board: "h99" is not
        // "h9" followed by junk, it is one out-of-range token (main.c reads the
        // second digit greedily too)
        assertThat(ConsoleCommand.cells("h99", size)).isEmpty()
    }

    @Test
    fun cellNamesAreCaseInsensitive() {
        assertThat(ConsoleCommand.cells("H8", size)).containsExactly(move("h8"))
        assertThat(parse("BLOCK H8")).isEqualTo(ConsoleCommand.Block(listOf(move("h8"))))
    }

    // ---- arguments ----------------------------------------------------------

    @Test
    fun missingArgumentsFallBackTheWayTheDesktopDoes() {
        assertThat(parse("nbest")).isEqualTo(ConsoleCommand.Nbest(null))
        assertThat(parse("nbest 4")).isEqualTo(ConsoleCommand.Nbest(4))
        assertThat(parse("nbest 0")).isEqualTo(ConsoleCommand.Nbest(1))   // clamped
        assertThat(parse("balance1")).isEqualTo(ConsoleCommand.Balance(false, 0))
        assertThat(parse("balance2 100")).isEqualTo(ConsoleCommand.Balance(true, 100))
        assertThat(parse("pushpos")).isEqualTo(ConsoleCommand.PushPos(0))
        assertThat(parse("poppos 7")).isEqualTo(ConsoleCommand.PopPos(7))
        assertThat(parse("search from")).isEqualTo(ConsoleCommand.SearchFrom(1))
    }

    /** The desktop reads one character: only "on" turns a flag on. */
    @Test
    fun onOffFlagsDefaultToOff() {
        assertThat(parse("hash autoclear")).isEqualTo(ConsoleCommand.HashAutoClear(false))
        assertThat(parse("block autoreset yes")).isEqualTo(ConsoleCommand.BlockAutoReset(false))
        assertThat(parse("block autoreset ON")).isEqualTo(ConsoleCommand.BlockAutoReset(true))
    }

    // ---- shape transforms ---------------------------------------------------

    @Test
    fun shapeCommandsMatchTheDesktopArguments() {
        assertThat(parse("rotate 90"))
            .isEqualTo(ConsoleCommand.Symmetry(BoardSymmetry.ROTATE_90))
        assertThat(parse("rotate 180"))
            .isEqualTo(ConsoleCommand.Symmetry(BoardSymmetry.ROTATE_180))
        assertThat(parse("rotate 270"))
            .isEqualTo(ConsoleCommand.Symmetry(BoardSymmetry.ROTATE_270))
        assertThat(parse("flip -"))
            .isEqualTo(ConsoleCommand.Symmetry(BoardSymmetry.MIRROR_UP_DOWN))
        assertThat(parse("flip |"))
            .isEqualTo(ConsoleCommand.Symmetry(BoardSymmetry.MIRROR_LEFT_RIGHT))
        assertThat(parse("flip \\"))
            .isEqualTo(ConsoleCommand.Symmetry(BoardSymmetry.MIRROR_DIAGONAL))
        assertThat(parse("move ^")).isEqualTo(ConsoleCommand.Shift(BoardShift.UP))
        assertThat(parse("move v")).isEqualTo(ConsoleCommand.Shift(BoardShift.DOWN))
        assertThat(parse("move <")).isEqualTo(ConsoleCommand.Shift(BoardShift.LEFT))
        assertThat(parse("move >")).isEqualTo(ConsoleCommand.Shift(BoardShift.RIGHT))
    }

    // ---- game and console ---------------------------------------------------

    @Test
    fun gameCommandsMapToTheirActions() {
        assertThat(parse("thinking start"))
            .isEqualTo(ConsoleCommand.ThinkingCmd(ConsoleCommand.Thinking.START))
        assertThat(parse("thinking stop"))
            .isEqualTo(ConsoleCommand.ThinkingCmd(ConsoleCommand.Thinking.STOP))
        assertThat(parse("thinking toggle"))
            .isEqualTo(ConsoleCommand.ThinkingCmd(ConsoleCommand.Thinking.TOGGLE))
        assertThat(parse("undo all")).isEqualTo(ConsoleCommand.Undo(all = true))
        assertThat(parse("undo one")).isEqualTo(ConsoleCommand.Undo(all = false))
        assertThat(parse("redo all")).isEqualTo(ConsoleCommand.Redo(all = true))
        assertThat(parse("redo one")).isEqualTo(ConsoleCommand.Redo(all = false))
        assertThat(parse("draw")).isEqualTo(ConsoleCommand.Draw)
        assertThat(parse("resign")).isEqualTo(ConsoleCommand.Resign)
    }

    @Test
    fun consolePlumbingParses() {
        assertThat(parse("echo hello world")).isEqualTo(ConsoleCommand.Echo("hello world"))
        assertThat(parse("sleep 5000")).isEqualTo(ConsoleCommand.Sleep(5000))
        assertThat(parse("help")).isEqualTo(ConsoleCommand.Help)
        assertThat(parse("clear")).isEqualTo(ConsoleCommand.ClearLog)
        assertThat(parse("callback off")).isEqualTo(ConsoleCommand.CallbackEnabled(false))
        assertThat(parse("getpos")).isEqualTo(ConsoleCommand.GetPos)
        assertThat(parse("putpos h8i9")).isEqualTo(ConsoleCommand.PutPos("h8i9"))
        assertThat(parse("send board")).isEqualTo(ConsoleCommand.SendBoard)
        assertThat(parse("print features")).isEqualTo(ConsoleCommand.PrintFeatures)
    }

    @Test
    fun scriptsDropBlankLinesAndCarriageReturns() {
        assertThat(ConsoleCommand.script("a\r\n\r\n b \n\nc"))
            .containsExactly("a", "b", "c").inOrder()
        assertThat(ConsoleCommand.script("")).isEmpty()
    }

    @Test
    fun anEmptyOrUnknownLineIsReportedNotGuessed() {
        assertThat(parse("")).isEqualTo(ConsoleCommand.Unknown(""))
        assertThat(parse("frobnicate")).isEqualTo(ConsoleCommand.Unknown("frobnicate"))
    }

    // ---- callback routing ---------------------------------------------------

    @Test
    fun moveCallbacksPickTheScriptByPly() {
        val cb = CallbackConfig(
            minPly = 2, maxPly = 20,
            onMove = "echo mid", onMoveMinPly = "echo early", onMoveMaxPly = "echo late",
        )
        assertThat(cb.moveScript(1)).isEqualTo("echo early")
        assertThat(cb.moveScript(2)).isEqualTo("echo early")   // <= minPly
        assertThat(cb.moveScript(3)).isEqualTo("echo mid")
        assertThat(cb.moveScript(20)).isEqualTo("echo late")   // >= maxPly
        assertThat(cb.moveScript(21)).isEqualTo("echo late")
    }
}
