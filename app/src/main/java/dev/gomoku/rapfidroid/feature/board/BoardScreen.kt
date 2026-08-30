package dev.gomoku.rapfidroid.feature.board

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.rapfidroid.core.designsystem.component.BoardRender
import dev.gomoku.rapfidroid.core.designsystem.component.GomokuBoard
import dev.gomoku.rapfidroid.core.designsystem.component.renderBoardPng
import dev.gomoku.rapfidroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.rapfidroid.core.designsystem.component.WideLayoutMin
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiTopBar
import dev.gomoku.rapfidroid.core.designsystem.theme.MOTION_VALUE
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme
import dev.gomoku.rapfidroid.core.designsystem.theme.expandFadeIn
import dev.gomoku.rapfidroid.core.designsystem.theme.shrinkFadeOut
import dev.gomoku.rapfidroid.core.designsystem.theme.tabular
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.BoardShift
import dev.gomoku.rapfidroid.core.model.BoardSymmetry
import dev.gomoku.rapfidroid.core.model.ClockSide
import dev.gomoku.rapfidroid.core.model.ComputerSide
import dev.gomoku.rapfidroid.core.model.DbCellKind
import dev.gomoku.rapfidroid.core.model.FontSpec
import dev.gomoku.rapfidroid.core.model.GamePrompt
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.OpeningProtocol
import dev.gomoku.rapfidroid.core.model.PvSnapshot
import dev.gomoku.rapfidroid.core.model.Swap2Choice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    /** Cell whose board text is being edited (the desktop's Board Text dialog). */
    var labelTarget by remember { mutableStateOf<Move?>(null) }
    var commentDraft by remember { mutableStateOf<String?>(null) }
    var showBalance by remember { mutableStateOf(false) }
    var showPosition by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }
    /** The two toolbar buttons that open a second row instead of acting at once. */
    var symmetryOpen by remember { mutableStateOf(false) }
    var shiftOpen by remember { mutableStateOf(false) }
    /** The rarely-used half of the toolbar, folded away by default. */
    var moreOpen by remember { mutableStateOf(false) }
    /** The two panels below the analysis, collapsed until wanted. */
    var dbOpen by remember { mutableStateOf(false) }
    var gameOpen by remember { mutableStateOf(false) }

    // `prove_pulse_tick` (main.c:9206): the focus stone blinks twice a second for
    // as long as a proof runs, and stops dead when it ends.
    var provePulse by remember { mutableStateOf(false) }
    val proving = ui.render.prove != null
    LaunchedEffect(proving) {
        provePulse = false
        while (proving) {
            kotlinx.coroutines.delay(500)
            provePulse = !provePulse
        }
    }

    val scope = rememberCoroutineScope()
    // The export launcher outlives recompositions, so it must not capture the
    // frame it was created with.
    val currentRender by rememberUpdatedState(ui.render)
    // The exported PNG is the board the user is looking at, dark wood and all —
    // `drawBoard` is the same function, so the skin has to travel with it.
    val skin = RapfiTheme.board
    val currentSkin by rememberUpdatedState(skin)
    val saveImage = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.Default) {
                    renderBoardPng(currentRender, currentSkin)
                }
                viewModel.onSaveImage(uri, bytes)
            }
        }
    }

    // One snackbar for the whole app (see [LocalSnackbarHostState]); this screen
    // used to build its own and time it by hand, which put a notice inside the
    // scrolling page where it could be scrolled away from.
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.onNoticeShown()
        }
    }

    // The board is full-bleed; everything else keeps the page margin.
    val pad = Modifier.padding(horizontal = 12.dp)

    // Portrait is one column, as it has been. Anything wider — a landscape phone,
    // any tablet — puts the board beside the panels instead of above them: the
    // board is square, so in landscape a single column wastes both halves of the
    // screen and buries the analysis below the fold. The two sides scroll
    // independently, which is what makes the board stay put while the PV list is
    // read. The desktop has the same split, it just never has to choose.
    val boardPane: @Composable ColumnScope.() -> Unit = {
        EvalHeader(ui, pad)
        // settings_dev.txt line 1 — the desktop's own toggle.
        if (ui.showEvalBar) EvalBar(blackWinRate = ui.blackWinRate, mate = ui.blackMate, modifier = pad)
        // The desktop paints the review / prove counters straight onto its
        // drawing area, top-left, in blue and orange (main.c:6861-6905). Here they
        // go onto the board itself — over the ghost stones of the line under
        // search, which is the only place a phone user is actually looking.
        Box {
            ZoomableBoard(
                render = ui.render.copy(provePulse = provePulse),
                scale = ui.boardScale,
                onTap = viewModel::onTap,
                onLongPress = { cell ->
                    // Empty points only: a board text belongs to a candidate move.
                    if (ui.dbActive && !ui.render.stones.contains(cell)) labelTarget = cell
                },
            )
            ui.research?.let {
                ResearchBadge(
                    banner = it,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    onStop = viewModel::onStopResearch,
                )
            }
        }
        BoardControls(
            ui = ui,
            modifier = pad,
            moreOpen = moreOpen,
            symmetryOpen = symmetryOpen,
            shiftOpen = shiftOpen,
            onFirst = viewModel::onFirst,
            onUndo = viewModel::onUndo,
            onRedo = viewModel::onRedo,
            onLast = viewModel::onLast,
            // One button for the search: the desktop's own default hotkey is
            // `thinking toggle` (F1), and a pair where one half is always
            // disabled wastes the best spot on the row.
            onToggleAnalyze = {
                if (ui.busy) viewModel.onStopAnalyze() else viewModel.onStartAnalyze()
            },
            onToggleMore = {
                moreOpen = !moreOpen
                // Folding the tools away takes their second rows with them, or a
                // shape row would outlive the button that opened it.
                if (!moreOpen) {
                    symmetryOpen = false
                    shiftOpen = false
                }
            },
            onDefend = viewModel::onSearchDefend,
            onBalance = { showBalance = true },
            onSaveImage = { saveImage.launch(imageFileName(ui.moveCount)) },
            onInfo = { showPosition = true },
            onReset = {
                // settings.txt line 17: warn before throwing the game away.
                if (ui.showWarning && ui.moveCount > 0) confirmReset = true else viewModel.onReset()
            },
            onToggleSymmetry = {
                symmetryOpen = !symmetryOpen
                shiftOpen = false
            },
            onToggleShift = {
                shiftOpen = !shiftOpen
                symmetryOpen = false
            },
            onSymmetry = viewModel::onSymmetry,
            onShift = viewModel::onShift,
        )
        // The user's own buttons (`function/toolbar*.txt`), running the same
        // console scripts the desktop runs (main.c:10064 `custom_function`).
        // Anything the board row already does was dropped in the view model, so
        // this is empty until a toolbar with something new in it is imported.
        AnimatedVisibility(moreOpen, enter = expandFadeIn, exit = shrinkFadeOut) {
            UserToolbar(
                items = ui.toolbar,
                language = ui.language,
                style = ui.toolbarStyle,
                enabled = ui.connection.isLive,
                onRun = viewModel::onRunScript,
                modifier = pad,
            )
        }
    }

    // What a search produces comes first, because that is what the board is for:
    // the status fields, then the candidate moves, then the graph. The database
    // and the game live below them in panels that open when they are wanted —
    // both are read far less often than the analysis, and on a phone every panel
    // above the fold pushes the next one off it.
    val sidePane: @Composable ColumnScope.() -> Unit = {
        StatusBar(ui, pad)
        PvHeader(multiPv = ui.multiPv, onMultiPv = viewModel::onMultiPvChange, modifier = pad)
        PvList(
            pvs = ui.snapshot?.pvs.orEmpty(),
            size = ui.render.size,
            previewPv = ui.previewPv,
            onPreview = viewModel::onPreviewPv,
            modifier = pad,
        )
        // settings_dev.txt line 2.
        if (ui.showWrGraph) WinRateGraph(ui.winRateHistory, ui.moveCount, pad)
        DatabaseSection(
            ui = ui,
            expanded = dbOpen,
            onToggle = { dbOpen = !dbOpen },
            modifier = pad,
            onQueryValue = viewModel::onQueryDbValue,
            onQueryComment = viewModel::onQueryDbComment,
            onEditComment = { commentDraft = ui.db.snapshot.comment },
            onSetBest = viewModel::onDbSetBestMove,
            onClearBest = viewModel::onDbClearBestMove,
            onDeleteOne = viewModel::onDbDeleteOne,
            onSave = viewModel::onDbSave,
        )
        GameSection(
            ui = ui,
            // A finished game states its result in the header, but the rematch
            // button is in the body — opening it there saves the tap.
            expanded = gameOpen || ui.game.result != null,
            onToggle = { gameOpen = !gameOpen },
            modifier = pad,
            onComputerSide = viewModel::onComputerSide,
            onEngineMove = viewModel::onEngineMove,
            onNewGame = {
                if (ui.showWarning && ui.moveCount > 0) confirmReset = true else viewModel.onNewGame()
            },
            onDraw = viewModel::onOfferDraw,
            onResign = { confirmResign = true },
            onToggleForbidden = viewModel::onToggleForbidden,
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= WideLayoutMin) {
            Row(
                modifier = Modifier.fillMaxSize().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // settings.txt line 33: 0 is the desktop's "left vertical". With
                // room for two columns the app can honour it; in portrait there
                // is none, so the toolbar stays a row above the board.
                if (ui.toolbarPos == 0) {
                    UserToolbarColumn(
                        items = ui.toolbar,
                        language = ui.language,
                        style = ui.toolbarStyle,
                        enabled = ui.connection.isLive,
                        onRun = viewModel::onRunScript,
                        modifier = Modifier.padding(start = 6.dp, top = 44.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = boardPane,
                )
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = sidePane,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                boardPane()
                sidePane()
            }
        }
    }

    if (showBalance) {
        BalanceDialog(
            onDismiss = { showBalance = false },
            onConfirm = { two, bias ->
                showBalance = false
                viewModel.onBalance(two, bias)
            },
        )
    }

    if (showPosition) {
        PositionDialog(
            ui = ui,
            onDismiss = { showPosition = false },
            onLoad = { text ->
                showPosition = false
                viewModel.onLoadPositionString(text)
            },
            onCopied = { viewModel.onNotice(tr("국면 문자열을 복사했습니다", "Position string copied")) },
        )
    }

    labelTarget?.let { cell ->
        BoardTextDialog(
            cell = cell,
            size = ui.render.size,
            initial = ui.db.snapshot.cells[cell]?.text.orEmpty(),
            editable = ui.canEditDb,
            onDismiss = { labelTarget = null },
            onConfirm = { text ->
                labelTarget = null
                viewModel.onCellLabel(cell, text)
            },
        )
    }

    commentDraft?.let { initial ->
        CommentDialog(
            initial = initial,
            editable = ui.canEditDb,
            editFont = ui.dbCommentEditFont,
            onDismiss = { commentDraft = null },
            onConfirm = { text ->
                commentDraft = null
                viewModel.onSaveComment(text)
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("판을 초기화할까요?", "Clear the board?")) },
            text = { Text(tr("${ui.moveCount}수를 모두 지웁니다. 되돌릴 수 없습니다.", "Removes all ${ui.moveCount} moves. This cannot be undone.")) },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    viewModel.onReset()
                }) { Text(tr("초기화", "Clear")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(tr("취소", "Cancel")) }
            },
        )
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text(tr("기권할까요?", "Resign?")) },
            text = { Text(tr("엔진에 `yxresign`을 보내고 이 대국을 끝냅니다.", "Sends `yxresign` to the engine and ends this game.")) },
            confirmButton = {
                Button(onClick = {
                    confirmResign = false
                    viewModel.onResign()
                }) { Text(tr("기권", "Resign")) }
            },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text(tr("취소", "Cancel")) } },
        )
    }

    // The desktop's game dialogs, one per prompt.
    ui.game.prompt?.let { prompt ->
        GamePromptDialog(
            prompt = prompt,
            size = ui.render.size,
            onSwap = viewModel::onSwapAnswer,
            onSwap2 = viewModel::onSwap2Answer,
            onFifthCount = viewModel::onFifthCount,
            onDismiss = viewModel::onDismissPrompt,
        )
    }
}

