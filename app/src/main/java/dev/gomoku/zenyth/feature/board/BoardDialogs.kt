package dev.gomoku.zenyth.feature.board

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
import dev.gomoku.zenyth.core.designsystem.component.Button
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.component.SmoothProgressBar
import dev.gomoku.zenyth.core.designsystem.component.ZenythSheet
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.DbCellKind
import dev.gomoku.zenyth.core.model.FontSpec
import dev.gomoku.zenyth.core.model.GamePrompt
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.Swap2Choice
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import dev.gomoku.zenyth.core.common.plainText
import dev.gomoku.zenyth.core.common.plainTextClip
import kotlinx.coroutines.launch

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
            title = { Text(tr("교환하시겠습니까?", "Swap?", "交换?")) },
            text = {
                Text(
                    buildString {
                        append(tr("돌을 바꿔 잡으면 이후 컴퓨터가 반대 색을 둡니다.", "Swapping colours makes the computer play the other side from here on.", "交换执子后，计算机将从此执另一方。"))
                        prompt.fifthCount?.let { append(tr("\n5수 후보 수 N = $it", "\nCandidate 5th moves N = $it", "\n第5手候选数 N = $it")) }
                    },
                )
            },
            confirmButton = { Button(onClick = { onSwap(true) }) { Text(tr("교환", "Swap", "交换")) } },
            dismissButton = { TextButton(onClick = { onSwap(false) }) { Text(tr("그대로", "Keep", "不交换")) } },
        )

        GamePrompt.Swap2 -> AlertDialog(
            onDismissRequest = { onSwap2(Swap2Choice.STAY_WHITE) },
            title = { Text(tr("하나를 선택하세요", "Choose one", "请选择一个选项")) },
            text = { Text(tr("스왑2: 백을 유지하거나, 돌을 바꾸거나, 2수를 더 놓습니다.", "Swap2: keep White, swap colours, or add two more moves.", "两次交换：继续执白、交换执子，或再下两手。")) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSwap2(Swap2Choice.STAY_WHITE) }) { Text(tr("백 유지", "Keep White", "继续执白")) }
                    TextButton(onClick = { onSwap2(Swap2Choice.SWAP) }) { Text(tr("교환", "Swap", "交换")) }
                    Button(onClick = { onSwap2(Swap2Choice.ADD_TWO) }) { Text(tr("2수 추가", "Add two", "增加两子")) }
                }
            },
        )

        GamePrompt.FifthCount -> AlertDialog(
            onDismissRequest = { onFifthCount(1) },
            title = { Text(tr("5수 후보 개수 (1~8)", "Number of 5th-move candidates (1-8)", "第5手打点数 (1~8)")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("상대에게 제시할 5수의 개수 N을 고르세요.", "Choose N, how many 5th moves to offer your opponent.", "请选择要给对手的第5手打点数 N。"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..8).forEach { n ->
                            AssistChip(onClick = { onFifthCount(n) }, label = { Text("$n") })
                        }
                    }
                }
            },
            confirmButton = {},
        )

        GamePrompt.SwapInfo -> InfoDialog(tr("교환", "Swap", "交换"), tr("상대가 돌을 바꿔 잡았습니다.", "Your opponent swapped colours.", "对手选择了交换执子。"), onDismiss)
        GamePrompt.IllegalOpening -> InfoDialog(
            tr("표준 오프닝이 아닙니다", "Illegal Opening", "开局不合法"),
            tr("첫 3수는 정중앙 근처(2수는 ±1, 3수는 ±2)여야 합니다. 판을 초기화했습니다.", "The first three moves must sit near the centre (move 2 within ±1, move 3 within ±2). The board has been cleared.", "前三手必须靠近天元(第2手在 ±1 内，第3手在 ±2 内)。棋盘已清空。"),
            onDismiss,
        )
        is GamePrompt.Forbidden -> InfoDialog(
            tr("금수", "Forbidden", "禁手"),
            tr("${prompt.cell.label(size)}는 흑의 금수입니다.", "${prompt.cell.label(size)} is forbidden for Black.", "${prompt.cell.label(size)} 是黑棋的禁手。"),
            onDismiss,
        )
        GamePrompt.Timeout -> InfoDialog(tr("시간 초과", "Time out", "超时"), tr("제한 시간을 모두 사용했습니다.", "The time limit has run out.", "限定时间已用完。"), onDismiss)
        is GamePrompt.Info -> InfoDialog(tr("대국", "Game", "对局"), prompt.text, onDismiss)
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onDismiss) { Text(tr("확인", "OK", "确定")) } },
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
    ZenythSheet(
        title = tr("균형점 찾기", "Balance", "平衡点"),
        onDismiss = onDismiss,
        dismiss = {
            TextButton(onClick = onDismiss) { Text(tr("취소", "Cancel", "取消")) }
            Button(onClick = { onConfirm(true, value) }) { Text(tr("두 수", "Two moves", "两手")) }
        },
        confirm = {
            Button(onClick = { onConfirm(false, value) }) { Text(tr("한 수", "One move", "一手")) }
        },
    ) {
        Text(
            tr("승률 50 %에 가장 가까운 수를 찾아 바로 둡니다", "Finds the move nearest 50 % and plays it", "寻找最接近 50 % 的着法并直接落子"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = bias,
            onValueChange = { bias = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("치우침 (0 = 완전 균형)", "Bias (0 = dead even)", "偏差 (0 = 完全均势)")) },
        )
    }
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
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    ZenythSheet(
        title = tr("국면 문자열", "Position string", "局面代码"),
        onDismiss = onDismiss,
        dismiss = { TextButton(onClick = onDismiss) { Text(tr("닫기", "Close", "关闭")) } },
        confirm = {
            Button(onClick = { onLoad(draft) }, enabled = draft.isNotBlank()) { Text(tr("불러오기", "Load", "载入")) }
        },
    ) {
        // This board's line, with the button that takes it away beside it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    ui.positionString.ifEmpty { tr("(빈 판)", "(empty board)", "(空棋盘)") },
                    style = MaterialTheme.typography.bodyMedium
                        .copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    tr("${ui.moveCount}수", "${ui.moveCount} moves", "${ui.moveCount} 手") + if (ui.futureCount > 0) tr(" · 되돌린 ${ui.futureCount}수는 제외", " · ${ui.futureCount} undone moves excluded", " · 不含已悔的 ${ui.futureCount} 手") else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    val text = ui.positionString
                    scope.launch {
                        clipboard.setClipEntry(plainTextClip(text))
                        onCopied()
                    }
                },
                enabled = ui.positionString.isNotEmpty(),
            ) { Text(tr("복사", "Copy", "复制")) }
        }
        // A line to load, with the button that fills it from the clipboard.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                label = { Text(tr("불러올 문자열 (예: h8i9g7)", "String to load (e.g. h8i9g7)", "要载入的代码 (例: h8i9g7)")) },
            )
            TextButton(onClick = { scope.launch { draft = clipboard.plainText().orEmpty() } }) {
                Text(tr("붙여넣기", "Paste", "粘贴"))
            }
        }
    }
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
    ZenythSheet(
        title = tr("보드 텍스트  ${cell.label(size)}", "Board text  ${cell.label(size)}", "棋盘文字  ${cell.label(size)}"),
        onDismiss = onDismiss,
        dismiss = { TextButton(onClick = onDismiss) { Text(tr("취소", "Cancel", "取消")) } },
        confirm = {
            Button(onClick = { onConfirm(text) }, enabled = editable) { Text(tr("확인", "OK", "确定")) }
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 6) text = it },
            singleLine = true,
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("최대 6자", "6 characters max", "最多 6 个字")) },
        )
        Text(
            if (editable) tr("비우고 확인하면 라벨이 삭제됩니다.", "Confirming an empty value deletes the label.", "留空并确认会删除该标签。")
            else tr("읽기 전용 상태에서는 편집할 수 없습니다.", "Nothing can be edited while the database is read-only.", "数据库只读时无法编辑。"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val kinds = mapOf(
            "W5" to DbCellKind.WIN, "L7" to DbCellKind.LOSS,
            "D" to DbCellKind.DRAW, "63%" to DbCellKind.RATE,
        )
        FlowRowChips(kinds.keys.toList(), enabled = editable) { text = it }
    }
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
    ZenythSheet(
        title = tr("국면 주석", "Position comment", "局面注释"),
        onDismiss = onDismiss,
        dismiss = { TextButton(onClick = onDismiss) { Text(tr("취소", "Cancel", "取消")) } },
        confirm = {
            Button(onClick = { onConfirm(text) }, enabled = editable) { Text(tr("저장", "Save", "保存")) }
        },
    ) {
        // settings.txt line 47, the desktop's second "Database Comment Font":
        // the one the editor uses.
        val base = MaterialTheme.typography.bodyMedium
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            enabled = editable,
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
            textStyle = base.copy(fontSize = base.fontSize * editFont.scale),
        )
    }
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
    banner: dev.gomoku.zenyth.feature.board.ResearchBanner,
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
                    Text(tr("중지", "Stop", "停止"), color = Color.White, style = MaterialTheme.typography.labelMedium)
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
                SmoothProgressBar(
                    fraction = it,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 6.dp),
                )
            }
        }
    }
}
