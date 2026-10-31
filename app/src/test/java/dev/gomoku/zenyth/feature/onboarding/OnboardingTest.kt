package dev.gomoku.zenyth.feature.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Who meets the first-run setup, and that the answer is kept. */
class OnboardingTest {

    @Test
    fun aFreshInstallSetsUpAnUpdateDoesNot() {
        assertThat(Onboarding.decide(Onboarding.UNDECIDED) { true }).isEqualTo(0)
        assertThat(Onboarding.decide(Onboarding.UNDECIDED) { false }).isEqualTo(Onboarding.VERSION)
        assertThat(Onboarding.needsSetup(0)).isTrue()
        assertThat(Onboarding.needsSetup(Onboarding.VERSION)).isFalse()
    }

    /** The install times change with the next update; a stored answer must not. */
    @Test
    fun aStoredAnswerIsNotAskedAgain() {
        var asked = false
        val freshInstall = { asked = true; false }
        // Setup left halfway, then the app updated: still to do.
        assertThat(Onboarding.decide(0, freshInstall)).isEqualTo(0)
        assertThat(Onboarding.decide(Onboarding.VERSION, freshInstall)).isEqualTo(Onboarding.VERSION)
        assertThat(asked).isFalse()
    }

    @Test
    fun aRaisedVersionAsksAgain() {
        assertThat(Onboarding.needsSetup(Onboarding.VERSION - 1)).isTrue()
        assertThat(Onboarding.needsSetup(Onboarding.UNDECIDED)).isFalse()
    }

    /** Android 13 asks for the notification permission; below it there is nothing to ask. */
    @Test
    fun twoScreensAtMost() {
        assertThat(setupSteps(sdk = 35)).containsExactly(SetupStep.Welcome, SetupStep.Notifications).inOrder()
        assertThat(setupSteps(sdk = 33)).containsExactly(SetupStep.Welcome, SetupStep.Notifications).inOrder()
        assertThat(setupSteps(sdk = 32)).containsExactly(SetupStep.Welcome)
        assertThat(setupSteps(sdk = 26)).containsExactly(SetupStep.Welcome)
    }
}
