package dev.gomoku.rapfidroid.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's visual scale in one place: corners, spacing, touch size.
 *
 * Before this, the scale lived in three spots — the corner steps in [Shapes],
 * the spacing in [Spacing], the screen padding in a constant beside the cards —
 * and none of them could vary with the screen. Mornye's `AppTokens` showed the
 * shape that fixes both: one value object, read through the theme, with a
 * standard set that a variant overrides only where it differs (a wide layout,
 * a compact one) instead of restating the whole table.
 *
 * Read it as `RapfiTheme.tokens`. [Shapes] and [Spacing] are built from
 * [Standard], so a component that already says `MaterialTheme.shapes.medium` or
 * `Spacing.m` is reading these numbers too.
 *
 * Introduced with the values already in use — nothing on screen moved
 * (docs/UI-개편-마스터플랜.md, P0; the screenshot goldens check it).
 */
@Immutable
data class RapfiTokens(
    /** Bars, meters, the small filled things — `shapes.extraSmall`. */
    val radiusBar: Dp,
    /** Chips, inputs, list rows — `shapes.small`. */
    val radiusChip: Dp,
    /** Cards and inset panels — `shapes.medium`. */
    val radiusCard: Dp,
    /** The board frame, dialogs — `shapes.large`. */
    val radiusPanel: Dp,
    /** Sheets, the biggest containers — `shapes.extraLarge`. */
    val radiusSheet: Dp,

    /** Hairline gaps inside a component. */
    val spaceXs: Dp,
    /** Between two things that belong together. */
    val spaceS: Dp,
    /** The default gap. */
    val spaceM: Dp,
    /** Inside a card. */
    val spaceL: Dp,
    /** Screen margin, and between sections. */
    val spaceXl: Dp,
    /** Between unrelated blocks. */
    val spaceXxl: Dp,

    /** Around the cards of every scrolling screen. */
    val screenPadding: Dp,
    /** Material's minimum interactive size. */
    val minTouchTarget: Dp,
) {
    companion object {
        val Standard = RapfiTokens(
            radiusBar = 4.dp,
            radiusChip = 8.dp,
            radiusCard = 12.dp,
            radiusPanel = 16.dp,
            radiusSheet = 28.dp,
            spaceXs = 2.dp,
            spaceS = 4.dp,
            spaceM = 8.dp,
            spaceL = 12.dp,
            spaceXl = 16.dp,
            spaceXxl = 24.dp,
            screenPadding = 12.dp,
            minTouchTarget = 48.dp,
        )
    }
}

/** Provided by `RapfiDroidTheme`; outside it (a preview, a test) the standard set. */
val LocalRapfiTokens = staticCompositionLocalOf { RapfiTokens.Standard }
