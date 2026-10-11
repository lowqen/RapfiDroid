package dev.gomoku.rapfidroid.core.designsystem.component

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The text-only values (RapfiWeb's second board redesign): hue carries the rank. */
class TextLadderTest {

    private val palette = TagPalette()

    @Test
    fun hueRunsFromBlueToRedAsTheWinRateRises() {
        var previous = Float.MAX_VALUE
        for (percent in 0..100 step 5) {
            val hue = palette.textHue(palette.positionOf(percent))
            assertThat(hue).isAtMost(previous)
            previous = hue
        }
        assertThat(palette.textHue(palette.positionOf(0))).isGreaterThan(200f)
        assertThat(palette.textHue(palette.positionOf(100))).isLessThan(30f)
        assertThat(palette.textHue(0.5f)).isEqualTo(120f)
    }

    @Test
    fun hslMatchesTheCssFormula() {
        assertThat(hsl(0f, 1f, 0.5f)).isEqualTo(Color(1f, 0f, 0f))
        assertThat(hsl(120f, 1f, 0.5f)).isEqualTo(Color(0f, 1f, 0f))
        assertThat(hsl(240f, 1f, 0.5f)).isEqualTo(Color(0f, 0f, 1f))
    }
}
