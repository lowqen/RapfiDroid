package dev.gomoku.rapfidroid.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.Move
import org.junit.Test

/**
 * Drawing and touch share one geometry, so this is the test that keeps a stone
 * from landing on the wrong point after a layout change.
 */
class BoardGeometryTest {

    @Test
    fun everyIntersectionHitsItsOwnCell() {
        val g = BoardGeometry(side = 1080f, n = 15)
        for (y in 0 until 15) {
            for (x in 0 until 15) {
                assertThat(g.cellAt(g.cx(x), g.cy(y))).isEqualTo(Move(x, y))
            }
        }
    }

    @Test
    fun aTouchNearAnIntersectionSnapsToIt() {
        val g = BoardGeometry(side = 1080f, n = 15)
        val nearly = g.step * 0.45f
        assertThat(g.cellAt(g.cx(7) + nearly, g.cy(7) - nearly)).isEqualTo(Move(7, 7))
    }

    @Test
    fun touchesOutsideTheGridClampToTheEdge() {
        val g = BoardGeometry(side = 1080f, n = 15)
        assertThat(g.cellAt(0f, 0f)).isEqualTo(Move(0, 0))
        assertThat(g.cellAt(1080f, 1080f)).isEqualTo(Move(14, 14))
    }

    /** The grid plus its label margins must fill the square exactly. */
    @Test
    fun theBoardFillsItsSquare() {
        val side = 1000f
        val g = BoardGeometry(side = side, n = 15)
        assertThat(g.origin).isWithin(0.01f).of(g.step * BoardGeometry.MARGIN)
        assertThat(g.cx(14) + g.origin).isWithin(0.01f).of(side)
    }

    /** Odd board sizes are supported settings (main.c allows 15 and 20). */
    @Test
    fun otherBoardSizesWorkToo() {
        listOf(9, 13, 15, 20).forEach { n ->
            val g = BoardGeometry(side = 720f, n = n)
            assertThat(g.cellAt(g.cx(n - 1), g.cy(0))).isEqualTo(Move(n - 1, 0))
        }
    }
}
