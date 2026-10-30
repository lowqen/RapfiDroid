package dev.gomoku.zenyth.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code the app runs from a cold start to its first screen, the
 * board (docs/리팩토링-성능-위생-계획.md, Phase 7-2). Compiled ahead of time from
 * that profile, the start and the first frames run without waiting on the JIT.
 *
 * Start-up only: nothing here touches the engine — no analysis, no game move —
 * and the emulator it runs on has no on-device engine anyway.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        // The notification permission's dialog would cover the board on every
        // launch; granted beforehand, the start-up recorded is the app's own.
        device.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "dev.gomoku.rapfidroid"
    }
}
