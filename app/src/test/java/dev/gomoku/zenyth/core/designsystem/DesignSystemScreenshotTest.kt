package dev.gomoku.zenyth.core.designsystem

import dev.gomoku.zenyth.core.designsystem.component.WavyProgress
import dev.gomoku.zenyth.core.designsystem.component.OutlinedButton
import dev.gomoku.zenyth.core.designsystem.component.FilledTonalButton
import dev.gomoku.zenyth.core.designsystem.component.Button
import dev.gomoku.zenyth.core.designsystem.component.LocalWaveFrames
import dev.gomoku.zenyth.feature.board.MiniAnalysisContent
import dev.gomoku.zenyth.core.designsystem.component.LabeledProgress
import dev.gomoku.zenyth.core.designsystem.component.PillBadge
import dev.gomoku.zenyth.core.designsystem.component.IconTile
import dev.gomoku.zenyth.core.designsystem.component.ItemCard
import dev.gomoku.zenyth.core.designsystem.component.RapfiFilterChip
import dev.gomoku.zenyth.core.designsystem.component.ChipRow
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.School
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gomoku.zenyth.core.designsystem.component.BoardRender
import dev.gomoku.zenyth.core.designsystem.component.CalmCard
import dev.gomoku.zenyth.core.designsystem.component.CardTitle
import dev.gomoku.zenyth.core.designsystem.component.ChoiceChips
import dev.gomoku.zenyth.core.designsystem.component.DisclosureCard
import dev.gomoku.zenyth.core.designsystem.component.EmptyState
import dev.gomoku.zenyth.core.designsystem.component.EngineChip
import dev.gomoku.zenyth.core.designsystem.component.GomokuBoard
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.component.LocalShellChrome
import dev.gomoku.zenyth.core.designsystem.component.LocalUiPrefs
import dev.gomoku.zenyth.core.designsystem.component.RapfiCollapsingTopBar
import dev.gomoku.zenyth.core.designsystem.component.RapfiSegmented
import dev.gomoku.zenyth.core.designsystem.component.RapfiTopBar
import dev.gomoku.zenyth.core.designsystem.component.ShellChrome
import dev.gomoku.zenyth.core.designsystem.component.StatusDot
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.designsystem.theme.RapfiTheme
import dev.gomoku.zenyth.core.model.AnalysisSnapshot
import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.CellTag
import dev.gomoku.zenyth.core.model.ConnectionState
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.PvSnapshot
import dev.gomoku.zenyth.core.model.SearchStats
import dev.gomoku.zenyth.core.model.TagKind
import dev.gomoku.zenyth.feature.board.AnalysisCard
import dev.gomoku.zenyth.feature.board.BoardLandscape
import dev.gomoku.zenyth.feature.board.BoardModeSwitch
import dev.gomoku.zenyth.feature.board.BoardPortrait
import dev.gomoku.zenyth.feature.board.BoardUiState
import dev.gomoku.zenyth.feature.board.EvalView
import dev.gomoku.zenyth.feature.board.GameCard
import dev.gomoku.zenyth.feature.board.GameFileActions
import dev.gomoku.zenyth.feature.board.MoveToolbar
import dev.gomoku.zenyth.feature.board.PositionCard
import dev.gomoku.zenyth.feature.board.PositionStrip
import dev.gomoku.zenyth.feature.board.SummaryStrip
import dev.gomoku.zenyth.feature.board.VerticalEvalBar
import dev.gomoku.zenyth.data.prefs.UiPrefs
import dev.gomoku.zenyth.feature.settings.SettingsActions
import dev.gomoku.zenyth.feature.settings.WhatsNew
import dev.gomoku.zenyth.feature.settings.WhatsNewCard
import dev.gomoku.zenyth.core.i18n.AppLanguage
import dev.gomoku.zenyth.feature.settings.SettingsPage
import dev.gomoku.zenyth.feature.settings.settingsSections
import dev.gomoku.zenyth.feature.settings.SettingsUiState
import dev.gomoku.zenyth.navigation.Destination
import dev.gomoku.zenyth.navigation.RapfiBottomBar
import dev.gomoku.zenyth.navigation.RapfiRail
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
                summary = { SummaryStrip(analysing) },
                cards = {
                    AnalysisCard(analysing, onPreview = {})
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
                AnalysisCard(analysing, onPreview = {})
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

    /** The engine seen from another tab, the game tab's toolbar, the wave at a few points (M5). */
    @Test
    fun boardMiniBar() = shot("board_mini") {
        MiniAnalysisContent(analysing, onOpen = {}, onPause = {})
        toolbar(playing)
        WavyProgress(0f)
        WavyProgress(0.4f)
        WavyProgress(1f)
    }

    /** A whole screen at [width] × [height], the shell's chip and switch provided. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun screen(
        name: String,
        width: Dp,
        height: Dp,
        dark: Boolean = variant.dark,
        amoled: Boolean = false,
        fontScale: Float = variant.fontScale,
        fileTag: String? = variant.tag,
        content: @Composable () -> Unit,
    ) {
        val file = if (fileTag == null) name else "${name}_$fileTag"
        captureRoboImage("src/test/screenshots/$file.png") {
            ZenythTheme(darkTheme = dark, amoled = amoled) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                    LocalShellChrome provides ShellChrome(ConnectionState.Thinking, dark, onStartEngine = {}, onToggleTheme = {}),
                    LocalWaveFrames provides false,
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

    /** The first settings page: the search pill, the pages as icon rows (M3). */
    @Test
    fun settingsRoot() = shot("settings_root") { SettingsPageShot(SettingsPage.Root, SettingsUiState(advanced = true)) }

    /**
     * M7's far ends, once each rather than per variant: the board screen on a
     * black (AMOLED) page, and the largest font Android offers (2.0) on the
     * board, a settings page and the welcome window — nothing may clip or
     * overlap there.
     */
    @Test
    fun extremes() {
        assumeTrue(variant == Variant(dark = false, fontScale = 1f))
        screen("m7_board_amoled", 411.dp, 771.dp, dark = true, amoled = true, fileTag = null) { portrait(sheetUp = false) }
        screen("m7_board_font200", 411.dp, 771.dp, fontScale = 2f, fileTag = null) { portrait(sheetUp = false) }
        shot("m7_settings_general_font200", dark = true, fontScale = 2f, fileTag = null) {
            SettingsPageShot(SettingsPage.General, SettingsUiState())
        }
        shot("m7_whats_new_font200", fontScale = 2f, fileTag = null) {
            val notes = WhatsNew.parse(java.io.File("src/main/assets/whatsnew/21.txt").readText())
            WhatsNewCard(notes.getValue(AppLanguage.KOREAN), onDismiss = {})
        }
    }

    /** The three pills (M7): 48dp, the press-morphing shape at rest. */
    @Test
    fun pills() = shot("pills") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("분석 시작") }
            FilledTonalButton(onClick = {}) { Text("저장") }
            OutlinedButton(onClick = {}) { Text("취소") }
        }
        Button(onClick = {}, enabled = false) { Text("엔진이 꺼져 있음") }
    }

    /** The window shown once after an update: mark, welcome, grouped notes, the fade, the pill. */
    @Test
    fun whatsNew() = shot("whats_new") {
        val notes = WhatsNew.parse(java.io.File("src/main/assets/whatsnew/21.txt").readText())
        WhatsNewCard(notes.getValue(AppLanguage.KOREAN), onDismiss = {})
    }

    /** 디자인: the round back button, the preview, the theme buttons, check-thumb switches (M3). */
    @Test
    fun settingsDesign() = shot("settings_design") { SettingsPageShot(SettingsPage.Design, SettingsUiState()) }

    /** 일반: the language as rows with a check on the chosen one (M3). */
    @Test
    fun settingsGeneral() = shot("settings_general") { SettingsPageShot(SettingsPage.General, SettingsUiState()) }

    /** 엔진: the thread chips and the on-device engine's own group (M3). */
    @Test
    fun settingsEngine() = shot("settings_engine") { SettingsPageShot(SettingsPage.Engine, SettingsUiState(advanced = true)) }

    /** A search finds an app setting a page down, and the desktop's entry of the same name (M3). */
    @Test
    fun settingsSearch() = shot("settings_search") { SettingsPageShot(SettingsPage.Root, SettingsUiState(query = "스레드")) }

    /** M4's list parts: a scrolling chip row, badges, icon tiles, item cards with their progress. */
    @Test
    fun listParts() = shot("list_parts") {
        ChipRow {
            RapfiFilterChip(selected = true, onClick = {}, label = { Text("전체") })
            RapfiFilterChip(selected = false, onClick = {}, label = { Text("시간") }, leadingIcon = { Icon(Icons.Outlined.Timer, null, Modifier.size(18.dp)) })
            RapfiFilterChip(selected = false, onClick = {}, label = { Text("깊이") })
            RapfiFilterChip(selected = false, onClick = {}, label = { Text("꺼짐") }, enabled = false)
        }
        ItemCard(
            title = "화월 기본",
            onClick = {},
            leading = { IconTile(Icons.Outlined.School) },
            badge = { PillBadge("입문") },
            description = "흑 3수 화월에서 백의 대표 응수를 따라 두며 익힙니다. 두 줄을 넘는 설명은 말줄임으로 끝납니다.",
            footer = { LabeledProgress(0.375f, label = "3/8") },
        )
        ItemCard(
            title = "포월 심화",
            leading = { IconTile(Icons.Outlined.School, active = false) },
            badge = { PillBadge("완료", accent = true) },
            description = "짧은 설명",
            footer = { LabeledProgress(1f) },
        )
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
        AnalysisCard(BoardUiState(connection = ConnectionState.Ready), onPreview = {})
        AnalysisCard(analysing, onPreview = {})
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
                AnalysisCard(analysing, onPreview = {})
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

    /** A settings page as the screen lays it out, minus the list's scrolling. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsPageShot(page: SettingsPage, state: SettingsUiState) {
        val context = LocalContext.current
        val prefs = remember { UiPrefs(context.applicationContext) }
        CompositionLocalProvider(LocalUiPrefs provides prefs) {
            if (page != SettingsPage.Root) {
                RapfiCollapsingTopBar(page.title, TopAppBarDefaults.exitUntilCollapsedScrollBehavior(), onBack = {})
            }
            settingsSections(page, state, SettingsActions(), pro = true, prefs = prefs).forEach { it.content() }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(
        name: String,
        dark: Boolean = variant.dark,
        amoled: Boolean = false,
        chromeHue: Float? = null,
        fontScale: Float = variant.fontScale,
        fileTag: String? = variant.tag,
        content: @Composable () -> Unit,
    ) {
        val file = if (fileTag == null) name else "${name}_$fileTag"
        captureRoboImage("src/test/screenshots/$file.png") {
            ZenythTheme(darkTheme = dark, amoled = amoled, chromeHue = chromeHue) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                    LocalWaveFrames provides false,
                ) {
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
        // A timed analysis, 3.2 s of 8 run: the sheet's wave shows it (M5).
        analysisSec = 8,
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
