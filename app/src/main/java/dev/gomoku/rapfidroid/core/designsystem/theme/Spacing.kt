package dev.gomoku.rapfidroid.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing scale: a 4dp grid with one half step, which is what the screens
 * were already reaching for by hand.
 *
 * This is not a rule that every dp in the app must become a token — hundreds of
 * them are drawing measurements, not layout rhythm. It is the scale that new
 * code and every screen being touched uses, so the rhythm converges instead of
 * drifting further apart.
 */
object Spacing {
    /** 2dp — hairline gaps inside a component. */
    val xs = 2.dp

    /** 4dp — between two things that belong together. */
    val s = 4.dp

    /** 8dp — the default gap. */
    val m = 8.dp

    /** 12dp — inside a card. */
    val l = 12.dp

    /** 16dp — screen margin, and between sections. */
    val xl = 16.dp

    /** 24dp — between unrelated blocks. */
    val xxl = 24.dp
}
