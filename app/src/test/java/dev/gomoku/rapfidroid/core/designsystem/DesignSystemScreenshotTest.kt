package dev.gomoku.rapfidroid.core.designsystem

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gomoku.rapfidroid.core.designsystem.component.BoardRender
import dev.gomoku.rapfidroid.core.designsystem.component.CalmCard
import dev.gomoku.rapfidroid.core.designsystem.component.CardTitle
import dev.gomoku.rapfidroid.core.designsystem.component.ChoiceChips
import dev.gomoku.rapfidroid.core.designsystem.component.DisclosureCard
import dev.gomoku.rapfidroid.core.designsystem.component.EmptyState
import dev.gomoku.rapfidroid.core.designsystem.component.EngineChip
import dev.gomoku.rapfidroid.core.designsystem.component.GomokuBoard
import dev.gomoku.rapfidroid.core.designsystem.component.Hint
import dev.gomoku.rapfidroid.core.designsystem.component.LocalShellChrome
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiTopBar
import dev.gomoku.rapfidroid.core.designsystem.component.ShellChrome
import dev.gomoku.rapfidroid.core.designsystem.component.StatusDot
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiDroidTheme
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme
import dev.gomoku.rapfidroid.core.model.AnalysisSnapshot
import dev.gomoku.rapfidroid.core.model.BoardMode
import dev.gomoku.rapfidroid.core.model.CellTag
import dev.gomoku.rapfidroid.core.model.ConnectionState
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.PvSnapshot
import dev.gomoku.rapfidroid.core.model.SearchStats
import dev.gomoku.rapfidroid.core.model.TagKind
import dev.gomoku.rapfidroid.feature.board.AnalysisCard
import dev.gomoku.rapfidroid.feature.board.BoardUiState
import dev.gomoku.rapfidroid.feature.board.EvalView
import dev.gomoku.rapfidroid.feature.board.GameCard
import dev.gomoku.rapfidroid.feature.board.GameFileActions
import dev.gomoku.rapfidroid.feature.board.MoveTransport
import dev.gomoku.rapfidroid.feature.board.PositionCard
import dev.gomoku.rapfidroid.feature.board.StageBar
import dev.gomoku.rapfidroid.feature.board.VerticalEvalBar
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The design system, photographed — the baseline the UI overhaul is judged
 * against (docs/UI-개편-마스터플랜.md, P0).
 *
 * Every part that can be drawn without a view model: the board with analysis
 * values on it, the eval bar, the move bar, the cards, the chips, the top bar.
 * Each in light and dark, at the default font scale and at 1.3. Whole screens
 * join this set as each one is split into a stateless body (P2 – P4).
 *
 * Unset, these tests only compose. With `-Pscreenshots=record` they write the
 * goldens in `src/test/screenshots/`; `-Pscreenshots=verify` fails on any pixel
 * that moved. The goldens are recorded on the CI runner (`screenshots.yml`), not
 * on a desktop: font rasterisation differs between machines, and a golden is
 * only worth what the machine that checks it agrees with.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one is a Hilt app that starts the engine watcher.
// Korean, the app's first language: Robolectric sets the JVM's default locale
// from the qualifiers, and `tr()` reads that, so without it every label is English.
@Config(sdk = [35], application = Application::class, qualifiers = "ko-rKR-w420dp-h900dp-xhdpi")
class DesignSystemScreenshotTest(private val variant: Variant) {

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

    @Test
    fun board() {
        // The board's text is drawn by `Paint` at its own size; the font scale
        // does not reach it, so the 1.3 picture would be the 1.0 one again.
        assumeTrue(variant.fontScale == 1f)
        shot("board") { GomokuBoard(render = sampleRender, modifier = Modifier.width(396.dp)) }
    }

