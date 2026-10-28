package dev.gomoku.zenyth.feature.joseki

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import dev.gomoku.zenyth.core.designsystem.component.TileShape
import dev.gomoku.zenyth.core.designsystem.component.RoundBackButton
import dev.gomoku.zenyth.core.designsystem.component.PillBadge
import dev.gomoku.zenyth.core.designsystem.component.ItemCard
import dev.gomoku.zenyth.core.designsystem.component.RapfiFilterChip
import dev.gomoku.zenyth.core.designsystem.component.rememberCalmFling
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.gomoku.zenyth.core.designsystem.component.BoardRender
import dev.gomoku.zenyth.core.designsystem.component.CalmCard
import dev.gomoku.zenyth.core.designsystem.component.CardTitle
import dev.gomoku.zenyth.core.designsystem.component.Disclosure
import dev.gomoku.zenyth.core.designsystem.component.GomokuBoard
import dev.gomoku.zenyth.core.designsystem.component.GradeMark
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.component.MiniBoard
import dev.gomoku.zenyth.core.designsystem.component.ReadingWidth
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.joseki.JosekiMark
import dev.gomoku.zenyth.core.joseki.JosekiOps
import dev.gomoku.zenyth.core.joseki.JosekiRole
import dev.gomoku.zenyth.core.joseki.JosekiTree

// ---- list ---------------------------------------------------------------------

/**
 * My joseki: what passes through the board's position first, then every
 * document with its derived class, and the file actions.
 */
@Composable
internal fun MyJosekiList(
    ui: JosekiUiState,
    vm: JosekiViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<JosekiTree?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().widthIn(max = ReadingWidth),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        flingBehavior = rememberCalmFling(),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.onNewJoseki(fromBoard = false) }) { Text(tr("새 정석", "New", "新建")) }
                OutlinedButton(onClick = { vm.onNewJoseki(fromBoard = true) }) { Text(tr("보드에서 만들기", "From the board", "从棋盘新建")) }
            }
        }
        if (ui.hits.isNotEmpty()) {
            item {
                CalmCard {
                    CardTitle(tr("지금 보드 국면", "The board's position", "当前棋盘局面"))
                    ui.hits.forEach { hit ->
                        Text(
                            "${hit.tree.title} · " + tr("${hit.ply}수", "move ${hit.ply}", "第 ${hit.ply} 手"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { vm.onEdit(hit.tree.id, hit.path) }.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
        if (ui.trees.isEmpty()) {
            item { Hint(tr("아직 정석이 없습니다", "No joseki yet", "还没有定式")) }
        }
        items(ui.trees, key = { it.id }) { tree ->
            val cls = JosekiOps.classify(tree)
            // An item card (M4): the line as the tile, the move count as the
            // badge, the kind under the name; the grade and «삭제» below.
            ItemCard(
                title = tree.title,
                onClick = { vm.onEdit(tree.id) },
                leading = {
                    MiniBoard(stones = JosekiOps.mainLine(tree.root), modifier = Modifier.size(64.dp).clip(TileShape), showNumbers = false)
                },
                badge = { PillBadge(tr("${JosekiOps.countNodes(tree.root)}수", "${JosekiOps.countNodes(tree.root)} moves", "${JosekiOps.countNodes(tree.root)} 手")) },
                description = cls.category,
                footer = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        cls.grade?.let { GradeMark(it, withName = false) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { confirmDelete = tree }) { Text(tr("삭제", "Delete", "删除")) }
                    }
                },
            )
        }
        item {
            dev.gomoku.zenyth.core.designsystem.component.DisclosureCard(id = "joseki-files", title = tr("파일", "Files", "文件")) {
                Hint(tr("Zenyth Web 과 같은 JSON · 기보 파일은 초안으로", "Same JSON as Zenyth Web; game files become drafts", "与 Zenyth Web 相同的 JSON；棋谱文件会成为草稿"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImport) { Text(tr("가져오기", "Import", "导入")) }
                    OutlinedButton(onClick = onExport, enabled = ui.trees.isNotEmpty()) { Text(tr("내보내기", "Export", "导出")) }
                }
            }
        }
    }
    confirmDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(tr("«${t.title}» 을 지울까요?", "Delete «${t.title}»?", "要删除 «${t.title}» 吗?")) },
            confirmButton = {
                Button(onClick = {
                    vm.onDelete(t.id)
                    confirmDelete = null
                }) { Text(tr("삭제", "Delete", "删除")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr("취소", "Cancel", "取消")) } },
        )
    }
}

// ---- editor -------------------------------------------------------------------

