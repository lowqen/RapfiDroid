package dev.gomoku.zenyth.feature.review

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Timer
import dev.gomoku.zenyth.core.designsystem.component.ChipRow
import dev.gomoku.zenyth.core.designsystem.component.ZenythFilterChip
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
import dev.gomoku.zenyth.core.designsystem.component.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import dev.gomoku.zenyth.core.designsystem.component.OutlinedButton
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
import dev.gomoku.zenyth.core.designsystem.component.WavyProgress
import dev.gomoku.zenyth.core.designsystem.component.CalmCard
import dev.gomoku.zenyth.core.designsystem.component.CardTitle
import dev.gomoku.zenyth.core.designsystem.component.Disclosure
import dev.gomoku.zenyth.core.designsystem.component.DisclosureCard
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.zenyth.core.designsystem.component.Stepper
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.GameReport
import dev.gomoku.zenyth.core.model.GradedMove
import dev.gomoku.zenyth.core.model.GradingPreset
import dev.gomoku.zenyth.core.model.MoveGrader
import dev.gomoku.zenyth.core.model.MoveQuality
import dev.gomoku.zenyth.core.model.QueueEntry
import dev.gomoku.zenyth.core.model.QueueStatus
import dev.gomoku.zenyth.core.model.ReviewBudget
import dev.gomoku.zenyth.feature.prove.ProveCard

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
            QueueCard(
                ui = ui,
                onAdd = { addToQueue.launch(arrayOf("*/*")) },
                onAddCurrent = viewModel::onEnqueueCurrent,
                onStart = viewModel::onStartQueue,
                onClear = viewModel::onClearQueue,
                onRemove = viewModel::onRemoveQueued,
            )
        }
        item {
            // The proof drives the same engine as the review, so it lives here,
            // folded at the bottom (Zenyth Web).
            ProveCard(onNotice = { viewModel.onExternalNotice(it) })
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
    Card(tr("게임 리뷰", "Game review", "对局复盘")) {
        Hint(tr("수마다 등급과 정확도를 매깁니다", "Grades every move and each side's accuracy", "为每一手评级，并给出双方的准确度"))
        val budget = ui.budget
        if (ui.running) {
            val progress = ui.progress
            // The review's place as a wave (2026-10-29): with a time budget the
            // head runs on through each position at the budget's pace and waits
            // at the next one's start; by depth it eases from position to position.
            val positions = progress.total + 1
            WavyProgress(
                fraction = progress.fraction,
                modifier = Modifier.fillMaxWidth(),
                perSecond = if (progress.budget.byDepth) 0f else 1f / (progress.budget.seconds * positions),
                ceiling = ((progress.index + 1f) / positions).coerceAtMost(1f),
            )
            Text(
                buildString {
                    progress.queue?.let { append(tr("큐 ${it.index}/${it.total} · ${it.name} · ", "Queue ${it.index}/${it.total} · ${it.name} · ", "队列 ${it.index}/${it.total} · ${it.name} ·")) }
                    append(tr("국면 ${progress.index}/${progress.total + 1} · ${progress.budget.label}", "Position ${progress.index}/${progress.total + 1} · ${progress.budget.label}", "局面 ${progress.index}/${progress.total + 1} · ${progress.budget.label}"))
                },
                style = MaterialTheme.typography.labelMedium,
            )
            OutlinedButton(onClick = onCancel) { Text(tr("중지", "Stop", "停止")) }
        } else {
            Button(onClick = onStart, enabled = ui.canReview, modifier = Modifier.fillMaxWidth()) {
                Text(tr("지금 판 리뷰 (${ui.lineLength}수)", "Review this game (${ui.lineLength} moves)", "复盘当前对局 (${ui.lineLength} 手)"))
            }
            if (!ui.connected) Hint(tr("엔진이 켜져야 합니다", "The engine has to be on", "需要开启引擎"), color = MaterialTheme.colorScheme.error)
        }
        val summary = (if (budget.byDepth) tr("깊이 ${budget.depth}", "depth ${budget.depth}", "深度 ${budget.depth}") else tr("${budget.seconds}초/수", "${budget.seconds}s per move", "每手 ${budget.seconds} 秒")) +
            if (ui.skipOpening) tr(" · 오프닝 제외", " · opening skipped", " · 跳过开局") else ""
        Disclosure(id = "review-options", title = tr("리뷰 설정", "Review settings", "复盘设置"), summary = summary) {
            ChipRow {
                ZenythFilterChip(
                    selected = !budget.byDepth,
                    leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                    onClick = { onBudget(budget.copy(byDepth = false)) },
                    label = { Text(tr("시간", "Time", "时间")) },
                    enabled = !ui.running,
                )
                ZenythFilterChip(
                    selected = budget.byDepth,
                    leadingIcon = { Icon(Icons.Outlined.Layers, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                    onClick = { onBudget(budget.copy(byDepth = true)) },
                    label = { Text(tr("깊이", "Depth", "深度")) },
                    enabled = !ui.running,
                )
            }
            if (budget.byDepth) {
                Stepper(tr("깊이", "Depth", "深度"), budget.depth, 4, 64, !ui.running) { onBudget(budget.copy(depth = it)) }
            } else {
                Stepper(tr("초/수", "s per move", "秒/手"), budget.seconds, 1, 120, !ui.running) { onBudget(budget.copy(seconds = it)) }
            }
            ChipRow {
                ZenythFilterChip(
                    selected = ui.skipOpening,
                    onClick = onSkipOpening,
                    label = { Text(tr("오프닝 1-5수 제외", "Skip moves 1-5", "跳过第1-5手")) },
                    enabled = !ui.running,
                )
                ZenythFilterChip(
                    selected = ui.showBadges,
                    onClick = onBadges,
                    label = { Text(tr("판에 등급 표시", "Grades on the board", "在棋盘上显示评级")) },
                )
            }
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
    Card(tr("리포트 · ${report.title}", "Report · ${report.title}", "报告 · ${report.title}")) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Accuracy(tr("흑", "Black", "黑"), report.tally.blackAccuracy)
            Accuracy(tr("백", "White", "白"), report.tally.whiteAccuracy)
            Column {
                Text(tr("수", "moves", "手"), style = MaterialTheme.typography.labelSmall)
                Text("${report.moveCount}", style = MaterialTheme.typography.titleMedium.tabular())
            }
            Column {
                Text(tr("예산", "Budget", "预算"), style = MaterialTheme.typography.labelSmall)
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
            Text(tr("최악의 수", "Worst moves", "最差着法"), style = MaterialTheme.typography.titleSmall)
            report.worst.forEach { move ->
                Text(
                    "#${move.index} ${MoveGrader.coord(move.move, report.size)} " +
                        "(${if (move.black) tr("흑", "Black", "黑") else tr("백", "White", "白")}) — " +
                        "${move.quality.display}, " +
                        "-${"%.1f".format(move.delta * 100)}%p, " +
                        tr("최선은 ", "best was ", "最佳应为") +
                        MoveGrader.coord(move.best, report.size),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        ChipRow {
            GradingPreset.entries.forEach { option ->
                ZenythFilterChip(
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
            AssistChip(onClick = onExportAll, label = { Text(tr("앱 폴더에 전부", "All to the app folder", "全部存到应用文件夹")) })
        }
    }
}

@Composable
private fun Accuracy(side: String, value: Double?) {
    Column {
        Text(tr("$side 정확도", "$side accuracy", "$side 准确度"), style = MaterialTheme.typography.labelSmall)
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
        HeaderCell(tr("수", "moves", "手"), 44.dp)
        HeaderCell(tr("등급", "Grade", "评级"), 92.dp)
        HeaderCell("dWR", 60.dp)
        HeaderCell(tr("승률", "Win rate", "胜率"), 56.dp)
        HeaderCell(tr("최선", "Best", "最佳"), 44.dp)
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
    DisclosureCard(
        id = "review-queue",
        title = tr("분석 큐", "Queue", "分析队列"),
        summary = tr("${ui.queue.size}판", "${ui.queue.size} games", "${ui.queue.size} 盘"),
    ) {
        Hint(tr("여러 기보를 차례로 리뷰합니다", "Reviews several games in turn", "依次复盘多局棋谱"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = onAdd, label = { Text(tr("기보 추가", "Add files", "添加棋谱")) })
            AssistChip(onClick = onAddCurrent, label = { Text(tr("지금 판 추가", "Add this game", "添加当前对局")) })
            AssistChip(onClick = onClear, label = { Text(tr("비우기", "Empty", "清空")) })
        }
        ui.queue.forEach { entry -> QueueRow(entry, ui.running) { onRemove(entry.uri) } }
        Button(onClick = onStart, enabled = ui.canQueue) { Text(tr("큐 실행", "Run the queue", "运行队列")) }
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
                tr("삭제", "Remove", "移除"),
                modifier = Modifier.clickable(onClick = onRemove),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LogCard(log: List<String>) {
    DisclosureCard(id = "review-log", title = tr("기록", "Log", "日志")) {
        log.takeLast(8).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---- small pieces ---------------------------------------------------------

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    CalmCard {
        CardTitle(title)
        content()
    }
}

/** `#rrggbb` from the desktop palette to an ARGB int. */
private fun parseHex(hex: String): Long =
    0xFF000000L or (hex.removePrefix("#").toLongOrNull(16) ?: 0x808080L)
