package dev.gomoku.zenyth.feature.prove

import dev.gomoku.zenyth.core.designsystem.component.RapfiFilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import dev.gomoku.zenyth.core.designsystem.component.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import dev.gomoku.zenyth.core.designsystem.component.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.zenyth.core.designsystem.component.GomokuBoard
import dev.gomoku.zenyth.core.designsystem.component.Stepper
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.designsystem.component.Disclosure
import dev.gomoku.zenyth.core.designsystem.component.DisclosureCard
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.model.ProveMark
import dev.gomoku.zenyth.core.model.ProveOptions
import kotlinx.coroutines.delay

/**
 * The desktop's "Prove Position" dialog plus its live badge, as a card on the
 * review screen (both live in the Analysis menu there). The board draws the
 * ghost stones and the candidate markers while this runs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProveCard(
    modifier: Modifier = Modifier,
    viewModel: ProveViewModel = hiltViewModel(),
    onNotice: (String) -> Unit = {},
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val notice = ui.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            onNotice(notice)
            viewModel.onNoticeShown()
        }
    }

    val options = ui.options
    val editable = !ui.running
    val budgetText = if (options.byDepth) tr("깊이 ${options.depth0}", "depth ${options.depth0}", "深度 ${options.depth0}")
    else tr("${options.budget0Sec}초", "${options.budget0Sec}s", "${options.budget0Sec} 秒")
    DisclosureCard(
        id = "prove",
        title = tr("국면 증명", "Prove position", "局面证明"),
        modifier = modifier,
        summary = if (ui.running) tr("증명 중", "Proving", "证明中") else budgetText,
    ) {
        Hint(tr("둘 차례가 이기는지 증명합니다", "Proves whether the side to move wins", "证明轮到的一方能否取胜"))
        if (ui.running) {
            RunningState(ui)
            OutlinedButton(onClick = viewModel::onCancel) { Text(tr("중지", "Stop", "停止")) }
        } else {
            Button(onClick = viewModel::onStart, enabled = ui.canStart, modifier = Modifier.fillMaxWidth()) {
                Text(
                    tr("지금 판 증명 (", "Prove this position (", "证明当前局面 (") +
                        (if (ui.blackToMove) tr("흑", "Black", "黑") else tr("백", "White", "白")) +
                        tr(" 차례)", " to move)", "轮到)"),
                )
            }
            ui.blocker?.let { Hint(it, color = MaterialTheme.colorScheme.error) }
        }
        Disclosure(id = "prove-options", title = tr("증명 설정", "Proof settings", "证明设置"), summary = budgetText) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RapfiFilterChip(
                    selected = !options.byDepth,
                    onClick = { viewModel.onOptions(options.copy(byDepth = false)) },
                    label = { Text(tr("시간", "Time", "时间")) },
                    enabled = editable,
                )
                RapfiFilterChip(
                    selected = options.byDepth,
                    onClick = { viewModel.onOptions(options.copy(byDepth = true)) },
                    label = { Text(tr("깊이", "Depth", "深度")) },
                    enabled = editable,
                )
            }
            if (options.byDepth) {
                Stepper(tr("초기 깊이", "Starting depth", "初始深度"), options.depth0, 4, 64, editable, step = ::budgetStep) {
                    viewModel.onOptions(options.copy(depth0 = it))
                }
                Stepper(tr("최대 깊이", "Depth cap", "深度上限"), options.depthMax, 4, 128, editable, step = ::budgetStep) {
                    viewModel.onOptions(options.copy(depthMax = it))
                }
            } else {
                Stepper(tr("초기 예산(초)", "Starting budget (s)", "初始预算(秒)"), options.budget0Sec, 1, 600, editable, step = ::budgetStep) {
                    viewModel.onOptions(options.copy(budget0Sec = it))
                }
                Stepper(tr("최대 예산(초)", "Budget cap (s)", "预算上限(秒)"), options.budgetMaxSec, 1, 3600, editable, step = ::budgetStep) {
                    viewModel.onOptions(options.copy(budgetMaxSec = it))
                }
            }
            Stepper(tr("공격 후보 수", "Attack candidates", "进攻候选数"), options.nbest, 1, ProveOptions.NBEST_MAX, editable, step = ::budgetStep) {
                viewModel.onOptions(options.copy(nbest = it))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RapfiFilterChip(
                    selected = options.bestFirst,
                    onClick = { viewModel.onOptions(options.copy(bestFirst = !options.bestFirst)) },
                    label = { Text(tr("최선 공격수 먼저", "Best attack first", "最佳进攻优先")) },
                    enabled = editable,
                )
                RapfiFilterChip(
                    selected = options.probe,
                    onClick = { viewModel.onOptions(options.copy(probe = !options.probe)) },
                    label = { Text(tr("최강 방어 조기 탐침", "Probe the strongest defence early", "提前探测最强防守")) },
                    enabled = editable,
                )
            }
        }
        if (ui.log.isNotEmpty()) {
            Disclosure(id = "prove-log", title = tr("기록", "Log", "日志")) {
                ui.log.takeLast(6).forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    ui.outcome?.let { outcome ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissOutcome,
            confirmButton = {
                TextButton(onClick = viewModel::onDismissOutcome) { Text(tr("확인", "OK", "确定")) }
            },
            title = { Text(outcome.title) },
            text = { Text(outcome.message) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunningState(ui: ProveUiState) {
    val (first, second) = ui.progress.badgeLines()
    // The board the proof is working on, with the line under search drawn on it
    // as ghost stones and every root candidate carrying its marker — the same
    // overlay the board tab shows, because a proof is watched, not read.
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            pulse = !pulse
        }
    }
    GomokuBoard(
        render = ui.render.copy(provePulse = pulse),
        modifier = Modifier.fillMaxWidth(),
    )
    // No total to count towards: the tree grows as the proof goes on, so the
    // desktop shows counters rather than a percentage.
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text(first, style = MaterialTheme.typography.labelMedium)
    if (second.isNotEmpty()) {
        Text(second, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
    Text(
        tr("탐색 중인 수순: ${ui.progress.path}", "Searching: ${ui.progress.path}", "正在搜索的手顺: ${ui.progress.path}"),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
    )
    if (ui.candidates.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ui.candidates.forEach { row ->
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            buildString {
                                append(row.label)
                                append(' ')
                                append(markLabel(row.mark))
                                if (row.budget.isNotEmpty()) append(" ${row.budget}")
                            },
                            fontWeight = FontWeight.Medium,
                        )
                    },
                )
            }
        }
    }
}

/** The desktop's marker glyphs (main.c:9116). */
private fun markLabel(mark: ProveMark): String = when (mark) {
    ProveMark.WIN -> "✓"
    ProveMark.LOSS -> "✗"
    ProveMark.EXH -> "!"
    ProveMark.LATENT -> tr("대기", "idle", "空闲")
    else -> "…"
}

/** Budgets reach into the hundreds of seconds, so the step grows with the value. */
private fun budgetStep(value: Int): Int = when {
    value >= 100 -> 20
    value >= 20 -> 5
    else -> 1
}
