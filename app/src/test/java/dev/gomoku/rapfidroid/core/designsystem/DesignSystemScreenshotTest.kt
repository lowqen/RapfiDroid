package dev.gomoku.rapfidroid.core.designsystem

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiCollapsingTopBar
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiSegmented
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
import dev.gomoku.rapfidroid.feature.board.BoardLandscape
import dev.gomoku.rapfidroid.feature.board.BoardModeSwitch
import dev.gomoku.rapfidroid.feature.board.BoardPortrait
import dev.gomoku.rapfidroid.feature.board.BoardUiState
import dev.gomoku.rapfidroid.feature.board.EvalView
import dev.gomoku.rapfidroid.feature.board.GameCard
import dev.gomoku.rapfidroid.feature.board.GameFileActions
import dev.gomoku.rapfidroid.feature.board.MoveToolbar
import dev.gomoku.rapfidroid.feature.board.PositionCard
import dev.gomoku.rapfidroid.feature.board.PositionStrip
import dev.gomoku.rapfidroid.feature.board.SummaryStrip
import dev.gomoku.rapfidroid.feature.board.VerticalEvalBar
import dev.gomoku.rapfidroid.navigation.Destination
import dev.gomoku.rapfidroid.navigation.RapfiBottomBar
import dev.gomoku.rapfidroid.navigation.RapfiRail
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
        BoardModeSwitch(BoardMode.ANALYSIS, onMode = {})
        toolbar(analysing)
        toolbar(analysing.copy(analyzing = false))
        toolbar(playing)
        // The summary line the sheet rests on: read, nothing read yet, a game.
        SummaryStrip(analysing)
        SummaryStrip(BoardUiState(connection = ConnectionState.Ready))
        SummaryStrip(playing)
    }

    /**
     * The board screen upright (P3): the board pinned, the cards on a sheet
     * resting on the summary line. A Galaxy A16's board area under the header:
     * 411 × 723dp, with the tab bar and status bar already taken.
     */
    @Test
    fun boardPortrait() = screen("screen_board_portrait", 411.dp, 771.dp) {
        portrait(sheetUp = false)
    }

    /** The same, the sheet pulled up over the board. */
    @Test
    fun boardPortraitSheet() {
        assumeTrue(variant.fontScale == 1f)
        screen("screen_board_sheet", 411.dp, 771.dp) { portrait(sheetUp = true) }
    }

    /** On its side, beside the rail: board at full height, cards on the right. */
    @Test
    @Config(qualifiers = "+w900dp-h420dp-land")
    fun boardLandscape() {
        assumeTrue(variant.fontScale == 1f)
        screen("screen_board_landscape", 811.dp, 371.dp) {
            BoardLandscape(
                ui = analysing,
                board = { mod -> GomokuBoard(render = sampleRender, modifier = mod) },
                modeSwitch = { BoardModeSwitch(BoardMode.ANALYSIS, onMode = {}) },
                toolbar = { toolbar(analysing) },
                cards = {
                    AnalysisCard(analysing, onStart = {}, onStop = {}, onPreview = {})
                    PositionCard(analysing, noFiles, onResign = {}, onDraw = {})
                },
            )
        }
    }

    @Composable
    private fun portrait(sheetUp: Boolean) {
        BoardPortrait(
            ui = analysing,
            board = { mod -> GomokuBoard(render = sampleRender, modifier = mod) },
            modeSwitch = { BoardModeSwitch(BoardMode.ANALYSIS, onMode = {}) },
            toolbar = { toolbar(analysing) },
            summary = { SummaryStrip(analysing) },
            cards = {
                PositionStrip(analysing)
                AnalysisCard(analysing, onStart = {}, onStop = {}, onPreview = {})
                PositionCard(analysing, noFiles, onResign = {}, onDraw = {})
            },
            sheetUp = sheetUp,
        )
    }

    @Composable
    private fun toolbar(ui: BoardUiState) = MoveToolbar(
        ui,
        onNewBoard = {},
        onFirst = {},
        onUndo = {},
        onToggle = {},
        onRedo = {},
        onLast = {},
        onSwapSides = {},
    )

    /** A whole screen at [width] × [height], the shell's chip and switch provided. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun screen(name: String, width: Dp, height: Dp, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/${name}_${variant.tag}.png") {
            RapfiDroidTheme(darkTheme = variant.dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, variant.fontScale),
                    LocalShellChrome provides ShellChrome(ConnectionState.Thinking, variant.dark, onStartEngine = {}, onToggleTheme = {}),
                ) {
                    Surface(Modifier.size(width, height), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    @Test
    fun calmParts() = shot("calm_parts") { calmPartsContent() }

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

    /** The six tabs: upright along the bottom, and down the side when wide (P2). */
    @Test
    fun shellNavigation() = shot("shell_nav") {
        RapfiBottomBar(selected = Destination.Board, connection = ConnectionState.Thinking, onSelect = {})
        Row(Modifier.height(360.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // A phone on its side: only the selected tab keeps its label.
            RapfiRail(selected = Destination.Research, connection = ConnectionState.Ready, onSelect = {}, showLabels = false)
            RapfiRail(selected = Destination.Research, connection = ConnectionState.Error("x"), onSelect = {})
        }
    }

    /** The board's title-less header, a list screen's tall one, the full-width tabs (P2). */
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun headers() = shot("headers") {
        CompositionLocalProvider(
            LocalShellChrome provides ShellChrome(ConnectionState.Ready, variant.dark, onStartEngine = {}, onToggleTheme = {}),
        ) {
            RapfiTopBar(title = null, height = 48.dp)
            RapfiCollapsingTopBar("설정", TopAppBarDefaults.exitUntilCollapsedScrollBehavior())
        }
        RapfiSegmented(
            options = listOf(0 to "리뷰·증명", 1 to "오프닝", 2 to "수순", 3 to "랭킹"),
            selection = 0,
            onSelect = {},
            fill = true,
        )
        RapfiSegmented(options = listOf(0 to "공식 커리큘럼", 1 to "내 정석"), selection = 1, onSelect = {}, fill = true)
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

    /**
     * The theme choices of P1 — AMOLED and the wallpaper tone at a blue and a
     * warm wallpaper — on the parts where they show: page, cards, chips, the
     * segmented control, the buttons, the top bar. Once, not per variant: each
     * choice fixes its own brightness.
     */
    @Test
    fun themeChoices() {
        assumeTrue(variant == Variant(dark = false, fontScale = 1f))
        listOf(
            Triple("amoled", true, null),
            Triple("tone250_light", false, 250f),
            Triple("tone250_dark", true, 250f),
            Triple("tone30_light", false, 30f),
        ).forEach { (tag, dark, hue) ->
            shot("theme_$tag", dark = dark, amoled = tag == "amoled", chromeHue = hue, fileTag = null) {
                CompositionLocalProvider(
                    LocalShellChrome provides ShellChrome(ConnectionState.Ready, dark, onStartEngine = {}, onToggleTheme = {}),
                ) { RapfiTopBar(title = "보드") }
                BoardModeSwitch(BoardMode.ANALYSIS, onMode = {})
                AnalysisCard(analysing, onStart = {}, onStop = {}, onPreview = {})
                calmPartsContent()
            }
        }
    }

    @Composable
    private fun calmPartsContent() {
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

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(
        name: String,
        dark: Boolean = variant.dark,
        amoled: Boolean = false,
        chromeHue: Float? = null,
        fileTag: String? = variant.tag,
        content: @Composable () -> Unit,
    ) {
        val file = if (fileTag == null) name else "${name}_$fileTag"
        captureRoboImage("src/test/screenshots/$file.png") {
            RapfiDroidTheme(darkTheme = dark, amoled = amoled, chromeHue = chromeHue) {
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
