package dev.gomoku.rapfidroid.feature.board

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.rapfidroid.core.designsystem.component.BoardRender
import dev.gomoku.rapfidroid.core.designsystem.component.GomokuBoard
import dev.gomoku.rapfidroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiTopBar
import dev.gomoku.rapfidroid.core.designsystem.component.renderBoardPng
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.GameFileFormat
import dev.gomoku.rapfidroid.core.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The board screen — two tabs, [분석] | [대국], over one board each
 * (RapfiWeb's calm board-first layout).
 *
 * **The board takes the room.** Nothing sits above or below it but the eval bar
 * beside it. On a phone in portrait the order is board, move bar, tabs, cards;
 * anything wider puts the board on the left at full height and the cards on
 * the right, tabs on top and the move bar at the bottom.
 *
 *  - Analysis: engine card (best move first, frozen after a stop), evaluation
 *    graph, analysis settings (folded), position, tools and database (folded).
 *  - Game: the game card (status, «지금 두기», colours, time, level, rules),
 *    then the position with resign and draw. No analysis overlay on the board.
 */
@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    var labelTarget by remember { mutableStateOf<Move?>(null) }
    var commentDraft by remember { mutableStateOf<String?>(null) }
    var showBalance by remember { mutableStateOf(false) }
    var showPosition by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }

    // `prove_pulse_tick` (main.c:9206): the focus stone blinks twice a second
    // while a proof runs.
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
    val currentRender by rememberUpdatedState(ui.render)
    val skin = RapfiTheme.board
    val currentSkin by rememberUpdatedState(skin)
    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.Default) { renderBoardPng(currentRender, currentSkin) }
                viewModel.onSaveImage(uri, bytes)
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onOpenFile(uri)
    }
    var saveFormat by remember { mutableStateOf(GameFileFormat.PSQ) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.onSaveFile(uri, saveFormat)
    }
    val clipboard = LocalClipboardManager.current
    val files = GameFileActions(
        open = { openFile.launch(arrayOf("*/*")) },
        save = { format ->
            saveFormat = format
            saveFile.launch(viewModel.saveName(format))
        },
        copyLink = {
            clipboard.setText(AnnotatedString(viewModel.linkText()))
            viewModel.onNotice(tr("이 국면의 링크를 복사했습니다", "Copied a link to this position"))
        },
        positionString = { showPosition = true },
        saveImage = { saveImage.launch(imageFileName(ui.moveCount)) },
    )

    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.onNoticeShown()
        }
    }

    val onNewBoard: () -> Unit = {
        if (ui.showWarning && ui.moveCount > 0 && !ui.analysisTab) confirmReset = true else viewModel.onNewBoard()
    }
    val toggleAnalysis: () -> Unit = {
        if (ui.analyzing || ui.balancing) viewModel.onStopAnalyze() else viewModel.onStartAnalyze()
    }

    val board: @Composable (Modifier) -> Unit = { mod ->
        Box(mod) {
            ZoomableBoard(
                render = ui.render.copy(provePulse = provePulse),
                scale = ui.boardScale,
                onTap = viewModel::onTap,
                onLongPress = { cell ->
                    if (ui.dbActive && !ui.render.stones.contains(cell)) labelTarget = cell
                },
            )
            ui.research?.let {
                ResearchBadge(banner = it, modifier = Modifier.align(Alignment.TopStart).padding(6.dp), onStop = viewModel::onStopResearch)
            }
        }
    }
    val transport: @Composable () -> Unit = {
        MoveTransport(
            ui = ui,
            onFirst = viewModel::onFirst,
            onUndo = viewModel::onUndo,
            onToggle = toggleAnalysis,
            onRedo = viewModel::onRedo,
            onLast = viewModel::onLast,
        )
    }
    val stage: @Composable () -> Unit = {
        StageBar(ui, viewModel::onMode, onNewBoard, viewModel::onSwapSides)
    }
    val cards: @Composable ColumnScope.(Boolean) -> Unit = { wide ->
        if (ui.linkBackup) LinkBackupCard(viewModel::onRestoreLinkBackup, viewModel::onDropLinkBackup)
        // Wide: the position card leads. Narrow: one line instead, the card after
        // the tab's own card, so «지금 두기» is never pushed off the screen.
        if (wide) {
            PositionCard(ui, files, onResign = { confirmResign = true }, onDraw = viewModel::onOfferDraw)
        } else {
            PositionStrip(ui)
        }
        if (ui.analysisTab) {
            AnalysisCard(ui, viewModel::onStartAnalyze, viewModel::onStopAnalyze, viewModel::onPreviewPv)
            if (ui.showWrGraph) EvalGraphCard(ui.winRateHistory, ui.moveCount)
            AnalysisOptions(ui, viewModel::onAnalysisSec, viewModel::onMultiPvChange, viewModel::onPlayBestOnStop, viewModel::onNotice)
        } else {
            GameCard(
                ui = ui,
                onComputerSide = viewModel::onComputerSide,
                onMoveTime = viewModel::onMoveTime,
                onStrength = viewModel::onStrength,
                onRule = viewModel::onRule,
                onMoveNow = viewModel::onMoveNow,
                onEngineMove = viewModel::onEngineMove,
                onNote = viewModel::onNotice,
            )
        }
        if (!wide) PositionCard(ui, files, onResign = { confirmResign = true }, onDraw = viewModel::onOfferDraw)
        if (ui.analysisTab) {
            ToolsCard(
                ui = ui,
                onDefend = viewModel::onSearchDefend,
                onBalance = { showBalance = true },
                onSymmetry = viewModel::onSymmetry,
                onShift = viewModel::onShift,
                onToggleForbidden = viewModel::onToggleForbidden,
                onRunScript = viewModel::onRunScript,
            )
            if (ui.db.enabled) {
                DbCard(
                    ui = ui,
                    onEditComment = { commentDraft = ui.db.snapshot.comment },
                    onSetBest = viewModel::onDbSetBestMove,
                    onClearBest = viewModel::onDbClearBestMove,
                    onDeleteOne = viewModel::onDbDeleteOne,
                    onSave = viewModel::onDbSave,
                )
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        RapfiTopBar(title = tr("보드", "Board"))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
            if (landscape) {
                // Board on the left at full height, the cards on the right.
                val side = min(maxHeight - 16.dp, maxWidth * 0.62f)
                val showBar = ui.showEvalBar && ui.analysisTab
                Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BoardWithBar(ui, showBar, compact = false, side = side, board = board)
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stage()
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) { cards(true) }
                        transport()
                    }
                }
            } else {
                val showBar = ui.showEvalBar && ui.analysisTab
                val bar = if (showBar) evalBarWidth(compact = true) + 4.dp else 0.dp
                val side = maxWidth - bar
                val wideCards = maxWidth >= 600.dp
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    BoardWithBar(ui, showBar, compact = true, side = side, board = board)
                    transport()
                    Column(
                        Modifier.padding(horizontal = 12.dp).padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        stage()
                        cards(wideCards)
                    }
                }
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
            title = { Text(tr("새 판을 시작할까요?", "Start a new board?")) },
            text = { Text(tr("${ui.moveCount}수를 지웁니다", "Clears ${ui.moveCount} moves")) },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    viewModel.onNewBoard()
                }) { Text(tr("새 판", "New board")) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("취소", "Cancel")) } },
        )
    }
    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text(tr("기권할까요?", "Resign?")) },
            confirmButton = {
                Button(onClick = {
                    confirmResign = false
                    viewModel.onResign()
                }) { Text(tr("기권", "Resign")) }
            },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text(tr("취소", "Cancel")) } },
        )
    }
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

