package dev.gomoku.rapfidroid.feature.settings

import dev.gomoku.rapfidroid.core.designsystem.component.rememberCalmFling
import dev.gomoku.rapfidroid.core.designsystem.component.staggerIn
import dev.gomoku.rapfidroid.core.designsystem.component.rememberStagger
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import dev.gomoku.rapfidroid.core.designsystem.component.FlatCard as Card
import androidx.compose.material3.FilterChip
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
import dev.gomoku.rapfidroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiCollapsingTopBar
import dev.gomoku.rapfidroid.core.designsystem.theme.expandFadeIn
import dev.gomoku.rapfidroid.core.designsystem.theme.shrinkFadeOut
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.designsystem.component.ConnectedGroup
import dev.gomoku.rapfidroid.core.designsystem.component.ChoiceChips
import dev.gomoku.rapfidroid.core.designsystem.component.DisclosureCard
import dev.gomoku.rapfidroid.core.designsystem.component.FieldRow
import dev.gomoku.rapfidroid.core.designsystem.component.Hint
import dev.gomoku.rapfidroid.core.designsystem.component.LocalUiPrefs
import dev.gomoku.rapfidroid.core.designsystem.theme.hapticsEnabled
import dev.gomoku.rapfidroid.core.designsystem.theme.themeAmoled
import dev.gomoku.rapfidroid.core.designsystem.theme.themeFollowsSystem
import dev.gomoku.rapfidroid.core.designsystem.theme.themeWallpaperTone
import dev.gomoku.rapfidroid.core.designsystem.theme.wallpaperToneAvailable
import dev.gomoku.rapfidroid.core.model.AppSettings
import dev.gomoku.rapfidroid.core.model.LocalEngineProfile
import dev.gomoku.rapfidroid.core.model.SettingCategory
import dev.gomoku.rapfidroid.core.model.SettingEditor
import dev.gomoku.rapfidroid.core.model.SettingSpec
import dev.gomoku.rapfidroid.core.model.SettingsFile
import dev.gomoku.rapfidroid.feature.data.RifBuildDialog

