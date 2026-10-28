package dev.gomoku.rapfidroid.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * How far a timed analysis has run, as Mornye's player shows a song's place: a
 * wave up to here, a flat track after it (UI overhaul 2, M5). Material 1.4 has
 * no wavy indicator, so it is drawn here.
 *
 * **The wave stands still.** Mornye's flows; this one is redrawn only when the
 * engine reports a new time, because an animation running for the whole of a
 * search is frames the phone draws while the engine wants every core.
 */
@Composable
fun WavyProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val f = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f) },
    ) {
        val stroke = 3.dp.toPx()
        val amplitude = 2.5.dp.toPx()
        val wavelength = 22.dp.toPx()
        val mid = size.height / 2
        val end = size.width * f
        val gap = if (f in 0.001f..0.999f) 6.dp.toPx() else 0f
        if (end + gap < size.width) {
            drawLine(trackColor, Offset(end + gap, mid), Offset(size.width, mid), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        if (end <= 0f) return@Canvas
        val path = Path().apply {
            moveTo(0f, mid)
            var x = 0f
            val step = 2f
            while (x < end) {
                x = (x + step).coerceAtMost(end)
                lineTo(x, mid + amplitude * sin(2 * PI * x / wavelength).toFloat())
            }
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}