/**
 * A panel that states its business in one line and keeps the buttons folded
 * until asked. The summary is the part that is read; the body is the part that
 * is used, and on a phone the two do not have to cost the same space.
 */
@Composable
private fun Section(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        // A real surface role, not an alpha over whatever happens to be behind:
        // the same panel used to come out a different colour on the board page
        // and inside a dialog, because 40 % of a variant is a different colour
        // over every parent.
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) tr("접기", "Collapse") else tr("펼치기", "Expand"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(expanded, enter = expandFadeIn, exit = shrinkFadeOut) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
            }
        }
    }
}

/**
 * The game side of the board: who the engine plays, the two clocks, and the
 * actions the desktop keeps in its Game menu (new game, draw, resign) plus the
 * forbidden-point toggle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameSection(
    ui: BoardUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onComputerSide: (ComputerSide) -> Unit,
    onEngineMove: () -> Unit,
    onNewGame: () -> Unit,
    onDraw: () -> Unit,
    onResign: () -> Unit,
    onToggleForbidden: () -> Unit,
) {
    val game = ui.game
    val summary = buildString {
        game.result?.let {
            append(it.describe())
            return@buildString
        }
        append(tr("${ui.sideToMove.label} 차례", "${ui.sideToMove.label} to move"))
        if (game.computerSide != ComputerSide.NONE) append(" · ${game.computerSide.label}")
        if (game.opening != OpeningProtocol.NONE) append(" · ${game.opening.label}")
    }
    Section(tr("대국", "Game"), summary, expanded, onToggle, modifier) {
        game.result?.let { result ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    result.describe(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        // computerside (settings.txt lines 4-5)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ComputerSide.entries.forEach { side ->
                FilterChip(
                    selected = game.computerSide == side,
                    onClick = { onComputerSide(side) },
                    label = { Text(side.label) },
                )
            }
        }

        if (ui.showClock) ClockRow(ui)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = onNewGame, label = { Text(tr("새 대국", "New game")) })
            AssistChip(
                onClick = onEngineMove,
                enabled = ui.engineOnMove,
                label = { Text(tr("엔진 착수", "Engine move")) },
            )
            AssistChip(
                onClick = onDraw,
                enabled = !game.over && ui.connection.isLive,
                label = { Text(tr("무승부 제안", "Offer draw")) },
            )
            AssistChip(
                onClick = onResign,
                enabled = !game.over && ui.moveCount > 0,
                label = { Text(tr("기권", "Resign")) },
            )
            if (ui.isRenju) {
                FilterChip(
                    selected = ui.showForbidden,
                    onClick = onToggleForbidden,
                    label = { Text(tr("금수 표시", "Show forbidden")) },
                )
            }
        }

        if (ui.openingNeedsOddSize) {
            Text(
                tr("오프닝 규칙은 판의 정중앙을 기준으로 하므로 홀수 크기 판이 필요합니다 (설정 1행).", "An opening rule works from the exact centre of the board, so the board size has to be odd (settings line 1)."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (game.offeringFifth) {
            Text(
                tr("5수 후보 ${game.fifthCount}개를 순서대로 놓으세요. 상대가 그중 하나를 고릅니다.", "Place ${game.fifthCount} candidate 5th moves in order. Your opponent picks one of them."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (game.log.isNotEmpty()) {
            Text(
                game.log.takeLast(2).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Used / left per side, in the desktop's four-field layout. */
