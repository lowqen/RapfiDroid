package dev.gomoku.rapfidroid.feature.board

import dev.gomoku.rapfidroid.core.designsystem.theme.TonalRamp
import dev.gomoku.rapfidroid.core.designsystem.theme.Maple
import dev.gomoku.rapfidroid.core.designsystem.component.WavyProgress
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.gomoku.rapfidroid.core.designsystem.component.rememberCalmFling
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiTopBar
import dev.gomoku.rapfidroid.core.designsystem.component.ShellActions
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme

/**
 * Height of the move toolbar's row under the board: the 56dp keys with 12dp
 * of air above and below, so the group reads as the board's and not as the
 * sheet's head (device review, 2026-10-29: at 64 the sheet's grip sat right
 * under ▶).
 */
internal val ToolbarHeight = 80.dp

/** The sheet's handle: a 4dp grip with 10dp above and below it. */
internal val SheetHandleHeight = 24.dp

/** The least of the sheet that always shows: its handle and the summary line. */
internal val MinPeek = SheetHandleHeight + SummaryHeight

/** How a phone held upright splits its height: the board, the toolbar, the sheet's peek. */
internal data class PortraitMetrics(val side: Dp, val peek: Dp)

/**
 * The board first, and the sheet gets what is left.
 *
 * The board takes the width (less the eval bar). Whatever height remains under
 * it and the toolbar is the sheet's peek, so at rest the sheet never covers a
 * point of the board. Only when the screen is too short for that — less than
 * [minPeek] would be left — does the board give up height, and then exactly
 * enough for the handle and the summary line.
 */
internal fun portraitMetrics(width: Dp, height: Dp, evalBar: Dp, toolbar: Dp, minPeek: Dp): PortraitMetrics {
    val side = max(0.dp, min(width - evalBar, height - toolbar - minPeek))
    return PortraitMetrics(side = side, peek = height - side - toolbar)
}

/**
 * The board screen held upright (UI overhaul P3): the board stays where it is,
 * and the cards come up over it on a sheet.
 *
 * It used to be one long scroll — board, move bar, cards — so reading the
 * engine card meant pushing the board off the top of the screen. Now the board
 * and its toolbar are pinned, and the cards live on a sheet that rests below
 * them showing the [summary] line (best move, win rate, depth, speed) and is
 * pulled up to read the rest.
 *
 * Two resting places, not three: Material's standard sheet, whose drag, nested
 * scroll and accessibility actions are tested, rather than a hand-made one with
 * a half stop. A half stop can come later if it is missed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardPortrait(
    ui: BoardUiState,
    board: @Composable (Modifier) -> Unit,
    modeSwitch: @Composable () -> Unit,
    toolbar: @Composable () -> Unit,
    summary: @Composable () -> Unit,
    cards: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    sheetUp: Boolean = false,
) {
    WoodTheme {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        RapfiTopBar(title = null, height = 48.dp, leading = { modeSwitch() })
        val skin = RapfiTheme.board
        val scheme = MaterialTheme.colorScheme
        val dark = scheme.surface.luminance() < 0.5f
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().drawBehind { stage(skin.wood, skin.woodEdge, dark) }) {
            val showBar = ui.showEvalBar && ui.analysisTab
            // A 4dp gutter left of the bar, so it does not touch the screen edge.
            val bar = if (showBar) evalBarWidth(compact = true) + 8.dp else 0.dp
            val metrics = portraitMetrics(maxWidth, maxHeight, bar, ToolbarHeight, MinPeek)
            val scaffold = rememberBottomSheetScaffoldState(
                bottomSheetState = rememberStandardBottomSheetState(
                    initialValue = if (sheetUp) SheetValue.Expanded else SheetValue.PartiallyExpanded,
                ),
            )
            BottomSheetScaffold(
                scaffoldState = scaffold,
                sheetPeekHeight = metrics.peek,
                // Mornye's queue sheet: a big top corner, and the face steeped in
                // the colour of what is playing — here the board's wood (M5).
                sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetShadowElevation = 3.dp,
                sheetDragHandle = { SheetHandle() },
                // The stage (drawn behind) shows through behind the board.
                containerColor = Color.Transparent,
                sheetContent = {
                    summary()
                    SheetRule(ui)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling())
                            .padding(RapfiTheme.tokens.screenPadding),
                        verticalArrangement = Arrangement.spacedBy(RapfiTheme.tokens.listGap),
                        content = cards,
                    )
                },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        BoardWithBar(
                            ui,
                            showBar,
                            compact = true,
                            side = metrics.side,
                            board = board,
                            modifier = Modifier.padding(start = if (showBar) 4.dp else 0.dp),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(ToolbarHeight), contentAlignment = Alignment.Center) {
                        toolbar()
                    }
                }
            }
        }
    }
    }
}

/**
 * On its side: the board on the left at full height, everything else in a
 * column on the right — the engine chip and theme switch at its top (there is
 * no header, P2), then the mode, the summary line, the cards, and the toolbar
 * at its foot.
 */
@Composable
internal fun BoardLandscape(
    ui: BoardUiState,
    board: @Composable (Modifier) -> Unit,
    modeSwitch: @Composable () -> Unit,
    toolbar: @Composable () -> Unit,
    summary: @Composable () -> Unit,
    cards: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    WoodTheme {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val side = min(maxHeight - 16.dp, maxWidth * 0.62f)
        val showBar = ui.showEvalBar && ui.analysisTab
        Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoardWithBar(ui, showBar, compact = false, side = side, board = board)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShellActions(Modifier.align(Alignment.End))
                modeSwitch()
                summary()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()),
                    verticalArrangement = Arrangement.spacedBy(RapfiTheme.tokens.listGap),
                    content = cards,
                )
                toolbar()
            }
        }
    }
    }
}

