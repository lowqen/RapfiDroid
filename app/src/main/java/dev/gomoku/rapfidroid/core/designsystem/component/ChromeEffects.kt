package dev.gomoku.rapfidroid.core.designsystem.component

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.designsystem.theme.ChromeMotion
import dev.gomoku.rapfidroid.core.designsystem.theme.LocalReducedMotion
import dev.gomoku.rapfidroid.core.designsystem.theme.MOTION_PANEL
import dev.gomoku.rapfidroid.core.designsystem.theme.MOTION_VALUE
import kotlinx.coroutines.delay

/**
 * A progress bar that moves instead of jumping (UI overhaul P5, Mornye's
 * `SmoothedProgress`).
 *
 * Progress arrives in steps — a pack build reports every few thousand games, a
 * review every finished move — and a bar that teleports between them looks
 * stuck and then broken. Here a step **forward** glides over [MOTION_VALUE]; a
 * step back (a new run), the first value, the end and reduced motion all snap
 * to the real figure, so the bar never shows more than has been done and never
 * animates a restart as if it were progress.
 *
 * Motion.kt's third rule allows this: a progress bar is a summary, not a live
 * count. The percentage written next to it is never interpolated.
 */
@Composable
fun SmoothProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    val target = fraction.coerceIn(0f, 1f)
    val shown = remember { Animatable(target) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(target, reduced) {
        if (reduced || target < shown.value || target >= 1f) {
            shown.snapTo(target)
        } else {
            shown.animateTo(target, tween(MOTION_VALUE, easing = LinearOutSlowInEasing))
        }
    }
    // Read in the draw phase: the bar redraws, nothing recomposes.
    LinearProgressIndicator(
        progress = { shown.value },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/**
 * A list's first entrance, staggered (UI overhaul P5, Mornye's
 * `StaggeredListItem`): each of the first [MaxStaggered] items rises 12dp and
 * fades in, [StaggerStep] after the one above it.
 *
 * Only the **first** time the list appears: [rememberStagger] is saved with
 * the screen, so coming back to the tab, rotating, or scrolling an item back
 * into view shows it at once — a list that re-animates every time it is seen
 * is a list that makes you wait. Off under reduced motion. Never on the board.
 */
class Stagger internal constructor(private val active: Boolean, private val bornAt: Long) {
    /** Whether the item at [index], appearing now, should animate. */
    internal fun plays(index: Int): Boolean =
        active && index < MaxStaggered && SystemClock.uptimeMillis() - bornAt < Window

    internal companion object {
        const val MaxStaggered = 10
        const val StaggerStep = 40L

        /** Long enough for the last staggered item to start; later arrivals do not. */
        const val Window = MaxStaggered * StaggerStep + MOTION_PANEL
    }
}

@Composable
fun rememberStagger(): Stagger {
    val reduced = LocalReducedMotion.current
    val played = rememberSaveable { mutableStateOf(false) }
    val stagger = remember { Stagger(active = !reduced && !played.value, bornAt = SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) { played.value = true }
    return stagger
}

/** See [Stagger]. [index] is the item's place in the list, from 0. */
fun Modifier.staggerIn(stagger: Stagger, index: Int): Modifier = composed {
    val plays = remember { stagger.plays(index) }
    if (!plays) return@composed Modifier
    val progress = remember { Animatable(0f) }
    val spec = ChromeMotion.spatial(tween<Float>(MOTION_PANEL))
    LaunchedEffect(Unit) {
        delay(index * Stagger.StaggerStep)
        progress.animateTo(1f, spec)
    }
    val rise = with(LocalDensity.current) { 12.dp.toPx() }
    Modifier.graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * rise
    }
}

/** How much of the finger's release speed a fling keeps (see [rememberCalmFling]). */
private const val FlingKeep = 0.6f

/**
 * A fling that goes less far and settles sooner (user request, 2026-10-29: lists
 * and the board's sheet slid away too fast to follow).
 *
 * It is the platform's own fling — the same spline deceleration Android uses
 * everywhere — fed 60% of the release velocity. The curve still looks like
 * Android; it just travels roughly half as far, so a flicked list is something
 * the eye can follow and a thumb can stop. Dragging is untouched: the content
 * stays under the finger exactly as before.
 *
 *     LazyColumn(flingBehavior = rememberCalmFling()) { … }
 *     Modifier.verticalScroll(state, flingBehavior = rememberCalmFling())
 */
@Composable
fun rememberCalmFling(): FlingBehavior {
    val platform = ScrollableDefaults.flingBehavior()
    return remember(platform) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val scope = this
                return with(platform) { scope.performFling(initialVelocity * FlingKeep) }
            }
        }
    }
}