@Composable
private fun ClockRow(ui: BoardUiState) {
    val clock = ui.game.clock
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(ClockSide.COMPUTER to tr("컴퓨터", "Computer"), ClockSide.HUMAN to tr("사람", "Human")).forEach { (side, name) ->
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium)
                    if (clock.running == side) {
                        Text(
                            "●",
                            style = MaterialTheme.typography.labelMedium,
                            color = RapfiTheme.colors.positive,
                        )
                    }
                }
                // A clock is the purest case for tabular figures: it counts down
                // once a second and every digit is a different width.
                Text(
                    tr("남음 ${clock.label(side)}", "left ${clock.label(side)}"),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    tr("사용 ${clock.usedLabel(side)}", "used ${clock.usedLabel(side)}"),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One dialog per desktop game dialog: the swap questions, the number of fifth
 * moves, and the three information popups.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamePromptDialog(
    prompt: GamePrompt,
    size: Int,
    onSwap: (Boolean) -> Unit,
    onSwap2: (Swap2Choice) -> Unit,
    onFifthCount: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        is GamePrompt.Swap -> AlertDialog(
            onDismissRequest = { onSwap(false) },
            title = { Text(tr("교환하시겠습니까?", "Swap?")) },
            text = {
                Text(
                    buildString {
                        append(tr("돌을 바꿔 잡으면 이후 컴퓨터가 반대 색을 둡니다.", "Swapping colours makes the computer play the other side from here on."))
                        prompt.fifthCount?.let { append(tr("\n5수 후보 수 N = $it", "\nCandidate 5th moves N = $it")) }
                    },
                )
            },
            confirmButton = { Button(onClick = { onSwap(true) }) { Text(tr("교환", "Swap")) } },
            dismissButton = { TextButton(onClick = { onSwap(false) }) { Text(tr("그대로", "Keep")) } },
        )

        GamePrompt.Swap2 -> AlertDialog(
            onDismissRequest = { onSwap2(Swap2Choice.STAY_WHITE) },
            title = { Text(tr("하나를 선택하세요", "Choose one")) },
            text = { Text(tr("스왑2: 백을 유지하거나, 돌을 바꾸거나, 2수를 더 놓습니다.", "Swap2: keep White, swap colours, or add two more moves.")) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSwap2(Swap2Choice.STAY_WHITE) }) { Text(tr("백 유지", "Keep White")) }
                    TextButton(onClick = { onSwap2(Swap2Choice.SWAP) }) { Text(tr("교환", "Swap")) }
                    Button(onClick = { onSwap2(Swap2Choice.ADD_TWO) }) { Text(tr("2수 추가", "Add two")) }
                }
            },
        )

        GamePrompt.FifthCount -> AlertDialog(
            onDismissRequest = { onFifthCount(1) },
            title = { Text(tr("5수 후보 개수 (1~8)", "Number of 5th-move candidates (1-8)")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("상대에게 제시할 5수의 개수 N을 고르세요.", "Choose N, how many 5th moves to offer your opponent."))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..8).forEach { n ->
                            AssistChip(onClick = { onFifthCount(n) }, label = { Text("$n") })
                        }
                    }
                }
            },
            confirmButton = {},
        )

        GamePrompt.SwapInfo -> InfoDialog(tr("교환", "Swap"), tr("상대가 돌을 바꿔 잡았습니다.", "Your opponent swapped colours."), onDismiss)
        GamePrompt.IllegalOpening -> InfoDialog(
            tr("표준 오프닝이 아닙니다", "Illegal Opening"),
            tr("첫 3수는 정중앙 근처(2수는 ±1, 3수는 ±2)여야 합니다. 판을 초기화했습니다.", "The first three moves must sit near the centre (move 2 within ±1, move 3 within ±2). The board has been cleared."),
            onDismiss,
        )
        is GamePrompt.Forbidden -> InfoDialog(
            tr("금수", "Forbidden"),
            tr("${prompt.cell.label(size)}는 흑의 금수입니다.", "${prompt.cell.label(size)} is forbidden for Black."),
            onDismiss,
        )
        GamePrompt.Timeout -> InfoDialog(tr("시간 초과", "Time out"), tr("제한 시간을 모두 사용했습니다.", "The time limit has run out."), onDismiss)
        is GamePrompt.Info -> InfoDialog(tr("대국", "Game"), prompt.text, onDismiss)
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onDismiss) { Text(tr("확인", "OK")) } },
    )
}

