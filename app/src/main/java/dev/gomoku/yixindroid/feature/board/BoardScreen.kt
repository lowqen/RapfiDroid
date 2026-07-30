package dev.gomoku.yixindroid.feature.board

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.component.GomokuBoard
import dev.gomoku.yixindroid.core.designsystem.component.renderBoardPng
import dev.gomoku.yixindroid.core.designsystem.theme.WinBlue
import dev.gomoku.yixindroid.core.model.BoardShift
import dev.gomoku.yixindroid.core.model.BoardSymmetry
import dev.gomoku.yixindroid.core.model.ClockSide
import dev.gomoku.yixindroid.core.model.ComputerSide
import dev.gomoku.yixindroid.core.model.DbCellKind
import dev.gomoku.yixindroid.core.model.GamePrompt
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.OpeningProtocol
import dev.gomoku.yixindroid.core.model.PvSnapshot
import dev.gomoku.yixindroid.core.model.Swap2Choice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val StoneBlack = Color(0xFF1C1A17)
private val StoneWhite = Color(0xFFEDEAE3)

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

    val scope = rememberCoroutineScope()
    // The export launcher outlives recompositions, so it must not capture the
    // frame it was created with.
    val currentRender by rememberUpdatedState(ui.render)
    val saveImage = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.Default) { renderBoardPng(currentRender) }
                viewModel.onSaveImage(uri, bytes)
            }
        }
    }

    // The board is full-bleed; everything else keeps the page margin.
    val pad = Modifier.padding(horizontal = 12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EvalHeader(ui, pad)
        // settings_dev.txt line 1 — the desktop's own toggle.
        if (ui.showEvalBar) EvalBar(blackWinRate = ui.blackWinRate, mate = ui.blackMate, modifier = pad)
        // While a proof runs the desktop paints its two counters over the win-rate
        // graph (`prove_badge_lines`); here they sit above the board, where the
        // ghost stones of the searched line are.
        ui.proveBadge?.let { (first, second) ->
            Column(pad) {
                Text(first, style = MaterialTheme.typography.labelMedium)
                if (second.isNotEmpty()) {
                    Text(
                        second,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ZoomableBoard(
            ui = ui,
            onTap = viewModel::onTap,
            onLongPress = { cell ->
                // Empty points only: a board text belongs to a candidate move.
                if (ui.dbActive && !ui.render.stones.contains(cell)) labelTarget = cell
            },
        )
        BoardToolbar(
            ui = ui,
            modifier = pad,
            symmetryOpen = symmetryOpen,
            shiftOpen = shiftOpen,
            onFirst = viewModel::onFirst,
            onUndo = viewModel::onUndo,
            onRedo = viewModel::onRedo,
            onLast = viewModel::onLast,
            onStart = viewModel::onStartAnalyze,
            onStop = viewModel::onStopAnalyze,
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
        // Directly under the toolbar: most notices answer a button that was just
        // pressed, and the page can be scrolled well past its bottom.
        ui.notice?.let { text ->
            Snackbar(pad) { Text(text) }
            LaunchedEffect(text) {
                kotlinx.coroutines.delay(3_000)
                viewModel.onNoticeShown()
            }
        }
        GamePanel(
            ui = ui,
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
        StatusBar(ui, pad)
        // settings_dev.txt line 2.
        if (ui.showWrGraph) WinRateGraph(ui.winRateHistory, ui.moveCount, pad)
        PvHeader(multiPv = ui.multiPv, onMultiPv = viewModel::onMultiPvChange, modifier = pad)
        PvList(
            pvs = ui.snapshot?.pvs.orEmpty(),
            size = ui.render.size,
            previewPv = ui.previewPv,
            onPreview = viewModel::onPreviewPv,
            modifier = pad,
        )
        DatabasePanel(
            ui = ui,
            modifier = pad,
            onQueryValue = viewModel::onQueryDbValue,
            onQueryComment = viewModel::onQueryDbComment,
            onEditComment = { commentDraft = ui.db.snapshot.comment },
            onSetBest = viewModel::onDbSetBestMove,
            onClearBest = viewModel::onDbClearBestMove,
            onDeleteOne = viewModel::onDbDeleteOne,
            onSave = viewModel::onDbSave,
        )
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
            onCopied = { viewModel.onNotice("국면 문자열을 복사했습니다") },
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
            title = { Text("판을 초기화할까요?") },
            text = { Text("${ui.moveCount}수를 모두 지웁니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    viewModel.onReset()
                }) { Text("초기화") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("취소") }
            },
        )
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text("기권할까요?") },
            text = { Text("엔진에 `yxresign`을 보내고 이 대국을 끝냅니다.") },
            confirmButton = {
                Button(onClick = {
                    confirmResign = false
                    viewModel.onResign()
                }) { Text("기권") }
            },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text("취소") } },
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
 * The game side of the board: who the engine plays, the two clocks, and the
 * actions the desktop keeps in its Game menu (new game, draw, resign) plus the
 * forbidden-point toggle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamePanel(
    ui: BoardUiState,
    modifier: Modifier = Modifier,
    onComputerSide: (ComputerSide) -> Unit,
    onEngineMove: () -> Unit,
    onNewGame: () -> Unit,
    onDraw: () -> Unit,
    onResign: () -> Unit,
    onToggleForbidden: () -> Unit,
) {
    val game = ui.game
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("대국", style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append("${ui.sideToMove.label} 차례")
                        if (game.opening != OpeningProtocol.NONE) append(" · ${game.opening.label}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            game.result?.let { result ->
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp),
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
                AssistChip(onClick = onNewGame, label = { Text("새 대국") })
                AssistChip(
                    onClick = onEngineMove,
                    enabled = ui.engineOnMove,
                    label = { Text("엔진 착수") },
                )
                AssistChip(
                    onClick = onDraw,
                    enabled = !game.over && ui.connection.isLive,
                    label = { Text("무승부 제안") },
                )
                AssistChip(
                    onClick = onResign,
                    enabled = !game.over && ui.moveCount > 0,
                    label = { Text("기권") },
                )
                if (ui.isRenju) {
                    FilterChip(
                        selected = ui.showForbidden,
                        onClick = onToggleForbidden,
                        label = { Text("금수 표시") },
                    )
                }
            }

            if (ui.openingNeedsOddSize) {
                Text(
                    "오프닝 규칙은 판의 정중앙을 기준으로 하므로 홀수 크기 판이 필요합니다 (설정 1행).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (game.offeringFifth) {
                Text(
                    "5수 후보 ${game.fifthCount}개를 순서대로 놓으세요. 상대가 그중 하나를 고릅니다.",
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
}

/** Used / left per side, in the desktop's four-field layout. */
@Composable
private fun ClockRow(ui: BoardUiState) {
    val clock = ui.game.clock
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(ClockSide.COMPUTER to "컴퓨터", ClockSide.HUMAN to "사람").forEach { (side, name) ->
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium)
                    if (clock.running == side) {
                        Text("●", style = MaterialTheme.typography.labelMedium, color = WinBlue)
                    }
                }
                Text(
                    "남음 ${clock.label(side)}",
                    style = MaterialTheme.typography.labelSmall
                        .copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "사용 ${clock.usedLabel(side)}",
                    style = MaterialTheme.typography.labelSmall
                        .copy(fontFamily = FontFamily.Monospace),
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
            title = { Text("교환하시겠습니까?") },
            text = {
                Text(
                    buildString {
                        append("돌을 바꿔 잡으면 이후 컴퓨터가 반대 색을 둡니다.")
                        prompt.fifthCount?.let { append("\n5수 후보 수 N = $it") }
                    },
                )
            },
            confirmButton = { Button(onClick = { onSwap(true) }) { Text("교환") } },
            dismissButton = { TextButton(onClick = { onSwap(false) }) { Text("그대로") } },
        )

        GamePrompt.Swap2 -> AlertDialog(
            onDismissRequest = { onSwap2(Swap2Choice.STAY_WHITE) },
            title = { Text("하나를 선택하세요") },
            text = { Text("스왑2: 백을 유지하거나, 돌을 바꾸거나, 2수를 더 놓습니다.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSwap2(Swap2Choice.STAY_WHITE) }) { Text("백 유지") }
                    TextButton(onClick = { onSwap2(Swap2Choice.SWAP) }) { Text("교환") }
                    Button(onClick = { onSwap2(Swap2Choice.ADD_TWO) }) { Text("2수 추가") }
                }
            },
        )

        GamePrompt.FifthCount -> AlertDialog(
            onDismissRequest = { onFifthCount(1) },
            title = { Text("5수 후보 개수 (1~8)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("상대에게 제시할 5수의 개수 N을 고르세요.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..8).forEach { n ->
                            AssistChip(onClick = { onFifthCount(n) }, label = { Text("$n") })
                        }
                    }
                }
            },
            confirmButton = {},
        )

        GamePrompt.SwapInfo -> InfoDialog("교환", "상대가 돌을 바꿔 잡았습니다.", onDismiss)
        GamePrompt.IllegalOpening -> InfoDialog(
            "표준 오프닝이 아닙니다",
            "첫 3수는 정중앙 근처(2수는 ±1, 3수는 ±2)여야 합니다. 판을 초기화했습니다.",
            onDismiss,
        )
        is GamePrompt.Forbidden -> InfoDialog(
            "금수",
            "${prompt.cell.label(size)}는 흑의 금수입니다.",
            onDismiss,
        )
        GamePrompt.Timeout -> InfoDialog("시간 초과", "제한 시간을 모두 사용했습니다.", onDismiss)
        is GamePrompt.Info -> InfoDialog("대국", prompt.text, onDismiss)
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onDismiss) { Text("확인") } },
    )
}

