package dev.gomoku.rapfidroid.feature.review

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.gomoku.rapfidroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.rapfidroid.core.designsystem.component.Stepper
import dev.gomoku.rapfidroid.core.designsystem.theme.tabular
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.GameReport
import dev.gomoku.rapfidroid.core.model.GradedMove
import dev.gomoku.rapfidroid.core.model.GradingPreset
import dev.gomoku.rapfidroid.core.model.MoveGrader
import dev.gomoku.rapfidroid.core.model.MoveQuality
import dev.gomoku.rapfidroid.core.model.QueueEntry
import dev.gomoku.rapfidroid.core.model.QueueStatus
import dev.gomoku.rapfidroid.core.model.ReviewBudget
import dev.gomoku.rapfidroid.feature.prove.ProveCard

/**
 * Game review, position prove, the analysis queue and the report — the desktop's
 * Analysis menu (Game Review / Prove Position / Analysis Queue / Game Report) on
 * one screen.
 */
@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current

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

    // Two columns on anything wider than a phone. This screen is a stack of
    // self-contained cards, so on a tablet a single column left two thirds of
    // the screen empty and pushed the report below the fold. The move table is
    // the one thing that must not be split — it is a table — so it spans.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 360.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            item(span = { GridItemSpan(maxLineSpan) }) {
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
            item(span = { GridItemSpan(maxLineSpan) }) { MoveTableHeader() }
            items(
                report.moves,
                key = { it.index },
                span = { GridItemSpan(maxLineSpan) },
            ) { move ->
                MoveRow(move, report.size) { viewModel.onJumpTo(move.index) }
            }
        }
        item {
            // The desktop's Analysis menu holds Prove Position next to Game
            // Review, and both drive the same engine, so they share a screen.
            ProveCard(onNotice = { viewModel.onExternalNotice(it) })
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
    Card(tr("게임 리뷰", "Game Review")) {
        Text(
            tr("판의 모든 국면을 같은 예산으로 분석해 수마다 등급을 매깁니다. ", "Analyses every position of the game on the same budget and grades each move.") +
                tr("리뷰 중에는 엔진이 계속 점유됩니다.", "The engine is busy for the whole run."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val budget = ui.budget
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = !budget.byDepth,
                onClick = { onBudget(budget.copy(byDepth = false)) },
                label = { Text(tr("초/수", "s per move")) },
                enabled = !ui.running,
            )
            FilterChip(
                selected = budget.byDepth,
                onClick = { onBudget(budget.copy(byDepth = true)) },
                label = { Text(tr("고정 깊이", "Fixed depth")) },
                enabled = !ui.running,
            )
        }
        if (budget.byDepth) {
            Stepper(tr("깊이", "Depth"), budget.depth, 4, 64, !ui.running) { onBudget(budget.copy(depth = it)) }
        } else {
            Stepper(tr("초", "s"), budget.seconds, 1, 120, !ui.running) { onBudget(budget.copy(seconds = it)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = ui.skipOpening,
                onClick = onSkipOpening,
                label = { Text(tr("오프닝 1-5수 제외", "Skip opening moves 1-5")) },
                enabled = !ui.running,
            )
            FilterChip(
                selected = ui.showBadges,
                onClick = onBadges,
                label = { Text(tr("보드에 등급 배지", "Grade badges on the board")) },
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
                    progress.queue?.let { append(tr("큐 ${it.index}/${it.total} · ${it.name} · ", "Queue ${it.index}/${it.total} · ${it.name} ·")) }
                    append(tr("국면 ${progress.index}/${progress.total + 1} · ${progress.budget.label}", "Position ${progress.index}/${progress.total + 1} · ${progress.budget.label}"))
                },
                style = MaterialTheme.typography.labelMedium,
            )
            OutlinedButton(onClick = onCancel) { Text(tr("중지", "Stop")) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = ui.canReview) {
                    Text(tr("리뷰 시작 (${ui.lineLength}수)", "Review (${ui.lineLength} moves)"))
                }
            }
            if (!ui.connected) {
                Text(
                    tr("엔진에 연결한 뒤 실행하세요.", "Connect to the engine first."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FileCard(onLoad: () -> Unit, onSave: () -> Unit, enabled: Boolean, lineLength: Int) {
    Card(tr("기보 파일", "Game files")) {
        Text(
            tr("PC와 같은 형식입니다 — 불러오기 .psq/.sav/.pos, 저장은 .sav(파일 이름을 .psq로 하면 .psq).", "The same formats as the PC — .psq/.sav/.pos to load, .sav to save (name it .psq and it writes .psq)."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLoad, enabled = enabled) { Text(tr("불러오기", "Load")) }
            OutlinedButton(onClick = onSave, enabled = enabled && lineLength > 0) { Text(tr("저장", "Save")) }
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
    Card(tr("리포트 — ${report.title}", "Report — ${report.title}")) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Accuracy(tr("흑", "Black"), report.tally.blackAccuracy)
            Accuracy(tr("백", "White"), report.tally.whiteAccuracy)
            Column {
                Text(tr("수", "moves"), style = MaterialTheme.typography.labelSmall)
                Text("${report.moveCount}", style = MaterialTheme.typography.titleMedium.tabular())
            }
            Column {
                Text(tr("예산", "Budget"), style = MaterialTheme.typography.labelSmall)
                Text(report.budget.label, style = MaterialTheme.typography.titleMedium)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MoveQuality.GRADED.forEach { quality ->
                val (black, white) = report.tally.counts[quality] ?: (0 to 0)
                if (black + white == 0) return@forEach
                Surface(
                    // The grade's own colour, at the weight a container has —
                    // the grades come from the desktop and keep their hues.
                    color = Color(parseHex(quality.colorHex)).copy(alpha = 0.22f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        "${quality.symbol} ${quality.display} $black·$white",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        if (report.worst.isNotEmpty()) {
            Text(tr("최악의 수", "Worst moves"), style = MaterialTheme.typography.titleSmall)
            report.worst.forEach { move ->
                Text(
                    "#${move.index} ${MoveGrader.coord(move.move, report.size)} " +
                        "(${if (move.black) tr("흑", "Black") else tr("백", "White")}) — " +
                        "${move.quality.display}, " +
                        "-${"%.1f".format(move.delta * 100)}%p, " +
                        tr("최선은 ", "best was ") +
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
            AssistChip(onClick = onExportAll, label = { Text(tr("앱 폴더에 전부", "All to the app folder")) })
        }
    }
}

@Composable
private fun Accuracy(side: String, value: Double?) {
    Column {
        Text(tr("$side 정확도", "$side accuracy"), style = MaterialTheme.typography.labelSmall)
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
        HeaderCell(tr("수", "moves"), 44.dp)
        HeaderCell(tr("등급", "Grade"), 92.dp)
        HeaderCell("dWR", 60.dp)
        HeaderCell(tr("승률", "Win rate"), 56.dp)
        HeaderCell(tr("최선", "Best"), 44.dp)
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
                    Text(move.quality.display, style = MaterialTheme.typography.labelMedium)
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    Card(tr("분석 큐 (${ui.queue.size})", "Queue (${ui.queue.size})")) {
        Text(
            tr("여러 기보를 차례로 리뷰합니다. 각 대국의 리포트는 앱 폴더에 남길 수 있습니다.", "Reviews several games one after another. Each report can be left in the app folder."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = onAdd, label = { Text(tr("기보 추가", "Add files")) })
            AssistChip(onClick = onAddCurrent, label = { Text(tr("현재 대국 추가", "Add this game")) })
            AssistChip(onClick = onClear, label = { Text(tr("비우기", "Empty")) })
        }
        ui.queue.forEach { entry -> QueueRow(entry, ui.running) { onRemove(entry.uri) } }
        Button(onClick = onStart, enabled = ui.canQueue) { Text(tr("큐 실행", "Run the queue")) }
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
                tr("삭제", "Remove"),
                modifier = Modifier.clickable(onClick = onRemove),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LogCard(log: List<String>) {
    Card(tr("기록", "Log")) {
        log.takeLast(8).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---- small pieces ---------------------------------------------------------

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/** `#rrggbb` from the desktop palette to an ARGB int. */
private fun parseHex(hex: String): Long =
    0xFF000000L or (hex.removePrefix("#").toLongOrNull(16) ?: 0x808080L)