/**
 * The board at the user's zoom (settings_dev line 8). Above 100 % it is wider
 * than the screen and scrolls sideways; the page itself scrolls vertically, so
 * the two axes never nest.
 */
@Composable
private fun ZoomableBoard(
    render: BoardRender,
    scale: Float,
    onTap: (Move) -> Unit,
    onLongPress: (Move) -> Unit,
) {
    if (scale <= 1f) {
        GomokuBoard(
            render = render,
            modifier = Modifier.fillMaxWidth(scale),
            onTap = onTap,
            onLongPress = onLongPress,
        )
        return
    }
    BoxWithConstraints {
        val width = maxWidth * scale
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            GomokuBoard(
                render = render,
                modifier = Modifier.width(width),
                onTap = onTap,
                onLongPress = onLongPress,
            )
        }
    }
}

/**
 * Win rate per ply from Black's perspective — the desktop's win-rate graph
 * (settings_dev line 2). Gaps (plies never analysed) are simply not connected.
 */
@Composable
private fun WinRateGraph(history: List<Double?>, currentPly: Int, modifier: Modifier = Modifier) {
    val samples = history.count { it != null }
    if (samples < 1) return
    // The curve is Black's win rate, so it is drawn in the colour every table in
    // the app uses for Black.
    val line = RapfiTheme.colors.resultBlack
    val fill = line.copy(alpha = 0.22f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val midGrid = MaterialTheme.colorScheme.outline
    val marker = MaterialTheme.colorScheme.tertiary
    val plotBg = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(tr("승률 그래프 (흑 기준)", "Win rate (Black)"), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val latest = history.getOrNull(currentPly) ?: history.lastOrNull { it != null }
            Text(
                latest?.let { tr("%.0f%% · %d점", "%.0f%% · %d pts").format(it * 100, samples) } ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(MaterialTheme.shapes.small)
                .background(plotBg),
        ) {
            val padY = size.height * 0.06f
            val plotH = size.height - padY * 2
            // A single sample would divide by zero; centre it instead.
            val lastIdx = (history.size - 1).coerceAtLeast(1)
            val stepX = size.width / lastIdx
            fun px(i: Int) = if (history.size == 1) size.width / 2 else i * stepX
            fun py(v: Double) = padY + plotH * (1f - v.toFloat().coerceIn(0f, 1f))

            // 0 / 25 / 50 / 75 / 100% guides, the midline emphasised
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                val y = padY + plotH * (1f - frac)
                drawLine(
                    if (frac == 0.5f) midGrid else grid,
                    Offset(0f, y), Offset(size.width, y),
                    strokeWidth = if (frac == 0.5f) 1.6f else 1f,
                )
            }

            // filled area + line per contiguous run of analysed plies
            var segment = mutableListOf<Offset>()
            fun flush() {
                if (segment.size >= 2) {
                    val area = Path().apply {
                        moveTo(segment.first().x, padY + plotH)
                        segment.forEach { lineTo(it.x, it.y) }
                        lineTo(segment.last().x, padY + plotH)
                        close()
                    }
                    drawPath(area, fill)
                    val stroke = Path().apply {
                        moveTo(segment.first().x, segment.first().y)
                        segment.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(stroke, line, style = Stroke(width = 4f))
                }
                segment.forEach { drawCircle(line, radius = 4f, center = it) }
                segment = mutableListOf()
            }
            history.forEachIndexed { i, v ->
                if (v == null) flush() else segment.add(Offset(px(i), py(v)))
            }
            flush()

            // current ply marker
            history.getOrNull(currentPly)?.let { v ->
                val c = Offset(px(currentPly), py(v))
                drawCircle(marker, radius = 6.5f, center = c)
                drawCircle(line, radius = 6.5f, center = c, style = Stroke(width = 2f))
            }
        }
    }
}

/** The nine status fields the desktop shows (lng strings 0..9). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusBar(ui: BoardUiState, modifier: Modifier = Modifier) {
    val s = ui.stats
    val evalText = when {
        s.mate != null && s.mate > 0 -> "+M${s.mate}"
        s.mate != null -> "-M${-s.mate}"
        s.evalCp != null -> "${s.evalCp}"
        else -> "—"
    }
    val fields = buildList {
        add("DEPTH" to (if (s.selDepth > 0) "${s.depth}-${s.selDepth}" else "${s.depth}"))
        add("EVAL" to evalText)
        add("WINRATE" to (s.winRatePct?.let { "$it%" } ?: "—"))
        add("VAL" to (s.realtimeVal?.toString() ?: "—"))
        add("TIME" to (s.timeMs?.let { "${it}ms" } ?: "—"))
        add("NODE" to (s.nodes?.let { "%,d".format(it) } ?: "—"))
        add("SPEED" to (s.speed?.let { "%,d/s".format(it) } ?: "—"))
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fields.forEach { (key, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(key, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Tabular: these seven fields update several times a second,
                    // and with proportional digits the whole row shivered.
                    Text(value, style = MaterialTheme.typography.labelMedium.tabular())
                }
            }
        }
        val bestLine = ui.bestLineLabels(ui.render.size)
        if (bestLine.isNotEmpty()) {
            Text(
                "BESTLINE  $bestLine",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EvalHeader(ui: BoardUiState, modifier: Modifier = Modifier) {
    val eval = when {
        ui.blackMate != null -> if (ui.blackMate!! > 0) tr("흑 M${ui.blackMate}", "Black M${ui.blackMate}") else tr("백 M${-ui.blackMate!!}", "White M${-ui.blackMate!!}")
        ui.blackWinRate != null -> tr("흑 ${(ui.blackWinRate!! * 100).toInt()}%", "Black ${(ui.blackWinRate!! * 100).toInt()}%")
        else -> "—"
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$eval  ·  depth ${ui.depth}", style = MaterialTheme.typography.titleMedium.tabular())
        Text(
            buildString {
                append(tr("${ui.moveCount}수", "${ui.moveCount} moves"))
                // The redo tail the desktop keeps in `movepath` past the cursor.
                if (ui.futureCount > 0) append(" (+${ui.futureCount})")
                when {
                    ui.balancing -> append(tr(" · 균형점 탐색", " · balancing"))
                    ui.defending -> append(tr(" · 방어수 탐색", " · defences"))
                    ui.analyzing -> append(tr(" · 분석 중", " · analysing"))
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EvalBar(blackWinRate: Double?, mate: Int?, modifier: Modifier = Modifier) {
    // A summary, not a live counter: it may animate. 200 ms, which is long
    // enough to see which way the position moved and short enough that the next
    // depth has not already arrived.
    val target = (blackWinRate ?: 0.5).coerceIn(0.0, 1.0).toFloat()
    val frac by animateFloatAsState(target, tween(MOTION_VALUE), label = "evalBar")
    // The two stones' own colours: the bar says "how much of the board is
    // Black's", so it should be made of the same material as the stones.
    val skin = RapfiTheme.board
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(MaterialTheme.shapes.extraSmall),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            Box(Modifier.weight(frac.coerceAtLeast(0.001f)).fillMaxHeight().background(skin.blackLow))
            Box(Modifier.weight((1f - frac).coerceAtLeast(0.001f)).fillMaxHeight().background(skin.whiteHigh))
        }
    }
}

/**
 * The board's own control strip. Each button maps to a desktop command —
 * `undo/redo one|all`, `thinking start|stop`, `searchdefend`, `balance1|2`,
 * `clear`, `rotate`/`flip`, `move` and `getpos`/`putpos` — but not in the
 * desktop's order, because a mouse and a thumb do not reach the same way.
 *
 * The row that is always visible holds only what gets pressed while reading a
 * position: step through the line, and start or stop the search. The search
 * button sits in the middle, largest, and toggles — the desktop's own default
 * hotkey is `thinking toggle`, and a start/stop pair always has one dead half.
 * Everything else is a tap further, behind «⋯», where it costs nothing until
 * it is wanted.
 *
 * Rotate/flip and shift open a second row rather than acting immediately: they
 * are repeat-until-right operations, and a menu that closed after every tap
 * would make nudging a shape four points a chore.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardControls(
    ui: BoardUiState,
    modifier: Modifier = Modifier,
    moreOpen: Boolean,
    symmetryOpen: Boolean,
    shiftOpen: Boolean,
    onFirst: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onLast: () -> Unit,
    onToggleAnalyze: () -> Unit,
    onToggleMore: () -> Unit,
    onDefend: () -> Unit,
    onBalance: () -> Unit,
    onSaveImage: () -> Unit,
    onInfo: () -> Unit,
    onReset: () -> Unit,
    onToggleSymmetry: () -> Unit,
    onToggleShift: () -> Unit,
    onSymmetry: (BoardSymmetry) -> Unit,
    onShift: (BoardShift) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Filled.FirstPage, tr("처음으로", "To start"), ui.canUndo, onFirst)
            ToolButton(Icons.Filled.ChevronLeft, tr("한 수 뒤로", "Back one"), ui.canUndo, onUndo)
            AnalyzeButton(running = ui.busy, enabled = ui.busy || ui.canAnalyze, onClick = onToggleAnalyze)
            ToolButton(Icons.Filled.ChevronRight, tr("한 수 앞으로", "Forward one"), ui.canRedo, onRedo)
            ToolButton(Icons.AutoMirrored.Filled.LastPage, tr("마지막 수로", "To end"), ui.canRedo, onLast)
            ToolButton(
                if (moreOpen) Icons.Filled.ExpandLess else Icons.Filled.MoreHoriz,
                tr("도구 더보기", "More tools"), true, onToggleMore, moreOpen,
            )
        }
        AnimatedVisibility(moreOpen, enter = expandFadeIn, exit = shrinkFadeOut) {
            // Words, not icons: these are used rarely enough that nobody will
            // have learned their glyphs, and a wrong guess here clears the board.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolChip(
                    Icons.Filled.Shield, tr("모든 방어수", "All defences"),
                    enabled = ui.canAnalyze && !ui.busy, onClick = onDefend,
                )
                ToolChip(
                    Icons.Filled.Balance, tr("균형점 찾기", "Balance"),
                    enabled = ui.canAnalyze && !ui.busy, onClick = onBalance,
                )
                ToolChip(Icons.Filled.Info, tr("국면 문자열", "Position string"), enabled = true, onClick = onInfo)
                ToolChip(Icons.Filled.Image, tr("이미지 저장", "Save image"), enabled = true, onClick = onSaveImage)
                ToolChip(
                    Icons.Filled.Flip, tr("모양 대칭", "Rotate / flip"), enabled = ui.canTransform,
                    onClick = onToggleSymmetry, active = symmetryOpen,
                )
                ToolChip(
                    Icons.Filled.OpenWith, tr("수 이동", "Shift"), enabled = ui.canTransform,
                    onClick = onToggleShift, active = shiftOpen,
                )
                ToolChip(Icons.Filled.Refresh, tr("판 초기화", "Clear board"), enabled = true, onClick = onReset)
            }
        }
        AnimatedVisibility(symmetryOpen, enter = expandFadeIn, exit = shrinkFadeOut) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 90/180/270 = `rotate`; the four mirrors = `flip` (main.c:10194).
                SymmetryChip("90°", BoardSymmetry.ROTATE_90, onSymmetry)
                SymmetryChip("180°", BoardSymmetry.ROTATE_180, onSymmetry)
                SymmetryChip("270°", BoardSymmetry.ROTATE_270, onSymmetry)
                SymmetryChip(tr("좌우", "Mirror ↔"), BoardSymmetry.MIRROR_LEFT_RIGHT, onSymmetry)
                SymmetryChip(tr("상하", "Mirror ↕"), BoardSymmetry.MIRROR_UP_DOWN, onSymmetry)
                SymmetryChip(tr("대각 ＼", "Diagonal ＼"), BoardSymmetry.MIRROR_DIAGONAL, onSymmetry)
                SymmetryChip(tr("대각 ／", "Diagonal ／"), BoardSymmetry.MIRROR_ANTI_DIAGONAL, onSymmetry)
            }
        }
        AnimatedVisibility(shiftOpen, enter = expandFadeIn, exit = shrinkFadeOut) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(Icons.Filled.ArrowUpward, tr("위로", "Up"), true, { onShift(BoardShift.UP) })
                ToolButton(Icons.Filled.ArrowDownward, tr("아래로", "Down"), true, { onShift(BoardShift.DOWN) })
                ToolButton(
                    Icons.AutoMirrored.Filled.ArrowBack, tr("왼쪽으로", "Left"), true,
                    { onShift(BoardShift.LEFT) },
                )
                ToolButton(
                    Icons.AutoMirrored.Filled.ArrowForward, tr("오른쪽으로", "Right"), true,
                    { onShift(BoardShift.RIGHT) },
                )
                Text(
                    tr("모든 수가 한 칸씩 이동합니다", "Every stone moves one point"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    // 48dp, the accessibility minimum. These are the most-pressed buttons in the
    // app and they were four short of it.
    if (active) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = label)
        }
    } else {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = label)
        }
    }
}

/**
 * Start or stop the search, in one button. It is bigger than its neighbours and
 * sits in the middle of them because it is pressed more often than all of them
 * together, and it turns red while something runs so that "is it still
 * thinking?" is answered by the same pixel that answers it.
 */
@Composable
private fun AnalyzeButton(running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(54.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (running) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (running) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(
            if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (running) tr("정지", "Stop") else tr("분석 시작", "Analyze"),
            modifier = Modifier.size(28.dp),
        )
    }
}

/** A labelled action in the overflow row; [active] marks one that opened a row. */
@Composable
private fun ToolChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val leading: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    if (active) {
        FilterChip(
            selected = true,
            onClick = onClick,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = leading,
            colors = FilterChipDefaults.filterChipColors(),
        )
    } else {
        AssistChip(
            onClick = onClick,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = leading,
            colors = AssistChipDefaults.assistChipColors(),
        )
    }
}

