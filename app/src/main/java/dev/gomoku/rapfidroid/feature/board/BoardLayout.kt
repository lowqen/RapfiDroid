package dev.gomoku.rapfidroid.feature.board

import dev.gomoku.rapfidroid.core.designsystem.component.WavyProgress
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
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

/** Height of the move toolbar under the board. */
internal val ToolbarHeight = 64.dp

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
    Column(modifier.fillMaxSize()) {
        RapfiTopBar(title = null, height = 48.dp, leading = { modeSwitch() })
        val skin = RapfiTheme.board
        val scheme = MaterialTheme.colorScheme
        val dark = scheme.surface.luminance() < 0.5f
        val sheetScheme = remember(scheme, skin.wood, dark) { woodSheetScheme(scheme, skin.wood, dark) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().drawBehind { stage(skin.wood, scheme.primary, dark) }) {
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
                sheetContainerColor = sheetScheme.surface,
                sheetShadowElevation = 3.dp,
                sheetDragHandle = { SheetHandle() },
                // The stage (drawn behind) shows through behind the board.
                containerColor = Color.Transparent,
                sheetContent = {
                    // The cards on the sheet take its wood too: their faces stay
                    // one step off the sheet, as they are off the page (M1).
                    MaterialTheme(colorScheme = sheetScheme) {
                        Column(Modifier.fillMaxWidth()) {
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
                        }
                    }
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
    BoxWithConstraints(modifier.fillMaxSize()) {
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
 * The sheet's colours: the page's, steeped in the board's wood — Mornye tints
 * its queue sheet with the album it plays (M5). Cream on paper, a dark wood
 * shade at night. A material colour, not a value: the board's own colours and
 * the value ladder are untouched.
 */
internal fun woodSheetScheme(scheme: ColorScheme, wood: Color, dark: Boolean): ColorScheme {
    val k = if (dark) 0.2f else 0.12f
    fun steep(c: Color) = lerp(c, wood, k)
    return scheme.copy(
        surface = steep(scheme.surface),
        surfaceVariant = steep(scheme.surfaceVariant),
        surfaceContainerLowest = steep(scheme.surfaceContainerLowest),
        surfaceContainerLow = steep(scheme.surfaceContainerLow),
        surfaceContainer = steep(scheme.surfaceContainer),
        surfaceContainerHigh = steep(scheme.surfaceContainerHigh),
        surfaceContainerHighest = steep(scheme.surfaceContainerHighest),
    )
}

/**
 * The board's stage: two faint rounds on the page behind it, one of wood and
 * one of the accent, as the design page's preview is lit (M5). The board is
 * drawn over them unchanged.
 */
private fun DrawScope.stage(wood: Color, accent: Color, dark: Boolean) {
    fun round(color: Color, x: Float, y: Float, r: Float) {
        val center = Offset(size.width * x, size.height * y)
        val radius = size.width * r
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
    }
    round(wood.copy(alpha = if (dark) 0.16f else 0.24f), 0.08f, 0.12f, 0.7f)
    round(accent.copy(alpha = if (dark) 0.10f else 0.07f), 0.95f, 0.6f, 0.6f)
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
