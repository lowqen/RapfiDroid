package dev.gomoku.zenyth.feature.database

import dev.gomoku.zenyth.core.designsystem.component.RapfiFilterChip
import dev.gomoku.zenyth.core.designsystem.component.RapfiSwitch
import dev.gomoku.zenyth.core.designsystem.component.rememberCalmFling
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.zenyth.core.designsystem.component.RapfiCollapsingTopBar
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.designsystem.component.CalmCard
import dev.gomoku.zenyth.core.designsystem.component.CardTitle
import dev.gomoku.zenyth.core.designsystem.component.DisclosureCard
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.zenyth.core.model.DbDeleteFilter
import dev.gomoku.zenyth.feature.board.CommentDialog
import kotlinx.coroutines.delay

/**
 * The desktop's Database menu plus its `db*` console commands, on one screen.
 * Everything that acts on the board's position is here too (RapfiWeb's layout:
 * the board shows the record, the DB tab edits it); the board keeps only the
 * long press that writes a cell label.
 *
 * Everything here runs **on the server**: the database file sits next to the
 * remote engine, so file operations take engine-side paths and the phone never
 * touches the file. Destructive operations (bulk delete, split) stay locked
 * until the user opts in, and deletes confirm first — the desktop's
 * `show_dbdelall_query` (settings.txt line 35).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(
    modifier: Modifier = Modifier,
    viewModel: DatabaseViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var commentDraft by remember { mutableStateOf<String?>(null) }

    val header = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(modifier = modifier.fillMaxWidth().nestedScroll(header.nestedScrollConnection)) {
        RapfiCollapsingTopBar(title = tr("데이터베이스", "Database", "数据库"), scrollBehavior = header)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        if (!ui.connected) {
            Hint(tr("엔진이 켜져야 DB 를 읽고 씁니다", "The engine has to be on to read or write the database", "引擎开启后才能读写数据库"), color = MaterialTheme.colorScheme.error)
        }
        PositionSection(ui, viewModel, onEditComment = { commentDraft = ui.db.snapshot.comment })
        RecordSection(ui, viewModel)
        FileSection(ui, viewModel)
        StatusSection(ui, viewModel)
        BulkDeleteSection(ui, viewModel)
        LogSection(ui, viewModel)
        }
    }

    commentDraft?.let { initial ->
        CommentDialog(
            initial = initial,
            editable = ui.canWrite,
            editFont = ui.commentEditFont,
            onDismiss = { commentDraft = null },
            onConfirm = { text ->
                commentDraft = null
                viewModel.onEditComment(text)
            },
        )
    }

    ui.pendingDelete?.let { scope ->
        AlertDialog(
            onDismissRequest = viewModel::onCancelDeleteAll,
            title = { Text(tr("정말 삭제할까요?", "Really delete?", "确定要删除吗?")) },
            text = {
                Text(
                    tr("${ui.pathLabels()} 아래의 ${scope.title()} 기록을 지웁니다.\n", "Deletes the ${scope.title()} records under ${ui.pathLabels()}.\n", "将删除 ${ui.pathLabels()} 之下的 ${scope.title()} 记录。\n") +
                        tr("되돌릴 수 없고, PC와 같은 파일을 공유합니다.", "This cannot be undone, and the file is the one the PC uses.", "此操作无法撤销，且与 PC 共用同一个文件。"),
                )
            },
            confirmButton = {
                Button(onClick = viewModel::onConfirmDeleteAll) { Text(tr("삭제", "Delete", "删除")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onCancelDeleteAll) { Text(tr("취소", "Cancel", "取消")) }
            },
        )
    }

    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.onNoticeShown()
        }
    }
}

@Composable
private fun StatusSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    FoldSection(
        "db-options",
        tr("옵션", "Options", "选项"),
        summary = (if (ui.db.enabled) tr("사용", "On", "开启") else tr("꺼짐", "Off", "关闭")) + if (ui.db.readOnly) tr(" · 읽기 전용", " · read-only", " · 只读") else "",
    ) {
        SwitchRow(
            title = tr("데이터베이스 사용", "Use database", "使用数据库"),
            subtitle = null,
            checked = ui.db.enabled,
            onChange = vm::onUseDatabase,
        )
        SwitchRow(
            title = tr("읽기 전용", "Read-only", "只读"),
            subtitle = tr("기록 · 편집을 막습니다", "Blocks writes and edits", "禁止写入和编辑"),
            checked = ui.db.readOnly,
            onChange = vm::onReadOnly,
        )
        SwitchRow(
            title = tr("보드 텍스트 표시", "Show board text", "显示棋盘文字"),
            subtitle = tr("판에 값 대신 칸 글자", "Board text instead of values", "棋盘上显示文字而非估值"),
            checked = ui.showBoardText,
            onChange = vm::onShowBoardText,
        )
        SwitchRow(
            title = tr("주기 자동 저장", "Periodic auto-save", "定期自动保存"),
            subtitle = tr("엔진이 쉴 때만", "Only while the engine is idle", "仅在引擎空闲时"),
            checked = ui.autoSave,
            onChange = vm::onAutoSave,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("저장 간격", "Interval", "保存间隔"), style = MaterialTheme.typography.bodyMedium)
            Stepper(ui.autoSaveMinutes, onChange = vm::onAutoSaveMinutes)
            Text(tr("분", "min", "分钟"), style = MaterialTheme.typography.bodyMedium)
        }
        SwitchRow(
            title = tr("파괴적 연산 해제", "Unlock destructive operations", "解锁破坏性操作"),
            subtitle = tr("일괄 삭제 · 분할", "Bulk delete and split", "批量删除 · 拆分"),
            checked = ui.db.destructiveUnlocked,
            onChange = vm::onUnlockDestructive,
        )
        ui.db.progress?.let { progress ->
            Text(
                (if (progress.saving) tr("저장 중: ", "Saving:", "正在保存:") else tr("불러오는 중: ", "Loading:", "正在载入:")) + progress.file,
                style = MaterialTheme.typography.labelMedium,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionSection(ui: DatabaseUiState, vm: DatabaseViewModel, onEditComment: () -> Unit) {
    Section(tr("현재 국면", "This position", "当前局面")) {
        Text(
            ui.pathLabels(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Text(tr("${ui.valueSummary()} · 자식 ${ui.db.snapshot.cells.size}칸", "${ui.valueSummary()} · ${ui.db.snapshot.cells.size} children", "${ui.valueSummary()} · 子节点 ${ui.db.snapshot.cells.size} 个"))
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
            AssistChip(onClick = vm::onRefresh, label = { Text(tr("다시 조회", "Query again", "重新查询")) })
            AssistChip(onClick = vm::onQueryValue, label = { Text(tr("값 조회 (dbval)", "Read value (dbval)", "查询估值 (dbval)")) })
            AssistChip(onClick = vm::onQueryComment, label = { Text(tr("주석 읽기", "Read comment", "读取注释")) })
            AssistChip(onClick = onEditComment, enabled = ui.canWrite, label = { Text(tr("주석 편집", "Edit note", "编辑注释")) })
            AssistChip(
                onClick = vm::onSetBestMove,
                enabled = ui.canWrite,
                label = { Text(tr("최선수 표시", "Mark best move", "标记最佳着法")) },
            )
            AssistChip(
                onClick = vm::onClearBestMove,
                enabled = ui.canWrite,
                label = { Text(tr("표시 해제", "Clear mark", "取消标记")) },
            )
            AssistChip(
                onClick = vm::onDeleteOne,
                enabled = ui.canWrite,
                label = { Text(tr("이 국면 삭제", "Delete this position", "删除此局面")) },
            )
        }
        Hint(tr("보드에서 빈 점을 길게 누르면 그 칸의 글자를 적습니다", "Long-press an empty point on the board to write its label", "在棋盘上长按空点可写入该点的文字"))
    }
}

/** `dbedittag` / `dbeditval` / `dbeditdep` — direct record editing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    var value by remember { mutableStateOf("0") }
    var depth by remember { mutableStateOf("0") }
    Section(tr("레코드 편집", "Edit record", "编辑记录")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf('W' to tr("승", "Win", "胜"), 'L' to tr("패", "Loss", "负"), 'D' to tr("무", "Draw", "和")).forEach { (tag, label) ->
                AssistChip(
                    onClick = { vm.onEditTag(tag) },
                    enabled = ui.canWrite,
                    label = { Text("$label ($tag)") },
                )
            }
            AssistChip(
                onClick = { vm.onEditTag(null) },
                enabled = ui.canWrite,
                label = { Text(tr("태그 삭제", "Clear tag", "清除标记")) },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text(tr("값", "Value", "估值")) },
                singleLine = true,
                enabled = ui.canWrite,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { value.toIntOrNull()?.let(vm::onEditValue) },
                enabled = ui.canWrite,
            ) { Text(tr("적용", "Apply", "应用")) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = depth,
                onValueChange = { depth = it.filter(Char::isDigit) },
                label = { Text(tr("깊이", "Depth", "深度")) },
                singleLine = true,
                enabled = ui.canWrite,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { depth.toIntOrNull()?.let(vm::onEditDepth) },
                enabled = ui.canWrite,
            ) { Text(tr("적용", "Apply", "应用")) }
        }
        Text(
            tr("보드 텍스트(라벨)는 보드 화면에서 점을 길게 눌러 편집합니다.", "Board text is edited by long-pressing a point on the board screen.", "棋盘文字(标签)在棋盘界面长按某点进行编辑。"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    Section(tr("파일 (엔진 서버 경로)", "File (path on the engine's machine)", "文件 (引擎服务器上的路径)")) {
        OutlinedTextField(
            value = ui.path,
            onValueChange = vm::onPathChange,
            label = { Text(tr("예: rapfi.db / backup/renju.db", "e.g. rapfi.db / backup/renju.db", "例: rapfi.db / backup/renju.db")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = vm::onSave, enabled = ui.canWrite, label = { Text(tr("저장", "Save", "保存")) })
            AssistChip(
                onClick = vm::onOpenFile,
                enabled = ui.canWrite,
                label = { Text(tr("열기 / 새로 만들기", "Open / create", "打开 / 新建")) },
            )
            AssistChip(onClick = vm::onMerge, enabled = ui.canWrite, label = { Text(tr("병합", "Merge", "合并")) })
            AssistChip(
                onClick = vm::onSplit,
                enabled = ui.canDestroy,
                label = { Text(tr("분할", "Split", "拆分") + if (ui.canDestroy) "" else " 🔒") },
            )
            AssistChip(
                onClick = vm::onImportLib,
                enabled = ui.canWrite,
                label = { Text(tr("Lib 가져오기", "Import Lib", "导入 Lib")) },
            )
            AssistChip(onClick = vm::onExportLib, label = { Text(tr("Lib 내보내기", "Export Lib", "导出 Lib")) })
            AssistChip(onClick = { vm.onExportText(false) }, label = { Text(tr("이 분기 CSV", "This branch as CSV", "此分支 CSV")) })
            AssistChip(onClick = { vm.onExportText(true) }, label = { Text(tr("전체 CSV", "Everything as CSV", "全部 CSV")) })
            AssistChip(
                onClick = vm::onImportText,
                enabled = ui.canWrite,
                label = { Text(tr("CSV 가져오기", "Import CSV", "导入 CSV")) },
            )
            AssistChip(onClick = vm::onExportPositions, label = { Text(tr("국면 목록 (.pos)", "Position list (.pos)", "局面列表 (.pos)")) })
            AssistChip(onClick = vm::onCheck, label = { Text(tr("무결성 검사", "Check", "完整性检查")) })
            AssistChip(onClick = vm::onFix, enabled = ui.canWrite, label = { Text(tr("복구", "Repair", "修复")) })
        }
        Text(
            tr("경로는 엔진이 실행되는 서버에서 해석됩니다. PC와 같은 파일을 쓰므로 ", "Paths are resolved on the machine the engine runs on. It is the same file the PC uses, so", "路径在运行引擎的服务器上解析。由于与 PC 共用同一个文件，") +
                tr("쓰기 작업은 한쪽에서만 하세요.", "write from one side only.", "请只在一端写入。"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkDeleteSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    FoldSection("db-bulk", tr("일괄 삭제", "Bulk delete", "批量删除")) {
        Text(
            tr("현재 국면 아래의 기록을 조건에 맞춰 지웁니다 (PC의 dbdel all).", "Deletes records under this position by rule (dbdel all on the PC).", "按条件删除当前局面之下的记录 (PC 上的 dbdel all)。"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DbDeleteFilter.entries.forEach { filter ->
                RapfiFilterChip(
                    selected = ui.deleteDraft.filter == filter,
                    onClick = { vm.onDeleteFilter(filter) },
                    label = { Text(filter.title) },
                )
            }
        }
        if (ui.deleteDraft.filter != DbDeleteFilter.ALL) {
            SwitchRow(
                title = tr("하위 분기까지 (recursive)", "Including sub-branches (recursive)", "包括子分支 (recursive)"),
                subtitle = null,
                checked = ui.deleteDraft.recursive,
                onChange = vm::onDeleteRecursive,
            )
        }
        if (ui.deleteDraft.filter == DbDeleteFilter.WL_IN_STEP) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("수순 상한", "Ply limit", "手数上限"), style = MaterialTheme.typography.bodyMedium)
                Stepper(ui.deleteDraft.step, onChange = vm::onDeleteStep)
                Text(tr("수", "moves", "手"), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = vm::onRequestDeleteAll,
            enabled = ui.canDestroy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (ui.canDestroy) tr("${ui.deleteDraft.title()} 삭제", "Delete ${ui.deleteDraft.title()}", "删除 ${ui.deleteDraft.title()}")
                else tr("잠금 상태 — 상태 절에서 해제", "Locked — unlock it in the State section", "已锁定，在状态部分解锁"),
            )
        }
    }
}

@Composable
private fun LogSection(ui: DatabaseUiState, vm: DatabaseViewModel) {
    if (ui.db.log.isEmpty()) return
    FoldSection("db-log", tr("기록", "Log", "日志")) {
        Text(
            ui.db.log.takeLast(30).joinToString("\n"),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        )
        TextButton(onClick = vm::onClearLog) { Text(tr("지우기", "Clear", "清除")) }
    }
}

// ---- small building blocks -------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    CalmCard {
        CardTitle(title)
        content()
    }
}

@Composable
private fun FoldSection(id: String, title: String, summary: String? = null, content: @Composable () -> Unit) {
    DisclosureCard(id = id, title = title, summary = summary) { content() }
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
        RapfiSwitch(checked = checked, onCheckedChange = onChange)
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
