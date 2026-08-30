package dev.gomoku.rapfidroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.Placement
import org.junit.Test

class EngineCommandTest {

    private val coord = CoordMapper()

    @Test
    fun startAndTurnUseRowColWire() {
        assertThat(EngineCommand.Start(15).serialize(coord)).isEqualTo("START 15")
        // Move(x=8, y=7) -> wire "7,8"
        assertThat(EngineCommand.Turn(Move(x = 8, y = 7)).serialize(coord)).isEqualTo("TURN 7,8")
    }

    @Test
    fun boardBlockIsRowColWho() {
        val cmd = EngineCommand.Board(
            listOf(
                Placement(Move(x = 7, y = 7), own = true),
                Placement(Move(x = 7, y = 6), own = false),
            ),
        )
        // "y,x,who": (7,7,1) then (6,7,2)
        assertThat(cmd.serialize(coord)).isEqualTo("BOARD\n7,7,1\n6,7,2\nDONE")
    }

    @Test
    fun nbestAndRaw() {
        assertThat(EngineCommand.YxNbest(4).serialize(coord)).isEqualTo("YXNBEST 4")
        assertThat(EngineCommand.Raw("ABOUT").serialize(coord)).isEqualTo("ABOUT")
    }

    /** `balance1` / `balance1 100` / `balance2` (main.c:10864). */
    @Test
    fun balanceMatchesTheDesktopCommand() {
        assertThat(EngineCommand.YxBalance(two = false).serialize(coord))
            .isEqualTo("yxbalanceone 0")
        assertThat(EngineCommand.YxBalance(two = false, bias = 100).serialize(coord))
            .isEqualTo("yxbalanceone 100")
        assertThat(EngineCommand.YxBalance(two = true).serialize(coord))
            .isEqualTo("yxbalancetwo 0")
    }

    // ---- engine tools (P10) -------------------------------------------------

    /** The path travels on its own line after the verb (main.c:10727-10760). */
    @Test
    fun hashDumpAndLoadPutThePathOnTheNextLine() {
        assertThat(EngineCommand.YxHashDump("/srv/rapfi.hash").serialize(coord))
            .isEqualTo("yxhashdump\n/srv/rapfi.hash")
        assertThat(EngineCommand.YxHashLoad("/srv/rapfi.hash").serialize(coord))
            .isEqualTo("yxhashload\n/srv/rapfi.hash")
        assertThat(EngineCommand.YxShowHashUsage.serialize(coord)).isEqualTo("yxshowhashusage")
    }

    /** Block commands are position blocks: one cell, then `done`. */
    @Test
    fun blockCommandsUseThePositionBlockForm() {
        assertThat(EngineCommand.YxBlock(Move(x = 7, y = 7)).serialize(coord))
            .isEqualTo("yxblock\n7,7\ndone")
        assertThat(EngineCommand.YxBlockUndo(Move(x = 8, y = 6)).serialize(coord))
            .isEqualTo("yxblockundo\n6,8\ndone")
        assertThat(EngineCommand.YxBlockReset.serialize(coord)).isEqualTo("yxblockreset")
        assertThat(EngineCommand.YxBlockPathReset.serialize(coord))
            .isEqualTo("yxblockpathreset")
    }

    /** `yxblockpath` carries the current line first, then the cells to block. */
    @Test
    fun blockPathSendsTheLineThenTheCells() {
        val cmd = EngineCommand.YxBlockPath(
            line = listOf(Move(x = 7, y = 7), Move(x = 8, y = 6)),
            cells = listOf(Move(x = 9, y = 5)),
        )
        assertThat(cmd.serialize(coord)).isEqualTo("yxblockpath\n7,7\n6,8\n5,9\ndone")
        assertThat(cmd.copy(undo = true).serialize(coord))
            .isEqualTo("yxblockpathundo\n7,7\n6,8\n5,9\ndone")
    }

    /**
     * `yxforbid` is the one position command with **inline** coordinates, and
     * the side comes last (main.c:10711).
     */
    @Test
    fun forbidPutsItsCoordinatesInline() {
        assertThat(
            EngineCommand.YxForbid(add = true, cell = Move(x = 7, y = 7), side = 0)
                .serialize(coord),
        ).isEqualTo("yxforbid add 7 7 0")
        assertThat(
            EngineCommand.YxForbid(add = false, cell = Move(x = 8, y = 6), side = 1)
                .serialize(coord),
        ).isEqualTo("yxforbid del 6 8 1")
    }

    @Test
    fun searchToolInfoLinesMatchTheDesktop() {
        assertThat(EngineCommand.InfoStartDepth(12).serialize(coord))
            .isEqualTo("info start_depth 12")
        assertThat(EngineCommand.InfoNbestSym(true).serialize(coord))
            .isEqualTo("info nbestsym 1")
        assertThat(EngineCommand.InfoNbestSym(false).serialize(coord))
            .isEqualTo("info nbestsym 0")
        assertThat(EngineCommand.YxPrintFeature.serialize(coord)).isEqualTo("yxprintfeature")
    }

    /**
     * A balance-two answer arrives as two coordinates on one line, and both are
     * played (main.c:13950).
     */
    @Test
    fun aBalanceTwoReplyParsesAsTwoMoves() {
        val response = ResponseParser.parse("7,7 8,8", coord)
        assertThat(response).isInstanceOf(EngineResponse.BestMove::class.java)
        assertThat((response as EngineResponse.BestMove).moves)
            .containsExactly(Move(x = 7, y = 7), Move(x = 8, y = 8)).inOrder()
    }
}
