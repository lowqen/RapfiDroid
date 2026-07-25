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
import dev.gomoku.yixindroid.core.model.CandidateState
import dev.gomoku.yixindroid.core.model.CellTag
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.TagKind
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
    val tags: Map<Move, CellTag> = emptyMap(),    // per-cell winrate / mate labels
    val candidates: Map<Move, CandidateState> = emptyMap(), // realtime POS/DONE
    val loseCells: Set<Move> = emptySet(),        // realtime LOSE
    val showNumbers: Boolean = true,              // settings.txt line 14
    val palette: TagPalette = TagPalette(),       // saturation/value settings
)

/**
 * Colour rules for the analysis tags, mirroring the desktop's saturation
 * settings (settings.txt lines 39–43: losing/winning move saturation, min/max
 * win-rate saturation, value).
 */
data class TagPalette(
    val losingSaturation: Int = 0,
    val winningSaturation: Int = 83,
    val minRateSaturation: Int = 20,
    val maxRateSaturation: Int = 80,
    val value: Int = 100,
) {
    /** Tag colour: mate wins/losses use the fixed hues, rates interpolate. */
    fun colorFor(tag: CellTag): Color = when (tag.kind) {
        TagKind.WIN -> hsv(WIN_HUE, winningSaturation / 100f, value / 100f)
        TagKind.LOSE -> hsv(LOSE_HUE, (100 - losingSaturation) / 100f, value / 100f)
        TagKind.RATE -> {
            val pct = (tag.winRatePct ?: 50).coerceIn(0, 100) / 100f
            val sat = (minRateSaturation + (maxRateSaturation - minRateSaturation) *
                kotlin.math.abs(pct - 0.5f) * 2f) / 100f
            hsv(if (pct >= 0.5f) WIN_HUE else LOSE_HUE, sat, value / 100f)
        }
    }

    private fun hsv(hue: Float, saturation: Float, v: Float): Color =
        Color.hsv(hue, saturation.coerceIn(0f, 1f), v.coerceIn(0.2f, 1f))

    private companion object {
        const val WIN_HUE = 210f   // blue = good for the side to move
        const val LOSE_HUE = 8f    // red
    }
}

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

        // realtime candidate cells (POS = live, DONE = settled) — drawn under stones
        render.candidates.forEach { (m, state) ->
            val c = Offset(cx(m.x), cy(m.y))
            val live = state == CandidateState.LIVE
            drawCircle(
                if (live) Accent else Line,
                radius = radius * if (live) 0.30f else 0.20f,
                center = c,
                alpha = if (live) 0.75f else 0.45f,
            )
        }

        // realtime losing cells
        render.loseCells.forEach { m ->
            val c = Offset(cx(m.x), cy(m.y))
            val r = radius * 0.5f
            drawLine(Forbid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 2.5f)
        }

        // per-cell analysis tags (winrate % / W n / L n) on empty points
        if (render.tags.isNotEmpty()) {
            val occupied = render.stones.toHashSet()
            render.tags.forEach { (m, tag) ->
                if (tag.label.isNotEmpty() && m !in occupied) {
                    drawTag(cx(m.x), cy(m.y), radius, tag.label, render.palette.colorFor(tag))
                }
            }
        }

        // played stones, numbered unless "show number" is off (settings.txt line 14)
        render.stones.forEachIndexed { i, m ->
            val black = i % 2 == 0
            val number = if (render.showNumbers) "${i + 1}" else ""
            drawStone(cx(m.x), cy(m.y), radius, black, number, alpha = 1f)
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
            val number = if (render.showNumbers) "${i + 1}" else ""
            drawStone(cx(m.x), cy(m.y), radius, black, number, alpha = 0.4f)
        }

        // best-move highlight
        render.bestMark?.let { m ->
            drawCircle(Gold, radius = radius * 0.85f, center = Offset(cx(m.x), cy(m.y)),
                style = Stroke(width = 3.5f))
        }
    }
}

/** A filled rounded chip with the tag text, sized to sit on one intersection. */
private fun DrawScope.drawTag(cx: Float, cy: Float, r: Float, label: String, color: Color) {
    drawCircle(color, radius = r * 0.82f, center = Offset(cx, cy), alpha = 0.92f)
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        textSize = r * (if (label.length >= 4) 0.62f else 0.78f)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        label, cx, cy - (paint.descent() + paint.ascent()) / 2, paint,
    )
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
