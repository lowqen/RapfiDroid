package dev.gomoku.yixindroid.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.SettingCategory
import dev.gomoku.yixindroid.core.model.SettingEditor
import dev.gomoku.yixindroid.core.model.SettingSpec
import dev.gomoku.yixindroid.core.model.SettingsFile

/**
 * Every persisted desktop setting, generated from [dev.gomoku.yixindroid.core.model.DesktopSettings]
 * so the screen cannot drift from the file layout: each row shows the file and
 * line it occupies, and the `INFO` key it drives when it is an engine parameter.
 */
@OptIn(ExperimentalLayoutApi::class)
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

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("설정 ${ui.visible.size}/${ui.total}", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (ui.connected) "엔진 연결됨 — 변경 즉시 반영" else "엔진 미연결 — 연결 시 반영",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::onQuery,
                label = { Text("설정 검색 (이름·주석·INFO 키)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = ui.category == null,
                    onClick = { viewModel.onCategory(null) },
                    label = { Text("전체") },
                )
                SettingCategory.entries.forEach { category ->
                    FilterChip(
                        selected = ui.category == category,
                        onClick = { viewModel.onCategory(category) },
                        label = { Text(category.label) },
                    )
                }
            }
            // PC와 파일을 그대로 주고받는다: 데스크톱이 쓰는 것과 같은 줄 배치.
            SettingsFile.entries.forEach { file ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${file.fileName} (${file.lineCount}줄)",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        viewModel.prepare(file)
                        importLauncher.launch(arrayOf("*/*"))
                    }) { Text("불러오기") }
                    OutlinedButton(onClick = {
                        viewModel.prepare(file)
                        exportLauncher.launch(file.fileName)
                    }) { Text("내보내기") }
                }
            }
            TextButton(onClick = viewModel::onReset) { Text("PC 기본값으로 되돌리기") }
            ui.message?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = viewModel::dismissMessage) {
                            Icon(Icons.Filled.Close, contentDescription = "닫기")
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        LazyColumn(
            // weight, not fillMaxSize: the header above must keep its height.
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
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
                        append("${spec.file.fileName} ${spec.line}행")
                        spec.engineKey?.let { append("  ·  INFO $it") }
                        if (!isDefault) append("  ·  PC 기본값과 다름")
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
                Button(onClick = { open = false }) { Text("닫기") }
            },
        )
    }
}
