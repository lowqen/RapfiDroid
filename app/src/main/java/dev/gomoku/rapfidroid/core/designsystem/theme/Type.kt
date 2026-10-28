package dev.gomoku.rapfidroid.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.gomoku.rapfidroid.R

/**
 * Pretendard, the face RapfiWeb uses (OFL-1.1), shipped as one variable font
 * cut down to the 2,350 Hangul of KS X 1001 plus Latin and symbols — 1.3 MB
 * instead of 6.7. Renamed RapfiSans inside, as the licence's reserved-name rule
 * asks of a modified copy. A syllable outside the subset falls back to the
 * system font for that one glyph, which only user-typed text can reach.
 */
@OptIn(ExperimentalTextApi::class)
val RapfiSans = FontFamily(
    Font(R.font.rapfi_sans, FontWeight.Normal),
    Font(R.font.rapfi_sans, FontWeight.Medium),
    Font(R.font.rapfi_sans, FontWeight.SemiBold),
    Font(R.font.rapfi_sans, FontWeight.Bold),
    Font(R.font.rapfi_sans, FontWeight.ExtraBold),
)

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
    displayLarge = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp),

    headlineLarge = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),

    // Titles and body one step up from the first cut (user request,
    // 2026-10-29: too small, too dense). Labels stay — they are the dense
    // parts: chips, tabs, the numbers under a stat.
    titleLarge = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),

    bodyLarge = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),

    labelLarge = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
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
