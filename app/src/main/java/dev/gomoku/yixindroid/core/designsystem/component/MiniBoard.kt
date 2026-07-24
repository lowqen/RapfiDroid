package dev.gomoku.yixindroid.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import dev.gomoku.yixindroid.core.model.Move
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val MiniGold = Color(0xFFE7C77E)
private val MiniLine = Color(0xFF8A6A3B)
private val MiniBlack = Color(0xFF1C1A17)
private val MiniWhite = Color(0xFFF5F2EA)

/**
 * A compact board that auto-crops a square window around the centre just large
 * enough to hold [stones] (with one line of padding). Used for opening (주형)
 * and 5-move shape thumbnails. Sized by the caller's [modifier] (pass a square).
 */
@androidx.compose.runtime.Composable
fun MiniBoard(
    stones: List<Move>,
    modifier: Modifier = Modifier,
    showNumbers: Boolean = true,
) {
    val center = Move.DEFAULT_SIZE / 2   // 7
    val reach = stones.maxOfOrNull { max(abs(it.x - center), abs(it.y - center)) } ?: 2
    val half = (reach + 1).coerceAtLeast(2)
    val lo = center - half
    val n = 2 * half + 1

    Canvas(modifier = modifier) {
        val side = min(size.width, size.height)
        val step = side / (n + 1)
        val ox = (size.width - side) / 2f
        val oy = (size.height - side) / 2f
        val radius = step * 0.42f
        fun cx(gx: Int) = ox + step + (gx - lo) * step
        fun cy(gy: Int) = oy + step + (gy - lo) * step

        drawRect(MiniGold, topLeft = Offset(ox, oy), size = androidx.compose.ui.geometry.Size(side, side))
        for (i in 0 until n) {
            val a = lo + i
            drawLine(MiniLine, Offset(cx(lo), cy(a)), Offset(cx(lo + n - 1), cy(a)), strokeWidth = 1f)
            drawLine(MiniLine, Offset(cx(a), cy(lo)), Offset(cx(a), cy(lo + n - 1)), strokeWidth = 1f)
        }
        // centre dot
        drawCircle(MiniLine, radius = step * 0.08f, center = Offset(cx(center), cy(center)))

        stones.forEachIndexed { i, m ->
            val black = i % 2 == 0
            drawCircle(if (black) MiniBlack else MiniWhite, radius, Offset(cx(m.x), cy(m.y)))
            drawCircle(Color(0x55000000), radius, Offset(cx(m.x), cy(m.y)), style = Stroke(1f))
            if (showNumbers) {
                val paint = android.graphics.Paint().apply {
                    color = (if (black) MiniWhite else MiniBlack).toArgb()
                    textSize = radius * 1.05f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "${i + 1}", cx(m.x), cy(m.y) - (paint.descent() + paint.ascent()) / 2, paint,
                )
            }
        }
    }
}
