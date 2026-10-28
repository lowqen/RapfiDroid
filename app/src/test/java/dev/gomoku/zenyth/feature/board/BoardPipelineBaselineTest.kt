package dev.gomoku.zenyth.feature.board

import android.app.Application
import androidx.lifecycle.viewModelScope
import dev.gomoku.zenyth.PerfBaseline
import dev.gomoku.zenyth.core.model.AppSettings
import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.ClockSide
import dev.gomoku.zenyth.core.model.ComputerSide
import dev.gomoku.zenyth.core.model.GameClock
import dev.gomoku.zenyth.core.model.GameState
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.domain.engine.CoordMapper
import dev.gomoku.zenyth.domain.engine.EngineStreams
import dev.gomoku.zenyth.domain.engine.ResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What one engine line and one clock tick cost the board's view model: how
 * often `uiState` changes and what building it allocates on the main thread.
 * Phase 3's before and after (docs/리팩토링-성능-위생-계획.md). See [PerfBaseline].
 *
 * Each line is let through on its own (`runCurrent` after every one) — the
 * worst case, a main thread that keeps up with the engine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ko-rKR")
class BoardPipelineBaselineTest {

    private val main = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A middle game: 30 stones spiralling out from the centre. */
    private val stones: List<Move> = buildList {
        var x = 7
        var y = 7
        val dirs = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1)
        var leg = 1
        var d = 0
        add(Move(x, y))
        while (size < 30) {
            repeat(2) {
                repeat(leg) {
                    if (size < 30) {
                        x += dirs[d].first
                        y += dirs[d].second
                        add(Move(x, y))
                    }
                }
                d = (d + 1) % 4
            }
            leg++
        }
    }

    @Test
    fun `board view model per engine line and per clock tick`() = runTest(main) {
        assumeTrue(PerfBaseline.enabled)
        val rig = boardRig(AppSettings(multiPv = 3), io = StandardTestDispatcher(testScheduler))
        var emissions = 0
        backgroundScope.launch { rig.vm.uiState.collect { emissions++ } }
        rig.game.replaceLine(stones)
        runCurrent()

        // ---- an analysis -------------------------------------------------
        val lines = EngineStreams.search(depths = 16, multiPv = 3).dropLast(1)
        val responses = lines.map { ResponseParser.parse(it, CoordMapper()) }
        rig.vm.onStartAnalyze()
        runCurrent()
        // Warm the path once, then measure a fresh search of the same shape.
        responses.forEach {
            rig.engine.responseFlow.emit(it)
            runCurrent()
        }
        rig.vm.onMultiPvChange(3)
        runCurrent()
        emissions = 0
        val bytes0 = PerfBaseline.allocatedBytes()
        val t0 = System.nanoTime()
        responses.forEach {
            rig.engine.responseFlow.emit(it)
            runCurrent()
        }
        val analysisNanos = System.nanoTime() - t0
        val analysisBytes = PerfBaseline.allocatedBytes() - bytes0
        val analysisEmissions = emissions
        rig.vm.onStopAnalyze()
        runCurrent()

        // ---- a game on the clock ----------------------------------------
        rig.game.modeFlow.value = BoardMode.GAME
        rig.game.stateFlow.value = GameState(
            computerSide = ComputerSide.WHITE,
            clock = GameClock(limited = true, turnLimitMs = 60_000, matchLimitMs = 600_000).start(ClockSide.HUMAN),
        )
        runCurrent()
        emissions = 0
        val bytes1 = PerfBaseline.allocatedBytes()
        repeat(TICKS) {
            rig.game.stateFlow.value = rig.game.stateFlow.value.let { it.copy(clock = it.clock.tick(250)) }
            runCurrent()
        }
        val clockBytes = PerfBaseline.allocatedBytes() - bytes1
        val clockEmissions = emissions

        PerfBaseline.report(
            "board_pipeline",
            listOf(
                "analysis: ${responses.size} engine lines on a ${stones.size}-stone board, multi-PV 3",
                "  uiState emissions: $analysisEmissions (${"%.2f".format(analysisEmissions.toDouble() / responses.size)} per line)",
                "  allocated: ${analysisBytes / responses.size} B/line, ${if (analysisEmissions > 0) analysisBytes / analysisEmissions else 0} B/emission",
                "  time: ${"%.1f".format(analysisNanos / 1e3 / responses.size)} µs/line",
                "clock: $TICKS ticks of 250 ms in the game tab",
                "  uiState emissions: $clockEmissions",
                "  allocated: ${clockBytes / TICKS} B/tick",
            ),
        )
        rig.vm.viewModelScope.cancel()
    }

    private companion object {
        const val TICKS = 200
    }
}
