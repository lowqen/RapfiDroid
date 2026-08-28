package dev.gomoku.yixindroid.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import dev.gomoku.yixindroid.core.designsystem.theme.BoardSkin
import dev.gomoku.yixindroid.core.designsystem.theme.YixinTheme
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.OpeningEval
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A compact board that auto-crops a square window around the centre just large
 * enough to hold [stones] (with one line of padding). Used for opening (주형)
 * and 5-move shape thumbnails. Sized by the caller's [modifier] (pass a square).
 *
 * It takes the same [BoardSkin] as the full board on purpose: a thumbnail that
 * kept its own gold while the board beside it went dark would read as a picture
 * of a different program.
 */
@androidx.compose.runtime.Composable
fun MiniBoard(
    stones: List<Move>,
    modifier: Modifier = Modifier,
    showNumbers: Boolean = true,
    /** 흑 5수 유불리 marks on empty points, drawn the way the user's own
     *  evaluation table draws them. Widens the crop so none falls outside. */
    marks: List<Pair<Move, OpeningEval.Grade>> = emptyList(),
    skin: BoardSkin = YixinTheme.board,
) {
    val center = Move.DEFAULT_SIZE / 2   // 7
    val reach = (stones + marks.map { it.first })
        .maxOfOrNull { max(abs(it.x - center), abs(it.y - center)) } ?: 2
    val half = (reach + 1).coerceAtLeast(2)
    val lo = center - half
    val n = 2 * half + 1

    Canvas(modifier = modifier) {
        val side = min(size.width, size.height)
        val step = side / (n + 1)
        val ox = (size.width - side) / 2f
        val oy = (size.height - side) / 2f
        val radius = step * 0.42f
        val hair = (step / 22f).coerceAtLeast(1f)
        fun cx(gx: Int) = ox + step + (gx - lo) * step
        fun cy(gy: Int) = oy + step + (gy - lo) * step

        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(skin.wood, skin.woodEdge),
                start = Offset(ox, oy),
                end = Offset(ox + side, oy + side),
            ),
            topLeft = Offset(ox, oy),
            size = Size(side, side),
            cornerRadius = CornerRadius(step * 0.45f),
        )
        for (i in 0 until n) {
            val a = lo + i
            val edge = i == 0 || i == n - 1
            val w = if (edge) hair * 1.6f else hair
            drawLine(skin.line, Offset(cx(lo), cy(a)), Offset(cx(lo + n - 1), cy(a)), strokeWidth = w)
            drawLine(skin.line, Offset(cx(a), cy(lo)), Offset(cx(a), cy(lo + n - 1)), strokeWidth = w)
        }
        // centre dot
        drawCircle(skin.line, radius = step * 0.09f, center = Offset(cx(center), cy(center)))

        stones.forEachIndexed { i, m ->
            val black = i % 2 == 0
            val c = Offset(cx(m.x), cy(m.y))
            drawCircle(
                skin.stoneShadow,
                radius * 1.04f,
                Offset(c.x + radius * 0.05f, c.y + radius * 0.09f),
                alpha = 0.5f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to (if (black) skin.blackHigh else skin.whiteHigh),
                    1f to (if (black) skin.blackLow else skin.whiteLow),
                    center = Offset(c.x - radius * 0.34f, c.y - radius * 0.38f),
                    radius = radius * 1.7f,
                ),
                radius = radius,
                center = c,
            )
            drawCircle(
                if (black) skin.blackRim else skin.whiteRim,
                radius, c, alpha = 0.8f, style = Stroke(hair),
            )
            if (showNumbers) {
                val paint = android.graphics.Paint().apply {
                    color = (if (black) skin.whiteHigh else skin.blackLow).toArgb()
                    textSize = radius * 1.05f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "${i + 1}", c.x, c.y - (paint.descent() + paint.ascent()) / 2, paint,
                )
            }
        }

        for ((m, g) in marks) drawGradeMark(g, Offset(cx(m.x), cy(m.y)), radius * 0.86f)
    }
}

/**
 * One 흑 5수 유불리 mark: a coloured shape from [OpeningEval]'s ladder.
 *
 * Lives here so the board and the list chip cannot drift apart, and stays out
 * of the engine's winrate colour ramp on purpose — that ramp is a number the
 * engine computed, this is one a person wrote down, and painting them alike
 * would make the two indistinguishable on the same screen. The shape carries
 * the meaning, so the mark still reads without colour at all.
 */
fun DrawScope.drawGradeMark(grade: OpeningEval.Grade, center: Offset, r: Float) {
    val fill = Color(grade.fill)
    val rim = Color(0x66000000)
    val hair = r * 0.13f
    when (grade.mark) {
        OpeningEval.Mark.CIRCLE -> {
            drawCircle(fill, r, center)
            drawCircle(rim, r, center, style = Stroke(width = hair))
        }
        OpeningEval.Mark.SQUARE -> {
            val s = r * 1.76f
            val tl = Offset(center.x - s / 2, center.y - s / 2)
            drawRect(fill, tl, Size(s, s))
            drawRect(rim, tl, Size(s, s), style = Stroke(width = hair))
        }
        OpeningEval.Mark.TRIANGLE -> drawMarkPoly(
            fill, rim, hair,
            listOf(
                Offset(center.x, center.y - r),
                Offset(center.x + r * 0.92f, center.y + r * 0.72f),
                Offset(center.x - r * 0.92f, center.y + r * 0.72f),
            ),
        )
        OpeningEval.Mark.PENTAGON -> drawMarkPoly(
            fill, rim, hair,
            (0 until 5).map {
                val a = (-Math.PI / 2 + it * 2 * Math.PI / 5).toFloat()
                Offset(center.x + r * cos(a), center.y + r * sin(a))
            },
        )
        OpeningEval.Mark.CROSS -> {
            val d = r * 0.78f
            val w = r * 0.34f
            drawLine(fill, Offset(center.x - d, center.y - d),
                Offset(center.x + d, center.y + d), w, StrokeCap.Round)
            drawLine(fill, Offset(center.x - d, center.y + d),
                Offset(center.x + d, center.y - d), w, StrokeCap.Round)
        }
    }
    grade.dot?.let { drawCircle(Color(it), r * 0.34f, center) }
}

private fun DrawScope.drawMarkPoly(fill: Color, rim: Color, hair: Float, pts: List<Offset>) {
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, fill)
    drawPath(path, rim, style = Stroke(width = hair))
}