/** The eval bar beside a board of side [side]; the bar is as tall as the board. */
@Composable
private fun BoardWithBar(
    ui: BoardUiState,
    showBar: Boolean,
    compact: Boolean,
    side: Dp,
    board: @Composable (Modifier) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
        if (showBar) {
            VerticalEvalBar(
                ui.eval,
                Modifier.width(evalBarWidth(compact)).height(if (compact) side else side),
                compact = compact,
            )
        }
        board(Modifier.size(side))
    }
}

/**
 * The board at the user's zoom (settings_dev line 8). Above 100 % it is wider
 * than its box and scrolls sideways.
 */
@Composable
private fun ZoomableBoard(
    render: BoardRender,
    scale: Float,
    onTap: (Move) -> Unit,
    onLongPress: (Move) -> Unit,
) {
    if (scale <= 1f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GomokuBoard(render = render, modifier = Modifier.fillMaxWidth(scale), onTap = onTap, onLongPress = onLongPress)
        }
        return
    }
    BoxWithConstraints {
        val width = maxWidth * scale
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            GomokuBoard(render = render, modifier = Modifier.width(width), onTap = onTap, onLongPress = onLongPress)
        }
    }
}

/** `rapfi_board_12_20260727-1530.png`. */
private fun imageFileName(moveCount: Int): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
    return "rapfi_board_${moveCount}_$stamp.png"
}