@Composable
private fun SymmetryChip(label: String, symmetry: BoardSymmetry, onPick: (BoardSymmetry) -> Unit) {
    AssistChip(onClick = { onPick(symmetry) }, label = { Text(label) })
}

/** Multi-PV stepper (settings.txt line 20), above the PV list it controls. */
@Composable
private fun PvHeader(multiPv: Int, onMultiPv: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("후보 수 (멀티 PV)", "Candidate moves (multi-PV)"), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMultiPv(multiPv - 1) }) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text("$multiPv", style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onMultiPv(multiPv + 1) }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/**
 * Balance search options. `balance1` looks for the single move that levels the
 * position, `balance2` for the pair; the number is the desktop's optional bias
 * argument in engine value units (`balance1 100`), 0 meaning dead even.
 */
@Composable
private fun BalanceDialog(onDismiss: () -> Unit, onConfirm: (Boolean, Int) -> Unit) {
    var bias by remember { mutableStateOf("0") }
    val value = bias.trim().toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("균형점 찾기", "Balance")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("엔진이 국면을 균형(승률 50 %)에 가장 가깝게 만드는 수를 찾습니다. ", "The engine looks for the move that brings the position closest to even (50 %).") +
                        tr("찾은 수는 바로 판에 놓입니다.", "What it finds is played straight onto the board."),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = bias,
                    onValueChange = { bias = it },
                    singleLine = true,
                    label = { Text(tr("치우침 (0 = 완전 균형)", "Bias (0 = dead even)")) },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(false, value) }) { Text(tr("한 수", "One move")) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text(tr("취소", "Cancel")) }
                Button(onClick = { onConfirm(true, value) }) { Text(tr("두 수", "Two moves")) }
            }
        },
    )
}

