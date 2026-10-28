package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * The app's mark — the launcher icon's «eclipse» (an ink stone passing in front
 * of a pearl one, parted by a cut of maple) for use inside the app: the About
 * and What's new windows. Same 100-unit geometry as `ic_launcher_foreground`,
 * on a rounded maple tile; the colours are the artwork's, not the theme's.
 */
@Composable
fun ZenythMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        drawRoundRect(MarkMaple, cornerRadius = CornerRadius(28 * u))
        val y = 50 * u
        drawCircle(MarkPearl, radius = 23 * u, center = Offset(60 * u, y))
        drawCircle(MarkMaple, radius = 25.25f * u, center = Offset(41 * u, y))
        drawCircle(MarkInk, radius = 20.75f * u, center = Offset(41 * u, y))
    }
}

private val MarkMaple = Color(0xFFDDB27C)
private val MarkPearl = Color(0xFFF6F1E9)
private val MarkInk = Color(0xFF171513)