    @Test
    fun evalBar() = shot("eval_bar") {
        Row(Modifier.height(240.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VerticalEvalBar(EvalView(blackPct = 63.0, evalCp = 85), Modifier.width(30.dp))
            VerticalEvalBar(EvalView(blackPct = 12.0, mate = -7), Modifier.width(20.dp), compact = true)
            VerticalEvalBar(EvalView(blackPct = 55.0, fromDb = true), Modifier.width(30.dp))
            VerticalEvalBar(null, Modifier.width(30.dp))
        }
    }

    @Test
    fun boardChrome() = shot("board_chrome") {
        StageBar(analysing, onMode = {}, onNewBoard = {}, onSwapSides = {})
        MoveTransport(analysing, onFirst = {}, onUndo = {}, onToggle = {}, onRedo = {}, onLast = {})
        StageBar(playing, onMode = {}, onNewBoard = {}, onSwapSides = {})
        MoveTransport(playing, onFirst = {}, onUndo = {}, onToggle = {}, onRedo = {}, onLast = {})
    }

    @Test
    fun calmParts() = shot("calm_parts") {
        CalmCard {
            CardTitle("카드 제목") { StatusDot(RapfiTheme.colors.positive) }
            Hint("한 줄 안내")
            ChoiceChips(
                options = listOf(1 to "1초", 3 to "3초", 10 to "10초", 0 to "무제한"),
                selected = 3,
                onSelect = {},
            )
        }
        DisclosureCard(id = "screenshot-closed", title = "분석 설정", summary = "3초 · 후보 1") {}
        DisclosureCard(id = "screenshot-open", title = "도구", defaultOpen = true) {
            Hint("펼친 내용")
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun chrome() = shot("chrome") {
        CompositionLocalProvider(
            LocalShellChrome provides ShellChrome(
                connection = ConnectionState.Ready,
                dark = variant.dark,
                onStartEngine = {},
                onToggleTheme = {},
            ),
        ) {
            RapfiTopBar(title = "보드")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EngineChip(ConnectionState.Ready, onStart = {})
            EngineChip(ConnectionState.Thinking, onStart = {})
            EngineChip(ConnectionState.Connecting, onStart = {})
            EngineChip(ConnectionState.Error("x"), onStart = {})
            EngineChip(ConnectionState.Disconnected, onStart = {})
            EngineChip(ConnectionState.Ready, onStart = {}, thermalThreads = 4)
        }
        EmptyState(Icons.Filled.Storage, "기보가 없습니다", "파일을 열면 여기에 나옵니다")
    }

    @Test
    fun analysisCards() = shot("analysis_cards") {
        AnalysisCard(BoardUiState(connection = ConnectionState.Ready), onStart = {}, onStop = {}, onPreview = {})
        AnalysisCard(analysing, onStart = {}, onStop = {}, onPreview = {})
        PositionCard(analysing, noFiles, onResign = {}, onDraw = {})
    }

    @Test
    fun gameCard() = shot("game_card") {
        GameCard(
            playing,
            onComputerSide = {},
            onMoveTime = {},
            onStrength = {},
            onRule = {},
            onMoveNow = {},
            onEngineMove = {},
            onNote = {},
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/${name}_${variant.tag}.png") {
            RapfiDroidTheme(darkTheme = variant.dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, variant.fontScale)) {
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

    // ---- fixtures -------------------------------------------------------------

    private val stones = listOf(
        Move(7, 7), Move(8, 6), Move(8, 8), Move(6, 6), Move(9, 9), Move(6, 8), Move(10, 10),
    )

    private val sampleRender = BoardRender(
        stones = stones,
        lastMove = stones.last(),
        forbidden = listOf(Move(11, 11)),
        bestMark = Move(6, 10),
        tags = mapOf(
            Move(6, 10) to CellTag("71", TagKind.RATE, depth = 18, winRatePct = 71),
            Move(11, 11) to CellTag("58", TagKind.RATE, depth = 18, winRatePct = 58),
            Move(5, 5) to CellTag("44", TagKind.RATE, depth = 17, winRatePct = 44),
            Move(10, 7) to CellTag("W9", TagKind.WIN, depth = 18),
            Move(4, 9) to CellTag("L5", TagKind.LOSE, depth = 16),
        ),
    )

    private val analysing = BoardUiState(
        render = sampleRender,
        moveCount = stones.size,
        connection = ConnectionState.Ready,
        analyzing = true,
        futureCount = 2,
        multiPv = 2,
        eval = EvalView(blackPct = 63.0, evalCp = 85),
        shown = AnalysisSnapshot(
            pvs = listOf(
                PvSnapshot(0, depth = 18, winRate = 0.71, mate = null, eval = 120, line = listOf(Move(6, 10), Move(5, 11), Move(7, 9))),
                PvSnapshot(1, depth = 18, winRate = 0.58, mate = null, eval = 40, line = listOf(Move(11, 11), Move(5, 5))),
            ),
            depth = 18,
            stats = SearchStats(depth = 18, selDepth = 27, evalCp = 120, winRatePct = 71, timeMs = 3_200, nodes = 1_230_000, speed = 384_000),
        ),
    )

    private val playing = BoardUiState(
        render = sampleRender.copy(tags = emptyMap(), bestMark = null),
        moveCount = stones.size,
        connection = ConnectionState.Ready,
        mode = BoardMode.GAME,
    )

    private val noFiles = GameFileActions(open = {}, save = {}, copyLink = {}, positionString = {}, saveImage = {})
}
