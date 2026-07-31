package dev.gomoku.yixindroid.feature.tools

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
import dev.gomoku.yixindroid.core.model.CallbackConfig
import dev.gomoku.yixindroid.core.model.ToolScripts
import dev.gomoku.yixindroid.core.model.ToolsState
import dev.gomoku.yixindroid.feature.connection.ConnectionScreen

/**
 * The engine tab: 「연결」 keeps the socket screen, 「도구」 adds the operations
 * menu. Two tabs rather than an eighth bottom-nav entry, the same way the
 * explorer pairs its two research tools.
 */
@Composable
fun EngineScreen(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = remember { listOf("연결", "도구") }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, style = MaterialTheme.typography.titleSmall) },
                )
            }
        }
        when (tab) {
            0 -> ConnectionScreen()
            else -> ToolsScreen()
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
            ToolCard("탐색 도구") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip({ viewModel.run("searchdefend") }, { Text("모든 방어수") })
                    AssistChip({ viewModel.run("nbest") }, { Text("멀티 PV") })
                    AssistChip({ viewModel.run("balance1") }, { Text("균형점 1수") })
                    AssistChip({ viewModel.run("balance2") }, { Text("균형점 2수") })
                    AssistChip({ viewModel.run("send board") }, { Text("국면 전송") })
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = ui.startDepth,
                        onValueChange = viewModel::onStartDepthChange,
                        label = { Text("시작 깊이") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.width(120.dp),
                    )
                    OutlinedButton(onClick = viewModel::onSearchFrom) { Text("이 깊이부터") }
                }
            }
        }

        item {
            ToolCard("해시 (전치표)") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip({ viewModel.run("hash clear") }, { Text("지우기") })
                    AssistChip({ viewModel.run("hash usage") }, { Text("사용량") })
                    FilterChip(
                        selected = ui.settings.hashAutoClear,
                        onClick = viewModel::onToggleHashAutoClear,
                        label = { Text("탐색 전 자동 지우기") },
                    )
                }
                OutlinedTextField(
                    value = ui.hashPath,
                    onValueChange = viewModel::onHashPathChange,
                    label = { Text("서버 경로 (엔진이 읽고 씁니다)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::onHashDump) { Text("저장") }
                    OutlinedButton(onClick = viewModel::onHashLoad) { Text("복원") }
                }
            }
        }

        item {
            ToolCard("차단 (${ui.blockedCount}점)") {
                Text(
                    "차단한 점은 엔진이 후보에서 제외합니다. 보드에 ✕ 로 표시됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip({ viewModel.run("block reset") }, { Text("차단 해제") })
                    AssistChip({ viewModel.run("blockpath reset") }, { Text("경로 차단 해제") })
                    FilterChip(
                        selected = ui.settings.blockAutoReset,
                        onClick = viewModel::onToggleBlockAutoReset,
                        label = { Text("착수 후 자동 해제") },
                    )
                    FilterChip(
                        selected = ui.settings.blockPathAutoReset,
                        onClick = viewModel::onToggleBlockPathAutoReset,
                        label = { Text("경로 자동 해제") },
                    )
                }
                Text(
                    "점 지정은 콘솔로: block h8i8 · block undo h8 · blockpath h8h7 · " +
                        "block compare h8 (이 점만 남기고 전부 차단)",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        item {
            ToolCard("국면 슬롯") {
                Text(
                    "현재 국면을 10칸에 담아 두고 되돌립니다 (pushpos / poppos).",
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
                    OutlinedButton(onClick = { viewModel.run("getpos") }) { Text("국면 문자열") }
                }
                Text(
                    "빈 칸을 누르면 저장, ↺ 는 되돌리기. 저장된 칸: " +
                        (ui.filledSlots.joinToString(", ").ifEmpty { "없음" }),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        item {
            ToolCard("엔진 유지보수") {
                Text(
                    "데스크톱 툴바와 같은 스크립트를 그대로 보냅니다 " +
                        "(command on → 엔진 명령 → command off).",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(viewModel::onBench, { Text("Bench") })
                    AssistChip(viewModel::onTrace, { Text("Trace") })
                    AssistChip({ viewModel.run("print features") }, { Text("특징값") })
                    AssistChip({ viewModel.run("dbrefresh") }, { Text("DB 플래그 재전송") })
                }
                Text("평가 모드 — 엔진 설정 파일을 다시 읽습니다", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, file) in ToolScripts.evaluationModes) {
                        AssistChip({ viewModel.onReload(file) }, { Text(label) })
                    }
                }
            }
        }

        item {
            ToolCard("콜백") {
                Text(
                    "엔진이 승/패/무를 알리거나 수를 둘 때 스크립트를 실행합니다.",
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
                        label = { Text(if (ui.tools.callbacksActive) "동작 중" else "정지") },
                    )
                    OutlinedButton(onClick = { viewModel.onEditCallbacks(true) }) {
                        Text("스크립트 편집")
                    }
                }
            }
        }

        item {
            ToolCard("콘솔") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = ui.draft,
                        onValueChange = viewModel::onDraftChange,
                        label = {
                            Text(if (ui.tools.commandMode) "엔진으로 그대로 전달" else "명령 (help)")
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = viewModel::onSubmitDraft) { Text("실행") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip({ viewModel.run("help") }, { Text("도움말") })
                    AssistChip(viewModel::onClearLog, { Text("지우기") })
                    FilterChip(
                        selected = ui.tools.commandMode,
                        onClick = {
                            viewModel.run(if (ui.tools.commandMode) "command off" else "command on")
                        },
                        label = { Text("전달 모드") },
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
        title = { Text("콜백 스크립트") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = draft.enabled,
                    onClick = { draft = draft.copy(enabled = !draft.enabled) },
                    label = { Text(if (draft.enabled) "사용" else "사용 안 함") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("무승부 횟수", draft.drawCount, Modifier.weight(1f)) {
                        draft = draft.copy(drawCount = it)
                    }
                    NumberField("최소 수", draft.minPly, Modifier.weight(1f)) {
                        draft = draft.copy(minPly = it)
                    }
                    NumberField("최대 수", draft.maxPly, Modifier.weight(1f)) {
                        draft = draft.copy(maxPly = it)
                    }
                }
                ScriptField("승리 감지", draft.onMate) { draft = draft.copy(onMate = it) }
                ScriptField("패배 감지", draft.onMated) { draft = draft.copy(onMated = it) }
                ScriptField("무승부 감지", draft.onDraw) { draft = draft.copy(onDraw = it) }
                ScriptField("착수", draft.onMove) { draft = draft.copy(onMove = it) }
                ScriptField("착수 (최소 수 이하)", draft.onMoveMinPly) {
                    draft = draft.copy(onMoveMinPly = it)
                }
                ScriptField("착수 (최대 수 이상)", draft.onMoveMaxPly) {
                    draft = draft.copy(onMoveMaxPly = it)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun ScriptField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("예: echo 승리!", style = MaterialTheme.typography.labelSmall) },
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
