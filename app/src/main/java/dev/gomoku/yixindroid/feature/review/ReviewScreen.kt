package dev.gomoku.yixindroid.feature.review

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.model.GameReport
import dev.gomoku.yixindroid.core.model.GradedMove
import dev.gomoku.yixindroid.core.model.GradingPreset
import dev.gomoku.yixindroid.core.model.MoveGrader
import dev.gomoku.yixindroid.core.model.MoveQuality
import dev.gomoku.yixindroid.core.model.QueueEntry
import dev.gomoku.yixindroid.core.model.QueueStatus
import dev.gomoku.yixindroid.core.model.ReviewBudget

/**
 * Game review, the analysis queue and the report — the desktop's Analysis menu
 * (Game Review / Analysis Queue / Game Report) on one screen.
 */
@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val loadGame = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onLoadGame)
    }
    val saveGame = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let(viewModel::onSaveGame) }
    val addToQueue = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.onEnqueue(uris) }
    var exportFormat by remember { mutableStateOf(ExportFormat.HTML) }
    val exportReport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri -> uri?.let { viewModel.onExport(it, exportFormat) } }

    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.onNoticeShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                RunCard(
                    ui = ui,
                    onBudget = viewModel::onBudgetChange,
                    onStart = viewModel::onStartReview,
                    onCancel = viewModel::onCancel,
                    onSkipOpening = viewModel::onToggleSkipOpening,
                    onBadges = viewModel::onToggleBadges,
                )
            }
            item {
                FileCard(
                    onLoad = { loadGame.launch(arrayOf("*/*")) },
                    onSave = { saveGame.launch("game.sav") },
                    enabled = !ui.running,
                    lineLength = ui.lineLength,
                )
            }
            ui.report?.let { report ->
                item {
                    ReportHeader(
                        report = report,
                        preset = ui.preset,
                        onPreset = viewModel::onPreset,
                        onExport = { format ->
                            exportFormat = format
                            exportReport.launch("${report.title}.${format.extension}")
                        },
                        onExportAll = viewModel::onExportAll,
                    )
                }
                item { MoveTableHeader() }
                items(report.moves, key = { it.index }) { move ->
                    MoveRow(move, report.size) { viewModel.onJumpTo(move.index) }
                }
            }
            item {
                QueueCard(
                    ui = ui,
                    onAdd = { addToQueue.launch(arrayOf("*/*")) },
                    onAddCurrent = viewModel::onEnqueueCurrent,
                    onStart = viewModel::onStartQueue,
                    onClear = viewModel::onClearQueue,
                    onRemove = viewModel::onRemoveQueued,
                )
            }
            if (ui.log.isNotEmpty()) {
                item { LogCard(ui.log) }
            }
        }
    }
}

