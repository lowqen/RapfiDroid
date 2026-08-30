package dev.gomoku.rapfidroid.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

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
 * - medium 12dp — cards and inset panels
 * - large 16dp — the board frame, dialogs
 * - extraLarge 28dp — sheets, the biggest containers
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
