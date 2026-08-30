package dev.gomoku.rapfidroid.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The contrast table, as a test.
 *
 * A palette is the one part of a design that can be checked by arithmetic, and
 * the failure it catches is not cosmetic: white text on the old light-theme
 * green measured 2.2:1, less than half the 4.5:1 that WCAG AA asks of body
 * text, and nobody noticed because nobody had multiplied it out. Every
 * text-on-background pair the schemes define is listed here, so a future tone
 * change cannot quietly go under.
 *
 * It also prints the table, which is the artefact the design plan asks each step
 * to leave behind — run with `--info` (or read the test's stdout) to see it.
 */
class ThemeContrastTest {

    /** WCAG AA for body text. */
    private val aa = 4.5f

    /** WCAG AA for large text, icons and other non-text essentials. */
    private val aaLarge = 3.0f

    @Test
    fun `every text pair clears AA in both schemes`() {
        listOf("light" to LightColorsForTest, "dark" to DarkColorsForTest).forEach { (name, s) ->
            val rows = textPairs(s)
            println("--- $name scheme ---")
            rows.forEach { (label, pair) ->
                val ratio = contrastRatio(pair.first, pair.second)
                println("%-34s %s on %s  %.2f:1".format(label, pair.first.toHex(), pair.second.toHex(), ratio))
            }
            rows.forEach { (label, pair) ->
                assertWithMessage("$name — $label")
                    .that(contrastRatio(pair.first, pair.second)).isAtLeast(aa)
            }
        }
    }

    @Test
    fun `outlines and disabled marks stay visible`() {
        listOf(LightColorsForTest, DarkColorsForTest).forEach { s ->
            assertThat(contrastRatio(s.outline, s.surface)).isAtLeast(aaLarge)
            // outlineVariant is a divider, not a boundary anyone must find: it
            // only has to be seen, not read.
            assertThat(contrastRatio(s.outlineVariant, s.surface)).isAtLeast(1.15f)
        }
    }

    @Test
    fun `the app's own colours are readable on its own surfaces`() {
        listOf(
            "light" to (YixinColors.Light to LightColorsForTest),
            "dark" to (YixinColors.Dark to DarkColorsForTest),
        ).forEach { (name, pair) ->
            val (colors, scheme) = pair
            listOf(
                "resultBlack" to colors.resultBlack,
                "resultWhite" to colors.resultWhite,
                "resultDraw" to colors.resultDraw,
                "positive" to colors.positive,
                "caution" to colors.caution,
            ).forEach { (label, color) ->
                val onSurface = contrastRatio(color, scheme.surface)
                val onCard = contrastRatio(color, scheme.surfaceContainer)
                println("$name %-12s %s  surface %.2f:1  card %.2f:1".format(label, color.toHex(), onSurface, onCard))
                assertThat(onSurface).isAtLeast(aa)
                assertThat(onCard).isAtLeast(aa)
            }
        }
    }

    @Test
    fun `the three result colours stay apart from each other`() {
        listOf(YixinColors.Light, YixinColors.Dark).forEach { c ->
            // Adjacent segments of one bar: they carry different meanings and
            // must not need a legend to tell apart.
            assertThat(distance(c.resultBlack, c.resultWhite)).isAtLeast(0.20f)
            assertThat(distance(c.resultBlack, c.resultDraw)).isAtLeast(0.20f)
            assertThat(distance(c.resultWhite, c.resultDraw)).isAtLeast(0.20f)
        }
    }

    @Test
    fun `both board skins keep stones and labels legible on their wood`() {
        listOf("light" to BoardSkin.Light, "dark" to BoardSkin.Dark).forEach { (name, skin) ->
            val black = contrastRatio(skin.blackLow, skin.wood)
            val white = contrastRatio(skin.whiteHigh, skin.wood)
            val label = contrastRatio(skin.label, skin.wood)
            val line = contrastRatio(skin.line, skin.wood)
            println("$name board: black %.2f  white %.2f  label %.2f  grid %.2f".format(black, white, label, line))
            assertThat(black).isAtLeast(1.8f)
            assertThat(white).isAtLeast(1.8f)
            // A coordinate is text, but large text on a surface nobody reads
            // words from; AA-large is the right bar.
            assertThat(label).isAtLeast(aaLarge)
            assertThat(line).isAtLeast(1.9f)
            // The stone's own number: white on black, black on white.
            assertThat(contrastRatio(skin.whiteHigh, skin.blackLow)).isAtLeast(aa)
        }
    }

    @Test
    fun `the dark board is no longer the brightest thing on screen`() {
        // The complaint the dark wood exists to answer: a gold board at night
        // out-shone the text on top of it.
        val wood = luminanceOf(BoardSkin.Dark.wood)
        assertThat(wood).isLessThan(luminanceOf(DarkColorsForTest.onSurface))
        assertThat(wood).isGreaterThan(luminanceOf(DarkColorsForTest.surface))
    }

    @Test
    fun `every ramp climbs from black to white`() {
        listOf(Ink, Neutral, NeutralVariant, Sage, Clay, Signal, ResultBlue, ResultGreen)
            .forEach { assertThat(rampIsMonotone(it)).isTrue() }
    }

    @Test
    fun `a tone is the lightness it says it is`() {
        // Tone is CIE L*, so tone 50 must land on the mid grey a person sees as
        // half way — luminance 0.184, not 0.5.
        assertThat(luminanceOf(Neutral.tone(50))).isWithin(0.01f).of(0.1842f)
        assertThat(luminanceOf(Neutral.tone(0))).isWithin(0.005f).of(0f)
        assertThat(luminanceOf(Neutral.tone(100))).isWithin(0.02f).of(1f)
    }

    @Test
    fun `chroma is capped by the gamut, never clipped by channel`() {
        // Asking for more chroma than sRGB holds must keep the hue and the
        // lightness and give up only the saturation, so a ramp cannot bend.
        val greedy = oklch(0.5f, 0.4f, 148f)
        val sane = oklch(0.5f, 0.06f, 148f)
        assertThat(luminanceOf(greedy)).isWithin(0.02f).of(luminanceOf(sane))
    }

    /** Every pair in the schemes where one colour is text on the other. */
    private fun textPairs(s: ColorScheme): List<Pair<String, Pair<Color, Color>>> = listOf(
        "onBackground / background" to (s.onBackground to s.background),
        "onSurface / surface" to (s.onSurface to s.surface),
        "onSurfaceVariant / surface" to (s.onSurfaceVariant to s.surface),
        "onSurface / surfaceContainer" to (s.onSurface to s.surfaceContainer),
        "onSurfaceVariant / surfaceContainer" to (s.onSurfaceVariant to s.surfaceContainer),
        "onSurface / surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
        "onSurfaceVariant / surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
        "onPrimary / primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer / primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondary / secondary" to (s.onSecondary to s.secondary),
        "onSecondaryContainer / secondaryCont." to (s.onSecondaryContainer to s.secondaryContainer),
        "onTertiary / tertiary" to (s.onTertiary to s.tertiary),
        "onTertiaryContainer / tertiaryCont." to (s.onTertiaryContainer to s.tertiaryContainer),
        "onError / error" to (s.onError to s.error),
        "onErrorContainer / errorContainer" to (s.onErrorContainer to s.errorContainer),
        "inverseOnSurface / inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
        "primary / surface" to (s.primary to s.surface),
        "secondary / surface" to (s.secondary to s.surface),
        "tertiary / surface" to (s.tertiary to s.surface),
        "error / surface" to (s.error to s.surface),
    )

    /** Rough perceptual distance, enough to say "these two are not the same colour". */
    private fun distance(a: Color, b: Color): Float {
        val dr = a.red - b.red
        val dg = a.green - b.green
        val db = a.blue - b.blue
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }
}
