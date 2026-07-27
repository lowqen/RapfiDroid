package dev.gomoku.yixindroid.feature.database

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.model.DbDeleteFilter
import kotlinx.coroutines.delay

/**
 * The desktop's Database menu plus its `db*` console commands, on one screen.
 *
 * Everything here runs **on the server**: the database file sits next to the
 * remote engine, so file operations take engine-side paths and the phone never
 * touches the file. Destructive operations (bulk delete, split) stay locked
 * until the user opts in, and deletes confirm first — the desktop's
 * `show_dbdelall_query` (settings.txt line 35).
 */
@Composable
fun DatabaseScreen(
    modifier: Modifier = Modifier,
    viewModel: DatabaseViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusSection(ui, viewModel)
        PositionSection(ui, viewModel)
        RecordSection(ui, viewModel)
        FileSection(ui, viewModel)
        BulkDeleteSection(ui, viewModel)
        LogSection(ui, viewModel)
    }

    ui.pendingDelete?.let { scope ->
        AlertDialog(
            onDismissRequest = viewModel::onCancelDeleteAll,
            title = { Text("정말 삭제할까요?") },
            text = {
                Text(
                    "${ui.pathLabels()} 아래의 ${scope.title()} 기록을 지웁니다.\n" +
                        "되돌릴 수 없고, PC와 같은 파일을 공유합니다.",
                )
            },
            confirmButton = {
                Button(onClick = viewModel::onConfirmDeleteAll) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onCancelDeleteAll) { Text("취소") }
            },
        )
    }

    ui.notice?.let { text ->
        Snackbar { Text(text) }
        LaunchedEffect(text) {
            delay(3_000)
            viewModel.onNoticeShown()
        }
    }
}

