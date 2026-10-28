package dev.gomoku.zenyth.feature.board

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import dev.gomoku.zenyth.PerfBaseline
import dev.gomoku.zenyth.core.designsystem.component.LocalShellChrome
import dev.gomoku.zenyth.core.designsystem.component.LocalWaveFrames
import dev.gomoku.zenyth.core.designsystem.component.ShellChrome
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.model.AppSettings
import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.ClockSide
import dev.gomoku.zenyth.core.model.ComputerSide
import dev.gomoku.zenyth.core.model.ConnectionState
import dev.gomoku.zenyth.core.model.GameClock
import dev.gomoku.zenyth.core.model.GameState
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.domain.engine.CoordMapper
import dev.gomoku.zenyth.domain.engine.EngineStreams
import dev.gomoku.zenyth.domain.engine.ResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Which composables run, and how often, while an analysis streams into the
 * board screen and while a game's clock ticks — Phase 4's before and after
 * (docs/리팩토링-성능-위생-계획.md). See [PerfBaseline].
 *
 * Counted with the runtime's own trace hook ([Composer.setTracer]): the
 * compiler brackets every composable body it does not skip with a trace event,
 * so the count is executions, not calls, and no app code is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ko-rKR-w411dp-h891dp-xhdpi")
class BoardRecompositionBaselineTest {

    @get:Rule
    val compose = createComposeRule()

    @OptIn(InternalComposeTracingApi::class)
    private class Counter : CompositionTracer {
        var on = false
        val counts = HashMap<String, Int>()
        override fun isTraceInProgress(): Boolean = on
        override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
            counts.merge(info.substringBefore(" ("), 1, Int::plus)
        }

        override fun traceEventEnd() = Unit

        fun take(): Map<String, Int> = HashMap(counts).also { counts.clear() }
    }

    private val stones = listOf(
        Move(7, 7), Move(8, 6), Move(8, 8), Move(6, 6), Move(9, 9), Move(6, 8), Move(10, 10),
        Move(5, 5), Move(9, 7), Move(7, 9), Move(6, 10), Move(10, 6),
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Lets the view model's coroutines run, then draws one frame. */
    private fun frame() {
        idle()
        compose.mainClock.advanceTimeByFrame()
        idle()
    }

    @OptIn(InternalComposeTracingApi::class)
    @Test
    fun `board screen recompositions per analysis frame and per clock tick`() {
        assumeTrue(PerfBaseline.enabled)
        val rig = boardRig(AppSettings(multiPv = 3), io = Dispatchers.Unconfined)
        runBlocking { rig.game.replaceLine(stones) }
        compose.setContent {
            ZenythTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalWaveFrames provides false,
                    LocalShellChrome provides ShellChrome(ConnectionState.Ready, false, onStartEngine = {}, onToggleTheme = {}),
                ) {
                    BoardScreen(viewModel = rig.vm)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val counter = Counter()
        Composer.setTracer(counter)
        val report = mutableListOf<String>()
        try {
            // ---- idle: nothing changes ------------------------------------
            counter.on = true
            repeat(FRAMES) { frame() }
            report += section("idle, $FRAMES frames", counter.take(), FRAMES)

            // ---- an analysis, LINES_PER_FRAME engine lines a frame ---------
            counter.on = false
            rig.vm.onStartAnalyze()
            val responses = EngineStreams.search(depths = 8, multiPv = 3).dropLast(1)
                .map { ResponseParser.parse(it, CoordMapper()) }
            // The first frames build the card; measure the steady state after.
            responses.take(responses.size / 2).chunked(LINES_PER_FRAME).forEach { chunk ->
                chunk.forEach { rig.engine.responseFlow.tryEmit(it) }
                frame()
            }
            counter.on = true
            val measured = responses.drop(responses.size / 2).chunked(LINES_PER_FRAME)
            measured.forEach { chunk ->
                chunk.forEach { rig.engine.responseFlow.tryEmit(it) }
                frame()
            }
            report += section("analysis, ${measured.size} frames of $LINES_PER_FRAME engine lines", counter.take(), measured.size)
            counter.on = false
            rig.vm.onStopAnalyze()
            frame()

            // ---- the game tab, its clock ticking ---------------------------
            rig.game.modeFlow.value = BoardMode.GAME
            rig.game.stateFlow.value = GameState(
                computerSide = ComputerSide.WHITE,
                clock = GameClock(limited = true, turnLimitMs = 60_000, matchLimitMs = 600_000).start(ClockSide.HUMAN),
            )
            repeat(3) { frame() }
            counter.on = true
            repeat(FRAMES) {
                rig.game.stateFlow.value = rig.game.stateFlow.value.let { it.copy(clock = it.clock.tick(250)) }
                frame()
            }
            report += section("game clock, $FRAMES ticks (one a frame)", counter.take(), FRAMES)
        } finally {
            counter.on = false
            Composer.setTracer(null)
            rig.vm.viewModelScope.cancel()
        }
        PerfBaseline.report("board_recomposition", report)
    }

    /** Totals, then the app's own composables by count. */
    private fun section(title: String, counts: Map<String, Int>, frames: Int): String {
        val ours = counts.filterKeys { it.startsWith(APP) }
        val lines = ours.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(40)
            .joinToString("\n") { (name, n) -> "  %6d  %6.1f/frame  %s".format(n, n.toDouble() / frames, name.removePrefix(APP)) }
        return "== $title\n" +
            "  all composables: ${counts.values.sum()} (${"%.1f".format(counts.values.sum().toDouble() / frames)}/frame), " +
            "app's own: ${ours.values.sum()} (${"%.1f".format(ours.values.sum().toDouble() / frames)}/frame)\n" +
            lines
    }

    private companion object {
        const val APP = "dev.gomoku.zenyth."
        const val FRAMES = 20
        const val LINES_PER_FRAME = 10
    }
}