/**
 * Every persisted desktop setting, generated from [dev.gomoku.rapfidroid.core.model.DesktopSettings]
 * so the screen cannot drift from the file layout: each row shows the file and
 * line it occupies, and the `INFO` key it drives when it is an engine parameter.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val prefs = LocalUiPrefs.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // A PC settings file is imported so the app looks like the PC: its
        // line 27 decides light or dark again, not the phone (decision D4).
        if (uri != null) prefs?.themeFollowsSystem?.set(false)
        viewModel.onImport(uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) viewModel.onImportAppearance(uri) }
    val debugLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> if (uri != null) viewModel.onExportDebugLog(uri) }
    // rememberSaveable, not remember: a rotation is exactly when losing an open
    // dialog or an expanded file list is most annoying.
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showRifBuild by rememberSaveable { mutableStateOf(false) }
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

    val searching = ui.query.isNotBlank()
    val header = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(modifier = modifier.fillMaxSize().nestedScroll(header.nestedScrollConnection)) {
        RapfiCollapsingTopBar(title = tr("설정", "Settings", "设置"), scrollBehavior = header)
        // RapfiWeb's settings: a search, the common card, one advanced switch,
        // then the desktop's entries grouped by what they are for. A row is its
        // name and its control; the why is not spelled out on the screen.
        // The groups rise in one after another the first time the screen opens (P5).
        val stagger = rememberStagger()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            flingBehavior = rememberCalmFling(),
        ) {
            var order = 0
            // An item of the list, numbered in the order the list adds them.
            fun entering(key: String, content: @Composable () -> Unit) {
                val at = order++
                item(key = key) { Box(Modifier.staggerIn(stagger, at)) { content() } }
            }
            entering("search") {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = viewModel::onQuery,
                    placeholder = { Text(tr("설정 검색", "Search settings", "搜索设置")) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!searching) {
                entering("common") {
                    CommonSettings(
                        ui = ui,
                        onDark = { viewModel.onValue("darkMode", if (it) "1" else "0") },
                        onThreads = viewModel::onLocalThreads,
                        onRifBuild = { showRifBuild = true },
                    )
                }
                entering("advanced") {
                    ConnectedGroup {
                        row {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("고급 설정", "Advanced settings", "高级设置"), style = MaterialTheme.typography.titleMedium)
                                    Hint(
                                        if (ui.advanced) tr("엔진 · 대국 · 표시 · DB · 시스템", "Engine, game, display, database, system", "引擎 · 对局 · 显示 · 数据库 · 系统")
                                        else tr("${ui.hidden}개 숨김", "${ui.hidden} hidden", "已隐藏 ${ui.hidden} 项"),
                                    )
                                }
                                Switch(checked = ui.advanced, onCheckedChange = viewModel::onAdvanced)
                            }
                        }
                    }
                }
            }
            if (ui.advanced && !searching) {
                entering("local") {
                    LocalEngineCard(
                        ui = ui,
                        onHash = viewModel::onLocalHash,
                        onDatabase = viewModel::onLocalDatabase,
                        onServerEnabled = viewModel::onServerEnabled,
                    )
                }
            }
            if (searching && ui.visible.isEmpty()) {
                entering("none") { Hint(tr("«${ui.query}» 에 맞는 설정이 없습니다", "No setting matches «${ui.query}»", "没有与 «${ui.query}» 匹配的设置")) }
            }
            val groups = ui.visible.groupBy { it.category }
            SettingCategory.entries.forEach { category ->
                val rows = groups[category] ?: return@forEach
                entering("group-" + category.name) {
                    ConnectedGroup(title = category.label) {
                        rows.forEach { spec ->
                            row {
                                SettingRow(
                                    spec = spec,
                                    state = ui,
                                    onValue = { raw -> viewModel.onValue(spec.id, raw) },
                                )
                            }
                        }
                    }
                }
            }
            if (ui.advanced && !searching) {
                entering("system") {
                    ConnectedGroup(title = tr("데스크톱 설정 파일", "Desktop settings files", "桌面版设置文件")) {
                        SettingsFile.entries.forEach { file ->
                            row {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        file.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(onClick = {
                                        viewModel.prepare(file)
                                        importLauncher.launch(arrayOf("*/*"))
                                    }) { Text(tr("가져오기", "Import", "导入")) }
                                    OutlinedButton(onClick = {
                                        viewModel.prepare(file)
                                        exportLauncher.launch(file.fileName)
                                    }) { Text(tr("내보내기", "Export", "导出")) }
                                }
                            }
                        }
                        row {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("툴바 · 핫키 · 언어 폴더", "Toolbar, hotkeys, language folder", "工具栏 · 快捷键 · 语言文件夹"), style = MaterialTheme.typography.bodyMedium)
                                    Hint(ui.appearanceSource ?: tr("기본값", "Defaults", "默认值"))
                                }
                                OutlinedButton(onClick = { folderLauncher.launch(null) }) { Text(tr("폴더", "Folder", "文件夹")) }
                                if (ui.appearanceSource != null) {
                                    TextButton(onClick = viewModel::onResetAppearance) { Text(tr("기본값", "Defaults", "默认值")) }
                                }
                            }
                        }
                        if (ui.settings.recordDebugLog || ui.debugLogBytes > 0) {
                            row {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        tr("디버그 로그 ${ui.debugLogBytes / 1024}KB", "Debug log ${ui.debugLogBytes / 1024}KB", "调试日志 ${ui.debugLogBytes / 1024}KB"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(onClick = { debugLogLauncher.launch("rapfidroid-debug.log") }, enabled = ui.debugLogBytes > 0) {
                                        Text(tr("내보내기", "Export", "导出"))
                                    }
                                    TextButton(onClick = viewModel::onClearDebugLog, enabled = ui.debugLogBytes > 0) { Text(tr("지우기", "Clear", "清除")) }
                                }
                            }
                        }
                        row { ResetRow(onReset = viewModel::onReset) }
                    }
                }
            }
            if (!searching) {
                entering("about") {
                    ConnectedGroup {
                        row {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("정보", "About", "关于"), style = MaterialTheme.typography.titleMedium)
                                    Hint("RapfiDroid ${dev.gomoku.rapfidroid.BuildConfig.VERSION_NAME}")
                                }
                                TextButton(onClick = { showAbout = true }) { Text(tr("자세히", "Details", "详情")) }
                                TextButton(onClick = { showLicenses = true }) { Text(tr("라이선스", "Licences", "许可证")) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onShowLicenses = { showAbout = false; showLicenses = true },
        )
    }
    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }
    if (showRifBuild) {
        RifBuildDialog(onDismiss = { showRifBuild = false })
    }
}

/**
 * The on-device engine's own settings.
 *
 * Its own card rather than three more rows in the list, because they are not the
 * same kind of thing: every row below comes from a numbered line of
 * `settings.txt`/`settings_dev.txt` and is sent to whichever engine is
 * connected. These three exist only on the phone, are stored outside
 * `AppSettings` so the file codec keeps its 47+20 shape, and **replace** the
 * desktop's threads/hash/database while the on-device engine is the chosen one.
 */