/**
 * The line as text, in the desktop's clipboard format (`getpos` / `putpos`,
 * main.c:10345-10405) — the practical way to carry a position between the phone
 * and the PC.
 */
@Composable
private fun PositionDialog(
    ui: BoardUiState,
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit,
    onCopied: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("국면 문자열", "Position string")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    ui.positionString.ifEmpty { tr("(빈 판)", "(empty board)") },
                    style = MaterialTheme.typography.bodyMedium
                        .copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    tr("${ui.moveCount}수", "${ui.moveCount} moves") + if (ui.futureCount > 0) tr(" · 되돌린 ${ui.futureCount}수는 제외", " · ${ui.futureCount} undone moves excluded") else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text(tr("불러올 문자열 (예: h8i9g7)", "String to load (e.g. h8i9g7)")) },
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(ui.positionString))
                        onCopied()
                    },
                    enabled = ui.positionString.isNotEmpty(),
                ) { Text(tr("복사", "Copy")) }
                TextButton(onClick = { draft = clipboard.getText()?.text.orEmpty() }) {
                    Text(tr("붙여넣기", "Paste"))
                }
                Button(onClick = { onLoad(draft) }, enabled = draft.isNotBlank()) { Text(tr("불러오기", "Load")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("닫기", "Close")) } },
    )
}

