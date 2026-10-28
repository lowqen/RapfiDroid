package dev.gomoku.zenyth.feature.board

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gomoku.zenyth.core.designsystem.component.BoardMark
import dev.gomoku.zenyth.core.designsystem.component.BoardMarkColors
import dev.gomoku.zenyth.core.designsystem.component.BoardRender
import dev.gomoku.zenyth.core.designsystem.component.DbLabel
import dev.gomoku.zenyth.core.designsystem.component.GomokuBoard
import dev.gomoku.zenyth.core.designsystem.component.LocalShellChrome
import dev.gomoku.zenyth.core.designsystem.component.LocalWaveFrames
import dev.gomoku.zenyth.core.designsystem.component.ShellChrome
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.model.AppSettings
import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.CandidateState
import dev.gomoku.zenyth.core.model.CellTag
import dev.gomoku.zenyth.core.model.ClockSide
import dev.gomoku.zenyth.core.model.ComputerSide
import dev.gomoku.zenyth.core.model.ConnectionState
import dev.gomoku.zenyth.core.model.DbCellKind
import dev.gomoku.zenyth.core.model.GameClock
import dev.gomoku.zenyth.core.model.GameState
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.MoveQuality
import dev.gomoku.zenyth.core.model.OpeningEval
import dev.gomoku.zenyth.core.model.ProveMark
import dev.gomoku.zenyth.core.model.ProveOverlay
import dev.gomoku.zenyth.core.model.TagKind
import dev.gomoku.zenyth.domain.engine.CoordMapper
import dev.gomoku.zenyth.domain.engine.ResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real [BoardScreen] — not its stateless parts — on a view model fed by
 * fakes, and a board with every overlay at once. Recorded before the
 * performance work (docs/리팩토링-성능-위생-계획.md, Phase 0) because that work
 * rewires exactly what lies between the view model and these pixels: which
 * state each card reads, how the board is handed its render, how it draws.
 *
 * Same rules as [dev.gomoku.zenyth.core.designsystem.DesignSystemScreenshotTest]:
 * `-Pscreenshots=record|verify`, goldens made on the CI runner only.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one is a Hilt app that starts the engine watcher.
@Config(sdk = [35], application = Application::class, qualifiers = "ko-rKR-w420dp-h900dp-xhdpi")
class BoardLiveScreenshotTest(private val variant: Variant) {

    data class Variant(val dark: Boolean, val fontScale: Float) {
        val tag: String get() = (if (dark) "dark" else "light") + "_" + (fontScale * 100).toInt()
        override fun toString(): String = tag
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = listOf(
            Variant(dark = false, fontScale = 1f),
            Variant(dark = true, fontScale = 1f),
            Variant(dark = false, fontScale = 1.3f),
            Variant(dark = true, fontScale = 1.3f),
        ).map { arrayOf(it) }
    }

    // ---- the live screen -----------------------------------------------------

    /** The analysis tab mid-search: two lines, the card, the graph, the board's values. */
    @Test
    fun liveAnalysis() {
        val rig = live(AppSettings(multiPv = 2)) { game.replaceLine(stones) }
        rig.vm.onStartAnalyze()
        rig.feed(*twoLines)
        rig.screen("screen_live_analysis")
    }

    /** The game tab with the clock running. */
    @Test
    fun liveGame() {
        val rig = live(AppSettings(showClock = true)) {
            game.replaceLine(stones)
            game.modeFlow.value = BoardMode.GAME
            game.stateFlow.value = GameState(
                computerSide = ComputerSide.WHITE,
                clock = GameClock(limited = true, turnLimitMs = 60_000, matchLimitMs = 600_000)
                    .start(ClockSide.HUMAN)
                    .tick(12_250),
            )
        }
        rig.screen("screen_live_game")
    }

    /** The board at 130 %: wider than its box, in a sideways scroll. */
    @Test
    fun liveZoom() {
        assumeTrue(variant == Variant(dark = false, fontScale = 1f))
        val rig = live(AppSettings(multiPv = 2, boardZoomPercent = 130)) { game.replaceLine(stones) }
        rig.vm.onStartAnalyze()
        rig.feed(*twoLines)
        rig.screen("screen_live_zoom", fileTag = null)
    }

    // ---- the board with everything on it ------------------------------------

    @Test
    fun boardDense() {
        // The board's text is drawn by `Paint` at its own size; the font scale
        // does not reach it, so the 1.3 picture would be the 1.0 one again.
        assumeTrue(variant.fontScale == 1f)
        shot("board_dense") {
            GomokuBoard(render = denseRender, modifier = Modifier.width(396.dp))
            GomokuBoard(render = proveRender, modifier = Modifier.width(396.dp))
        }
    }

    // ---- harness -------------------------------------------------------------

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun live(settings: AppSettings, setup: suspend BoardRig.() -> Unit): BoardRig {
        val rig = boardRig(settings, io = Dispatchers.Unconfined)
        runBlocking { rig.setup() }
        idle()
        return rig
    }

    private fun BoardRig.feed(vararg lines: String) {
        idle()
        lines.forEach { engine.responseFlow.tryEmit(ResponseParser.parse(it, CoordMapper())) }
        idle()
    }