// ---- run ------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunCard(
    ui: ReviewUiState,
    onBudget: (ReviewBudget) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onSkipOpening: () -> Unit,
    onBadges: () -> Unit,
) {
    Card("게임 리뷰") {
        Text(
            "판의 모든 국면을 같은 예산으로 분석해 수마다 등급을 매깁니다. " +
                "리뷰 중에는 엔진이 계속 점유됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val budget = ui.budget
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = !budget.byDepth,
                onClick = { onBudget(budget.copy(byDepth = false)) },
                label = { Text("초/수") },
                enabled = !ui.running,
            )
            FilterChip(
                selected = budget.byDepth,
                onClick = { onBudget(budget.copy(byDepth = true)) },
                label = { Text("고정 깊이") },
                enabled = !ui.running,
            )
        }
        if (budget.byDepth) {
            Stepper("깊이", budget.depth, 4, 64, !ui.running) { onBudget(budget.copy(depth = it)) }
        } else {
            Stepper("초", budget.seconds, 1, 120, !ui.running) { onBudget(budget.copy(seconds = it)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = ui.skipOpening,
                onClick = onSkipOpening,
                label = { Text("오프닝 1-5수 제외") },
                enabled = !ui.running,
            )
            FilterChip(
                selected = ui.showBadges,
                onClick = onBadges,
                label = { Text("보드에 등급 배지") },
            )
        }
        if (ui.running) {
            val progress = ui.progress
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                buildString {
                    progress.queue?.let { append("큐 ${it.index}/${it.total} · ${it.name} · ") }
                    append("국면 ${progress.index}/${progress.total + 1} · ${progress.budget.label}")
                },
                style = MaterialTheme.typography.labelMedium,
            )
            OutlinedButton(onClick = onCancel) { Text("중지") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = ui.canReview) {
                    Text("리뷰 시작 (${ui.lineLength}수)")
                }
            }
            if (!ui.connected) {
                Text(
                    "엔진에 연결한 뒤 실행하세요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FileCard(onLoad: () -> Unit, onSave: () -> Unit, enabled: Boolean, lineLength: Int) {
    Card("기보 파일") {
        Text(
            "PC와 같은 형식입니다 — 불러오기 .psq/.sav/.pos, 저장은 .sav(파일 이름을 .psq로 하면 .psq).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLoad, enabled = enabled) { Text("불러오기") }
            OutlinedButton(onClick = onSave, enabled = enabled && lineLength > 0) { Text("저장") }
        }
    }
}

// ---- report ---------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportHeader(
    report: GameReport,
    preset: GradingPreset,
    onPreset: (GradingPreset) -> Unit,
    onExport: (ExportFormat) -> Unit,
    onExportAll: () -> Unit,
) {
    Card("리포트 — ${report.title}") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Accuracy("흑", report.tally.blackAccuracy)
            Accuracy("백", report.tally.whiteAccuracy)
            Column {
                Text("수", style = MaterialTheme.typography.labelSmall)
                Text("${report.moveCount}", style = MaterialTheme.typography.titleMedium)
            }
            Column {
                Text("예산", style = MaterialTheme.typography.labelSmall)
                Text(report.budget.label, style = MaterialTheme.typography.titleMedium)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MoveQuality.GRADED.forEach { quality ->
                val (black, white) = report.tally.counts[quality] ?: (0 to 0)
                if (black + white == 0) return@forEach
                Surface(
                    color = Color(parseHex(quality.colorHex)).copy(alpha = 0.22f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "${quality.symbol} ${quality.korean} $black·$white",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        if (report.worst.isNotEmpty()) {
            Text("최악의 수", style = MaterialTheme.typography.titleSmall)
            report.worst.forEach { move ->
                Text(
                    "#${move.index} ${MoveGrader.coord(move.move, report.size)} " +
                        "(${if (move.black) "흑" else "백"}) — ${move.quality.korean}, " +
                        "-${"%.1f".format(move.delta * 100)}%p, 최선은 " +
                        MoveGrader.coord(move.best, report.size),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GradingPreset.entries.forEach { option ->
                FilterChip(
                    selected = preset == option,
                    onClick = { onPreset(option) },
                    label = { Text(option.label) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExportFormat.entries.forEach { format ->
                AssistChip(onClick = { onExport(format) }, label = { Text(format.label) })
            }
            AssistChip(onClick = onExportAll, label = { Text("앱 폴더에 전부") })
        }
    }
}

@Composable
private fun Accuracy(side: String, value: Double?) {
    Column {
        Text("$side 정확도", style = MaterialTheme.typography.labelSmall)
        Text(
            if (value == null) "-" else "%.1f%%".format(value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MoveTableHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HeaderCell("#", 28.dp)
        HeaderCell("수", 44.dp)
        HeaderCell("등급", 92.dp)
        HeaderCell("dWR", 60.dp)
        HeaderCell("승률", 56.dp)
        HeaderCell("최선", 44.dp)
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MoveRow(move: GradedMove, size: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${move.index}",
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                MoveGrader.coord(move.move, size),
                modifier = Modifier.width(44.dp),
                style = MaterialTheme.typography.bodyMedium
                    .copy(fontFamily = FontFamily.Monospace),
                fontWeight = if (move.black) FontWeight.Bold else FontWeight.Normal,
            )
            Row(
                modifier = Modifier.width(92.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (move.quality != MoveQuality.NONE) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(Color(parseHex(move.quality.colorHex)), CircleShape),
                    )
                    Text(move.quality.korean, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                if (move.quality == MoveQuality.NONE || move.quality == MoveQuality.FORCED) "-"
                else "-${"%.1f".format(move.delta * 100)}%p",
                modifier = Modifier.width(60.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
            )
            Text(
                MoveGrader.winRateCell(move),
                modifier = Modifier.width(56.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
            )
            Text(
                MoveGrader.coord(move.best, size),
                modifier = Modifier.width(44.dp),
                style = MaterialTheme.typography.bodySmall
                    .copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (move.comment.isNotEmpty()) {
            Text(
                move.comment,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

// ---- queue ----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueCard(
    ui: ReviewUiState,
    onAdd: () -> Unit,
    onAddCurrent: () -> Unit,
    onStart: () -> Unit,
    onClear: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Card("분석 큐 (${ui.queue.size})") {
        Text(
            "여러 기보를 차례로 리뷰합니다. 각 대국의 리포트는 앱 폴더에 남길 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = onAdd, label = { Text("기보 추가") })
            AssistChip(onClick = onAddCurrent, label = { Text("현재 대국 추가") })
            AssistChip(onClick = onClear, label = { Text("비우기") })
        }
        ui.queue.forEach { entry -> QueueRow(entry, ui.running) { onRemove(entry.uri) } }
        Button(onClick = onStart, enabled = ui.canQueue) { Text("큐 실행") }
    }
}

@Composable
private fun QueueRow(entry: QueueEntry, running: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (entry.status) {
                QueueStatus.PENDING -> "·"
                QueueStatus.RUNNING -> "▶"
                QueueStatus.DONE -> "✓"
                QueueStatus.FAILED -> "✕"
            },
            style = MaterialTheme.typography.labelMedium,
        )
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodySmall)
            if (entry.result.isNotEmpty()) {
                Text(
                    entry.result,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!running) {
            Text(
                "삭제",
                modifier = Modifier.clickable(onClick = onRemove),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LogCard(log: List<String>) {
    Card("기록") {
        log.takeLast(8).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---- small pieces ---------------------------------------------------------

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { onChange(value - 1) }, enabled = enabled && value > min) {
            Text("−")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange(value + 1) }, enabled = enabled && value < max) {
            Text("+")
        }
    }
}

/** `#rrggbb` from the desktop palette to an ARGB int. */
private fun parseHex(hex: String): Long =
    0xFF000000L or (hex.removePrefix("#").toLongOrNull(16) ?: 0x808080L)
