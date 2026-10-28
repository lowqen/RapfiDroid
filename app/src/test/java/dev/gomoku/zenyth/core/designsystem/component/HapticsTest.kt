package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HapticsTest {

    private class Recorder : HapticFeedback {
        val played = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }

    @Test
    fun `each event has its own vibration`() {
        val r = Recorder()
        RapfiHaptics(r, enabled = true).apply {
            stonePlaced()
            refused()
            decided()
            longPress()
            scrubTick()
            toggled(on = true)
            toggled(on = false)
        }
        assertThat(r.played).containsExactly(
            HapticFeedbackType.SegmentTick,
            HapticFeedbackType.Reject,
            HapticFeedbackType.Confirm,
            HapticFeedbackType.LongPress,
            HapticFeedbackType.SegmentFrequentTick,
            HapticFeedbackType.ToggleOn,
            HapticFeedbackType.ToggleOff,
        ).inOrder()
    }

    @Test
    fun `switched off means silent`() {
        val r = Recorder()
        RapfiHaptics(r, enabled = false).apply {
            stonePlaced()
            refused()
            decided()
        }
        assertThat(r.played).isEmpty()
    }
}
