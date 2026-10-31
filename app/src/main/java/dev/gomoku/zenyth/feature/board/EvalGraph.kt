package dev.gomoku.zenyth.feature.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.component.CalmCard
import dev.gomoku.zenyth.core.designsystem.component.CardTitle
import dev.gomoku.zenyth.core.designsystem.component.rememberZenythHaptics
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The game's course, move by move (docs/보드-연구-개선-계획.md, R3). It was a
 * 96dp line with a dot on every move over a faint fill — who stood better was
 * hard to read, and it did nothing when touched.
 *
 * Now a small wood plate, like the board: the stretch above the middle is
 * filled in the black stones' colour, the stretch below in the white stones',
 * so the area says who was better and by how much. A smooth line without dots;
 * unread moves break it, a dashed thread across. A gold point where the game
 * swung by 15 %p or more in one move, a cursor on the move shown, move numbers
 * under the plate. Above it, the side ahead and its share in large figures,
 * and how the last move changed it.
 *
 * Tap it, or drag along it, to go to that move — a tick under the finger at
 * each one, as on the toolbar's scrub.
 */
@Composable
internal fun EvalGraphCard(
    history: List<Double?>,
    currentPly: Int,
    lineLength: Int,
    onJump: (Int) -> Unit,
) {
    if (history.none { it != null }) return
    val now = history.getOrNull(currentPly)
    val change = EvalGraphMath.change(history, currentPly)
    val said = tr("형세 그래프, ${currentPly}수", "Evaluation graph, move $currentPly", "形势曲线，第 ${currentPly} 手") +
        (now?.let { ", " + leadText(it) } ?: "")
    CalmCard(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = said
            customActions = listOf(
                CustomAccessibilityAction(tr("이전 수", "Previous move", "上一手")) { onJump(currentPly - 1); true },
                CustomAccessibilityAction(tr("다음 수", "Next move", "下一手")) { onJump(currentPly + 1); true },
            )
        },
    ) {
        CardTitle(tr("형세", "Evaluation", "形势")) {
            Spacer(Modifier.weight(1f))
            Text(
                tr("${currentPly}수", "move $currentPly", "第${currentPly}手"),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                now?.let { leadText(it) } ?: "—",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold).tabular(),
            )
            change?.takeIf { abs(it) >= 0.005 }?.let { ChangePill(it) }
        }
        EvalPlot(history, currentPly, lineLength, onJump)
    }
}

/** The side ahead and its share: «흑 63%», «백 58%». */
private fun leadText(blackRate: Double): String {
    val black = blackRate >= 0.5
    val pct = ((if (black) blackRate else 1 - blackRate) * 100).roundToInt()
    return if (black) tr("흑 $pct%", "Black $pct%", "黑 $pct%") else tr("백 $pct%", "White $pct%", "白 $pct%")
}

