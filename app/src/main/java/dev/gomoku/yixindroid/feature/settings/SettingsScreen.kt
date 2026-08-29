package dev.gomoku.yixindroid.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.yixindroid.core.designsystem.component.YixinTopBar
import dev.gomoku.yixindroid.core.designsystem.theme.expandFadeIn
import dev.gomoku.yixindroid.core.designsystem.theme.shrinkFadeOut
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.SettingCategory
import dev.gomoku.yixindroid.core.model.SettingEditor
import dev.gomoku.yixindroid.core.model.SettingSpec
import dev.gomoku.yixindroid.core.model.SettingsFile
import dev.gomoku.yixindroid.feature.bundle.DataImportCard

/**
 * Every persisted desktop setting, generated from [dev.gomoku.yixindroid.core.model.DesktopSettings]
 * so the screen cannot drift from the file layout: each row shows the file and
 * line it occupies, and the `INFO` key it drives when it is an engine parameter.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val defaults = remember { AppSettings() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> viewModel.onExport(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onImport(uri) }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) viewModel.onImportAppearance(uri) }
    val debugLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> if (uri != null) viewModel.onExportDebugLog(uri) }
    // rememberSaveable, not remember: a rotation is exactly when losing an open
    // dialog or an expanded file list is most annoying.
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showFiles by rememberSaveable { mutableStateOf(false) }

    // One snackbar for the whole app (see [LocalSnackbarHostState]). This screen
    // was the last holdout with an inline banner of its own, which said the same
    // thing in a different place from every other screen — and did it from
    // inside the header, where an extra row costs the list below its height.
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // The screen's name and the one action that is not a setting. Both used
        // to live in the body: the title as a bare line of text, "정보 · 도움말"
        // as a text button between two unrelated rows.
        YixinTopBar(
            title = tr("설정", "Settings"),
            subtitle = tr("${ui.visible.size}/${ui.total} · ", "${ui.visible.size}/${ui.total} · ") +
                if (ui.connected) tr("엔진 연결됨 — 변경 즉시 반영", "engine connected, changes apply at once")
                else tr("엔진 미연결 — 연결 시 반영", "engine not connected, changes apply when it is"),
            actions = {
                IconButton(onClick = { showAbout = true }) {
                    Icon(Icons.Filled.Info, contentDescription = tr("정보 · 도움말", "About · help"))
                }
            },
        )
        // Pinned: the search field and the category row. Nothing else.
        //
        // A `Column` measures its unweighted children before the weighted one
        // and hands that one whatever is left, and it does not clip — so a
        // header taller than the screen both starves the list of height *and*
        // draws over the space it should have had. This block used to carry the
        // advanced switch, the import card, the per-file rows, the debug-log row
        // and the reset button too: around 650dp of them, which on a phone in
        // landscape measured the list at 0dp and painted the buttons across it.
        // The settings were unreachable, and it looked like the header was
        // covering them because it was.
        //
        // What stays is bounded — a text field and a single line of chips, about
        // 120dp — so the list always has room. The chips scroll sideways rather
        // than wrapping, which is what kept this block from having a fixed
        // height at all: eight labels wrap to two rows in English and three in
        // Korean at a large font scale.
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::onQuery,
                label = { Text(tr("설정 검색 (이름·주석·INFO 키)", "Search settings (name, comment, INFO key)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = ui.category == null,
                        onClick = { viewModel.onCategory(null) },
                        label = { Text(tr("전체", "All")) },
                    )
                }
                items(SettingCategory.entries) { category ->
                    FilterChip(
                        selected = ui.category == category,
                        onClick = { viewModel.onCategory(category) },
                        label = { Text(category.label) },
                    )
                }
            }
        }
        HorizontalDivider()
        LazyColumn(
            // weight, not fillMaxSize: the pinned block above keeps its height.
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // The tools scroll with the settings instead of sitting on top of
            // them. One item rather than several, so the 8dp rhythm between them
            // stays a single `spacedBy` and does not become a padding on each.
            item(key = "tools") {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 67 desktop settings do not fit one phone list. The everyday
                    // ones show by default; the rest are one switch away and
                    // always findable by search.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("고급 설정", "Advanced"), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (ui.advanced) tr("데스크톱의 67개 항목을 모두 표시합니다", "Shows all 67 desktop entries")
                                else tr("자주 쓰지 않는 ${ui.hidden}개를 숨겼습니다 (검색하면 나옵니다)", "${ui.hidden} rarely-used entries are hidden (search finds them)"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = ui.advanced, onCheckedChange = viewModel::onAdvanced)
                    }
                    // 반입은 여기 하나로 끝난다 — 아래 개별 버튼은 파일 하나만 갈아 끼우거나
                    // PC 로 되돌려 보낼 때를 위한 것이다.
                    DataImportCard()
                    TextButton(onClick = { showFiles = !showFiles }) {
                        Text(
                            if (showFiles) tr("개별 파일 접기", "Hide individual files")
                            else tr("개별 파일로 넣기 · 내보내기", "Individual files · export"),
                        )
                    }
                    AnimatedVisibility(showFiles, enter = expandFadeIn, exit = shrinkFadeOut) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // PC와 파일을 그대로 주고받는다: 데스크톱이 쓰는 것과 같은 줄 배치.
                            SettingsFile.entries.forEach { file ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        tr("${file.fileName} (${file.lineCount}줄)", "${file.fileName} (${file.lineCount} lines)"),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(onClick = {
                                        viewModel.prepare(file)
                                        importLauncher.launch(arrayOf("*/*"))
                                    }) { Text(tr("불러오기", "Load")) }
                                    OutlinedButton(onClick = {
                                        viewModel.prepare(file)
                                        exportLauncher.launch(file.fileName)
                                    }) { Text(tr("내보내기", "Export")) }
                                }
                            }
                            // The desktop's `function/` and `language/` folders —
                            // user-defined toolbar buttons and hotkeys, and the
                            // labels their numeric ids point at. The card above
                            // reads these too; this row stays for the one case it
                            // cannot cover — going back to the built-in defaults.
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        tr("툴바 · 핫키 · 언어", "Toolbar · hotkeys · language"),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Text(
                                        ui.appearanceSource?.let { tr("$it 에서 불러옴", "from $it") }
                                            ?: tr("데스크톱 기본값 (Yixin 폴더를 선택하면 내 설정을 씁니다)", "Desktop defaults (pick a Yixin folder to use your own)"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = { folderLauncher.launch(null) }) { Text(tr("폴더 선택", "Pick a folder")) }
                                if (ui.appearanceSource != null) {
                                    TextButton(onClick = viewModel::onResetAppearance) { Text(tr("기본값", "Defaults")) }
                                }
                            }
                        }
                    }
                    // settings.txt line 36 stores the flag; this is what makes it
                    // useful — the transcript is only worth recording if it can be
                    // handed over.
                    if (ui.settings.recordDebugLog || ui.debugLogBytes > 0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                tr("디버그 로그 ${ui.debugLogBytes / 1024}KB", "Debug log, ${ui.debugLogBytes / 1024}KB"),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = { debugLogLauncher.launch("yixindroid-debug.log") },
                                enabled = ui.debugLogBytes > 0,
                            ) { Text(tr("내보내기", "Export")) }
                            TextButton(
                                onClick = viewModel::onClearDebugLog,
                                enabled = ui.debugLogBytes > 0,
                            ) { Text(tr("지우기", "Clear")) }
                        }
                    }
                    TextButton(onClick = viewModel::onReset) { Text(tr("PC 기본값으로 되돌리기", "Back to the PC defaults")) }
                }
                // Where the tools end and the 67 settings begin.
                HorizontalDivider()
            }
            items(ui.visible, key = { it.file.name + it.line }) { spec ->
                SettingRow(
                    spec = spec,
                    state = ui,
                    isDefault = spec.read(ui.settings) == spec.read(defaults),
                    onValue = { raw -> viewModel.onValue(spec.id, raw) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun SettingRow(
    spec: SettingSpec,
    state: SettingsUiState,
    isDefault: Boolean,
    onValue: (String) -> Unit,
) {
    val value = spec.read(state.settings)
    val editor = state.editorFor(spec)
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(spec.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    buildString {
                        append(tr("${spec.file.fileName} ${spec.line}행", "${spec.file.fileName} line ${spec.line}"))
                        spec.engineKey?.let { append("  ·  INFO $it") }
                        if (!isDefault) append(tr("  ·  PC 기본값과 다름", "  ·  differs from the PC default"))
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(contentAlignment = Alignment.CenterEnd) {
                when (editor) {
                    is SettingEditor.Toggle -> Switch(
                        checked = value != "0",
                        onCheckedChange = { onValue(if (it) "1" else "0") },
                    )
                    is SettingEditor.Number -> NumberEditor(value, editor, onValue)
                    is SettingEditor.Choice -> ChoiceEditor(spec.label, value, editor, onValue)
                    is SettingEditor.Text -> Unit // full-width field below
                    is SettingEditor.Fixed -> Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (editor is SettingEditor.Text) {
            CommitField(
                value = value,
                onCommit = onValue,
                keyboard = KeyboardType.Text,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        spec.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun NumberEditor(
    value: String,
    editor: SettingEditor.Number,
    onValue: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CommitField(
            value = value,
            onCommit = onValue,
            keyboard = KeyboardType.Number,
            modifier = Modifier.width(if (editor.max > 100_000) 132.dp else 96.dp),
            supporting = "${editor.min}~${editor.max}",
        )
        if (editor.unit.isNotEmpty()) {
            Text(
                " ${editor.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A text field that commits on IME action or focus loss, never mid-typing: the
 * spec clamps on write, so committing every keystroke would fight the user
 * (typing "8" into a field with min 1000 would snap to 1000).
 */
@Composable
private fun CommitField(
    value: String,
    onCommit: (String) -> Unit,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        supportingText = supporting?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier.onFocusChanged { focus ->
            if (!focus.isFocused && text != value) onCommit(text)
        },
    )
}

@Composable
private fun ChoiceEditor(
    title: String,
    value: String,
    editor: SettingEditor.Choice,
    onValue: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = editor.options.firstOrNull { it.value.toString() == value }
    OutlinedButton(onClick = { open = true }) {
        Text(current?.label ?: value, style = MaterialTheme.typography.bodyMedium)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                LazyColumn {
                    items(editor.options) { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option.value.toString() == value,
                                onClick = {
                                    onValue(option.value.toString())
                                    open = false
                                },
                            )
                            Text(option.label)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { open = false }) { Text(tr("닫기", "Close")) }
            },
        )
    }
}
