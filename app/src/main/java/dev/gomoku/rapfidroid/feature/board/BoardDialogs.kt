package dev.gomoku.rapfidroid.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.DbCellKind
import dev.gomoku.rapfidroid.core.model.FontSpec
import dev.gomoku.rapfidroid.core.model.GamePrompt
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.Swap2Choice

/*
 * The board screen's dialogs: the desktop's game prompts, balance, position
 * string, board text and comment, plus the research badge drawn over the board.
 */

/**
 * One dialog per desktop game dialog: the swap questions, the number of fifth
 * moves, and the three information popups.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GamePromptDialog(
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
            title = { Text(tr("교환하시겠습니까?", "Swap?")) },
            text = {
                Text(
                    buildString {
                        append(tr("돌을 바꿔 잡으면 이후 컴퓨터가 반대 색을 둡니다.", "Swapping colours makes the computer play the other side from here on."))
                        prompt.fifthCount?.let { append(tr("\n5수 후보 수 N = $it", "\nCandidate 5th moves N = $it")) }
                    },
                )
            },
            confirmButton = { Button(onClick = { onSwap(true) }) { Text(tr("교환", "Swap")) } },
            dismissButton = { TextButton(onClick = { onSwap(false) }) { Text(tr("그대로", "Keep")) } },
        )

        GamePrompt.Swap2 -> AlertDialog(
            onDismissRequest = { onSwap2(Swap2Choice.STAY_WHITE) },
            title = { Text(tr("하나를 선택하세요", "Choose one")) },
            text = { Text(tr("스왑2: 백을 유지하거나, 돌을 바꾸거나, 2수를 더 놓습니다.", "Swap2: keep White, swap colours, or add two more moves.")) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSwap2(Swap2Choice.STAY_WHITE) }) { Text(tr("백 유지", "Keep White")) }
                    TextButton(onClick = { onSwap2(Swap2Choice.SWAP) }) { Text(tr("교환", "Swap")) }
                    Button(onClick = { onSwap2(Swap2Choice.ADD_TWO) }) { Text(tr("2수 추가", "Add two")) }
                }
            },
        )

        GamePrompt.FifthCount -> AlertDialog(
            onDismissRequest = { onFifthCount(1) },
            title = { Text(tr("5수 후보 개수 (1~8)", "Number of 5th-move candidates (1-8)")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("상대에게 제시할 5수의 개수 N을 고르세요.", "Choose N, how many 5th moves to offer your opponent."))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..8).forEach { n ->
                            AssistChip(onClick = { onFifthCount(n) }, label = { Text("$n") })
                        }
                    }
                }
            },
            confirmButton = {},
        )

        GamePrompt.SwapInfo -> InfoDialog(tr("교환", "Swap"), tr("상대가 돌을 바꿔 잡았습니다.", "Your opponent swapped colours."), onDismiss)
        GamePrompt.IllegalOpening -> InfoDialog(
            tr("표준 오프닝이 아닙니다", "Illegal Opening"),
            tr("첫 3수는 정중앙 근처(2수는 ±1, 3수는 ±2)여야 합니다. 판을 초기화했습니다.", "The first three moves must sit near the centre (move 2 within ±1, move 3 within ±2). The board has been cleared."),
            onDismiss,
        )
        is GamePrompt.Forbidden -> InfoDialog(
            tr("금수", "Forbidden"),
            tr("${prompt.cell.label(size)}는 흑의 금수입니다.", "${prompt.cell.label(size)} is forbidden for Black."),
            onDismiss,
        )
        GamePrompt.Timeout -> InfoDialog(tr("시간 초과", "Time out"), tr("제한 시간을 모두 사용했습니다.", "The time limit has run out."), onDismiss)
        is GamePrompt.Info -> InfoDialog(tr("대국", "Game"), prompt.text, onDismiss)
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onDismiss) { Text(tr("확인", "OK")) } },
    )
}

/**
 * Balance search options. `balance1` looks for the single move that levels the
 * position, `balance2` for the pair; the number is the desktop's optional bias
 * argument in engine value units (`balance1 100`), 0 meaning dead even.
 */
