package dev.gomoku.rapfidroid.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** When the bottom bar folds away, and — the bug it was written for — when it must not. */
class BarHiderTest {

    private val finger = NestedScrollSource.UserInput

    /** One drag frame: the list scrolled [scrolled] of the finger's [dy]. */
    private fun BarHider.drag(dy: Float, scrolled: Float, source: NestedScrollSource = finger) {
        onPostScroll(Offset(0f, scrolled), Offset(0f, dy - scrolled), source)
    }

    @Test
    fun `a drag on a screen that does not scroll leaves the bar alone`() {
        val h = BarHider(threshold = 50f)
        repeat(20) { h.drag(dy = -30f, scrolled = 0f) }
        assertThat(h.hidden).isFalse()
    }

    @Test
    fun `reading down a list folds it, turning back brings it`() {
        val h = BarHider(threshold = 50f)
        h.drag(dy = -30f, scrolled = -30f)
        assertThat(h.hidden).isFalse()
        h.drag(dy = -30f, scrolled = -30f)
        assertThat(h.hidden).isTrue()
        h.drag(dy = 60f, scrolled = 60f)
        assertThat(h.hidden).isFalse()
    }

    @Test
    fun `reaching the top brings it back`() {
        val h = BarHider(threshold = 50f)
        h.drag(dy = -80f, scrolled = -80f)
        assertThat(h.hidden).isTrue()
        // Pulling down with nothing left above: none of it scrolls.
        h.drag(dy = 10f, scrolled = 0f)
        assertThat(h.hidden).isFalse()
    }

    @Test
    fun `a fling's tail and the board's tab do nothing`() {
        val h = BarHider(threshold = 50f)
        h.drag(dy = -200f, scrolled = -200f, source = NestedScrollSource.SideEffect)
        assertThat(h.hidden).isFalse()
        h.enabled = false
        h.drag(dy = -200f, scrolled = -200f)
        assertThat(h.hidden).isFalse()
    }
}
