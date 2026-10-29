package dev.gomoku.zenyth.core.designsystem.theme

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
 * Read it as `ZenythTheme.tokens`. [Shapes] and [Spacing] are built from
 * [Standard], so a component that already says `MaterialTheme.shapes.medium` or
 * `Spacing.m` is reading these numbers too.
 *
 * Introduced with the values already in use — nothing on screen moved
 * (docs/UI-개편-마스터플랜.md, P0; the screenshot goldens check it).
 */
@Immutable
data class ZenythTokens(
    /** Bars, meters, the small filled things — `shapes.extraSmall`. */
    val radiusBar: Dp,
    /** Chips, inputs, list rows — `shapes.small`. */
    val radiusChip: Dp,
    /** Panels inset in a card, menus — `shapes.medium`. */
    val radiusInset: Dp,
    /** Cards, group rows, dialogs — `shapes.large`. 20 since UI overhaul 2 (Mornye's card corner, measured). */
    val radiusCard: Dp,
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
    /** Between the cards of a list. */
    val listGap: Dp,
    /** Inside a card, and between the things in it. */
    val cardPadding: Dp,
    val cardSpacing: Dp,
    /** Inside one row of a connected group. */
    val rowPaddingH: Dp,
    val rowPaddingV: Dp,
    /** Material's minimum interactive size. */
    val minTouchTarget: Dp,
) {
    companion object {
        val Standard = ZenythTokens(
            radiusBar = 4.dp,
            radiusChip = 8.dp,
            radiusInset = 12.dp,
            radiusCard = 20.dp,
            radiusSheet = 28.dp,
            spaceXs = 2.dp,
            spaceS = 4.dp,
            spaceM = 8.dp,
            spaceL = 12.dp,
            spaceXl = 16.dp,
            spaceXxl = 24.dp,
            // One step roomier than the first cut (user request, 2026-10-29: the
            // screens read as cramped). Zenyth Web's spacing is not changed with it.
            screenPadding = 16.dp,
            listGap = 12.dp,
            cardPadding = 20.dp,
            cardSpacing = 14.dp,
            rowPaddingH = 20.dp,
            rowPaddingV = 16.dp,
            minTouchTarget = 48.dp,
        )
    }
}

/** Provided by `ZenythTheme`; outside it (a preview, a test) the standard set. */
val LocalZenythTokens = staticCompositionLocalOf { ZenythTokens.Standard }