/** `board_12수_20260727-1530.png`, minus the non-ASCII. */
private fun imageFileName(moveCount: Int): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
        .format(java.util.Date())
    return "yixin_board_${moveCount}_$stamp.png"
}

@Composable
private fun PvList(
    pvs: List<PvSnapshot>,
    size: Int,
    previewPv: Int?,
    onPreview: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pvs.isEmpty()) return
    // A plain Column, not LazyColumn: multi-PV is capped at 8 and the parent is
    // already vertically scrollable (nesting same-axis scrolls would crash).
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pvs.forEach { pv ->
            val selected = pv.index == previewPv
            Surface(
                onClick = { onPreview(if (selected) null else pv.index) },
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            pvLabel(pv),
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                        )
                        Text("d${pv.depth}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.tabular())
                    }
                    Text(
                        pv.line.take(10).joinToString(" ") { it.label(size) },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

/**
 * The database side of the board: what yixindb stores for this position — the
 * derived value, the record, the comment — plus the desktop's per-position
 * actions (`dbval`, `dbtext`, best-move mark, `dbdel one`, `dbsave`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatabaseSection(
    ui: BoardUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onQueryValue: () -> Unit,
    onQueryComment: () -> Unit,
    onEditComment: () -> Unit,
    onSetBest: () -> Unit,
    onClearBest: () -> Unit,
    onDeleteOne: () -> Unit,
    onSave: () -> Unit,
) {
    val db = ui.db
    if (!db.enabled) {
        Text(
            tr("데이터베이스 사용이 꺼져 있습니다 (설정 32행)", "The database is turned off (settings line 32)"),
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val value = ui.dbValue
    // The stored value is the whole reason to look at this panel, so it is the
    // header line rather than the first thing inside it.
    val summary = when {
        value?.stmMate != null && value.stmMate > 0 -> tr("두는 쪽 승 (M${value.stmMate})", "Side to move wins (M${value.stmMate})")
        value?.stmMate != null -> tr("두는 쪽 패 (M${-value.stmMate})", "Side to move loses (M${-value.stmMate})")
        value != null -> tr("흑 ${(value.blackWinRate * 100).toInt()}%", "Black ${(value.blackWinRate * 100).toInt()}%")
        else -> tr("저장된 값 없음", "No stored value")
    } + if (db.readOnly) tr(" · 읽기 전용", " · read-only") else ""
    Section(tr("데이터베이스", "Database"), summary, expanded, onToggle, modifier) {
        Text(
            tr("이 국면에 ${db.snapshot.cells.size}칸이 표시됩니다", "${db.snapshot.cells.size} points carry a value here"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        db.snapshot.entry?.let { entry ->
            Text(
                entry.summary(),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (db.snapshot.comment.isNotBlank()) {
            // settings.txt line 46 "Database Comment Font" — size only.
            val base = MaterialTheme.typography.bodySmall
            Text(
                db.snapshot.comment,
                style = base.copy(fontSize = base.fontSize * ui.dbCommentFont.scale),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AssistChip(onClick = onQueryValue, label = { Text(tr("값 조회", "Read value")) })
            AssistChip(onClick = onQueryComment, label = { Text(tr("주석 읽기", "Read comment")) })
            AssistChip(
                onClick = onEditComment,
                enabled = ui.canEditDb,
                label = { Text(tr("주석 편집", "Edit comment")) },
            )
            AssistChip(
                onClick = onSetBest,
                enabled = ui.canEditDb && ui.moveCount > 0,
                label = { Text(tr("최선수 표시", "Mark best move")) },
            )
            AssistChip(
                onClick = onClearBest,
                enabled = ui.canEditDb && ui.moveCount > 0,
                label = { Text(tr("표시 해제", "Clear mark")) },
            )
            AssistChip(
                onClick = onDeleteOne,
                enabled = ui.canEditDb,
                label = { Text(tr("이 국면 삭제", "Delete this position")) },
            )
            AssistChip(onClick = onSave, enabled = ui.canEditDb, label = { Text(tr("DB 저장", "Save DB")) })
        }
        Text(
            tr("빈 점을 길게 누르면 보드 텍스트를 입력합니다 (PC의 Ctrl+클릭).", "Long-press an empty point to write board text (Ctrl+click on the PC)."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (db.log.isNotEmpty()) {
            Text(
                db.log.takeLast(3).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Board text on one point — the desktop's dialog (main.c `show_dialog_boardtext`):
 * max six characters, and an empty value deletes the label.
 */
@Composable
private fun BoardTextDialog(
    cell: Move,
    size: Int,
    initial: String,
    editable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(cell) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("보드 텍스트  ${cell.label(size)}", "Board text  ${cell.label(size)}")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 6) text = it },
                    singleLine = true,
                    enabled = editable,
                    label = { Text(tr("최대 6자", "6 characters max")) },
                )
                Text(
                    if (editable) tr("비우고 확인하면 라벨이 삭제됩니다.", "Confirming an empty value deletes the label.")
                    else tr("읽기 전용 상태에서는 편집할 수 없습니다.", "Nothing can be edited while the database is read-only."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val kinds = mapOf(
                    "W5" to DbCellKind.WIN, "L7" to DbCellKind.LOSS,
                    "D" to DbCellKind.DRAW, "63%" to DbCellKind.RATE,
                )
                FlowRowChips(kinds.keys.toList(), enabled = editable) { text = it }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = editable) { Text(tr("확인", "OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("취소", "Cancel")) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(values: List<String>, enabled: Boolean, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value ->
            AssistChip(onClick = { onPick(value) }, enabled = enabled, label = { Text(value) })
        }
    }
}

/** Position comment (`yxedittextdatabase`), multi-line like the desktop's panel. */
@Composable
private fun CommentDialog(
    initial: String,
    editable: Boolean,
    editFont: FontSpec,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("국면 주석", "Position comment")) },
        text = {
            // settings.txt line 47, the desktop's second "Database Comment Font":
            // the one the editor uses.
            val base = MaterialTheme.typography.bodyMedium
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = editable,
                minLines = 3,
                maxLines = 8,
                textStyle = base.copy(fontSize = base.fontSize * editFont.scale),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = editable) { Text(tr("저장", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("취소", "Cancel")) } },
    )
}

