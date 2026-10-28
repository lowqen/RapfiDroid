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
            "light" to (RapfiColors.Light to LightColorsForTest),
            "dark" to (RapfiColors.Dark to DarkColorsForTest),
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
        listOf(RapfiColors.Light, RapfiColors.Dark).forEach { c ->
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
        listOf(Ink, Neutral, NeutralVariant, Sage, Clay, Signal, ResultBlue, ResultGreen, Violet, Lilac)
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

    // ---- theme choices (UI overhaul P1: AMOLED, wallpaper tone) --------------

    /** No tone, then every 15° of the wheel — any wallpaper a phone can have. */
    private val hues: List<Float?> = listOf<Float?>(null) + (0 until 360 step 15).map { it.toFloat() }

    private fun variants(): List<Pair<String, ColorScheme>> = hues.flatMap { hue ->
        listOf(
            "light@$hue" to colorSchemeFor(dark = false, amoled = false, chromeHue = hue),
            "dark@$hue" to colorSchemeFor(dark = true, amoled = false, chromeHue = hue),
            "amoled@$hue" to colorSchemeFor(dark = true, amoled = true, chromeHue = hue),
        )
    }

    @Test
    fun `every theme choice keeps every text pair at AA`() {
        variants().forEach { (name, s) ->
            textPairs(s).forEach { (label, pair) ->
                assertWithMessage("$name — $label")
                    .that(contrastRatio(pair.first, pair.second)).isAtLeast(aa)
            }
            assertWithMessage("$name — outline").that(contrastRatio(s.outline, s.surface)).isAtLeast(aaLarge)
            assertWithMessage("$name — outlineVariant").that(contrastRatio(s.outlineVariant, s.surface)).isAtLeast(1.15f)
        }
    }

    @Test
    fun `the app's own colours stay readable whatever the theme choice`() {
        variants().forEach { (name, s) ->
            val colors = if (name.startsWith("light")) RapfiColors.Light else RapfiColors.Dark
            listOf(colors.resultBlack, colors.resultWhite, colors.resultDraw, colors.positive, colors.caution).forEach { c ->
                assertWithMessage("$name — ${c.toHex()} on surface").that(contrastRatio(c, s.surface)).isAtLeast(aa)
                assertWithMessage("$name — ${c.toHex()} on card").that(contrastRatio(c, s.surfaceContainer)).isAtLeast(aa)
            }
        }
    }

    @Test
    fun `the wallpaper tone leaves the paper nearly colourless`() {
        // D1: the wallpaper moves the hue of the paper and the outlines, never
        // enough for either to read as a colour.
        variants().forEach { (name, s) ->
            listOf(
                "surface" to s.surface, "surfaceContainer" to s.surfaceContainer,
                "surfaceContainerHighest" to s.surfaceContainerHighest, "onSurface" to s.onSurface,
                "outline" to s.outline,
            ).forEach { (role, c) ->
                assertWithMessage("$name — $role chroma").that(oklchOf(c)[1]).isAtMost(0.025f)
            }
        }
    }

    @Test
    fun `the accent does not follow the wallpaper`() {
        // A wallpaper-green button is the collision D1 rules out: the violet
        // accent is the same whatever the wallpaper.
        hues.forEach { hue ->
            assertThat(colorSchemeFor(dark = false, amoled = false, chromeHue = hue).primary).isEqualTo(LightColorsForTest.primary)
            assertThat(colorSchemeFor(dark = true, amoled = false, chromeHue = hue).primary).isEqualTo(DarkColorsForTest.primary)
            assertThat(colorSchemeFor(dark = true, amoled = false, chromeHue = hue).secondaryContainer).isEqualTo(DarkColorsForTest.secondaryContainer)
        }
    }

    @Test
    fun `a tinted button never passes for a colour that means something`() {
        // The primary button against every colour that carries a meaning, in
        // OKLab. 0.08 is a plain "different colour". The violet accent's worst
        // case is the dark theme against «black won» blue, ~0.09 — why it is
        // violet at 298° and not royal blue (0.05 at 270°).
        variants().forEach { (name, s) ->
            val colors = if (name.startsWith("light")) RapfiColors.Light else RapfiColors.Dark
            listOf(colors.resultBlack, colors.resultWhite, colors.resultDraw, colors.positive, colors.caution).forEach { c ->
                assertWithMessage("$name — primary ${s.primary.toHex()} vs ${c.toHex()}")
                    .that(oklabDistance(s.primary, c)).isAtLeast(0.08f)
            }
        }
    }

    @Test
    fun `amoled is black and its cards still stand off it`() {
        val s = colorSchemeFor(dark = true, amoled = true, chromeHue = null)
        assertThat(luminanceOf(s.surface)).isWithin(0.0005f).of(0f)
        assertThat(luminanceOf(s.background)).isWithin(0.0005f).of(0f)
        // Cards, the navigation bar and dialogs climb in Material's dark steps.
        val ladder = listOf(s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest)
            .map { luminanceOf(it) }
        assertThat(ladder).isInStrictOrder()
        // Tone 7 on black is 1.155:1 — faint on purpose (Mornye's #161616 does
        // the same job); the card's hairline border carries the edge.
        assertThat(contrastRatio(s.surfaceContainer, s.surface)).isAtLeast(1.12f)
        assertThat(contrastRatio(s.outlineVariant, s.surfaceContainer)).isAtLeast(1.15f)
        // The board keeps its dark wood, which still sits above the page.
        assertThat(luminanceOf(BoardSkin.Dark.wood)).isGreaterThan(luminanceOf(s.surface))
    }

    @Test
    fun `no wallpaper tone is the app's own palette exactly`() {
        assertThat(colorSchemeFor(dark = false, amoled = false, chromeHue = null)).isSameInstanceAs(LightColorsForTest)
        assertThat(colorSchemeFor(dark = true, amoled = false, chromeHue = null)).isSameInstanceAs(DarkColorsForTest)
    }

    @Test
    fun `reading a colour back gives the hue it was made with`() {
        val c = oklch(0.6f, 0.08f, 148f)
        val (_, chroma, hue) = oklchOf(c)
        assertThat(hue).isWithin(1.5f).of(148f)
        assertThat(chroma).isWithin(0.005f).of(0.08f)
    }

    private fun oklabDistance(a: Color, b: Color): Float {
        fun lab(c: Color): Triple<Float, Float, Float> {
            val (l, ch, h) = oklchOf(c)
            val rad = Math.toRadians(h.toDouble())
            return Triple(l, ch * kotlin.math.cos(rad).toFloat(), ch * kotlin.math.sin(rad).toFloat())
        }
        val (l1, a1, b1) = lab(a)
        val (l2, a2, b2) = lab(b)
        return kotlin.math.sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
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