@Composable
internal fun LocalEngineCard(
    ui: SettingsUiState,
    onHash: (Int) -> Unit,
    onDatabase: (Boolean) -> Unit,
    onServerEnabled: (Boolean) -> Unit,
) {
    ConnectedGroup(title = tr("기기 내 엔진", "On-device engine", "本机引擎")) {
        row {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("해시 크기 (MB)", "Hash size (MB)", "哈希表大小 (MB)"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                CommitField(
                    value = ui.localProfile.hashMb.toString(),
                    onCommit = { raw -> raw.toIntOrNull()?.let(onHash) },
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.width(96.dp),
                    supporting = "${LocalEngineProfile.MIN_HASH_MB}~${LocalEngineProfile.MAX_HASH_MB}",
                )
            }
            // The phone's own ceiling after a kill for memory (T3) — said here, where the
            // number it overrides is, and lifted by picking a size again.
            val cap = ui.memoryCapMb
            if (cap != null && cap < ui.localProfile.hashMb) {
                Hint(
                    tr(
                        "메모리가 모자라 엔진이 종료된 적이 있어 ${cap} MB 로 줄여 씁니다 — 해시 크기를 다시 고르면 풀립니다",
                        "The engine was once killed for lack of memory, so it uses $cap MB — pick a hash size again to lift this",
                        "引擎曾因内存不足被终止，现使用 $cap MB — 重新选择哈希表大小即可解除",
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        row {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("기기 데이터베이스 사용", "Use the device database", "使用本机数据库"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = ui.localProfile.useDatabase, onCheckedChange = onDatabase)
            }
            // yixindb is read whole into the engine's memory, on top of the table (T3).
            val dbBytes = ui.localDbBytes
            if (ui.localProfile.useDatabase && dbBytes != null) {
                val mb = (dbBytes + (1 shl 20) - 1) shr 20
                if (dbBytes >= ui.localDbWarnBytes) {
                    Hint(
                        tr(
                            "rapfi.db ${mb} MB — 엔진이 이만큼 메모리를 더 씁니다. 메모리가 모자라면 DB 탭에서 정리하세요",
                            "rapfi.db $mb MB — the engine holds all of it in memory. If memory runs short, tidy it in the DB tab",
                            "rapfi.db $mb MB — 引擎会把它全部放进内存。内存不足时请在数据库页整理",
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Hint(tr("rapfi.db ${mb} MB", "rapfi.db $mb MB", "rapfi.db $mb MB"))
                }
            }
        }
        row {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("서버 엔진 사용", "Use a server engine", "使用服务器引擎"), style = MaterialTheme.typography.bodyMedium)
                    Hint(tr("직접 운영하는 Rapfi 서버 (Tailscale)", "A Rapfi server you run (Tailscale)", "你自己运行的 Rapfi 服务器 (Tailscale)"))
                }
                Switch(checked = ui.serverEnabled, onCheckedChange = onServerEnabled)
            }
            if (!ui.localMode) Hint(tr("지금은 서버 엔진을 씁니다", "The server engine is in use", "当前正在使用服务器引擎"))
        }
    }
}

@Composable
internal fun SettingRow(
    spec: SettingSpec,
    state: SettingsUiState,
    onValue: (String) -> Unit,
) {
    val value = spec.read(state.settings)
    val editor = state.editorFor(spec)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spec.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
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
        // What the phone actually uses instead, when it differs: without this
        // the screen would read 8192 MB while the engine ran on 128.
        state.overrideFor(spec)?.let { Hint(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun ResetRow(onReset: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    if (!asking) {
        TextButton(onClick = { asking = true }) { Text(tr("PC 기본값으로 되돌리기", "Back to the PC defaults", "恢复 PC 默认值")) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onReset()
                asking = false
            }) { Text(tr("되돌리기", "Reset", "恢复")) }
            TextButton(onClick = { asking = false }) { Text(tr("취소", "Cancel", "取消")) }
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
                Button(onClick = { open = false }) { Text(tr("닫기", "Close", "关闭")) }
            },
        )
    }
}


/**
 * 자주 쓰는 설정 — language, threads, theme, starting the engine with the app,
 * the curriculum and the RenjuNet game data (RapfiWeb's basic group), one row
 * each in a [ConnectedGroup] (UI overhaul P4).
 *
 * The theme is three rows, not one: «테마» picks light or dark and writes
 * settings.txt line 27 (or hands it to the phone, D4); the black background
 * and the wallpaper tone are the app's own and change only the look (D1).
 */
@Composable
internal fun CommonSettings(
    ui: SettingsUiState,
    onDark: (Boolean) -> Unit,
    onThreads: (Int) -> Unit,
    onRifBuild: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = LocalUiPrefs.current
    val locales = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(android.app.LocaleManager::class.java)
    } else {
        null
    }
    ConnectedGroup(title = tr("자주 쓰는 설정", "Common", "常用设置")) {
        if (locales != null) {
            row {
                val current = locales.applicationLocales.toLanguageTags().substringBefore('-').substringBefore(',')
                FieldRow(tr("언어", "Language", "语言")) {
                    ChoiceChips(
                        options = listOf(
                            "" to tr("기기 설정", "System", "跟随系统"),
                            "ko" to "한국어",
                            "en" to "English",
                            "zh" to "中文",
                        ),
                        selected = current,
                        // Chinese is Simplified, so ask for the mainland locale.
                        onSelect = { tag ->
                            locales.applicationLocales =
                                android.os.LocaleList.forLanguageTags(if (tag == "zh") "zh-CN" else tag)
                        },
                    )
                }
            }
        }
        row {
            FieldRow(tr("스레드 수", "Threads", "线程数")) {
                ChoiceChips(
                    options = ui.threadChoices.map { it to "$it" },
                    selected = ui.localProfile.threads,
                    onSelect = onThreads,
                )
            }
        }
        if (prefs != null) {
            row {
                val follow by prefs.themeFollowsSystem.state.collectAsStateWithLifecycle()
                FieldRow(tr("테마", "Theme", "主题")) {
                    ChoiceChips(
                        options = listOf(
                            0 to tr("기기 설정", "System", "跟随系统"),
                            1 to tr("라이트", "Light", "浅色"),
                            2 to tr("다크", "Dark", "深色"),
                        ),
                        selected = when {
                            follow -> 0
                            ui.settings.darkMode -> 2
                            else -> 1
                        },
                        onSelect = { choice ->
                            prefs.themeFollowsSystem.set(choice == 0)
                            if (choice != 0) onDark(choice == 2)
                        },
                    )
                }
            }
            row {
                // Only the dark theme changes, and the row says so rather than being
                // greyed out: someone on a light phone who turns it on gets it the
                // moment the phone goes dark.
                val amoled by prefs.themeAmoled.state.collectAsStateWithLifecycle()
                SwitchRow(
                    title = tr("검은 배경", "Black background", "纯黑背景"),
                    note = tr("다크 테마에서 OLED 화면용", "Dark theme, for OLED screens", "深色主题下，适合 OLED 屏幕"),
                    checked = amoled,
                    onChange = { prefs.themeAmoled.set(it) },
                )
            }
            if (wallpaperToneAvailable) {
                row {
                    val tone by prefs.themeWallpaperTone.state.collectAsStateWithLifecycle()
                    SwitchRow(
                        title = tr("배경화면 색조", "Wallpaper tone", "壁纸色调"),
                        note = tr("회색 바탕만 배경화면 쪽으로. 판과 값의 색은 그대로", "Only the greys lean toward it. Board and value colours stay", "仅灰色底色偏向壁纸，棋盘与数值颜色不变"),
                        checked = tone,
                        onChange = { prefs.themeWallpaperTone.set(it) },
                    )
                }
            }
            row {
                val haptics by prefs.hapticsEnabled.state.collectAsStateWithLifecycle()
                SwitchRow(
                    title = tr("진동", "Vibration", "振动"),
                    note = tr("돌을 놓을 때 · 둘 수 없을 때 · 승부가 났을 때", "When a stone lands, a move is refused, a game ends", "落子 · 无法落子 · 分出胜负时"),
                    checked = haptics,
                    onChange = { prefs.hapticsEnabled.set(it) },
                )
            }
            row {
                val auto by prefs.autoStartEngine.state.collectAsStateWithLifecycle()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("앱을 열면 엔진 시작", "Start the engine with the app", "打开应用时启动引擎"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { prefs.autoStartEngine.set(it) })
                }
            }
            row {
                val curriculum by prefs.curriculumAutoUpdate.state.collectAsStateWithLifecycle()
                SwitchRow(
                    title = tr("정석 커리큘럼 자동 업데이트", "Update the joseki curriculum", "自动更新定式课程"),
                    note = tr("rapfiweb.pages.dev 에서 받습니다. 보내는 정보는 없습니다", "From rapfiweb.pages.dev. Nothing is sent", "从 rapfiweb.pages.dev 获取，不发送任何信息"),
                    checked = curriculum,
                    onChange = { prefs.curriculumAutoUpdate.set(it) },
                )
            }
        } else {
            row {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("다크 모드", "Dark mode", "深色模式"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = ui.settings.darkMode, onCheckedChange = onDark)
                }
            }
        }
        row {
            OutlinedButton(onClick = onRifBuild, modifier = Modifier.fillMaxWidth()) {
                Text(tr("대국 데이터 추가 (RenjuNet)", "Add game data (RenjuNet)", "添加对局数据 (RenjuNet)"))
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
