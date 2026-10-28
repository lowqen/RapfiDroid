package dev.gomoku.zenyth.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Both schemes, **every role filled**.
 *
 * Material falls back to its own baseline — a purple — for any role a scheme
 * leaves out, and a scheme that names eleven of thirty roles is therefore two
 * palettes at once: the app's on the parts somebody thought about, and purple
 * on the rest (every card, the navigation bar, every divider, the big analyse
 * button). Filling the table is not thoroughness for its own sake; it is what
 * makes the app one colour system.
 *
 * The tones below are Material's own recipe, moved onto this app's ramps, and
 * since UI overhaul 2 (M1) set to the relations measured off Mornye's screens:
 * a near-white page (L* 99) with the faces one step **darker** (L* 95), no
 * borders; dark the same the other way (page 10, faces 18). `surfaceContainer`
 * is the face of every card and group row, and of the navigation bar.
 */
private fun darkColors(c: ChromeRamps) = darkColorScheme(
    primary = Violet.tone(80),
    onPrimary = Violet.tone(20),
    primaryContainer = Violet.tone(30),
    onPrimaryContainer = Violet.tone(92),
    inversePrimary = Violet.tone(40),

    secondary = Lilac.tone(80),
    onSecondary = Lilac.tone(20),
    secondaryContainer = Lilac.tone(30),
    onSecondaryContainer = Lilac.tone(92),

    tertiary = Clay.tone(80),
    onTertiary = Clay.tone(18),
    tertiaryContainer = Clay.tone(28),
    onTertiaryContainer = Clay.tone(92),

    error = Signal.tone(78),
    onError = Signal.tone(16),
    errorContainer = Signal.tone(28),
    onErrorContainer = Signal.tone(92),

    background = c.neutral.tone(10),
    onBackground = c.neutral.tone(92),
    surface = c.neutral.tone(10),
    onSurface = c.neutral.tone(92),
    surfaceVariant = c.neutralVariant.tone(26),
    onSurfaceVariant = c.neutralVariant.tone(80),
    surfaceTint = Violet.tone(80),
    inverseSurface = c.neutral.tone(90),
    inverseOnSurface = c.neutral.tone(20),

    outline = c.neutralVariant.tone(60),
    outlineVariant = c.neutralVariant.tone(30),
    scrim = Color.Black,

    surfaceBright = c.neutral.tone(26),
    surfaceDim = c.neutral.tone(8),
    surfaceContainerLowest = c.neutral.tone(7),
    surfaceContainerLow = c.neutral.tone(14),
    surfaceContainer = c.neutral.tone(18),
    surfaceContainerHigh = c.neutral.tone(22),
    surfaceContainerHighest = c.neutral.tone(26),
)

private fun lightColors(c: ChromeRamps) = lightColorScheme(
    primary = Violet.tone(40),
    onPrimary = c.neutral.tone(100),
    primaryContainer = Violet.tone(90),
    onPrimaryContainer = Violet.tone(20),
    inversePrimary = Violet.tone(80),

    secondary = Lilac.tone(40),
    onSecondary = c.neutral.tone(100),
    secondaryContainer = Lilac.tone(90),
    onSecondaryContainer = Lilac.tone(20),

    tertiary = Clay.tone(38),
    onTertiary = c.neutral.tone(100),
    tertiaryContainer = Clay.tone(90),
    onTertiaryContainer = Clay.tone(20),

    error = Signal.tone(42),
    onError = c.neutral.tone(100),
    errorContainer = Signal.tone(92),
    onErrorContainer = Signal.tone(20),

    background = c.neutral.tone(99),
    onBackground = c.neutral.tone(10),
    surface = c.neutral.tone(99),
    onSurface = c.neutral.tone(10),
    surfaceVariant = c.neutralVariant.tone(90),
    onSurfaceVariant = c.neutralVariant.tone(30),
    surfaceTint = Violet.tone(40),
    inverseSurface = c.neutral.tone(20),
    inverseOnSurface = c.neutral.tone(95),

    outline = c.neutralVariant.tone(50),
    outlineVariant = c.neutralVariant.tone(82),
    scrim = Color.Black,

    surfaceBright = c.neutral.tone(99),
    surfaceDim = c.neutral.tone(88),
    surfaceContainerLowest = c.neutral.tone(100),
    surfaceContainerLow = c.neutral.tone(96),
    surfaceContainer = c.neutral.tone(95),
    surfaceContainerHigh = c.neutral.tone(92),
    surfaceContainerHighest = c.neutral.tone(90),
)

private val DarkColors = darkColors(ChromeRamps.Standard)
private val LightColors = lightColors(ChromeRamps.Standard)

