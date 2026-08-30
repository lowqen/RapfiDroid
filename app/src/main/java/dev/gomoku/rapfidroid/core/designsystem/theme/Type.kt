package dev.gomoku.rapfidroid.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale — **all fifteen slots**, not the three that happened to be
 * customised.
 *
 * Leaving twelve of them at Material's defaults meant two scales were running at
 * once: the ones named here, and Material's own beside them with a different
 * rhythm and different letter spacing. A card title and the label under it came
 * from different systems.
 *
 * Tuned for a dense analysis tool rather than for a reading app: a little
 * tighter than Material's defaults, with the weight — not the size — carrying
 * most of the hierarchy, so more of the screen stays available for the board and
 * the numbers. Letter spacing is zero or negative throughout; Material's
 * positive tracking is drawn for Latin and makes Hangul look loose.
 */
val Typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp),

    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),

    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),

    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),

    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

/**
 * The same style with **tabular figures**.
 *
 * A proportional 1 is narrower than a 0, so a number that updates in place —
 * node count, depth, speed, a clock, a win rate — changes width several times a
 * second and drags the whole row left and right with it. `tnum` fixes every
 * digit to one advance, which costs nothing and stops the status bar shivering.
 *
 * Use it on anything that counts:
 *
 *     Text(nodes, style = MaterialTheme.typography.labelLarge.tabular())
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Monospace style for the piskvork console and coordinate lines. */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)
