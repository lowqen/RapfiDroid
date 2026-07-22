package dev.gomoku.yixindroid.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.gomoku.yixindroid.core.model.Move
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything the board needs to draw one frame (pure data → easy to preview). */
data class BoardRender(
    val size: Int = Move.DEFAULT_SIZE,
    val stones: List<Move> = emptyList(),        // played order, black first
    val lastMove: Move? = null,
    val forbidden: List<Move> = emptyList(),
    val ghosts: List<Move> = emptyList(),         // PV preview from the current position
    val bestMark: Move? = null,                   // realtime / PV-head highlight
)

private val Gold = Color(0xFFDCB35C)
private val Line = Color(0xFF7A5A2B)
private val Black = Color(0xFF1C1A17)
private val White = Color(0xFFF5F2EA)
private val Accent = Color(0xFF81B64C)
private val Forbid = Color(0xFFE2705F)

@androidx.compose.runtime.Composable
fun GomokuBoard(
    render: BoardRender,
    modifier: Modifier = Modifier,
    onTap: ((Move) -> Unit)? = null,
) {
    val n = render.size
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (onTap != null) {
                    Modifier.pointerInput(n) {
                        detectTapGestures { offset ->
                            val step = this.size.width.toFloat() / (n + 1)
                            val col = ((offset.x - step) / step).roundToInt().coerceIn(0, n - 1)
                            val row = ((offset.y - step) / step).roundToInt().coerceIn(0, n - 1)
                            onTap(Move(col, row))
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val side = min(size.width, size.height)
        val step = side / (n + 1)
        val radius = step * 0.45f
        fun cx(x: Int) = step + x * step
        fun cy(y: Int) = step + y * step

        drawRect(Gold, size = size)

        // grid
        for (i in 0 until n) {
            drawLine(Line, Offset(cx(0), cy(i)), Offset(cx(n - 1), cy(i)), strokeWidth = 1.5f)
            drawLine(Line, Offset(cx(i), cy(0)), Offset(cx(i), cy(n - 1)), strokeWidth = 1.5f)
        }
        // star points (15x15): center + 4 (3,3)-style
        if (n == 15) {
            listOf(3 to 3, 3 to 11, 11 to 3, 11 to 11, 7 to 7).forEach { (x, y) ->
                drawCircle(Line, radius = step * 0.09f, center = Offset(cx(x), cy(y)))
            }
        }

        drawLabels(n, step)

        // forbidden markers
        render.forbidden.forEach { m ->
            val c = Offset(cx(m.x), cy(m.y))
            val r = radius * 0.7f
            drawLine(Forbid, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), strokeWidth = 3f)
            drawLine(Forbid, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), strokeWidth = 3f)
        }

        // played stones with move numbers
        render.stones.forEachIndexed { i, m ->
            val black = i % 2 == 0
            drawStone(cx(m.x), cy(m.y), radius, black, "${i + 1}", alpha = 1f)
        }

        // last-move ring
        render.lastMove?.let { m ->
            drawCircle(Accent, radius = radius * 0.55f, center = Offset(cx(m.x), cy(m.y)),
                style = Stroke(width = 3f))
        }

        // PV ghosts: continue numbering/colour from the current position
        val start = render.stones.size
        render.ghosts.forEachIndexed { i, m ->
            val black = (start + i) % 2 == 0
            drawStone(cx(m.x), cy(m.y), radius, black, "${i + 1}", alpha = 0.4f)
        }

        // best-move highlight
        render.bestMark?.let { m ->
            drawCircle(Gold, radius = radius * 0.85f, center = Offset(cx(m.x), cy(m.y)),
                style = Stroke(width = 3.5f))
        }
    }
}

private fun DrawScope.drawStone(cx: Float, cy: Float, r: Float, black: Boolean, number: String, alpha: Float) {
    drawCircle(if (black) Black else White, radius = r, center = Offset(cx, cy), alpha = alpha)
    drawCircle(Color(0x55000000), radius = r, center = Offset(cx, cy), alpha = alpha, style = Stroke(width = 1f))
    val paint = android.graphics.Paint().apply {
        color = (if (black) White else Black).copy(alpha = alpha).toArgb()
        textSize = r * 0.9f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        number, cx, cy - (paint.descent() + paint.ascent()) / 2, paint,
    )
}

private fun DrawScope.drawLabels(n: Int, step: Float) {
    val paint = android.graphics.Paint().apply {
        color = Line.toArgb()
        textSize = step * 0.34f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    for (i in 0 until n) {
        val colLabel = ('A' + i).toString()
        drawContext.canvas.nativeCanvas.drawText(colLabel, step + i * step, step * 0.55f, paint)
        val rowLabel = (n - i).toString()
        drawContext.canvas.nativeCanvas.drawText(rowLabel, step * 0.4f, step + i * step + step * 0.12f, paint)
    }
}
