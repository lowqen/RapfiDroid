package dev.gomoku.zenyth.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * One corner scale, five steps.
 *
 * Six different radii were in use (3, 4, 6, 8, 10 and 15dp), each chosen a
 * screen at a time, which is why a card, the graph inside it and the bar inside
 * that were rounded three different ways. These are the Material slots, so
 * components pick the right one on their own and a screen only names a shape
 * when it draws something Material has no component for.
 *
 * - extraSmall 4dp — bars, meters, the small filled things
 * - small 8dp — chips, inputs, list rows
 * - medium 12dp — panels inset in a card, menus
 * - large 20dp — cards, group rows, dialogs. 16 until UI overhaul 2, which took
 *   Mornye's card corner (measured off its screens, and its own token says 20);
 *   Zenyth Web's `.panel` stays 16 — the two now differ (D5).
 * - extraLarge 28dp — sheets, the biggest containers
 *
 * The numbers live in [RapfiTokens]; this maps them onto the Material slots.
 */
val Shapes = RapfiTokens.Standard.let { t ->
    Shapes(
        extraSmall = RoundedCornerShape(t.radiusBar),
        small = RoundedCornerShape(t.radiusChip),
        medium = RoundedCornerShape(t.radiusInset),
        large = RoundedCornerShape(t.radiusCard),
        extraLarge = RoundedCornerShape(t.radiusSheet),
    )
}
