package dev.gomoku.rapfidroid.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.rapfidroid.core.designsystem.component.QuietSwitch
import dev.gomoku.rapfidroid.core.designsystem.component.YixinTopBar
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.CallbackConfig
import dev.gomoku.rapfidroid.core.model.ToolScripts
import dev.gomoku.rapfidroid.core.model.ToolsState
import dev.gomoku.rapfidroid.feature.connection.ConnectionScreen

/**
 * The engine tab: 「연결」 keeps the socket screen, 「도구」 adds the operations
 * menu. Two tabs rather than an eighth bottom-nav entry, the same way the
 * explorer pairs its two research tools.
 */
@Composable
fun EngineScreen(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = remember { listOf(tr("연결", "Connect"), tr("도구", "Tools")) }

    Column(modifier = modifier.fillMaxSize()) {
        YixinTopBar(title = tr("엔진", "Engine"), subtitle = titles.getOrNull(tab))
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, style = MaterialTheme.typography.titleSmall) },
                )
            }
        }
        QuietSwitch(tab, Modifier.fillMaxSize()) { index ->
            when (index) {
                0 -> ConnectionScreen()
                else -> ToolsScreen()
            }
        }
    }
}

/**
 * Engine operations — hash tools, blocked points, the position stack,
 * maintenance scripts and the console command language (main.c
 * `execute_command`, and the toolbar scripts in `function/toolbar33-36.txt`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    if (ui.editingCallbacks) {
        CallbackDialog(
            config = ui.tools.callbacks,
            onDismiss = { viewModel.onEditCallbacks(false) },
            onSave = {
                viewModel.onCallbacksChange(it)
                viewModel.onEditCallbacks(false)
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ToolCard(tr("탐색 도구", "Search tools")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip({ viewModel.run("searchdefend") }, { Text(tr("모든 방어수", "All defences")) })
                    AssistChip({ viewModel.run("nbest") }, { Text(tr("멀티 PV", "Multi-PV")) })
                    AssistChip({ viewModel.run("balance1") }, { Text(tr("균형점 1수", "Balance, one move")) })
                    AssistChip({ viewModel.run("balance2") }, { Text(tr("균형점 2수", "Balance, two moves")) })
                    AssistChip({ viewModel.run("send board") }, { Text(tr("국면 전송", "Send position")) })
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = ui.startDepth,
                        onValueChange = viewModel::onStartDepthChange,
                        label = { Text(tr("시작 깊이", "Start depth")) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.width(120.dp),
                    )
                    OutlinedButton(onClick = viewModel::onSearchFrom) { Text(tr("이 깊이부터", "From this depth")) }
                }
            }
        }

        item {
            ToolCard(tr("해시 (전치표)", "Hash (transposition table)")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip({ viewModel.run("hash clear") }, { Text(tr("지우기", "Clear")) })
                    AssistChip({ viewModel.run("hash usage") }, { Text(tr("사용량", "Usage")) })
                    FilterChip(
                        selected = ui.settings.hashAutoClear,
                        onClick = viewModel::onToggleHashAutoClear,
                        label = { Text(tr("탐색 전 자동 지우기", "Clear before every search")) },
                    )
                }
                OutlinedTextField(
                    value = ui.hashPath,
                    onValueChange = viewModel::onHashPathChange,
                    label = { Text(tr("서버 경로 (엔진이 읽고 씁니다)", "Server path (the engine reads and writes it)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::onHashDump) { Text(tr("저장", "Save")) }
                    OutlinedButton(onClick = viewModel::onHashLoad) { Text(tr("복원", "Restore")) }
                }
            }
        }

        item {
            ToolCard(tr("차단 (${ui.blockedCount}점)", "Blocked (${ui.blockedCount} points)")) {
                Text(
                    tr("차단한 점은 엔진이 후보에서 제외합니다. 보드에 ✕ 로 표시됩니다.", "The engine leaves blocked points out of its candidates. They carry an ✕ on the board."),
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip({ viewModel.run("block reset") }, { Text(tr("차단 해제", "Clear blocks")) })
                    AssistChip({ viewModel.run("blockpath reset") }, { Text(tr("경로 차단 해제", "Clear blocked paths")) })
                    FilterChip(
                        selected = ui.settings.blockAutoReset,
                        onClick = viewModel::onToggleBlockAutoReset,
                        label = { Text(tr("착수 후 자동 해제", "Auto-clear after a move")) },
                    )
                    FilterChip(
                        selected = ui.settings.blockPathAutoReset,
                        onClick = viewModel::onToggleBlockPathAutoReset,
                        label = { Text(tr("경로 자동 해제", "Auto-clear paths")) },
                    )
                }
                Text(
                    tr("점 지정은 콘솔로: block h8i8 · block undo h8 · blockpath h8h7 · ", "Points are named on the console: block h8i8 · block undo h8 · blockpath h8h7 ·") +
                        tr("block compare h8 (이 점만 남기고 전부 차단)", "block compare h8 (blocks everything but these)"),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        item {
            ToolCard(tr("국면 슬롯", "Position slots")) {
                Text(
                    tr("현재 국면을 10칸에 담아 두고 되돌립니다 (pushpos / poppos).", "Parks the current position in one of ten slots and brings it back (pushpos / poppos)."),
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (slot in 0 until ToolsState.STACK_SLOTS) {
                        val filled = slot in ui.filledSlots
                        AssistChip(
                            onClick = { if (filled) viewModel.onPop(slot) else viewModel.onPush(slot) },
                            label = { Text(if (filled) "↺$slot" else "$slot") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.run("getpos") }) { Text(tr("국면 문자열", "Position string")) }
                }
                Text(
                    tr("빈 칸을 누르면 저장, ↺ 는 되돌리기. 저장된 칸: ", "An empty slot stores, ↺ restores. In use:") +
                        (ui.filledSlots.joinToString(", ").ifEmpty { tr("없음", "none") }),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        item {
            ToolCard(tr("엔진 유지보수", "Engine maintenance")) {
                Text(
                    tr("데스크톱 툴바와 같은 스크립트를 그대로 보냅니다 ", "Sends the same scripts the desktop toolbar does") +
                        tr("(command on → 엔진 명령 → command off).", "(command on → engine command → command off)."),
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(viewModel::onBench, { Text("Bench") })
                    AssistChip(viewModel::onTrace, { Text("Trace") })
                    AssistChip({ viewModel.run("print features") }, { Text(tr("특징값", "Features")) })
                    AssistChip({ viewModel.run("dbrefresh") }, { Text(tr("DB 플래그 재전송", "Re-send DB flags")) })
                }
                Text(tr("평가 모드 — 엔진 설정 파일을 다시 읽습니다", "Evaluation mode — reloads the engine's config file"), style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, file) in ToolScripts.evaluationModes) {
                        AssistChip({ viewModel.onReload(file) }, { Text(label) })
                    }
                }
            }
        }

        item {
            ToolCard(tr("콜백", "Callbacks")) {
                Text(
                    tr("엔진이 승/패/무를 알리거나 수를 둘 때 스크립트를 실행합니다.", "Runs a script when the engine reports a win, a loss, a draw, or plays a move."),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = ui.tools.callbacksActive,
                        onClick = {
                            viewModel.run(if (ui.tools.callbacksSuspended) "callback on" else "callback off")
                        },
                        label = { Text(if (ui.tools.callbacksActive) tr("동작 중", "running") else tr("정지", "Stop")) },
                    )
                    OutlinedButton(onClick = { viewModel.onEditCallbacks(true) }) {
                        Text(tr("스크립트 편집", "Edit scripts"))
                    }
                }
            }
        }

        item {
            ToolCard(tr("콘솔", "Console")) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = ui.draft,
                        onValueChange = viewModel::onDraftChange,
                        label = {
                            Text(if (ui.tools.commandMode) tr("엔진으로 그대로 전달", "Pass straight to the engine") else tr("명령 (help)", "Command (help)"))
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = viewModel::onSubmitDraft) { Text(tr("실행", "Run")) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip({ viewModel.run("help") }, { Text(tr("도움말", "Help")) })
                    AssistChip(viewModel::onClearLog, { Text(tr("지우기", "Clear")) })
                    FilterChip(
                        selected = ui.tools.commandMode,
                        onClick = {
                            viewModel.run(if (ui.tools.commandMode) "command off" else "command on")
                        },
                        label = { Text(tr("전달 모드", "Pass-through")) },
                    )
                }
            }
        }

        items(ui.log) { line ->
            Text(
                line.text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (line.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun CallbackDialog(
    config: CallbackConfig,
    onDismiss: () -> Unit,
    onSave: (CallbackConfig) -> Unit,
) {
    var draft by remember { mutableStateOf(config) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("콜백 스크립트", "Callback scripts")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = draft.enabled,
                    onClick = { draft = draft.copy(enabled = !draft.enabled) },
                    label = { Text(if (draft.enabled) tr("사용", "on") else tr("사용 안 함", "off")) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(tr("무승부 횟수", "Draw count"), draft.drawCount, Modifier.weight(1f)) {
                        draft = draft.copy(drawCount = it)
                    }
                    NumberField(tr("최소 수", "Minimum moves"), draft.minPly, Modifier.weight(1f)) {
                        draft = draft.copy(minPly = it)
                    }
                    NumberField(tr("최대 수", "Maximum moves"), draft.maxPly, Modifier.weight(1f)) {
                        draft = draft.copy(maxPly = it)
                    }
                }
                ScriptField(tr("승리 감지", "On win"), draft.onMate) { draft = draft.copy(onMate = it) }
                ScriptField(tr("패배 감지", "On loss"), draft.onMated) { draft = draft.copy(onMated = it) }
                ScriptField(tr("무승부 감지", "On draw"), draft.onDraw) { draft = draft.copy(onDraw = it) }
                ScriptField(tr("착수", "On move"), draft.onMove) { draft = draft.copy(onMove = it) }
                ScriptField(tr("착수 (최소 수 이하)", "On move (below the minimum)"), draft.onMoveMinPly) {
                    draft = draft.copy(onMoveMinPly = it)
                }
                ScriptField(tr("착수 (최대 수 이상)", "On move (above the maximum)"), draft.onMoveMaxPly) {
                    draft = draft.copy(onMoveMaxPly = it)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text(tr("저장", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("취소", "Cancel")) } },
    )
}

@Composable
private fun ScriptField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(tr("예: echo 승리!", "e.g. echo Win!"), style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 1,
        maxLines = 3,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.toIntOrNull()?.let(onChange) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Number,
        ),
        modifier = modifier,
    )
}