/**
 * The two ramps the page is drawn from — the paper and the outlines.
 *
 * They are the only colour anything outside the app may move (decision D1 of
 * docs/UI-개편-마스터플랜.md). The wallpaper tone swaps their **hue** and keeps
 * their chroma, which is a few thousandths: the paper leans toward the
 * wallpaper, and nothing on screen starts to mean something. The accent
 * ([Violet], [Lilac]) does not follow the wallpaper — a wallpaper-green button
 * is exactly the collision D1 rules out — and Sage, Clay, Signal, the result
 * colours and the board are not chrome and never move.
 */
internal class ChromeRamps(val neutral: TonalRamp, val neutralVariant: TonalRamp) {
    companion object {
        val Standard = ChromeRamps(Mist, MistVariant)

        fun ofHue(hue: Float) = ChromeRamps(
            TonalRamp(hue, Mist.chroma),
            TonalRamp(hue, MistVariant.chroma),
        )
    }
}

/**
 * Black instead of near-black, for OLED screens — Mornye's AMOLED pass, on this
 * app's warm paper.
 *
 * Only the page goes to tone 0. The containers keep Material's dark spacing
 * between them (tones 6 · 10 · 14 · 19 — one step above Mornye's 5 · 7 since the
 * faces lost their borders and carry the edge alone) so a card still stands off
 * the page where no shadow can show, and
 * the navigation bar keeps its container tier instead of dissolving into the
 * black. The board keeps its dark wood: it is drawn on the page, not by it.
 */
internal fun ColorScheme.amoled(neutral: TonalRamp): ColorScheme = copy(
    background = neutral.tone(0),
    surface = neutral.tone(0),
    surfaceDim = neutral.tone(0),
    surfaceContainerLowest = neutral.tone(0),
    surfaceContainerLow = neutral.tone(6),
    surfaceContainer = neutral.tone(10),
    surfaceContainerHigh = neutral.tone(14),
    surfaceContainerHighest = neutral.tone(19),
    surfaceBright = neutral.tone(24),
)

/** The scheme for one theme choice; [chromeHue] null is the app's own warm paper. */
internal fun colorSchemeFor(dark: Boolean, amoled: Boolean, chromeHue: Float?): ColorScheme {
    val chrome = chromeHue?.let(ChromeRamps::ofHue) ?: ChromeRamps.Standard
    return when {
        !dark -> if (chromeHue == null) LightColors else lightColors(chrome)
        amoled -> (if (chromeHue == null) DarkColors else darkColors(chrome)).amoled(chrome.neutral)
        else -> if (chromeHue == null) DarkColors else darkColors(chrome)
    }
}

/** The app's own colours, alongside Material's. See [RapfiColors]. */
val LocalYixinColors = staticCompositionLocalOf { RapfiColors.Light }

/**
 * Everything the theme offers that MaterialTheme has no name for.
 *
 *     Text(label, color = RapfiTheme.colors.resultBlack)
 *     GomokuBoard(render, skin = RapfiTheme.board)
 *     Spacer(Modifier.height(RapfiTheme.spacing.l))
 *     Modifier.padding(RapfiTheme.tokens.screenPadding)
 */
object RapfiTheme {
    val colors: RapfiColors
        @Composable @ReadOnlyComposable get() = LocalYixinColors.current

    /** Shorthand for the board's skin — by far the most-read entry here. */
    val board: BoardSkin
        @Composable @ReadOnlyComposable get() = LocalYixinColors.current.board

    val spacing: Spacing get() = Spacing

    /** Corners, spacing and touch size; see [RapfiTokens]. */
    val tokens: RapfiTokens
        @Composable @ReadOnlyComposable get() = LocalRapfiTokens.current
}

/**
 * @param darkTheme settings.txt line 27 by default — the desktop setting is the
 *   oracle, and a user who imported their PC settings expects the app to look
 *   like their PC — or the phone's own setting when the user chose «기기 설정»
 *   ([themeFollowsSystem]). The activity decides; this only paints.
 * @param amoled black page and containers in the dark theme ([amoled]).
 * @param chromeHue the wallpaper's hue for the chrome ramps, or null.
 *
 * **Full dynamic colour is deliberately not offered.** Material You would
 * repaint the chrome from the user's wallpaper, and this app already spends
 * colour on meaning: a wallpaper-green button next to a board where green means
 * "even" is a collision the user cannot switch off. What is offered instead is
 * the wallpaper's *hue* on the near-colourless chrome ramps ([ChromeRamps]).
 */
@Composable
fun ZenythTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    chromeHue: Float? = null,
    content: @Composable () -> Unit,
) {
    val scheme = remember(darkTheme, amoled, chromeHue) { colorSchemeFor(darkTheme, amoled, chromeHue) }
    val context = LocalContext.current
    val reduced = remember(context) { reducedMotion(context) }
    CompositionLocalProvider(
        LocalYixinColors provides if (darkTheme) RapfiColors.Dark else RapfiColors.Light,
        LocalRapfiTokens provides RapfiTokens.Standard,
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

/** The two schemes, for the theme's own contrast test — not for screens. */
internal val DarkColorsForTest = DarkColors
internal val LightColorsForTest = LightColors