/** What the last move did: a stone of the side it helped, and by how much. */
@Composable
private fun ChangePill(change: Double) {
    val skin = ZenythTheme.board
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(
            Modifier.padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(if (change > 0) skin.blackLow else skin.whiteHigh),
            )
            Text(
                "+${(abs(change) * 100).roundToInt()}%p",
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EvalPlot(history: List<Double?>, currentPly: Int, lineLength: Int, onJump: (Int) -> Unit) {
    val skin = ZenythTheme.board
    val numberColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val haptics = rememberZenythHaptics()
    // The plot spans the whole line, read or not, so any move can be reached.
    val count = maxOf(history.size, lineLength + 1, currentPly + 1, 2)
    val swings = remember(history) { EvalGraphMath.swings(history) }
    val jump by rememberUpdatedState(onJump)
    var dragged by remember { mutableIntStateOf(-1) }
    val blackWord = tr("흑", "B", "黑")
    val whiteWord = tr("백", "W", "白")

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(PlotHeight)
            .pointerInput(count) {
                val pad = PlatePad.toPx()
                detectTapGestures { o ->
                    haptics.scrubTick()
                    jump(EvalGraphMath.plyAt(o.x - pad, size.width - 2 * pad, count))
                }
            }
            .pointerInput(count) {
                // Horizontal only: a vertical pull still scrolls the sheet.
                val pad = PlatePad.toPx()
                fun follow(x: Float) {
                    val ply = EvalGraphMath.plyAt(x - pad, size.width - 2 * pad, count)
                    if (ply != dragged) {
                        dragged = ply
                        haptics.scrubTick()
                        jump(ply)
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = { follow(it.x) },
                    onDragEnd = { dragged = -1 },
                    onDragCancel = { dragged = -1 },
                ) { change, _ ->
                    change.consume()
                    follow(change.position.x)
                }
            },
    ) {
        val pad = PlatePad.toPx()
        val band = NumberBand.toPx()
        val plateH = size.height - band
        val plotTop = pad * 0.6f
        val plotH = plateH - plotTop * 2
        val plotW = size.width - 2 * pad
        val mid = plotTop + plotH / 2
        fun x(i: Int) = pad + plotW * i / (count - 1)
        fun y(v: Double) = plotTop + plotH * (1f - v.toFloat().coerceIn(0f, 1f))

        // The plate.
        drawRoundRect(skin.wood, size = Size(size.width, plateH), cornerRadius = CornerRadius(12.dp.toPx()))
        listOf(0.25, 0.75).forEach { v ->
            drawLine(skin.line.copy(alpha = 0.25f), Offset(pad, y(v)), Offset(size.width - pad, y(v)), strokeWidth = 1.dp.toPx())
        }
        drawLine(skin.line.copy(alpha = 0.7f), Offset(pad, mid), Offset(size.width - pad, mid), strokeWidth = 1.2.dp.toPx())

        // Read stretches, each its own run; between them, a dashed thread.
        val runs = EvalGraphMath.runs(history).map { run -> run.map { (i, v) -> Offset(x(i), y(v)) } }
        runs.zipWithNext { a, b ->
            drawLine(
                skin.line.copy(alpha = 0.5f), a.last(), b.first(),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
        runs.forEach { pts ->
            if (pts.size == 1) {
                drawCircle(skin.line, radius = 2.5.dp.toPx(), center = pts[0])
                return@forEach
            }
            val curve = Path().apply { monotoneThrough(pts) }
            val area = Path().apply {
                monotoneThrough(pts)
                lineTo(pts.last().x, mid)
                lineTo(pts.first().x, mid)
                close()
            }
            clipRect(bottom = mid) { drawPath(area, skin.blackLow.copy(alpha = 0.9f)) }
            clipRect(top = mid) { drawPath(area, skin.whiteHigh.copy(alpha = 0.95f)) }
            drawPath(curve, skin.line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }

        // Where the game swung.
        swings.forEach { i ->
            val v = history[i] ?: return@forEach
            val c = Offset(x(i), y(v))
            drawCircle(skin.bestHalo, radius = 4.6.dp.toPx(), center = c)
            drawCircle(skin.best, radius = 3.4.dp.toPx(), center = c)
        }

        // The move on the board.
        val cx = x(currentPly.coerceIn(0, count - 1))
        drawLine(skin.lastMove.copy(alpha = 0.85f), Offset(cx, plotTop), Offset(cx, plotTop + plotH), strokeWidth = 1.5.dp.toPx())
        history.getOrNull(currentPly)?.let { v ->
            val c = Offset(cx, y(v))
            drawCircle(skin.whiteHigh, radius = 6.dp.toPx(), center = c)
            drawCircle(skin.lastMove, radius = 4.5.dp.toPx(), center = c)
        }

        // Whose side is whose, in the plate's corners.
        val side = labelStyle.copy(color = skin.line)
        drawText(measurer, blackWord, Offset(pad * 0.5f, plotTop), side)
        val white = measurer.measure(whiteWord, side)
        drawText(white, topLeft = Offset(pad * 0.5f, plotTop + plotH - white.size.height))

        // Move numbers under the plate.
        val step = EvalGraphMath.tickStep(count - 1)
        var tick = step
        while (tick <= count - 1) {
            val text = measurer.measure(tick.toString(), labelStyle.copy(color = numberColor))
            val tx = (x(tick) - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(tx, plateH + (band - text.size.height) / 2f))
            tick += step
        }
    }
}

/** A smooth line through [pts] that never swings past them (Fritsch–Carlson). */
private fun Path.monotoneThrough(pts: List<Offset>) {
    moveTo(pts[0].x, pts[0].y)
    val n = pts.size
    if (n == 2) {
        lineTo(pts[1].x, pts[1].y)
        return
    }
    val dx = FloatArray(n - 1) { pts[it + 1].x - pts[it].x }
    val slope = FloatArray(n - 1) { (pts[it + 1].y - pts[it].y) / dx[it] }
    val tangent = FloatArray(n)
    tangent[0] = slope[0]
    tangent[n - 1] = slope[n - 2]
    for (i in 1 until n - 1) {
        tangent[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else (slope[i - 1] + slope[i]) / 2f
    }
    for (i in 0 until n - 1) {
        if (slope[i] == 0f) {
            tangent[i] = 0f
            tangent[i + 1] = 0f
        } else {
            val a = tangent[i] / slope[i]
            val b = tangent[i + 1] / slope[i]
            val s = a * a + b * b
            if (s > 9f) {
                val k = 3f / sqrt(s)
                tangent[i] = k * a * slope[i]
                tangent[i + 1] = k * b * slope[i]
            }
        }
    }
    for (i in 0 until n - 1) {
        val h = dx[i] / 3f
        cubicTo(
            pts[i].x + h, pts[i].y + tangent[i] * h,
            pts[i + 1].x - h, pts[i + 1].y - tangent[i + 1] * h,
            pts[i + 1].x, pts[i + 1].y,
        )
    }
}

/** The graph's arithmetic, apart from the drawing so it can be tested. */
internal object EvalGraphMath {
    /** A swing worth a mark: 15 %p in one move. */
    const val SWING = 0.15

    /** Plies whose reading moved by at least [threshold] from the ply just before. */
    fun swings(history: List<Double?>, threshold: Double = SWING): List<Int> =
        (1 until history.size).filter { i ->
            val a = history[i - 1]
            val b = history[i]
            a != null && b != null && abs(b - a) >= threshold
        }

    /** What [ply] changed from the last read ply before it, Black's way; null if either is unread. */
    fun change(history: List<Double?>, ply: Int): Double? {
        val now = history.getOrNull(ply) ?: return null
        val before = (ply - 1 downTo 0).firstNotNullOfOrNull { history.getOrNull(it) } ?: return null
        return now - before
    }

    /** The ply under [x] on a plot [width] wide spanning plies 0 until [count]. */
    fun plyAt(x: Float, width: Float, count: Int): Int =
        if (count <= 1 || width <= 0f) 0 else ((x / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)

    /** Runs of consecutive read plies, as (ply, value). */
    fun runs(history: List<Double?>): List<List<Pair<Int, Double>>> {
        val out = mutableListOf<List<Pair<Int, Double>>>()
        var run = mutableListOf<Pair<Int, Double>>()
        history.forEachIndexed { i, v ->
            if (v == null) {
                if (run.isNotEmpty()) out += run
                run = mutableListOf()
            } else {
                run += i to v
            }
        }
        if (run.isNotEmpty()) out += run
        return out
    }

    /** Move numbers every 5 on a short game, 10 on a longer one, 20 on a long one. */
    fun tickStep(lastPly: Int): Int = when {
        lastPly <= 30 -> 5
        lastPly <= 90 -> 10
        else -> 20
    }
}

private val PlotHeight = 150.dp
private val PlatePad = 14.dp
private val NumberBand = 18.dp
