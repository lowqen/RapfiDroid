package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's haptics, one word per event.
 *
 * Mornye's rule, which is the right one for a board being read: buzz at a
 * **threshold** — a stone lands, a move is refused, a game is decided, a scrub
 * crosses a move — never on every touch. And the engine's own moves are silent;
 * the hand did nothing, and a phone that buzzes while it lies on the table
 * thinking is a phone that gets its vibration turned off.
 *
 * Named by what happened rather than by the vibration, so the board, the move
 * bar and a settings switch cannot drift into three vocabularies. Nothing calls
 * this yet; the board takes it up in P3 (docs/UI-개편-마스터플랜.md).
 */
class RapfiHaptics internal constructor(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {
    /** The user's stone is on the board. */
    fun stonePlaced() = perform(HapticFeedbackType.SegmentTick)

    /** A tap that does nothing: a forbidden point, an occupied one, not your turn. */
    fun refused() = perform(HapticFeedbackType.Reject)

    /** Five in a row, a resignation accepted, a proof finished. */
    fun decided() = perform(HapticFeedbackType.Confirm)

    /** A long press took — the board-text dialog, a context action. */
    fun longPress() = perform(HapticFeedbackType.LongPress)

    /** Scrubbing through the game crossed one move. */
    fun scrubTick() = perform(HapticFeedbackType.SegmentFrequentTick)

    /** A switch changed. */
    fun toggled(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

/** False turns every haptic in the app off (the setting arrives in P5). */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberRapfiHaptics(): RapfiHaptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { RapfiHaptics(feedback, enabled) }
}