@Composable
internal fun BalanceDialog(onDismiss: () -> Unit, onConfirm: (Boolean, Int) -> Unit) {
    var bias by remember { mutableStateOf("0") }
    val value = bias.trim().toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("균형점 찾기", "Balance")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("엔진이 국면을 균형(승률 50 %)에 가장 가깝게 만드는 수를 찾습니다. ", "The engine looks for the move that brings the position closest to even (50 %).") +
                        tr("찾은 수는 바로 판에 놓입니다.", "What it finds is played straight onto the board."),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = bias,
                    onValueChange = { bias = it },
                    singleLine = true,
                    label = { Text(tr("치우침 (0 = 완전 균형)", "Bias (0 = dead even)")) },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(false, value) }) { Text(tr("한 수", "One move")) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text(tr("취소", "Cancel")) }
                Button(onClick = { onConfirm(true, value) }) { Text(tr("두 수", "Two moves")) }
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
internal fun PositionDialog(
    ui: BoardUiState,
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit,
    onCopied: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("국면 문자열", "Position string")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    ui.positionString.ifEmpty { tr("(빈 판)", "(empty board)") },
                    style = MaterialTheme.typography.bodyMedium
                        .copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    tr("${ui.moveCount}수", "${ui.moveCount} moves") + if (ui.futureCount > 0) tr(" · 되돌린 ${ui.futureCount}수는 제외", " · ${ui.futureCount} undone moves excluded") else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text(tr("불러올 문자열 (예: h8i9g7)", "String to load (e.g. h8i9g7)")) },
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
                ) { Text(tr("복사", "Copy")) }
                TextButton(onClick = { draft = clipboard.getText()?.text.orEmpty() }) {
                    Text(tr("붙여넣기", "Paste"))
                }
                Button(onClick = { onLoad(draft) }, enabled = draft.isNotBlank()) { Text(tr("불러오기", "Load")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("닫기", "Close")) } },
    )
}

/**
 * Board text on one point — the desktop's dialog (main.c `show_dialog_boardtext`):
 * max six characters, and an empty value deletes the label.
 */
@Composable
internal fun BoardTextDialog(
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
        title = { Text(tr("보드 텍스트  ${cell.label(size)}", "Board text  ${cell.label(size)}")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 6) text = it },
                    singleLine = true,
                    enabled = editable,
                    label = { Text(tr("최대 6자", "6 characters max")) },
                )
                Text(
                    if (editable) tr("비우고 확인하면 라벨이 삭제됩니다.", "Confirming an empty value deletes the label.")
                    else tr("읽기 전용 상태에서는 편집할 수 없습니다.", "Nothing can be edited while the database is read-only."),
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
            Button(onClick = { onConfirm(text) }, enabled = editable) { Text(tr("확인", "OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("취소", "Cancel")) } },
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
internal fun CommentDialog(
    initial: String,
    editable: Boolean,
    editFont: FontSpec,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("국면 주석", "Position comment")) },
        text = {
            // settings.txt line 47, the desktop's second "Database Comment Font":
            // the one the editor uses.
            val base = MaterialTheme.typography.bodyMedium
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = editable,
                minLines = 3,
                maxLines = 8,
                textStyle = base.copy(fontSize = base.fontSize * editFont.scale),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = editable) { Text(tr("저장", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("취소", "Cancel")) } },
    )
}

/** `rgba(0.85, 0.45, 0.15, 0.90)` — the desktop's prove badge (main.c:6892). */
private val ProveBadgeColor = Color(0xE6D97326)

/** `rgba(0.29, 0.62, 1.0, 0.90)` — the desktop's review badge (main.c:6869). */
private val ReviewBadgeColor = Color(0xE64A9EFF)

/**
 * The counters the desktop paints in the top-left corner of its drawing area
 * while a review or a proof runs (main.c:6860-6905): one line of totals, a second
 * of live search state, white on a filled box. It sits on the board here for the
 * same reason it does there — that is where the eye already is — and carries the
 * stop button, which on a phone would otherwise be a tab away.
 */
@Composable
internal fun ResearchBadge(
    banner: dev.gomoku.rapfidroid.feature.board.ResearchBanner,
    modifier: Modifier = Modifier,
    onStop: () -> Unit,
) {
    Surface(
        modifier = modifier.widthIn(max = 300.dp),
        color = if (banner.isProve) ProveBadgeColor else ReviewBadgeColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    banner.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(
                    onClick = onStop,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text(tr("중지", "Stop"), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (banner.detail.isNotEmpty()) {
                Text(
                    banner.detail,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
            banner.progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 6.dp),
                )
            }
        }
    }
}