/**
 * The board at the user's zoom (settings_dev line 8). Above 100 % it is wider
 * than the screen and scrolls sideways; the page itself scrolls vertically, so
 * the two axes never nest.
 */
@Composable
private fun ZoomableBoard(
    ui: BoardUiState,
    onTap: (Move) -> Unit,
    onLongPress: (Move) -> Unit,
) {
    val scale = ui.boardScale
    if (scale <= 1f) {
        GomokuBoard(
            render = ui.render,
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
                render = ui.render,
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
    val line = WinBlue
    val fill = WinBlue.copy(alpha = 0.22f)
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val midGrid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val marker = MaterialTheme.colorScheme.tertiary
    val plotBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("승률 그래프 (흑 기준)", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val latest = history.getOrNull(currentPly) ?: history.lastOrNull { it != null }
            Text(
                latest?.let { "%.0f%% · %d점".format(it * 100, samples) } ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(6.dp))
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
                    Text(value, style = MaterialTheme.typography.labelMedium)
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
        ui.blackMate != null -> if (ui.blackMate!! > 0) "흑 M${ui.blackMate}" else "백 M${-ui.blackMate!!}"
        ui.blackWinRate != null -> "흑 ${(ui.blackWinRate!! * 100).toInt()}%"
        else -> "—"
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$eval  ·  depth ${ui.depth}", style = MaterialTheme.typography.titleMedium)
        Text(
            buildString {
                append("${ui.moveCount}수")
                // The redo tail the desktop keeps in `movepath` past the cursor.
                if (ui.futureCount > 0) append(" (+${ui.futureCount})")
                if (ui.balancing) append(" · 균형점 탐색") else if (ui.analyzing) append(" · 분석 중")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EvalBar(blackWinRate: Double?, mate: Int?, modifier: Modifier = Modifier) {
    val frac = (blackWinRate ?: 0.5).coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            Box(Modifier.weight(frac.coerceAtLeast(0.001f)).fillMaxHeight().background(StoneBlack))
            Box(Modifier.weight((1f - frac).coerceAtLeast(0.001f)).fillMaxHeight().background(StoneWhite))
        }
    }
}

/**
 * The board's own control strip, in the desktop toolbar's order: navigate the
 * line, run the engine, export, then the shape tools. Each button maps to a
 * desktop command — `undo/redo one|all`, `thinking start|stop`, `balance1|2`,
 * `clear`, `rotate`/`flip`, `move` and `getpos`/`putpos`.
 *
 * Rotate/flip and shift open a second row rather than acting immediately: they
 * are repeat-until-right operations, and a menu that closed after every tap
 * would make nudging a shape four points a chore.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardToolbar(
    ui: BoardUiState,
    modifier: Modifier = Modifier,
    symmetryOpen: Boolean,
    shiftOpen: Boolean,
    onFirst: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onLast: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBalance: () -> Unit,
    onSaveImage: () -> Unit,
    onInfo: () -> Unit,
    onReset: () -> Unit,
    onToggleSymmetry: () -> Unit,
    onToggleShift: () -> Unit,
    onSymmetry: (BoardSymmetry) -> Unit,
    onShift: (BoardShift) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolButton(Icons.Filled.FirstPage, "처음으로", ui.canUndo, onFirst)
            ToolButton(Icons.Filled.ChevronLeft, "한 수 뒤로", ui.canUndo, onUndo)
            ToolButton(Icons.Filled.ChevronRight, "한 수 앞으로", ui.canRedo, onRedo)
            ToolButton(Icons.AutoMirrored.Filled.LastPage, "마지막 수로", ui.canRedo, onLast)
            ToolDivider()
            ToolButton(Icons.Filled.PlayArrow, "분석 시작", ui.canAnalyze && !ui.busy, onStart)
            ToolButton(Icons.Filled.Stop, "정지", ui.busy, onStop)
            ToolButton(Icons.Filled.Balance, "균형점 찾기", ui.canAnalyze && !ui.busy, onBalance)
            ToolDivider()
            ToolButton(Icons.Filled.Image, "이미지 저장", true, onSaveImage)
            ToolButton(Icons.Filled.Info, "국면 문자열", true, onInfo)
            ToolDivider()
            ToolButton(Icons.Filled.Refresh, "판 초기화", true, onReset)
            ToolButton(Icons.Filled.Flip, "모양 대칭", ui.canTransform, onToggleSymmetry, symmetryOpen)
            ToolButton(Icons.Filled.OpenWith, "수 이동", ui.canTransform, onToggleShift, shiftOpen)
        }
        if (symmetryOpen) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 90/180/270 = `rotate`; the four mirrors = `flip` (main.c:10194).
                SymmetryChip("90°", BoardSymmetry.ROTATE_90, onSymmetry)
                SymmetryChip("180°", BoardSymmetry.ROTATE_180, onSymmetry)
                SymmetryChip("270°", BoardSymmetry.ROTATE_270, onSymmetry)
                SymmetryChip("좌우", BoardSymmetry.MIRROR_LEFT_RIGHT, onSymmetry)
                SymmetryChip("상하", BoardSymmetry.MIRROR_UP_DOWN, onSymmetry)
                SymmetryChip("대각 ＼", BoardSymmetry.MIRROR_DIAGONAL, onSymmetry)
                SymmetryChip("대각 ／", BoardSymmetry.MIRROR_ANTI_DIAGONAL, onSymmetry)
            }
        }
        if (shiftOpen) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(Icons.Filled.ArrowUpward, "위로", true, { onShift(BoardShift.UP) })
                ToolButton(Icons.Filled.ArrowDownward, "아래로", true, { onShift(BoardShift.DOWN) })
                ToolButton(
                    Icons.AutoMirrored.Filled.ArrowBack, "왼쪽으로", true,
                    { onShift(BoardShift.LEFT) },
                )
                ToolButton(
                    Icons.AutoMirrored.Filled.ArrowForward, "오른쪽으로", true,
                    { onShift(BoardShift.RIGHT) },
                )
                Text(
                    "모든 수가 한 칸씩 이동합니다",
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
    if (active) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
            Icon(icon, contentDescription = label)
        }
    } else {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun ToolDivider() {
    VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 3.dp))
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
        Text("후보 수 (멀티 PV)", style = MaterialTheme.typography.labelMedium,
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
        title = { Text("균형점 찾기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "엔진이 국면을 균형(승률 50 %)에 가장 가깝게 만드는 수를 찾습니다. " +
                        "찾은 수는 바로 판에 놓입니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = bias,
                    onValueChange = { bias = it },
                    singleLine = true,
                    label = { Text("치우침 (0 = 완전 균형)") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(false, value) }) { Text("한 수") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text("취소") }
                Button(onClick = { onConfirm(true, value) }) { Text("두 수") }
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
        title = { Text("국면 문자열") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    ui.positionString.ifEmpty { "(빈 판)" },
                    style = MaterialTheme.typography.bodyMedium
                        .copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    "${ui.moveCount}수" + if (ui.futureCount > 0) " · 되돌린 ${ui.futureCount}수는 제외" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("불러올 문자열 (예: h8i9g7)") },
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
                ) { Text("복사") }
                TextButton(onClick = { draft = clipboard.getText()?.text.orEmpty() }) {
                    Text("붙여넣기")
                }
                Button(onClick = { onLoad(draft) }, enabled = draft.isNotBlank()) { Text("불러오기") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
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
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pvLabel(pv), color = WinBlue, style = MaterialTheme.typography.bodyMedium)
                        Text("d${pv.depth}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        pv.line.take(10).joinToString(" ") { it.label(size) },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
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
private fun DatabasePanel(
    ui: BoardUiState,
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
            "데이터베이스 사용이 꺼져 있습니다 (설정 32행)",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("데이터베이스", style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append("${db.snapshot.cells.size}칸")
                        if (db.readOnly) append(" · 읽기 전용")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val value = ui.dbValue
            Text(
                when {
                    value?.stmMate != null && value.stmMate > 0 -> "저장된 값: 두는 쪽 승 (M${value.stmMate})"
                    value?.stmMate != null -> "저장된 값: 두는 쪽 패 (M${-value.stmMate})"
                    value != null -> "저장된 값: 흑 ${(value.blackWinRate * 100).toInt()}%"
                    else -> "이 국면에는 저장된 값이 없습니다"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            db.snapshot.entry?.let { entry ->
                Text(
                    entry.summary(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (db.snapshot.comment.isNotBlank()) {
                Text(db.snapshot.comment, style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onQueryValue, label = { Text("값 조회") })
                AssistChip(onClick = onQueryComment, label = { Text("주석 읽기") })
                AssistChip(
                    onClick = onEditComment,
                    enabled = ui.canEditDb,
                    label = { Text("주석 편집") },
                )
                AssistChip(
                    onClick = onSetBest,
                    enabled = ui.canEditDb && ui.moveCount > 0,
                    label = { Text("최선수 표시") },
                )
                AssistChip(
                    onClick = onClearBest,
                    enabled = ui.canEditDb && ui.moveCount > 0,
                    label = { Text("표시 해제") },
                )
                AssistChip(
                    onClick = onDeleteOne,
                    enabled = ui.canEditDb,
                    label = { Text("이 국면 삭제") },
                )
                AssistChip(onClick = onSave, enabled = ui.canEditDb, label = { Text("DB 저장") })
            }
            Text(
                "빈 점을 길게 누르면 보드 텍스트를 입력합니다 (PC의 Ctrl+클릭).",
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
        title = { Text("보드 텍스트  ${cell.label(size)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 6) text = it },
                    singleLine = true,
                    enabled = editable,
                    label = { Text("최대 6자") },
                )
                Text(
                    if (editable) "비우고 확인하면 라벨이 삭제됩니다."
                    else "읽기 전용 상태에서는 편집할 수 없습니다.",
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
            Button(onClick = { onConfirm(text) }, enabled = editable) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
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
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("국면 주석") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = editable,
                minLines = 3,
                maxLines = 8,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = editable) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun pvLabel(pv: PvSnapshot): String = when {
    pv.mate != null && pv.mate > 0 -> "#${pv.index + 1}  +M${pv.mate}"
    pv.mate != null -> "#${pv.index + 1}  -M${-pv.mate}"
    pv.winRate != null -> "#${pv.index + 1}  ${(pv.winRate * 100).toInt()}%"
    else -> "#${pv.index + 1}"
}
