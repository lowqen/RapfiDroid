package dev.gomoku.zenyth.feature.board

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The upright board screen's one promise (UI overhaul P3): at rest, the sheet
 * never covers the board — and on a screen too short for that, the board gives
 * up exactly the height the handle and the summary line need, no more.
 */
class PortraitMetricsTest {

    private val evalBar = 28.dp // 20dp bar + its 8dp gutter

    @Test
    fun `a tall phone keeps the full-width board and the rest is peek`() {
        // Galaxy A16 upright: 411dp wide, 723dp under the board's header.
        val m = portraitMetrics(411.dp, 723.dp, evalBar, ToolbarHeight, MinPeek)
        assertThat(m.side).isEqualTo(383.dp)
        assertThat(m.peek).isEqualTo(723.dp - 383.dp - ToolbarHeight)
        assertThat(m.peek).isAtLeast(MinPeek)
    }

    @Test
    fun `a short phone shrinks the board just enough for the summary line`() {
        // 360 × 640, under status bar, tab bar and header: 472dp.
        val m = portraitMetrics(360.dp, 472.dp, evalBar, ToolbarHeight, MinPeek)
        assertThat(m.peek).isEqualTo(MinPeek)
        assertThat(m.side).isEqualTo(472.dp - ToolbarHeight - MinPeek)
        assertThat(m.side).isLessThan(360.dp - evalBar)
    }

    @Test
    fun `board, toolbar and peek always add up to the height`() {
        listOf(400.dp, 472.dp, 600.dp, 723.dp, 900.dp).forEach { h ->
            listOf(320.dp, 360.dp, 411.dp, 480.dp).forEach { w ->
                val m = portraitMetrics(w, h, evalBar, ToolbarHeight, MinPeek)
                assertThat(m.side + ToolbarHeight + m.peek).isEqualTo(h)
                assertThat(m.peek).isAtLeast(MinPeek)
                assertThat(m.side).isAtMost(w - evalBar)
            }
        }
    }
}