@Composable
private fun StatusSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    Section("상태") {
        SwitchRow(
            title = "데이터베이스 사용",
            subtitle = "settings.txt 32행 · info usedatabase",
            checked = ui.db.enabled,
            onChange = vm::onUseDatabase,
        )
        SwitchRow(
            title = "읽기 전용",
            subtitle = "settings.txt 33행 · 켜면 모든 기록·편집이 차단됩니다",
            checked = ui.db.readOnly,
            onChange = vm::onReadOnly,
        )
        SwitchRow(
            title = "보드 텍스트 표시",
            subtitle = "settings.txt 34행 · 저장값 대신 사용자 라벨을 보드에 표시",
            checked = ui.showBoardText,
            onChange = vm::onShowBoardText,
        )
        SwitchRow(
            title = "주기 자동 저장",
            subtitle = "settings_dev 10·11행 · 엔진이 쉴 때만 저장합니다",
            checked = ui.autoSave,
            onChange = vm::onAutoSave,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("저장 간격", style = MaterialTheme.typography.bodyMedium)
            Stepper(ui.autoSaveMinutes, onChange = vm::onAutoSaveMinutes)
            Text("분", style = MaterialTheme.typography.bodyMedium)
        }
        SwitchRow(
            title = "파괴적 연산 해제",
            subtitle = "일괄 삭제·분할을 허용합니다 (기본 잠금)",
            checked = ui.db.destructiveUnlocked,
            onChange = vm::onUnlockDestructive,
        )
        if (!ui.connected) {
            Text(
                "엔진에 연결되어 있지 않습니다 — 연결 탭에서 접속하세요.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        ui.db.progress?.let { progress ->
            Text(
                (if (progress.saving) "저장 중: " else "불러오는 중: ") + progress.file,
                style = MaterialTheme.typography.labelMedium,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    Section("현재 국면") {
        Text(
            ui.pathLabels(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Text("${ui.valueSummary()} · 자식 ${ui.db.snapshot.cells.size}칸")
        ui.db.snapshot.entry?.let {
            Text(
                it.summary(),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ui.db.snapshot.comment.isNotBlank()) {
            Text(ui.db.snapshot.comment, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = vm::onRefresh, label = { Text("다시 조회") })
            AssistChip(onClick = vm::onQueryValue, label = { Text("값 조회 (dbval)") })
            AssistChip(onClick = vm::onQueryComment, label = { Text("주석 읽기") })
            AssistChip(
                onClick = vm::onSetBestMove,
                enabled = ui.canWrite,
                label = { Text("최선수 표시") },
            )
            AssistChip(
                onClick = vm::onClearBestMove,
                enabled = ui.canWrite,
                label = { Text("표시 해제") },
            )
            AssistChip(
                onClick = vm::onDeleteOne,
                enabled = ui.canWrite,
                label = { Text("이 국면 삭제") },
            )
        }
    }
}

/** `dbedittag` / `dbeditval` / `dbeditdep` — direct record editing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    var value by remember { mutableStateOf("0") }
    var depth by remember { mutableStateOf("0") }
    Section("레코드 편집") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf('W' to "승", 'L' to "패", 'D' to "무").forEach { (tag, label) ->
                AssistChip(
                    onClick = { vm.onEditTag(tag) },
                    enabled = ui.canWrite,
                    label = { Text("$label ($tag)") },
                )
            }
            AssistChip(
                onClick = { vm.onEditTag(null) },
                enabled = ui.canWrite,
                label = { Text("태그 삭제") },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("값") },
                singleLine = true,
                enabled = ui.canWrite,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { value.toIntOrNull()?.let(vm::onEditValue) },
                enabled = ui.canWrite,
            ) { Text("적용") }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = depth,
                onValueChange = { depth = it.filter(Char::isDigit) },
                label = { Text("깊이") },
                singleLine = true,
                enabled = ui.canWrite,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { depth.toIntOrNull()?.let(vm::onEditDepth) },
                enabled = ui.canWrite,
            ) { Text("적용") }
        }
        Text(
            "보드 텍스트(라벨)는 보드 화면에서 점을 길게 눌러 편집합니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    Section("파일 (엔진 서버 경로)") {
        OutlinedTextField(
            value = ui.path,
            onValueChange = vm::onPathChange,
            label = { Text("예: rapfi.db / backup/renju.db") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = vm::onSave, enabled = ui.canWrite, label = { Text("저장") })
            AssistChip(
                onClick = vm::onOpenFile,
                enabled = ui.canWrite,
                label = { Text("열기 / 새로 만들기") },
            )
            AssistChip(onClick = vm::onMerge, enabled = ui.canWrite, label = { Text("병합") })
            AssistChip(
                onClick = vm::onSplit,
                enabled = ui.canDestroy,
                label = { Text("분할" + if (ui.canDestroy) "" else " 🔒") },
            )
            AssistChip(
                onClick = vm::onImportLib,
                enabled = ui.canWrite,
                label = { Text("Lib 가져오기") },
            )
            AssistChip(onClick = vm::onExportLib, label = { Text("Lib 내보내기") })
            AssistChip(onClick = { vm.onExportText(false) }, label = { Text("이 분기 CSV") })
            AssistChip(onClick = { vm.onExportText(true) }, label = { Text("전체 CSV") })
            AssistChip(
                onClick = vm::onImportText,
                enabled = ui.canWrite,
                label = { Text("CSV 가져오기") },
            )
            AssistChip(onClick = vm::onExportPositions, label = { Text("국면 목록 (.pos)") })
            AssistChip(onClick = vm::onCheck, label = { Text("무결성 검사") })
            AssistChip(onClick = vm::onFix, enabled = ui.canWrite, label = { Text("복구") })
        }
        Text(
            "경로는 엔진이 실행되는 서버에서 해석됩니다. PC와 같은 파일을 쓰므로 " +
                "쓰기 작업은 한쪽에서만 하세요.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkDeleteSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    Section("일괄 삭제") {
        Text(
            "현재 국면 아래의 기록을 조건에 맞춰 지웁니다 (PC의 dbdel all).",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DbDeleteFilter.entries.forEach { filter ->
                FilterChip(
                    selected = ui.deleteDraft.filter == filter,
                    onClick = { vm.onDeleteFilter(filter) },
                    label = { Text(filter.title) },
                )
            }
        }
        if (ui.deleteDraft.filter != DbDeleteFilter.ALL) {
            SwitchRow(
                title = "하위 분기까지 (recursive)",
                subtitle = null,
                checked = ui.deleteDraft.recursive,
                onChange = vm::onDeleteRecursive,
            )
        }
        if (ui.deleteDraft.filter == DbDeleteFilter.WL_IN_STEP) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("수순 상한", style = MaterialTheme.typography.bodyMedium)
                Stepper(ui.deleteDraft.step, onChange = vm::onDeleteStep)
                Text("수", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = vm::onRequestDeleteAll,
            enabled = ui.canDestroy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (ui.canDestroy) "${ui.deleteDraft.title()} 삭제"
                else "잠금 상태 — 상태 절에서 해제",
            )
        }
    }
}

@Composable
private fun LogSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    if (ui.db.log.isEmpty()) return
    Section("기록") {
        Text(
            ui.db.log.takeLast(30).joinToString("\n"),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        )
        TextButton(onClick = vm::onClearLog) { Text("지우기") }
    }
}

// ---- small building blocks -------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(value - 1) }) { Text("−") }
        Text("$value", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onChange(value + 1) }) { Text("+") }
    }
}
