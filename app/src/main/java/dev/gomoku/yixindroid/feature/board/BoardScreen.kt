package dev.gomoku.yixindroid.feature.board

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.component.GomokuBoard
import dev.gomoku.yixindroid.core.designsystem.theme.WinBlue
import dev.gomoku.yixindroid.core.model.DbCellKind
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.PvSnapshot

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EvalHeader(ui)
        // settings_dev.txt lines 1 and 2 — the desktop's own two toggles.
        if (ui.showEvalBar) EvalBar(blackWinRate = ui.blackWinRate, mate = ui.blackMate)
        if (ui.showWrGraph) WinRateGraph(ui.winRateHistory, ui.moveCount)
        ZoomableBoard(
            ui = ui,
            onTap = viewModel::onTap,
            onLongPress = { cell ->
                // Empty points only: a board text belongs to a candidate move.
                if (ui.dbActive && !ui.render.stones.contains(cell)) labelTarget = cell
            },
        )
        StatusBar(ui)
        Controls(
            analyzing = ui.analyzing,
            canAnalyze = ui.canAnalyze,
            multiPv = ui.multiPv,
            onUndo = viewModel::onUndo,
            onReset = {
                // settings.txt line 17: warn before throwing the game away.
                if (ui.showWarning && ui.moveCount > 0) confirmReset = true else viewModel.onReset()
            },
            onToggle = viewModel::onToggleAnalyze,
            onMultiPv = viewModel::onMultiPvChange,
        )
        PvList(
            pvs = ui.snapshot?.pvs.orEmpty(),
            size = ui.render.size,
            previewPv = ui.previewPv,
            onPreview = viewModel::onPreviewPv,
        )
        DatabasePanel(
            ui = ui,
            onQueryValue = viewModel::onQueryDbValue,
            onQueryComment = viewModel::onQueryDbComment,
            onEditComment = { commentDraft = ui.db.snapshot.comment },
            onSetBest = viewModel::onDbSetBestMove,
            onClearBest = viewModel::onDbClearBestMove,
            onDeleteOne = viewModel::onDbDeleteOne,
            onSave = viewModel::onDbSave,
        )
        ui.notice?.let { text ->
            Snackbar { Text(text) }
            LaunchedEffect(text) {
                kotlinx.coroutines.delay(3_000)
                viewModel.onNoticeShown()
            }
        }
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
private fun WinRateGraph(history: List<Double?>, currentPly: Int) {
    val samples = history.count { it != null }
    if (samples < 1) return
    val line = WinBlue
    val fill = WinBlue.copy(alpha = 0.22f)
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val midGrid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val marker = MaterialTheme.colorScheme.tertiary
    val plotBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
private fun StatusBar(ui: BoardUiState) {
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
private fun EvalHeader(ui: BoardUiState) {
    val eval = when {
        ui.blackMate != null -> if (ui.blackMate!! > 0) "흑 M${ui.blackMate}" else "백 M${-ui.blackMate!!}"
        ui.blackWinRate != null -> "흑 ${(ui.blackWinRate!! * 100).toInt()}%"
        else -> "—"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$eval  ·  depth ${ui.depth}", style = MaterialTheme.typography.titleLarge)
        Text("${ui.moveCount}수", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun Controls(
    analyzing: Boolean,
    canAnalyze: Boolean,
    multiPv: Int,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onToggle: () -> Unit,
    onMultiPv: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedIconButton(onClick = onUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "무르기")
        }
        OutlinedIconButton(onClick = onReset) {
            Icon(Icons.Filled.Refresh, contentDescription = "초기화")
        }
        Button(onClick = onToggle, enabled = canAnalyze) {
            Icon(if (analyzing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
            Text(if (analyzing) "  정지" else "  분석")
        }
        Box(Modifier.weight(1f))
        Stepper(label = "PV", value = multiPv, onChange = onMultiPv)
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onChange(value - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$value", style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { onChange(value + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
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