/**
 * Editing one joseki: tap the board or type `h8 i9 j10` to add moves (an
 * existing move is followed), give the current move a symbol, a role and a
 * markdown comment, reorder or remove branches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JosekiEditor(ui: JosekiUiState, tree: JosekiTree, vm: JosekiViewModel, onExportPsq: () -> Unit) {
    val path = ui.editPath
    val node = JosekiOps.nodeAt(tree.root, path) ?: tree.root
    val moves = JosekiOps.movesTo(tree.root, path)
    val render = BoardRender(stones = moves, lastMove = moves.lastOrNull(), marks = childMarks(node))
    var lineText by remember { mutableStateOf("") }
    var comment by remember(tree.id, path) { mutableStateOf(node.comment) }
    var title by remember(tree.id) { mutableStateOf(tree.title) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cls = JosekiOps.classify(tree)

    val board: @Composable (Modifier) -> Unit = { m -> GomokuBoard(render = render, modifier = m, onTap = vm::onEditTap) }
    val panel: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBackButton({ vm.onBack() }, Modifier.padding(end = 8.dp), label = tr("목록으로", "Back to the list", "返回列表"))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    vm.onTitle(it)
                },
                singleLine = true,
                label = { Text(tr("제목", "Title", "标题")) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(cls.chain.joinToString(" › ").ifEmpty { cls.category }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            cls.grade?.let { GradeMark(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { vm.onEditPath(emptyList()) }, enabled = path.isNotEmpty()) { Icon(Icons.Filled.FirstPage, tr("처음", "Start", "开头")) }
            IconButton(onClick = { vm.onEditPath(path.dropLast(1)) }, enabled = path.isNotEmpty()) { Icon(Icons.Filled.ChevronLeft, tr("뒤로", "Back", "后退")) }
            IconButton(onClick = { vm.onEditPath(path + 0) }, enabled = node.children.isNotEmpty()) { Icon(Icons.Filled.ChevronRight, tr("앞으로", "Forward", "前进")) }
            IconButton(onClick = { vm.onEditPath(JosekiOps.mainLineFrom(tree.root, path)) }, enabled = node.children.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.LastPage, tr("끝으로", "To end", "到末尾"))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = lineText,
                onValueChange = { lineText = it },
                singleLine = true,
                label = { Text(tr("수순 입력 (h8 i9 j10)", "Type a line (h8 i9 j10)", "输入手顺 (h8 i9 j10)")) },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                vm.onAddText(lineText)
                lineText = ""
            }, enabled = lineText.isNotBlank()) { Text(tr("넣기", "Add", "加入")) }
        }
        if (path.isNotEmpty()) {
            CalmCard {
                CardTitle(tr("${path.size}수 ${moves.last().label()}", "Move ${path.size} ${moves.last().label()}", "第 ${path.size} 手 ${moves.last().label()}"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    JosekiMark.entries.forEach { mk ->
                        RapfiFilterChip(
                            selected = node.mark == mk,
                            onClick = { vm.onMark(if (node.mark == mk) null else mk) },
                            label = { Text(mk.symbol) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    JosekiRole.entries.forEach { role ->
                        RapfiFilterChip(
                            selected = node.role == role,
                            onClick = { vm.onRole(if (node.role == role) null else role) },
                            label = { Text(role.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = {
                        comment = it
                        vm.onComment(it)
                    },
                    label = { Text(tr("메모 (마크다운 · 좌표는 링크)", "Note (markdown, coordinates link)", "注释 (Markdown，坐标可点击)")) },
                    minLines = 3,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = vm::onPromote, label = { Text(tr("주 변화로", "Make main line", "设为主变")) })
                    AssistChip(onClick = { vm.onMoveSibling(-1) }, label = { Text(tr("앞으로 옮기기", "Move up", "上移")) })
                    AssistChip(onClick = { vm.onMoveSibling(1) }, label = { Text(tr("뒤로 옮기기", "Move down", "下移")) })
                    AssistChip(onClick = { confirmDelete = true }, label = { Text(tr("이 가지 삭제", "Delete branch", "删除此分支")) })
                }
            }
        } else {
            OutlinedTextField(
                value = comment,
                onValueChange = {
                    comment = it
                    vm.onComment(it)
                },
                label = { Text(tr("정석 소개 (마크다운)", "About this joseki (markdown)", "定式介绍 (Markdown)")) },
                minLines = 2,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (comment.isNotBlank()) {
            Disclosure(id = "joseki-preview", title = tr("메모 미리보기", "Note preview", "注释预览")) {
                MarkdownText(comment, onCoord = { m ->
                    val i = moves.indexOf(m)
                    if (i >= 0) vm.onEditPath(path.take(i + 1))
                })
            }
        }
        Text(tr("수순", "Moves", "手顺"), style = MaterialTheme.typography.titleSmall)
        TreeView(tree.root, path, tree.boardSize, vm::onEditPath)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::onEditToBoard) { Text(tr("보드에 올리기", "Put on the board", "放到棋盘上")) }
            OutlinedButton(onClick = onExportPsq, enabled = path.isNotEmpty()) { Text(tr(".psq 저장", "Save .psq", "保存 .psq")) }
        }
        if (JosekiOps.countNodes(tree.root) > JosekiOps.NODE_WARN) {
            Hint(tr("수가 많습니다. 나누어 두면 가벼워집니다", "This is large; splitting it keeps it light", "手数较多，拆分后会更轻快"), color = MaterialTheme.colorScheme.error)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight && maxWidth >= 600.dp) {
            val side = min(maxHeight - 16.dp, maxWidth * 0.55f)
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                board(Modifier.size(side))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = panel,
                )
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling())) {
                board(Modifier.fillMaxWidth())
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = panel)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("이 가지를 지울까요?", "Delete this branch?", "要删除此分支吗?")) },
            text = { Text(tr("이 수와 그 아래가 모두 지워집니다", "This move and everything after it go", "这一手及其后的所有着法都会被删除")) },
            confirmButton = {
                Button(onClick = {
                    vm.onDeleteBranch()
                    confirmDelete = false
                }) { Text(tr("삭제", "Delete", "删除")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("취소", "Cancel", "取消")) } },
        )
    }
}
