package dev.gomoku.zenyth.core.designsystem.theme

import androidx.compose.ui.unit.Dp

/**
 * The spacing scale: a 4dp grid with one half step, which is what the screens
 * were already reaching for by hand.
 *
 * This is not a rule that every dp in the app must become a token — hundreds of
 * them are drawing measurements, not layout rhythm. It is the scale that new
 * code and every screen being touched uses, so the rhythm converges instead of
 * drifting further apart.
 *
 * The short names for [ZenythTokens.Standard]'s spacing, for code that is not
 * composable; inside a composable `ZenythTheme.tokens` is the same numbers.
 */
object Spacing {
    /** 2dp — hairline gaps inside a component. */
    val xs: Dp = ZenythTokens.Standard.spaceXs

    /** 4dp — between two things that belong together. */
    val s: Dp = ZenythTokens.Standard.spaceS

    /** 8dp — the default gap. */
    val m: Dp = ZenythTokens.Standard.spaceM

    /** 12dp — inside a card. */
    val l: Dp = ZenythTokens.Standard.spaceL

    /** 16dp — screen margin, and between sections. */
    val xl: Dp = ZenythTokens.Standard.spaceXl

    /** 24dp — between unrelated blocks. */
    val xxl: Dp = ZenythTokens.Standard.spaceXxl
}
