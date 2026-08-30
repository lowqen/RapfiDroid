package dev.gomoku.rapfidroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.Move
import org.junit.Test

class CoordMapperTest {

    @Test
    fun wireIsRowThenCol() {
        val c = CoordMapper(size = 15, flipY = false)
        // Move(x=col, y=row); wire = "row,col"
        assertThat(c.toWire(Move(x = 8, y = 7))).isEqualTo("7,8")
        assertThat(c.fromWire(row = 7, col = 8)).isEqualTo(Move(x = 8, y = 7))
        assertThat(c.parsePair("7,8")).isEqualTo(Move(x = 8, y = 7))
    }

    @Test
    fun flipReflectsRow() {
        val c = CoordMapper(size = 15, flipY = true)
        assertThat(c.toWire(Move(x = 0, y = 0))).isEqualTo("14,0")
        assertThat(c.fromWire(row = 14, col = 0)).isEqualTo(Move(x = 0, y = 0))
    }

    @Test
    fun roundTripsUnderBothModes() {
        for (flip in listOf(false, true)) {
            val c = CoordMapper(size = 15, flipY = flip)
            for (x in 0 until 15) for (y in 0 until 15) {
                val move = Move(x, y)
                assertThat(c.parsePair(c.toWire(move))).isEqualTo(move)
            }
        }
    }
}
