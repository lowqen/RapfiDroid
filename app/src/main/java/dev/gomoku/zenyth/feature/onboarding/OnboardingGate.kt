package dev.gomoku.zenyth.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.zenyth.core.common.isFreshInstall
import dev.gomoku.zenyth.core.designsystem.component.LocalUiPrefs

/**
 * The first-run windows (plan: `docs/온보딩-계획.md`): a setup that asks the few
 * things worth asking before anything else, then a tour of the tabs.
 *
 * Only a fresh install gets them. An update keeps the setup its user already
 * made, so the first launch of a version with this feature decides once, and
 * stores the answer — the install times it reads change with the next update.
 */
object Onboarding {
    /** The setup's current form. Raise it only for a step every user must see. */
    const val VERSION = 1

    /** [UNDECIDED], 0 (to do), or the [VERSION] last finished. */
    const val DONE_PREF = "onboarding.done"

    /**
     * The setup step reached. Choosing a language recreates the activity, and
     * the app can be closed halfway; either way the setup resumes here.
     */
    const val STEP_PREF = "onboarding.step"

    /** Nothing stored yet: the first launch of a version with this feature. */
    const val UNDECIDED = -1

    /** [done] as it should be stored; [freshInstall] is asked only while undecided. */
    fun decide(done: Int, freshInstall: () -> Boolean): Int = when {
        done != UNDECIDED -> done
        freshInstall() -> 0
        else -> VERSION
    }

    fun needsSetup(done: Int): Boolean = done in 0 until VERSION
}

/**
 * Puts the setup in front of the app and the tour over it, then [afterwards].
 *
 * The setup stands *in place of* [app], so the shell — and with it the engine's
 * auto start — does not exist until it is done: no engine notification before
 * the step that explains notifications, no network load fighting the setup's
 * motion. The tour lies *over* [app], so the engine comes up behind it while it
 * is read. [afterwards] («새로워진 점») waits until both are gone (user request,
 * 2026-10-31).
 */
@Composable
fun OnboardingGate(
    app: @Composable () -> Unit,
    afterwards: @Composable () -> Unit,
) {
    val prefs = LocalUiPrefs.current
    if (prefs == null) {
        app()
        afterwards()
        return
    }
    val context = LocalContext.current
    // Decided in the first frame, so neither the app nor the setup flashes
    // before the other. The install-time lookup runs once in the app's life.
    val done = remember(prefs) {
        prefs.int(Onboarding.DONE_PREF, Onboarding.UNDECIDED).also { pref ->
            val decided = Onboarding.decide(pref.value) { isFreshInstall(context) }
            if (decided != pref.value) pref.set(decided)
        }
    }
    val doneValue by done.state.collectAsStateWithLifecycle()
    // Saveable: a rotation must not end the tour. A process death does — the
    // tour is a courtesy, not a gate.
    var touring by rememberSaveable { mutableStateOf(false) }

    if (Onboarding.needsSetup(doneValue)) {
        SetupScreen(
            onFinish = {
                prefs.int(Onboarding.STEP_PREF, 0).set(0)
                touring = true
                done.set(Onboarding.VERSION)
            },
        )
        return
    }
    Box(Modifier.fillMaxSize()) {
        app()
        if (touring) {
            TourScreen(onFinish = { touring = false })
        } else {
            afterwards()
        }
    }
}
