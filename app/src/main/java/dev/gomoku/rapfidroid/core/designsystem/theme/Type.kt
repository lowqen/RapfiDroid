package dev.gomoku.rapfidroid.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.gomoku.rapfidroid.R

/**
 * Pretendard, the face RapfiWeb uses (OFL-1.1), shipped as one variable font
 * cut down to the 2,350 Hangul of KS X 1001 plus Latin and symbols — 1.3 MB
 * instead of 6.7. Renamed RapfiSans inside, as the licence's reserved-name rule
 * asks of a modified copy. A syllable outside the subset falls back to the
 * system font for that one glyph, which only user-typed text can reach.
 *
 * **Each weight sets the `wght` axis itself.** The file's default is 400, and a
 * `Font` entry without `variationSettings` is the file at its default: every
 * weight here was drawn Regular — the 34sp ExtraBold page titles and the
 * SemiBold section heads included (found on a phone, 2026-10-29) — and since
 * each weight is declared to exist, nothing synthesised a bold either.
 */
@OptIn(ExperimentalTextApi::class)
val RapfiSans = FontFamily(
    rapfiSans(FontWeight.Normal),
    rapfiSans(FontWeight.Medium),
    rapfiSans(FontWeight.SemiBold),
    rapfiSans(FontWeight.Bold),
    rapfiSans(FontWeight.ExtraBold),
)

/** One weight of the variable file, its `wght` axis set to that weight (45 ~ 930 in the file). */
@OptIn(ExperimentalTextApi::class)
private fun rapfiSans(weight: FontWeight) = Font(
    R.font.rapfi_sans,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
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
    // The page title (UI overhaul 2, M2): what a list screen's tall header shows
    // before it folds — Mornye's «설정» / «디자인», measured at ~34sp and very
    // heavy. Material's large top bar reads this slot for its expanded title.
    headlineMedium = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.6).sp),
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

/**
 * The title of one row in a settings group or list (UI overhaul 2, M2):
 * Mornye's rows read at ~18sp, regular weight — the size carries the row, the
 * page title carries the weight. Its own style rather than `bodyLarge`, which
 * every text field also reads.
 */
val RowTitleStyle = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp)

/** The line under a row's title: one step down, in the variant colour at the call site. */
val RowNoteStyle = TextStyle(fontFamily = RapfiSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)

/** Monospace style for the piskvork console and coordinate lines. */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)
