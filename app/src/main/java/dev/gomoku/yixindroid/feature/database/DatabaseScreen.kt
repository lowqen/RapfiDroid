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
import dev.gomoku.yixindroid.core.i18n.tr
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
            title = { Text(tr("정말 삭제할까요?", "Really delete?")) },
            text = {
                Text(
                    tr("${ui.pathLabels()} 아래의 ${scope.title()} 기록을 지웁니다.\n", "Deletes the ${scope.title()} records under ${ui.pathLabels()}.\n") +
                        tr("되돌릴 수 없고, PC와 같은 파일을 공유합니다.", "This cannot be undone, and the file is the one the PC uses."),
                )
            },
            confirmButton = {
                Button(onClick = viewModel::onConfirmDeleteAll) { Text(tr("삭제", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onCancelDeleteAll) { Text(tr("취소", "Cancel")) }
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
    Section(tr("상태", "State")) {
        SwitchRow(
            title = tr("데이터베이스 사용", "Use database"),
            subtitle = tr("settings.txt 32행 · info usedatabase", "settings.txt line 32 · info usedatabase"),
            checked = ui.db.enabled,
            onChange = vm::onUseDatabase,
        )
        SwitchRow(
            title = tr("읽기 전용", "Read-only"),
            subtitle = tr("settings.txt 33행 · 켜면 모든 기록·편집이 차단됩니다", "settings.txt line 33 · on blocks every write and edit"),
            checked = ui.db.readOnly,
            onChange = vm::onReadOnly,
        )
        SwitchRow(
            title = tr("보드 텍스트 표시", "Show board text"),
            subtitle = tr("settings.txt 34행 · 저장값 대신 사용자 라벨을 보드에 표시", "settings.txt line 34 · shows your labels on the board instead of the stored values"),
            checked = ui.showBoardText,
            onChange = vm::onShowBoardText,
        )
        SwitchRow(
            title = tr("주기 자동 저장", "Periodic auto-save"),
            subtitle = tr("settings_dev 10·11행 · 엔진이 쉴 때만 저장합니다", "settings_dev lines 10-11 · saves only while the engine is idle"),
            checked = ui.autoSave,
            onChange = vm::onAutoSave,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("저장 간격", "Interval"), style = MaterialTheme.typography.bodyMedium)
            Stepper(ui.autoSaveMinutes, onChange = vm::onAutoSaveMinutes)
            Text(tr("분", "min"), style = MaterialTheme.typography.bodyMedium)
        }
        SwitchRow(
            title = tr("파괴적 연산 해제", "Unlock destructive operations"),
            subtitle = tr("일괄 삭제·분할을 허용합니다 (기본 잠금)", "Allows bulk deletes and splits (locked by default)"),
            checked = ui.db.destructiveUnlocked,
            onChange = vm::onUnlockDestructive,
        )
        if (!ui.connected) {
            Text(
                tr("엔진에 연결되어 있지 않습니다 — 연결 탭에서 접속하세요.", "Not connected to the engine — connect on the engine tab."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        ui.db.progress?.let { progress ->
            Text(
                (if (progress.saving) tr("저장 중: ", "Saving:") else tr("불러오는 중: ", "Loading:")) + progress.file,
                style = MaterialTheme.typography.labelMedium,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    Section(tr("현재 국면", "This position")) {
        Text(
            ui.pathLabels(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Text(tr("${ui.valueSummary()} · 자식 ${ui.db.snapshot.cells.size}칸", "${ui.valueSummary()} · ${ui.db.snapshot.cells.size} children"))
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
            AssistChip(onClick = vm::onRefresh, label = { Text(tr("다시 조회", "Query again")) })
            AssistChip(onClick = vm::onQueryValue, label = { Text(tr("값 조회 (dbval)", "Read value (dbval)")) })
            AssistChip(onClick = vm::onQueryComment, label = { Text(tr("주석 읽기", "Read comment")) })
            AssistChip(
                onClick = vm::onSetBestMove,
                enabled = ui.canWrite,
                label = { Text(tr("최선수 표시", "Mark best move")) },
            )
            AssistChip(
                onClick = vm::onClearBestMove,
                enabled = ui.canWrite,
                label = { Text(tr("표시 해제", "Clear mark")) },
            )
            AssistChip(
                onClick = vm::onDeleteOne,
                enabled = ui.canWrite,
                label = { Text(tr("이 국면 삭제", "Delete this position")) },
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
    Section(tr("레코드 편집", "Edit record")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf('W' to tr("승", "Win"), 'L' to tr("패", "Loss"), 'D' to tr("무", "Draw")).forEach { (tag, label) ->
                AssistChip(
                    onClick = { vm.onEditTag(tag) },
                    enabled = ui.canWrite,
                    label = { Text("$label ($tag)") },
                )
            }
            AssistChip(
                onClick = { vm.onEditTag(null) },
                enabled = ui.canWrite,
                label = { Text(tr("태그 삭제", "Clear tag")) },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text(tr("값", "Value")) },
                singleLine = true,
                enabled = ui.canWrite,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { value.toIntOrNull()?.let(vm::onEditValue) },
                enabled = ui.canWrite,
            ) { Text(tr("적용", "Apply")) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = depth,
                onValueChange = { depth = it.filter(Char::isDigit) },
                label = { Text(tr("깊이", "Depth")) },
                singleLine = true,
                enabled = ui.canWrite,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { depth.toIntOrNull()?.let(vm::onEditDepth) },
                enabled = ui.canWrite,
            ) { Text(tr("적용", "Apply")) }
        }
        Text(
            tr("보드 텍스트(라벨)는 보드 화면에서 점을 길게 눌러 편집합니다.", "Board text is edited by long-pressing a point on the board screen."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    Section(tr("파일 (엔진 서버 경로)", "File (path on the engine's machine)")) {
        OutlinedTextField(
            value = ui.path,
            onValueChange = vm::onPathChange,
            label = { Text(tr("예: rapfi.db / backup/renju.db", "e.g. rapfi.db / backup/renju.db")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = vm::onSave, enabled = ui.canWrite, label = { Text(tr("저장", "Save")) })
            AssistChip(
                onClick = vm::onOpenFile,
                enabled = ui.canWrite,
                label = { Text(tr("열기 / 새로 만들기", "Open / create")) },
            )
            AssistChip(onClick = vm::onMerge, enabled = ui.canWrite, label = { Text(tr("병합", "Merge")) })
            AssistChip(
                onClick = vm::onSplit,
                enabled = ui.canDestroy,
                label = { Text(tr("분할", "Split") + if (ui.canDestroy) "" else " 🔒") },
            )
            AssistChip(
                onClick = vm::onImportLib,
                enabled = ui.canWrite,
                label = { Text(tr("Lib 가져오기", "Import Lib")) },
            )
            AssistChip(onClick = vm::onExportLib, label = { Text(tr("Lib 내보내기", "Export Lib")) })
            AssistChip(onClick = { vm.onExportText(false) }, label = { Text(tr("이 분기 CSV", "This branch as CSV")) })
            AssistChip(onClick = { vm.onExportText(true) }, label = { Text(tr("전체 CSV", "Everything as CSV")) })
            AssistChip(
                onClick = vm::onImportText,
                enabled = ui.canWrite,
                label = { Text(tr("CSV 가져오기", "Import CSV")) },
            )
            AssistChip(onClick = vm::onExportPositions, label = { Text(tr("국면 목록 (.pos)", "Position list (.pos)")) })
            AssistChip(onClick = vm::onCheck, label = { Text(tr("무결성 검사", "Check")) })
            AssistChip(onClick = vm::onFix, enabled = ui.canWrite, label = { Text(tr("복구", "Repair")) })
        }
        Text(
            tr("경로는 엔진이 실행되는 서버에서 해석됩니다. PC와 같은 파일을 쓰므로 ", "Paths are resolved on the machine the engine runs on. It is the same file the PC uses, so") +
                tr("쓰기 작업은 한쪽에서만 하세요.", "write from one side only."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkDeleteSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    Section(tr("일괄 삭제", "Bulk delete")) {
        Text(
            tr("현재 국면 아래의 기록을 조건에 맞춰 지웁니다 (PC의 dbdel all).", "Deletes records under this position by rule (dbdel all on the PC)."),
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
                title = tr("하위 분기까지 (recursive)", "Including sub-branches (recursive)"),
                subtitle = null,
                checked = ui.deleteDraft.recursive,
                onChange = vm::onDeleteRecursive,
            )
        }
        if (ui.deleteDraft.filter == DbDeleteFilter.WL_IN_STEP) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("수순 상한", "Ply limit"), style = MaterialTheme.typography.bodyMedium)
                Stepper(ui.deleteDraft.step, onChange = vm::onDeleteStep)
                Text(tr("수", "moves"), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = vm::onRequestDeleteAll,
            enabled = ui.canDestroy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (ui.canDestroy) tr("${ui.deleteDraft.title()} 삭제", "Delete ${ui.deleteDraft.title()}")
                else tr("잠금 상태 — 상태 절에서 해제", "Locked — unlock it in the State section"),
            )
        }
    }
}

@Composable
private fun LogSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    if (ui.db.log.isEmpty()) return
    Section(tr("기록", "Log")) {
        Text(
            ui.db.log.takeLast(30).joinToString("\n"),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        )
        TextButton(onClick = vm::onClearLog) { Text(tr("지우기", "Clear")) }
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
