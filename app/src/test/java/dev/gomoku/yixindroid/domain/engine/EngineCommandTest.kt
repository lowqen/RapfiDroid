package dev.gomoku.yixindroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Placement
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