/** The eval bar beside a board of side [side]; the bar is as tall as the board. */
@Composable
internal fun BoardWithBar(
    ui: BoardUiState,
    showBar: Boolean,
    compact: Boolean,
    side: Dp,
    board: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
        if (showBar) {
            VerticalEvalBar(ui.eval, Modifier.width(evalBarWidth(compact)).height(side), compact = compact)
        }
        board(Modifier.size(side))
    }
}

/**
 * The line between the summary and the cards: during an analysis with a time
 * limit, how much of it has run, as a wave ([WavyProgress], M5); otherwise a
 * hairline.
 */
@Composable
private fun SheetRule(ui: BoardUiState) {
    val timed = ui.analysisTab && ui.analyzing && ui.analysisSec > 0 && !ui.stale
    Box(
        Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (timed) {
            val ms = ui.shown?.stats?.timeMs ?: 0L
            WavyProgress(ms / (ui.analysisSec * 1000f))
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * The board screen in the board's colours (M5, after the device review of
 * 2026-10-29). Mornye's player takes its colours from the album it plays — the
 * play button, the sheet, the page. Here the album is the board: the warm wood
 * under a cool violet page and a violet ▶ cut the screen in two. So the whole
 * board screen — page, sheet, cards, ▶ and its keys — is drawn from the wood's
 * own hue ([Maple], 70°), and the violet stays on the other tabs.
 *
 * The keys are the board's other material, the stones (2026-10-29): ▶ is a
 * black stone by day and a white one at night, ‹ › pale maple. The first cut
 * took ▶ from the wood ramp itself, and a dark wood tone is a mustard brown
 * («똥색»).
 *
 * Only chrome roles change. The board's skin, the value ladder on it, the eval
 * bar and the graph are untouched: the colour there is the value. Black
 * (AMOLED) pages stay black.
 */
@Composable
internal fun WoodTheme(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val wood = remember(scheme) { woodScheme(scheme) }
    MaterialTheme(colorScheme = wood, content = content)
}

/** The palette [WoodTheme] puts on; see there. Tones as the app's own scheme sets them (M1). */
internal fun woodScheme(scheme: ColorScheme): ColorScheme {
    val dark = scheme.surface.luminance() < 0.5f
    val black = scheme.surface.luminance() < 0.001f
    val paper = TonalRamp(hue = Maple.hue, chroma = if (dark) 0.012f else 0.014f)
    // Stone ink: near-neutral, only warm enough to sit on the maple.
    val ink = TonalRamp(hue = Maple.hue, chroma = 0.018f)
    // The pale keys and selections: quiet wood, not the board's full colour
    // (Mornye's pale tones sit at chroma ~0.03 ~ 0.04).
    val pale = TonalRamp(hue = Maple.hue, chroma = 0.035f)
    val accents = if (dark) {
        scheme.copy(
            primary = paper.tone(92), onPrimary = ink.tone(12),
            primaryContainer = pale.tone(30), onPrimaryContainer = paper.tone(92),
            secondaryContainer = pale.tone(28), onSecondaryContainer = paper.tone(92),
            onSurfaceVariant = ink.tone(78), outlineVariant = paper.tone(32),
        )
    } else {
        scheme.copy(
            primary = ink.tone(22), onPrimary = paper.tone(99),
            primaryContainer = pale.tone(88), onPrimaryContainer = ink.tone(20),
            secondaryContainer = pale.tone(89), onSecondaryContainer = ink.tone(24),
            onSurfaceVariant = ink.tone(38), outlineVariant = paper.tone(84),
        )
    }
    if (black) return accents
    return if (dark) {
        accents.copy(
            background = paper.tone(10), surface = paper.tone(10), surfaceVariant = paper.tone(26),
            surfaceContainerLowest = paper.tone(7), surfaceContainerLow = paper.tone(14),
            surfaceContainer = paper.tone(18), surfaceContainerHigh = paper.tone(22),
            surfaceContainerHighest = paper.tone(26),
        )
    } else {
        accents.copy(
            background = paper.tone(98), surface = paper.tone(98), surfaceVariant = paper.tone(90),
            surfaceContainerLowest = paper.tone(100), surfaceContainerLow = paper.tone(96),
            surfaceContainer = paper.tone(94), surfaceContainerHigh = paper.tone(91),
            surfaceContainerHighest = paper.tone(89),
        )
    }
}

/**
 * The board's stage: two faint rounds of wood on the page behind it, as the
 * design page's preview is lit (M5). The board is drawn over them unchanged.
 * Both are wood since ▶ took the stones' colours — a charcoal haze is not light.
 */
private fun DrawScope.stage(wood: Color, edge: Color, dark: Boolean) {
    fun round(color: Color, x: Float, y: Float, r: Float) {
        val center = Offset(size.width * x, size.height * y)
        val radius = size.width * r
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
    }
    round(wood.copy(alpha = if (dark) 0.16f else 0.24f), 0.08f, 0.12f, 0.7f)
    round(edge.copy(alpha = if (dark) 0.12f else 0.14f), 0.95f, 0.6f, 0.6f)
}

/** A grip, not Material's 48dp handle: the peek is height the board gave up. */
@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().height(SheetHandleHeight), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        )
    }
}
