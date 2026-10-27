package dev.gomoku.rapfidroid.feature.board

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
import dev.gomoku.rapfidroid.core.designsystem.component.cardColor
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
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
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
                sheetShape = RoundedCornerShape(topStart = RapfiTheme.tokens.radiusSheet, topEnd = RapfiTheme.tokens.radiusSheet),
                // The card face, so the sheet reads as the cards' own surface
                // pulled over the page, not as a third colour.
                sheetContainerColor = cardColor(),
                sheetShadowElevation = 3.dp,
                sheetDragHandle = { SheetHandle() },
                containerColor = MaterialTheme.colorScheme.surface,
                sheetContent = {
                    summary()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
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

/**
 * On its side: the board on the left at full height, everything else in a
 * column on the right — the engine chip and theme switch at its top (there is
 * no header, P2), then the mode, the cards, and the toolbar at its foot.
 */
@Composable
internal fun BoardLandscape(
    ui: BoardUiState,
    board: @Composable (Modifier) -> Unit,
    modeSwitch: @Composable () -> Unit,
    toolbar: @Composable () -> Unit,
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
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