private fun pvLabel(pv: PvSnapshot): String = when {
    pv.mate != null && pv.mate > 0 -> "#${pv.index + 1}  +M${pv.mate}"
    pv.mate != null -> "#${pv.index + 1}  -M${-pv.mate}"
    pv.winRate != null -> "#${pv.index + 1}  ${(pv.winRate * 100).toInt()}%"
    else -> "#${pv.index + 1}"
}

/** `rgba(0.85, 0.45, 0.15, 0.90)` — the desktop's prove badge (main.c:6892). */
private val ProveBadgeColor = Color(0xE6D97326)

/** `rgba(0.29, 0.62, 1.0, 0.90)` — the desktop's review badge (main.c:6869). */
private val ReviewBadgeColor = Color(0xE64A9EFF)

/**
 * The counters the desktop paints in the top-left corner of its drawing area
 * while a review or a proof runs (main.c:6860-6905): one line of totals, a second
 * of live search state, white on a filled box. It sits on the board here for the
 * same reason it does there — that is where the eye already is — and carries the
 * stop button, which on a phone would otherwise be a tab away.
 */
@Composable
private fun ResearchBadge(
    banner: dev.gomoku.rapfidroid.feature.board.ResearchBanner,
    modifier: Modifier = Modifier,
    onStop: () -> Unit,
) {
    Surface(
        modifier = modifier.widthIn(max = 300.dp),
        color = if (banner.isProve) ProveBadgeColor else ReviewBadgeColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    banner.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(
                    onClick = onStop,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text(tr("중지", "Stop"), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (banner.detail.isNotEmpty()) {
                Text(
                    banner.detail,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
            banner.progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 6.dp),
                )
            }
        }
    }
}