    private fun BoardRig.screen(name: String, fileTag: String? = variant.tag) {
        try {
            screen(name, 411.dp, 771.dp, fileTag) { BoardScreen(viewModel = vm) }
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun screen(name: String, width: Dp, height: Dp, fileTag: String?, content: @Composable () -> Unit) {
        val file = if (fileTag == null) name else "${name}_$fileTag"
        captureRoboImage("src/test/screenshots/$file.png") {
            ZenythTheme(darkTheme = variant.dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, variant.fontScale),
                    LocalShellChrome provides ShellChrome(ConnectionState.Ready, variant.dark, onStartEngine = {}, onToggleTheme = {}),
                    LocalWaveFrames provides false,
                ) {
                    Surface(Modifier.size(width, height), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/${name}_${variant.tag}.png") {
            ZenythTheme(darkTheme = variant.dark) {
                CompositionLocalProvider(LocalWaveFrames provides false) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.width(420.dp).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) { content() }
                    }
                }
            }
        }
    }

    // ---- fixtures ------------------------------------------------------------

    private val stones = listOf(
        Move(7, 7), Move(8, 6), Move(8, 8), Move(6, 6), Move(9, 9), Move(6, 8), Move(10, 10),
    )

    /** Two `yxnbest` lines, the counters, and the realtime overlays between them. */
    private val twoLines = arrayOf(
        "MESSAGE REALTIME POS 10,6",
        "MESSAGE REALTIME DONE 10,6",
        "MESSAGE REALTIME POS 11,11",
        "MESSAGE REALTIME LOSE 4,9",
        "INFO PV 0",
        "INFO DEPTH 18-27",
        "INFO EVAL 120",
        "INFO WINRATE 0.71",
        "INFO NODES 1230000",
        "INFO SPEED 384000",
        "INFO TIME 3200",
        "INFO BESTLINE 10,6 11,5 9,7",
        "INFO PV DONE",
        "INFO PV 1",
        "INFO DEPTH 18-25",
        "INFO EVAL 40",
        "INFO WINRATE 0.58",
        "INFO BESTLINE 11,11 5,5",
        "INFO PV DONE",
    )

    private val denseRender = BoardRender(
        stones = stones + listOf(Move(5, 5), Move(9, 7), Move(7, 9), Move(6, 10), Move(10, 6)),
        lastMove = Move(10, 6),
        forbidden = listOf(Move(11, 11), Move(4, 4)),
        ghosts = listOf(Move(6, 9), Move(5, 10), Move(4, 11)),
        bestMark = Move(6, 9),
        tags = mapOf(
            Move(6, 9) to CellTag("71%", TagKind.RATE, depth = 18, winRatePct = 71),
            Move(11, 7) to CellTag("58%", TagKind.RATE, depth = 18, winRatePct = 58),
            Move(3, 3) to CellTag("44%", TagKind.RATE, depth = 17, winRatePct = 44),
            Move(12, 10) to CellTag("W9", TagKind.WIN, depth = 18),
            Move(2, 12) to CellTag("L5", TagKind.LOSE, depth = 16),
            Move(13, 13) to CellTag("W13", TagKind.WIN, depth = 22),
        ),
        candidates = mapOf(Move(8, 11) to CandidateState.LIVE, Move(9, 12) to CandidateState.DONE),
        loseCells = setOf(Move(1, 1), Move(13, 1)),
        dbLabels = mapOf(
            Move(4, 7) to DbLabel("63%", DbCellKind.RATE),
            Move(11, 3) to DbLabel("W", DbCellKind.WIN),
            Move(12, 5) to DbLabel("L", DbCellKind.LOSS),
            Move(2, 6) to DbLabel("=", DbCellKind.DRAW),
            Move(3, 9) to DbLabel("note", DbCellKind.NOTE),
        ),
        badges = mapOf(Move(10, 6) to MoveQuality.BRILLIANT, Move(7, 9) to MoveQuality.BLUNDER),
        blocked = setOf(Move(0, 14), Move(14, 0)),
        marks = mapOf(Move(1, 8) to BoardMark("A", BoardMarkColors.Good), Move(13, 8) to BoardMark("B", BoardMarkColors.Bad)),
        gradeMarks = mapOf(Move(0, 7) to OpeningEval.ladder.first(), Move(14, 7) to OpeningEval.ladder.last()),
    )

    private val proveRender = BoardRender(
        stones = stones,
        lastMove = stones.last(),
        showNumbers = false,
        textScale = 1.3f,
        tags = mapOf(Move(11, 7) to CellTag("58%", TagKind.RATE, depth = 18, winRatePct = 58)),
        prove = ProveOverlay(
            ghost = mapOf(Move(6, 9) to 1, Move(5, 10) to 2),
            marks = mapOf(
                Move(12, 3) to ProveMark.WIN,
                Move(13, 2) to ProveMark.LOSS,
                Move(12, 12) to ProveMark.OPEN,
                Move(2, 2) to ProveMark.EXH,
                Move(2, 12) to ProveMark.LATENT,
            ),
            budgets = mapOf(Move(12, 12) to 40),
            ghostLen = 2,
        ),
    )
}
