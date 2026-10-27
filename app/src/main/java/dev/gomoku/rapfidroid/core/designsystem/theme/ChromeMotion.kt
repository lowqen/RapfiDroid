package dev.gomoku.rapfidroid.core.designsystem.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion for the chrome — sheets, toolbars, navigation, chips — and never for
 * the board.
 *
 * Decision D6 of the UI overhaul (docs/UI-개편-마스터플랜.md): the four rules in
 * Motion.kt stay the board's rules — the board, the eval bar and every live
 * number keep to height-and-opacity, 200 ms, no interpolated counts. What sits
 * around the board may move the way Material 3 Expressive moves: springs, which
 * end when the motion physically settles rather than at a fixed time.
 *
 * Two families, as in Material and in Mornye's `AppMotion`:
 *  - **spatial** — position, size, scale. Slightly springy.
 *  - **effects** — opacity and colour. Never overshoots: a colour past its
 *    target is a different colour.
 *
 * Every call names the spec it falls back to. When the system asks for reduced
 * motion (animator duration scale 0, [LocalReducedMotion]) the call site's own
 * fallback is used, so turning animations off never leaves half the app on
 * springs. The springs are Material's own (`MotionScheme.expressive()`), not
 * numbers copied into this file.
 */
object ChromeMotion {
    private val scheme = MotionScheme.expressive()

    /** Small components: a chip, a check, a button. */
    @Composable
    @ReadOnlyComposable
    fun <T> spatialFast(fallback: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) fallback else scheme.fastSpatialSpec()

    /** List items, panels, content changing size. */
    @Composable
    @ReadOnlyComposable
    fun <T> spatial(fallback: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) fallback else scheme.defaultSpatialSpec()

    /** Large surfaces travelling far. */
    @Composable
    @ReadOnlyComposable
    fun <T> spatialSlow(fallback: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) fallback else scheme.slowSpatialSpec()

    @Composable
    @ReadOnlyComposable
    fun <T> effectsFast(fallback: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) fallback else scheme.fastEffectsSpec()

    @Composable
    @ReadOnlyComposable
    fun <T> effects(fallback: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) fallback else scheme.defaultEffectsSpec()

    @Composable
    @ReadOnlyComposable
    fun <T> effectsSlow(fallback: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) fallback else scheme.slowEffectsSpec()

    /**
     * A sheet or menu arriving. Critically damped: a surface that overshoots its
     * own size shows, for a frame, whatever is behind its edge — on this app,
     * the board.
     */
    @Composable
    @ReadOnlyComposable
    fun <T> surfaceEntrance(fallback: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) {
            fallback
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
        }
}

/** The system's «remove animations»; provided by `RapfiDroidTheme`. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Read once per theme composition. Developer options and the accessibility
 * «remove animations» switch both set this scale to 0.
 */
internal fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
