package dev.gomoku.rapfidroid.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.designsystem.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * How far a timed run has got, as Mornye's player shows a song's place: a
 * flowing wave up to here, a flat track after it (UI overhaul 2, M5). Material
 * 1.4 has no wavy indicator, so it is drawn here. Used for a timed analysis, an
 * engine move in a game with a time limit, and a game review.
 *
 * **It moves every frame, not every report** (2026-10-29: «매우 부자연스럽고
 * 뚝뚝 끊김»). The first cut was redrawn only when the engine reported a time,
 * twice a second at best, so the head jumped and the wave stood still. Now
 * [fraction] is only the latest *known* place: between reports the head runs
 * on by itself at [perSecond] (the run's own speed — 1 / the time limit), and a
 * report that disagrees is absorbed by running a little faster or slower for a
 * moment, never by a jump back. A drop larger than that is a new run and
 * starts over. [ceiling] is where it must wait for the next report (a review's
 * next position). With no [perSecond] the head eases to each report instead.
 *
 * **Straight, then a wave, always**: the first [WaveFrom] of the run is a flat
 * line, and the wave swells in once the head has passed it — the M3 Expressive
 * indicator's way of not wobbling over nothing.
 *
 * The cost is one short path per frame, drawn without recomposing (the state is
 * read in the draw phase only). Reduced motion (animator scale 0) keeps the
 * head honest and stills the wave.
 */
@Composable
fun WavyProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    perSecond: Float = 0f,
    ceiling: Float = 1f,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val reported = fraction.coerceIn(0f, 1f)
    val wave = remember { WaveState(reported) }
    val known by rememberUpdatedState(reported)
    val rate by rememberUpdatedState(perSecond.coerceAtLeast(0f))
    val cap by rememberUpdatedState(ceiling.coerceIn(reported, 1f))
    val reduced by rememberUpdatedState(LocalReducedMotion.current)
    val frames = LocalWaveFrames.current
    if (frames) LaunchedEffect(wave) {
        var last = withFrameNanos { it }
        var anchor = known
        var anchorAt = last
        while (true) {
            withFrameNanos { now ->
                if (known != anchor) {
                    // Further back than two seconds of the run's own speed is not
                    // jitter but a new run (a new position, a new move).
                    if (known < wave.head - (rate * 2f + 0.02f)) wave.restart(known)
                    anchor = known
                    anchorAt = now
                }
                // A paused frame clock (the app in the background) resumes, not leaps.
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                val target = min(cap, anchor + rate * (now - anchorAt) / 1e9f)
                wave.advance(target, cap, dt, rate, reduced)
            }
        }
    }
    val path = remember { Path() }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(reported, 0f..1f) },
    ) {
        val f = if (frames) wave.head else reported
        val swell = if (frames) wave.swell else if (reported > WaveFrom) 1f else 0f
        val stroke = 3.dp.toPx()
        val amplitude = 2.5.dp.toPx() * swell
        val wavelength = 22.dp.toPx()
        val mid = size.height / 2
        val end = size.width * f
        val gap = if (f in 0.001f..0.999f) 6.dp.toPx() else 0f
        if (end + gap < size.width) {
            drawLine(trackColor, Offset(end + gap, mid), Offset(size.width, mid), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        if (end <= 0f) return@Canvas
        if (amplitude < 0.05f) {
            drawLine(color, Offset(0f, mid), Offset(end, mid), strokeWidth = stroke, cap = StrokeCap.Round)
            return@Canvas
        }
        // The wave leaves the start on the line: it swells over its first half
        // wavelength, so the round cap at the left never bobs.
        val ease = wavelength / 2
        path.reset()
        path.moveTo(0f, mid)
        var x = 0f
        while (x < end) {
            x = (x + 1.5f).coerceAtMost(end)
            val taper = min(1f, x / ease)
            path.lineTo(x, mid + amplitude * taper * sin(2 * PI * x / wavelength - wave.phase).toFloat())
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

/**
 * False draws the wave as a still picture of [fraction]. For the screenshot
 * tests: Robolectric hands out frames as fast as they are asked for, so a loop
 * that waits for the next frame never lets the capture see an idle screen.
 */
val LocalWaveFrames = staticCompositionLocalOf { true }

/** Where the wave starts: the first tenth of a run is a straight line. */
const val WaveFrom = 0.10f

/** One wavelength a second, flowing toward the head. */
private const val PhasePerSecond = 6.2831855f

/** The head's place, the wave's height (0 flat … 1 full) and its phase — read only when drawing. */
internal class WaveState(start: Float) {
    var head by mutableFloatStateOf(start)
    var swell by mutableFloatStateOf(if (start > WaveFrom) 1f else 0f)
    var phase by mutableFloatStateOf(0f)

    fun restart(at: Float) {
        head = at
        if (at <= WaveFrom) swell = 0f
    }

    /** One frame toward [target] (where the clock says the head is), never past [cap]. */
    fun advance(target: Float, cap: Float, dt: Float, rate: Float, reduced: Boolean) {
        when {
            reduced -> head = maxOf(head, target)
            rate > 0f -> {
                // The run's own speed, faster when behind the clock and slower
                // when ahead of it — never backwards, never a jump, never a stop.
                val lead = (target - head) / rate
                val speed = rate * (1f + lead * 2f).coerceIn(0.4f, 3f)
                head = minOf(head + speed * dt, cap, 1f).coerceAtLeast(head)
            }
            target > head -> {
                head += (target - head) * (1f - exp(-dt / 0.25f))
                if (target - head < 0.0005f) head = target
            }
        }
        val wanted = if (head > WaveFrom) 1f else 0f
        swell = if (reduced) wanted else swell + (wanted - swell) * (1f - exp(-dt / 0.3f))
        if (!reduced) phase = (phase + dt * PhasePerSecond) % (2 * PI).toFloat()
    }
}
