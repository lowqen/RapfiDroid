package dev.gomoku.rapfidroid.feature.prove

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import dev.gomoku.rapfidroid.core.designsystem.component.GomokuBoard
import dev.gomoku.rapfidroid.core.designsystem.component.Stepper
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.designsystem.component.Disclosure
import dev.gomoku.rapfidroid.core.designsystem.component.DisclosureCard
import dev.gomoku.rapfidroid.core.designsystem.component.Hint
import dev.gomoku.rapfidroid.core.model.ProveMark
import dev.gomoku.rapfidroid.core.model.ProveOptions
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
    val budgetText = if (options.byDepth) tr("깊이 ${options.depth0}", "depth ${options.depth0}")
    else tr("${options.budget0Sec}초", "${options.budget0Sec}s")
    DisclosureCard(
        id = "prove",
        title = tr("국면 증명", "Prove position"),
        modifier = modifier,
        summary = if (ui.running) tr("증명 중", "Proving") else budgetText,
    ) {
        Hint(tr("둘 차례가 이기는지 증명합니다", "Proves whether the side to move wins"))
        if (ui.running) {
            RunningState(ui)
            OutlinedButton(onClick = viewModel::onCancel) { Text(tr("중지", "Stop")) }
        } else {
            Button(onClick = viewModel::onStart, enabled = ui.canStart, modifier = Modifier.fillMaxWidth()) {
                Text(
                    tr("지금 판 증명 (", "Prove this position (") +
                        (if (ui.blackToMove) tr("흑", "Black") else tr("백", "White")) +
                        tr(" 차례)", " to move)"),
                )
            }
            ui.blocker?.let { Hint(it, color = MaterialTheme.colorScheme.error) }
        }
        Disclosure(id = "prove-options", title = tr("증명 설정", "Proof settings"), summary = budgetText) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !options.byDepth,
                    onClick = { viewModel.onOptions(options.copy(byDepth = false)) },
                    label = { Text(tr("시간", "Time")) },
                    enabled = editable,
                )
                FilterChip(
                    selected = options.byDepth,
                    onClick = { viewModel.onOptions(options.copy(byDepth = true)) },
                    label = { Text(tr("깊이", "Depth")) },
                    enabled = editable,
                )
            }
            if (options.byDepth) {
                Stepper(tr("초기 깊이", "Starting depth"), options.depth0, 4, 64, editable, step = ::budgetStep) {
                    viewModel.onOptions(options.copy(depth0 = it))
                }
                Stepper(tr("최대 깊이", "Depth cap"), options.depthMax, 4, 128, editable, step = ::budgetStep) {
                    viewModel.onOptions(options.copy(depthMax = it))
                }
            } else {
                Stepper(tr("초기 예산(초)", "Starting budget (s)"), options.budget0Sec, 1, 600, editable, step = ::budgetStep) {
                    viewModel.onOptions(options.copy(budget0Sec = it))
                }
                Stepper(tr("최대 예산(초)", "Budget cap (s)"), options.budgetMaxSec, 1, 3600, editable, step = ::budgetStep) {
                    viewModel.onOptions(options.copy(budgetMaxSec = it))
                }
            }
            Stepper(tr("공격 후보 수", "Attack candidates"), options.nbest, 1, ProveOptions.NBEST_MAX, editable, step = ::budgetStep) {
                viewModel.onOptions(options.copy(nbest = it))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = options.bestFirst,
                    onClick = { viewModel.onOptions(options.copy(bestFirst = !options.bestFirst)) },
                    label = { Text(tr("최선 공격수 먼저", "Best attack first")) },
                    enabled = editable,
                )
                FilterChip(
                    selected = options.probe,
                    onClick = { viewModel.onOptions(options.copy(probe = !options.probe)) },
                    label = { Text(tr("최강 방어 조기 탐침", "Probe the strongest defence early")) },
                    enabled = editable,
                )
            }
        }
        if (ui.log.isNotEmpty()) {
            Disclosure(id = "prove-log", title = tr("기록", "Log")) {
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
                TextButton(onClick = viewModel::onDismissOutcome) { Text(tr("확인", "OK")) }
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
        tr("탐색 중인 수순: ${ui.progress.path}", "Searching: ${ui.progress.path}"),
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
    ProveMark.LATENT -> tr("대기", "idle")
    else -> "…"
}

/** Budgets reach into the hundreds of seconds, so the step grows with the value. */
private fun budgetStep(value: Int): Int = when {
    value >= 100 -> 20
    value >= 20 -> 5
    else -> 1
}
