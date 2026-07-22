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
}
