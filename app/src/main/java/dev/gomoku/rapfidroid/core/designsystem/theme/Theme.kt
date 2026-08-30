package dev.gomoku.rapfidroid.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
 * The tones below are Material's own recipe, moved onto this app's ramps: the
 * dark surface family sits a little higher than Material's default because the
 * near-black the desktop has always used is a shade lighter than Material's.
 */
private val DarkColors = darkColorScheme(
    primary = Ink.tone(90),
    onPrimary = Ink.tone(18),
    primaryContainer = Ink.tone(30),
    onPrimaryContainer = Ink.tone(93),
    inversePrimary = Ink.tone(35),

    secondary = Sage.tone(80),
    onSecondary = Sage.tone(18),
    secondaryContainer = Sage.tone(28),
    onSecondaryContainer = Sage.tone(92),

    tertiary = Clay.tone(80),
    onTertiary = Clay.tone(18),
    tertiaryContainer = Clay.tone(28),
    onTertiaryContainer = Clay.tone(92),

    error = Signal.tone(78),
    onError = Signal.tone(16),
    errorContainer = Signal.tone(28),
    onErrorContainer = Signal.tone(92),

    background = Neutral.tone(12),
    onBackground = Neutral.tone(92),
    surface = Neutral.tone(12),
    onSurface = Neutral.tone(92),
    surfaceVariant = NeutralVariant.tone(26),
    onSurfaceVariant = NeutralVariant.tone(78),
    surfaceTint = Ink.tone(90),
    inverseSurface = Neutral.tone(90),
    inverseOnSurface = Neutral.tone(18),

    outline = NeutralVariant.tone(52),
    outlineVariant = NeutralVariant.tone(28),
    scrim = Color.Black,

    surfaceBright = Neutral.tone(30),
    surfaceDim = Neutral.tone(9),
    surfaceContainerLowest = Neutral.tone(8),
    surfaceContainerLow = Neutral.tone(14),
    surfaceContainer = Neutral.tone(17),
    surfaceContainerHigh = Neutral.tone(21),
    surfaceContainerHighest = Neutral.tone(25),
)

private val LightColors = lightColorScheme(
    primary = Ink.tone(24),
    onPrimary = Neutral.tone(99),
    primaryContainer = Ink.tone(90),
    onPrimaryContainer = Ink.tone(18),
    inversePrimary = Ink.tone(80),

    secondary = Sage.tone(36),
    onSecondary = Neutral.tone(100),
    secondaryContainer = Sage.tone(90),
    onSecondaryContainer = Sage.tone(20),

    tertiary = Clay.tone(38),
    onTertiary = Neutral.tone(100),
    tertiaryContainer = Clay.tone(90),
    onTertiaryContainer = Clay.tone(20),

    error = Signal.tone(42),
    onError = Neutral.tone(100),
    errorContainer = Signal.tone(92),
    onErrorContainer = Signal.tone(20),

    background = Neutral.tone(98),
    onBackground = Neutral.tone(14),
    surface = Neutral.tone(98),
    onSurface = Neutral.tone(14),
    surfaceVariant = NeutralVariant.tone(92),
    onSurfaceVariant = NeutralVariant.tone(38),
    surfaceTint = Ink.tone(24),
    inverseSurface = Neutral.tone(20),
    inverseOnSurface = Neutral.tone(96),

    outline = NeutralVariant.tone(52),
    outlineVariant = NeutralVariant.tone(84),
    scrim = Color.Black,

    surfaceBright = Neutral.tone(99),
    surfaceDim = Neutral.tone(88),
    surfaceContainerLowest = Neutral.tone(100),
    surfaceContainerLow = Neutral.tone(96),
    surfaceContainer = Neutral.tone(94),
    surfaceContainerHigh = Neutral.tone(92),
    surfaceContainerHighest = Neutral.tone(90),
)

/** The app's own colours, alongside Material's. See [YixinColors]. */
val LocalYixinColors = staticCompositionLocalOf { YixinColors.Light }

/**
 * Everything the theme offers that MaterialTheme has no name for.
 *
 *     Text(label, color = YixinTheme.colors.resultBlack)
 *     GomokuBoard(render, skin = YixinTheme.board)
 *     Spacer(Modifier.height(YixinTheme.spacing.l))
 */
object YixinTheme {
    val colors: YixinColors
        @Composable @ReadOnlyComposable get() = LocalYixinColors.current

    /** Shorthand for the board's skin — by far the most-read entry here. */
    val board: BoardSkin
        @Composable @ReadOnlyComposable get() = LocalYixinColors.current.board

    val spacing: Spacing get() = Spacing
}

/**
 * @param darkTheme follows settings.txt line 27, not the system — the desktop
 *   setting is the oracle, and a user who imported their PC settings expects the
 *   app to look like their PC. [isSystemInDarkTheme] is only the default.
 *
 * **Dynamic colour is deliberately not offered.** Material You would repaint the
 * chrome from the user's wallpaper, and this app already spends colour on
 * meaning: a wallpaper-green button next to a board where green means "even" is
 * a collision the user cannot switch off.
 */
@Composable
fun RapfiDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalYixinColors provides if (darkTheme) YixinColors.Dark else YixinColors.Light,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

/** The two schemes, for the theme's own contrast test — not for screens. */
internal val DarkColorsForTest = DarkColors
internal val LightColorsForTest = LightColors
