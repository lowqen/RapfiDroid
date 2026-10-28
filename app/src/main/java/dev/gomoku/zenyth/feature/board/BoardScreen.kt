package dev.gomoku.zenyth.feature.board

import dev.gomoku.zenyth.core.designsystem.theme.LocalProMode
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.zenyth.core.designsystem.component.BoardRender
import dev.gomoku.zenyth.core.designsystem.component.GomokuBoard
import dev.gomoku.zenyth.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.zenyth.core.designsystem.component.rememberRapfiHaptics
import dev.gomoku.zenyth.core.designsystem.component.renderBoardPng
import dev.gomoku.zenyth.core.designsystem.theme.RapfiTheme
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.GameFileFormat
import dev.gomoku.zenyth.core.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The board screen — two tabs, [분석] | [대국], over one board each
 * (Zenyth Web's calm board-first layout).
 *
 * **The board takes the room, and keeps it.** Upright, the board and its move
 * toolbar are pinned under a header holding 분석 | 대국, and the cards ride on a
 * sheet that rests below them on a one-line summary (UI overhaul P3,
 * [BoardPortrait]). On its side the board stands at full height on the left and
 * the cards scroll on the right ([BoardLandscape]).
 *
 *  - Analysis: engine card (best move first, frozen after a stop), evaluation
 *    graph, analysis settings (folded), position, tools. The database's
 *    per-position actions live in the DB tab (Zenyth Web's layout); the board
 *    keeps only the long press that writes a cell label.
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
            viewModel.onNotice(tr("이 국면의 링크를 복사했습니다", "Copied a link to this position", "已复制此局面的链接"))
        },
        positionString = { showPosition = true },
        saveImage = { saveImage.launch(imageFileName(ui.moveCount)) },
    )

    // Haptics at the thresholds (UI overhaul P3): the user's own stone landing,
    // a tap that did nothing, a game decided. The engine's moves are silent —
    // the hand did nothing. The view model is not asked: a tap is matched to
    // what the state does next (a stone on that point, or a notice) within a
    // second and a half, and forgotten after that.
    val haptics = rememberRapfiHaptics()
    var pendingTap by remember { mutableStateOf<PendingTap?>(null) }
    fun takeTap(): PendingTap? = pendingTap?.takeIf { SystemClock.uptimeMillis() - it.at < 1_500 }.also { pendingTap = null }
    LaunchedEffect(ui.moveCount, ui.render.lastMove) {
        val tap = pendingTap ?: return@LaunchedEffect
        if (ui.moveCount > tap.moves) {
            if (takeTap()?.cell == ui.render.lastMove) haptics.stonePlaced()
        }
    }
    var lastResult by remember { mutableStateOf(ui.game.result) }
    LaunchedEffect(ui.game.result) {
        if (ui.game.result != null && lastResult == null) haptics.decided()
        lastResult = ui.game.result
    }

    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            // A notice right after a tap is that tap refused (occupied, forbidden,
            // not your turn, the engine busy) — a buzz, then the words.
            if (takeTap() != null) haptics.refused()
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
                onTap = { cell ->
                    pendingTap = PendingTap(cell, ui.moveCount, SystemClock.uptimeMillis())
                    viewModel.onTap(cell)
                },
                onLongPress = { cell ->
                    if (ui.dbActive && !ui.render.stones.contains(cell)) {
                        haptics.longPress()
                        labelTarget = cell
                    }
                },
            )
            ui.research?.let {
                ResearchBadge(banner = it, modifier = Modifier.align(Alignment.TopStart).padding(6.dp), onStop = viewModel::onStopResearch)
            }
        }
    }
    val modeSwitch: @Composable () -> Unit = { BoardModeSwitch(ui.mode, viewModel::onMode) }
    val toolbar: @Composable () -> Unit = {
        MoveToolbar(
            ui = ui,
            onNewBoard = onNewBoard,
            onFirst = viewModel::onFirst,
            onUndo = viewModel::onUndo,
            onToggle = toggleAnalysis,
            onRedo = viewModel::onRedo,
            onLast = viewModel::onLast,
            onSwapSides = viewModel::onSwapSides,
        )
    }
    val pro = LocalProMode.current
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
            AnalysisCard(ui, viewModel::onPreviewPv)
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
        // Defences, balance, transforms, the user's scripts: expert tools,
        // shown in «고급 사용자 모드» only.
        if (ui.analysisTab && pro) {
            ToolsCard(
                ui = ui,
                onDefend = viewModel::onSearchDefend,
                onBalance = { showBalance = true },
                onSymmetry = viewModel::onSymmetry,
                onShift = viewModel::onShift,
                onToggleForbidden = viewModel::onToggleForbidden,
                onRunScript = viewModel::onRunScript,
            )
        }
    }

    // Upright: the board pinned, the cards on a sheet (BoardPortrait). On its
    // side: the board at full height, the cards beside it (BoardLandscape).
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Read here: the layouts below are scopes of their own and cannot reach
        // this one's constraints.
        val width = maxWidth
        val landscape = width > maxHeight && width >= 560.dp
        if (landscape) {
            BoardLandscape(ui, board, modeSwitch, toolbar, summary = { SummaryStrip(ui) }, cards = { cards(true) })
        } else {
            BoardPortrait(
                ui = ui,
                board = board,
                modeSwitch = modeSwitch,
                toolbar = toolbar,
                summary = { SummaryStrip(ui) },
                cards = { cards(width >= 600.dp) },
            )
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
            onCopied = { viewModel.onNotice(tr("국면 문자열을 복사했습니다", "Position string copied", "已复制局面代码")) },
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
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("새 판을 시작할까요?", "Start a new board?", "开始新棋盘吗?")) },
            text = { Text(tr("${ui.moveCount}수를 지웁니다", "Clears ${ui.moveCount} moves", "将清除 ${ui.moveCount} 手")) },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    viewModel.onNewBoard()
                }) { Text(tr("새 판", "New board", "新棋盘")) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("취소", "Cancel", "取消")) } },
        )
    }
    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text(tr("기권할까요?", "Resign?", "要认输吗?")) },
            confirmButton = {
                Button(onClick = {
                    confirmResign = false
                    viewModel.onResign()
                }) { Text(tr("기권", "Resign", "认输")) }
            },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text(tr("취소", "Cancel", "取消")) } },
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

/** A tap on [cell] at [at] (uptime ms), when the board had [moves] moves. */
private data class PendingTap(val cell: Move, val moves: Int, val at: Long)
