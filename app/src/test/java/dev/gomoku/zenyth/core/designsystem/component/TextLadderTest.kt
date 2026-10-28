package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.gomoku.zenyth.core.designsystem.theme.oklchOf
import dev.gomoku.zenyth.core.model.CellTag
import dev.gomoku.zenyth.core.model.TagKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The text-only values (Zenyth Web's second board redesign): hue carries the rank. */
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

    /**
     * Not neon (2026-10-29): every ink, at every win rate, stays at the chroma
     * the ladder was placed at — HSL at 95 % reached 0.25 in green — and still
     * stands off its dark outline.
     */
    @Test
    fun inksAreQuietAndStillReadOnTheirOutline() {
        val outline = TextOutline.luminance() + 0.05f
        for (percent in 0..100) {
            val ink = palette.textFor(CellTag("$percent", TagKind.RATE, depth = 10, winRatePct = percent))
            assertThat(oklchOf(ink)[1]).isAtMost(0.145f)
            assertThat((ink.luminance() + 0.05f) / outline).isAtLeast(4.5f)
        }
        for (mark in listOf(BoardMarkColors.Good, BoardMarkColors.Bad)) {
            assertThat(oklchOf(mark)[1]).isAtMost(0.145f)
        }
    }

    /** Red stays the deepest warm ink and yellow the lightest: the weights balance, the poles still read. */
    @Test
    fun lightnessFollowsTheHue() {
        val red = oklchOf(textInkAt(0f))[0]
        val yellow = oklchOf(textInkAt(60f))[0]
        val blue = oklchOf(textInkAt(240f))[0]
        assertThat(yellow).isGreaterThan(red)
        assertThat(yellow).isGreaterThan(blue)
    }
}
