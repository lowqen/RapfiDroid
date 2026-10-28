package dev.gomoku.zenyth.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The board tab, chosen: `grid_on` with its cells filled and its lines left as
 * gaps. Material Icons draws `grid_on` the same filled or outlined, so the
 * selected half of the pair is drawn here, on the same 24-unit grid and the
 * same cell edges (2 · 8 | 10 · 14 | 16 · 22) so the two swap without a jump.
 */
internal val FilledGrid: ImageVector = ImageVector.Builder(
    name = "FilledGrid",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    val edges = listOf(2f to 8f, 10f to 14f, 16f to 22f)
    path(fill = SolidColor(Color.Black)) {
        for ((top, bottom) in edges) for ((left, right) in edges) roundRect(left, top, right, bottom, 1.5f)
    }
}.build()

private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
    moveTo(l + rad, t)
    horizontalLineTo(r - rad)
    arcTo(rad, rad, 0f, false, true, r, t + rad)
    verticalLineTo(b - rad)
    arcTo(rad, rad, 0f, false, true, r - rad, b)
    horizontalLineTo(l + rad)
    arcTo(rad, rad, 0f, false, true, l, b - rad)
    verticalLineTo(t + rad)
    arcTo(rad, rad, 0f, false, true, l + rad, t)
    close()
}
