package dev.gomoku.yixindroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.Move
import org.junit.Test

class CoordMapperTest {

    @Test
    fun noFlip_isIdentityOnY() {
        val c = CoordMapper(size = 15, flipY = false)
        assertThat(c.toWire(Move(7, 7))).isEqualTo("7,7")
        assertThat(c.toWire(Move(0, 0))).isEqualTo("0,0")
        assertThat(c.fromWire(3, 9)).isEqualTo(Move(3, 9))
    }

    @Test
    fun flip_reflectsYAcrossBoard() {
        val c = CoordMapper(size = 15, flipY = true)
        assertThat(c.toWire(Move(0, 0))).isEqualTo("0,14")
        assertThat(c.toWire(Move(7, 7))).isEqualTo("7,7") // center is fixed
        assertThat(c.fromWire(0, 14)).isEqualTo(Move(0, 0))
    }

    @Test
    fun roundTripsUnderBothModes() {
        for (flip in listOf(false, true)) {
            val c = CoordMapper(size = 15, flipY = flip)
            for (x in 0 until 15) for (y in 0 until 15) {
                val wire = c.toWire(Move(x, y)).split(",")
                val back = c.fromWire(wire[0].toInt(), wire[1].toInt())
                assertThat(back).isEqualTo(Move(x, y))
            }
        }
    }
}
