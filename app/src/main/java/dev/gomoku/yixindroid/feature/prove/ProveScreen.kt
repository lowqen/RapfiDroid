package dev.gomoku.yixindroid.feature.prove

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.component.GomokuBoard
import dev.gomoku.yixindroid.core.model.ProveMark
import dev.gomoku.yixindroid.core.model.ProveOptions
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

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("국면 증명", style = MaterialTheme.typography.titleSmall)
            Text(
                "차례인 쪽이 이 국면에서 이기는지 AND/OR 탐색으로 증명합니다. " +
                    "결론(메이트)은 엔진의 데이터베이스에 기록되므로 PC에서도 그대로 보입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val options = ui.options
            val editable = !ui.running
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !options.byDepth,
                    onClick = { viewModel.onOptions(options.copy(byDepth = false)) },
                    label = { Text("초/노드") },
                    enabled = editable,
                )
                FilterChip(
                    selected = options.byDepth,
                    onClick = { viewModel.onOptions(options.copy(byDepth = true)) },
                    label = { Text("깊이/노드") },
                    enabled = editable,
                )
            }
            if (options.byDepth) {
                Stepper("초기 깊이", options.depth0, 4, 64, editable) {
                    viewModel.onOptions(options.copy(depth0 = it))
                }
                Stepper("최대 깊이", options.depthMax, 4, 128, editable) {
                    viewModel.onOptions(options.copy(depthMax = it))
                }
            } else {
                Stepper("초기 예산(초)", options.budget0Sec, 1, 600, editable) {
                    viewModel.onOptions(options.copy(budget0Sec = it))
                }
                Stepper("최대 예산(초)", options.budgetMaxSec, 1, 3600, editable) {
                    viewModel.onOptions(options.copy(budgetMaxSec = it))
                }
            }
            Stepper("공격 후보 수", options.nbest, 1, ProveOptions.NBEST_MAX, editable) {
                viewModel.onOptions(options.copy(nbest = it))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = options.bestFirst,
                    onClick = { viewModel.onOptions(options.copy(bestFirst = !options.bestFirst)) },
                    label = { Text("최선 공격수 먼저") },
                    enabled = editable,
                )
                FilterChip(
                    selected = options.probe,
                    onClick = { viewModel.onOptions(options.copy(probe = !options.probe)) },
                    label = { Text("최강 방어 조기 탐침") },
                    enabled = editable,
                )
            }

            if (ui.running) {
                RunningState(ui)
                OutlinedButton(onClick = viewModel::onCancel) { Text("중지") }
            } else {
                Button(onClick = viewModel::onStart, enabled = ui.canStart) {
                    Text("증명 시작 (${if (ui.blackToMove) "흑" else "백"} 차례, ${ui.moveCount}수)")
                }
                ui.blocker?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (ui.log.isNotEmpty()) {
                Text("기록", style = MaterialTheme.typography.labelMedium)
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
                TextButton(onClick = viewModel::onDismissOutcome) { Text("확인") }
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
        "탐색 중인 수순: ${ui.progress.path}",
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
    ProveMark.LATENT -> "대기"
    else -> "…"
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
        OutlinedButton(onClick = { onChange(value - step(value)) }, enabled = enabled && value > min) {
            Text("−")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange(value + step(value)) }, enabled = enabled && value < max) {
            Text("+")
        }
    }
}

/** Budgets reach into the hundreds of seconds, so the step grows with the value. */
private fun step(value: Int): Int = when {
    value >= 100 -> 20
    value >= 20 -> 5
    else -> 1
}
