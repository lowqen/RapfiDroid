package dev.gomoku.yixindroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Placement
import org.junit.Test

class EngineCommandTest {

    private val coord = CoordMapper()

    @Test
    fun start_and_turn() {
        assertThat(EngineCommand.Start(15).serialize(coord)).isEqualTo("START 15")
        assertThat(EngineCommand.Turn(Move(7, 7)).serialize(coord)).isEqualTo("TURN 7,7")
    }

    @Test
    fun info_and_nbest() {
        assertThat(EngineCommand.Info("timeout_turn", "5000").serialize(coord))
            .isEqualTo("INFO timeout_turn 5000")
        assertThat(EngineCommand.YxNbest(4).serialize(coord)).isEqualTo("YXNBEST 4")
    }

    @Test
    fun board_block_marks_own_and_opponent() {
        val cmd = EngineCommand.Board(
            listOf(
                Placement(Move(7, 7), own = true),
                Placement(Move(7, 6), own = false),
            ),
        )
        assertThat(cmd.serialize(coord)).isEqualTo("BOARD\n7,7,1\n7,6,2\nDONE")
    }

    @Test
    fun raw_passes_through() {
        assertThat(EngineCommand.Raw("ABOUT").serialize(coord)).isEqualTo("ABOUT")
    }
}
