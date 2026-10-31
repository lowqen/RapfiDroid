package dev.gomoku.zenyth.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import dev.gomoku.zenyth.core.designsystem.component.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.zenyth.BuildConfig
import dev.gomoku.zenyth.core.designsystem.component.ChoiceChips
import dev.gomoku.zenyth.core.designsystem.component.ConnectedGroup
import dev.gomoku.zenyth.core.designsystem.component.FieldRow
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.component.ZenythSwitch
import dev.gomoku.zenyth.core.designsystem.theme.RowTitleStyle
import dev.gomoku.zenyth.core.designsystem.theme.hapticsEnabled
import dev.gomoku.zenyth.core.designsystem.theme.themeAmoled
import dev.gomoku.zenyth.core.designsystem.theme.themeFollowsSystem
import dev.gomoku.zenyth.core.designsystem.theme.themeWallpaperTone
import dev.gomoku.zenyth.core.designsystem.theme.wallpaperToneAvailable
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.SettingCategory
import dev.gomoku.zenyth.core.model.SettingsFile
import dev.gomoku.zenyth.data.prefs.UiPrefs

/**
 * The settings as Mornye lays its own out (UI overhaul 2, M3): one list of
 * pages, each a row with an icon, a name, what is inside and a chevron, and
 * the settings themselves one level down. The desktop's 67 entries are one
 * more page, offered in expert mode only.
 */
internal enum class SettingsPage {
    Root, Design, General, Engine, Data, Desktop;

    val title: String
        get() = when (this) {
            Root -> tr("설정", "Settings", "设置")
            Design -> tr("디자인", "Design", "外观")
            General -> tr("일반", "General", "常规")
            Engine -> tr("엔진", "Engine", "引擎")
            Data -> tr("데이터", "Data", "数据")
            Desktop -> tr("PC 설정", "PC settings", "PC 设置")
        }

    val icon: ImageVector
        get() = when (this) {
            Root -> Icons.Outlined.Tune
            Design -> Icons.Outlined.Palette
            General -> Icons.Outlined.Tune
            Engine -> Icons.Outlined.Memory
            Data -> Icons.Outlined.Storage
            Desktop -> Icons.Outlined.Computer
        }
}
/**
 * What a page can ask of the screen. Plain callbacks rather than the view
 * model, so the pages can be drawn for the screenshot tests; every one
 * defaults to nothing for the same reason.
 */
internal class SettingsActions(
    val onOpen: (SettingsPage) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onValue: (id: String, raw: String) -> Unit = { _, _ -> },
    val onDark: (Boolean) -> Unit = {},
    val onPro: (Boolean) -> Unit = {},
    val onThreads: (Int) -> Unit = {},
    val onHash: (Int) -> Unit = {},
    val onDatabase: (Boolean) -> Unit = {},
    val onServerEnabled: (Boolean) -> Unit = {},
    val onImport: (SettingsFile) -> Unit = {},
    val onExport: (SettingsFile) -> Unit = {},
    val onFolder: () -> Unit = {},
    val onResetAppearance: () -> Unit = {},
    val onExportDebugLog: () -> Unit = {},
    val onClearDebugLog: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onRifBuild: () -> Unit = {},
    val onAbout: () -> Unit = {},
    val onLicenses: () -> Unit = {},
    /** The tour of the four tabs; null where nothing can show it (a screenshot). */
    val onTour: (() -> Unit)? = null,
)
/** One block of a page: the screen lays them in a list, a test in a column. */
internal class SettingsSection(val key: String, val content: @Composable () -> Unit)
private class SectionsBuilder {
    val sections = mutableListOf<SettingsSection>()
    fun section(key: String, content: @Composable () -> Unit) {
        sections += SettingsSection(key, content)
    }
}
/**
 * The blocks of [page]. [pro] is «고급 사용자 모드», [prefs] the app's own
 * settings (null only in a preview without them).
 */
internal fun settingsSections(
    page: SettingsPage,
    ui: SettingsUiState,
    actions: SettingsActions,
    pro: Boolean,
    prefs: UiPrefs?,
): List<SettingsSection> = SectionsBuilder().apply {
    when (page) {
        SettingsPage.Root -> rootSections(ui, actions, pro)
        SettingsPage.Design -> designSections(ui, actions, prefs)
        SettingsPage.General -> generalSections(prefs)
        SettingsPage.Engine -> engineSections(ui, actions, pro)
        SettingsPage.Data -> dataSections(actions, prefs)
        SettingsPage.Desktop -> desktopSections(ui, actions)
    }
}.sections
// ---- the first page ---------------------------------------------------------------

private fun SectionsBuilder.rootSections(ui: SettingsUiState, actions: SettingsActions, pro: Boolean) {
    section("search") { SearchPill(ui.query, actions.onQuery) }
    val q = ui.query.trim()
    if (q.isNotEmpty()) {
        // The app's own settings live a page down now; a search finds them by
        // name and opens their page.
        val found = appEntries(pro).filter { it.matches(q) }
        if (found.isNotEmpty()) {
            section("found-app") {
                ConnectedGroup(title = tr("앱 설정", "App settings", "应用设置")) {
                    found.forEach { entry ->
                        row(onClick = {
                            actions.onQuery("")
                            actions.onOpen(entry.page)
                        }) { LinkRow(entry.page.icon, entry.title, entry.page.title) }
                    }
                }
            }
        }
        desktopGroups(ui, actions)
        if (found.isEmpty() && ui.visible.isEmpty()) {
            section("none") { Hint(tr("«$q» 에 맞는 설정이 없습니다", "No setting matches «$q»", "没有与 «$q» 匹配的设置")) }
        }
        return
    }
    section("pages") {
        ConnectedGroup {
            row(onClick = { actions.onOpen(SettingsPage.Design) }) {
                LinkRow(SettingsPage.Design.icon, SettingsPage.Design.title, tr("테마 · 검은 배경 · 배경화면 색조", "Theme, black background, wallpaper tone", "主题 · 纯黑背景 · 壁纸色调"))
            }
            row(onClick = { actions.onOpen(SettingsPage.General) }) {
                LinkRow(SettingsPage.General.icon, SettingsPage.General.title, tr("언어 · 진동 · 엔진 자동 시작", "Language, vibration, engine at launch", "语言 · 振动 · 自动启动引擎"))
            }
            row(onClick = { actions.onOpen(SettingsPage.Engine) }) {
                LinkRow(
                    SettingsPage.Engine.icon,
                    SettingsPage.Engine.title,
                    if (pro) tr("스레드 · 해시 · 기기 DB · 서버", "Threads, hash, device DB, server", "线程 · 哈希 · 本机数据库 · 服务器")
                    else tr("스레드 수", "Threads", "线程数"),
                )
            }
            row(onClick = { actions.onOpen(SettingsPage.Data) }) {
                LinkRow(SettingsPage.Data.icon, SettingsPage.Data.title, tr("RenjuNet 대국 · 정석 커리큘럼", "RenjuNet games, joseki curriculum", "RenjuNet 对局 · 定式课程"))
            }
        }
    }
    section("pro") {
        ConnectedGroup {
            row(onClick = { actions.onPro(!pro) }) {
                LinkRow(
                    Icons.Outlined.Science,
                    tr("고급 사용자 모드", "Expert mode", "高级用户模式"),
                    tr("DB 탭 · 엔진 콘솔 · 도구 · PC 설정 ${ui.total}개", "DB tab, engine console, tools, ${ui.total} PC settings", "数据库页 · 引擎控制台 · 工具 · ${ui.total} 项 PC 设置"),
                ) { ZenythSwitch(checked = pro, onCheckedChange = actions.onPro) }
            }
            if (pro) {
                row(onClick = { actions.onOpen(SettingsPage.Desktop) }) {
                    LinkRow(SettingsPage.Desktop.icon, SettingsPage.Desktop.title, tr("데스크톱 설정 · 설정 파일 · 기본값", "Desktop settings, files, defaults", "桌面版设置 · 设置文件 · 默认值"))
                }
            }
        }
    }
    section("about") {
        ConnectedGroup {
            row(onClick = actions.onAbout) {
                LinkRow(Icons.Outlined.Info, tr("정보", "About", "关于"), "Zenyth ${BuildConfig.VERSION_NAME}")
            }
            actions.onTour?.let { onTour ->
                row(onClick = onTour) {
                    LinkRow(
                        Icons.Outlined.Explore,
                        tr("둘러보기", "Tour", "功能导览"),
                        tr("보드 · 연구 · 정석 · 엔진", "Board, research, joseki, engine", "棋盘 · 研究 · 定式 · 引擎"),
                    )
                }
            }
            row(onClick = actions.onLicenses) {
                LinkRow(Icons.AutoMirrored.Outlined.Article, tr("오픈소스 라이선스", "Open-source licences", "开源许可证"), null)
            }
        }
    }
}
/** A setting of the app's own, as the search knows it: its name, more words for it, its page. */
private class AppEntry(val page: SettingsPage, val title: String, val words: String = "") {
    fun matches(q: String): Boolean =
        title.contains(q, ignoreCase = true) || words.contains(q, ignoreCase = true) || page.title.contains(q, ignoreCase = true)
}
private fun appEntries(pro: Boolean): List<AppEntry> = buildList {
    add(AppEntry(SettingsPage.Design, tr("테마", "Theme", "主题"), "테마 다크 라이트 밝은 어두운 theme dark light 深色 浅色"))
    add(AppEntry(SettingsPage.Design, tr("검은 배경", "Black background", "纯黑背景"), "amoled black 검정"))
    if (wallpaperToneAvailable) add(AppEntry(SettingsPage.Design, tr("배경화면 색조", "Wallpaper tone", "壁纸色调"), "wallpaper 배경화면"))
    add(AppEntry(SettingsPage.General, tr("언어", "Language", "语言"), "언어 language 한국어 English 中文"))
    add(AppEntry(SettingsPage.General, tr("진동", "Vibration", "振动"), "진동 haptic vibration"))
    add(AppEntry(SettingsPage.General, tr("앱을 열면 엔진 시작", "Start the engine with the app", "打开应用时启动引擎"), "auto start 자동"))
    add(AppEntry(SettingsPage.Engine, tr("스레드 수", "Threads", "线程数"), "스레드 threads"))
    if (pro) {
        add(AppEntry(SettingsPage.Engine, tr("해시 크기 (MB)", "Hash size (MB)", "哈希表大小 (MB)"), "해시 hash memory 메모리"))
        add(AppEntry(SettingsPage.Engine, tr("기기 데이터베이스 사용", "Use the device database", "使用本机数据库"), "rapfi.db database 데이터베이스"))
        add(AppEntry(SettingsPage.Engine, tr("서버 엔진 사용", "Use a server engine", "使用服务器引擎"), "server 서버 tailscale"))
        add(AppEntry(SettingsPage.Desktop, tr("데스크톱 설정 파일", "Desktop settings files", "桌面版设置文件"), "settings.txt 가져오기 내보내기 import export"))
        add(AppEntry(SettingsPage.Desktop, tr("PC 기본값으로 되돌리기", "Back to the PC defaults", "恢复 PC 默认值"), "reset 초기화"))
    }
    add(AppEntry(SettingsPage.Data, tr("대국 데이터 추가 (RenjuNet)", "Add game data (RenjuNet)", "添加对局数据 (RenjuNet)"), "renjunet rif 기보 통계"))
    add(AppEntry(SettingsPage.Data, tr("정석 커리큘럼 자동 업데이트", "Update the joseki curriculum", "自动更新定式课程"), "정석 커리큘럼 joseki curriculum"))
}
/** The desktop's entries the state shows, one group per category. */
private fun SectionsBuilder.desktopGroups(ui: SettingsUiState, actions: SettingsActions) {
    val groups = ui.visible.groupBy { it.category }
    SettingCategory.entries.forEach { category ->
        val rows = groups[category] ?: return@forEach
        section("group-" + category.name) {
            ConnectedGroup(title = category.label) {
                rows.forEach { spec ->
                    row { SettingRow(spec = spec, state = ui, onValue = { raw -> actions.onValue(spec.id, raw) }) }
                }
            }
        }
    }
}
// ---- 디자인 ----------------------------------------------------------------------------

private fun SectionsBuilder.designSections(ui: SettingsUiState, actions: SettingsActions, prefs: UiPrefs?) {
    section("preview") { ThemePreview() }
    if (prefs == null) {
        section("theme") {
            ConnectedGroup(title = tr("테마", "Theme", "主题")) {
                row { SwitchRow(tr("다크 모드", "Dark mode", "深色模式"), null, ui.settings.darkMode, actions.onDark) }
            }
        }
        return
    }
    section("theme") {
        val follow by prefs.themeFollowsSystem.state.collectAsStateWithLifecycle()
        ConnectedGroup(title = tr("테마", "Theme", "主题")) {
            row {
                // «테마» writes settings.txt line 27 or hands it to the phone (D4).
                ThemeButtons(
                    selected = when {
                        follow -> 0
                        ui.settings.darkMode -> 2
                        else -> 1
                    },
                    onSelect = { choice ->
                        prefs.themeFollowsSystem.set(choice == 0)
                        if (choice != 0) actions.onDark(choice == 2)
                    },
                )
            }
        }
    }
    section("look") {
        // The black background and the wallpaper tone are the app's own and
        // change only the look (D1).
        val amoled by prefs.themeAmoled.state.collectAsStateWithLifecycle()
        val tone by prefs.themeWallpaperTone.state.collectAsStateWithLifecycle()
        ConnectedGroup(title = tr("바탕", "Background", "背景")) {
            row {
                // Only the dark theme changes, and the row says so rather than
                // being greyed out: on a light phone it waits for the dark.
                SwitchRow(
                    tr("검은 배경", "Black background", "纯黑背景"),
                    tr("다크 테마에서만", "Dark theme only", "仅深色主题"),
                    amoled,
                ) { prefs.themeAmoled.set(it) }
            }
            if (wallpaperToneAvailable) {
                row {
                    SwitchRow(
                        tr("배경화면 색조", "Wallpaper tone", "壁纸色调"),
                        tr("바탕의 회색만 · 판과 값의 색은 그대로", "The greys only · board colours stay", "仅灰色底色 · 棋盘颜色不变"),
                        tone,
                    ) { prefs.themeWallpaperTone.set(it) }
                }
            }
        }
    }
}
// ---- 일반 ------------------------------------------------------------------------------

private fun SectionsBuilder.generalSections(prefs: UiPrefs?) {
    section("language") { LanguageGroup() }
    if (prefs == null) return
    section("behaviour") {
        val haptics by prefs.hapticsEnabled.state.collectAsStateWithLifecycle()
        val auto by prefs.autoStartEngine.state.collectAsStateWithLifecycle()
        ConnectedGroup(title = tr("동작", "Behaviour", "行为")) {
            row { SwitchRow(tr("진동", "Vibration", "振动"), null, haptics) { prefs.hapticsEnabled.set(it) } }
            row { SwitchRow(tr("앱을 열면 엔진 시작", "Start the engine with the app", "打开应用时启动引擎"), null, auto) { prefs.autoStartEngine.set(it) } }
        }
    }
}
// ---- 엔진 ------------------------------------------------------------------------------

private fun SectionsBuilder.engineSections(ui: SettingsUiState, actions: SettingsActions, pro: Boolean) {
    section("threads") {
        ConnectedGroup(title = tr("탐색", "Search", "搜索")) {
            row {
                // Many small numbers: these stay chips (M3 keeps chips where the
                // choices are many and short).
                FieldRow(tr("스레드 수", "Threads", "线程数"), note = tr("많을수록 빠르고 · 뜨거워짐", "More is faster · and warmer", "越多越快 · 也越热")) {
                    ChoiceChips(
                        options = ui.threadChoices.map { it to "$it" },
                        selected = ui.localProfile.threads,
                        onSelect = actions.onThreads,
                    )
                }
            }
        }
    }
    if (pro) {
        section("local") {
            LocalEngineCard(ui = ui, onHash = actions.onHash, onDatabase = actions.onDatabase, onServerEnabled = actions.onServerEnabled)
        }
    }
}
// ---- 데이터 ----------------------------------------------------------------------------

private fun SectionsBuilder.dataSections(actions: SettingsActions, prefs: UiPrefs?) {
    section("games") {
        ConnectedGroup(title = tr("대국 데이터", "Game data", "对局数据")) {
            row(onClick = actions.onRifBuild) {
                LinkRow(
                    Icons.Outlined.Download,
                    tr("대국 데이터 추가 (RenjuNet)", "Add game data (RenjuNet)", "添加对局数据 (RenjuNet)"),
                    tr("오프닝 통계를 기기에서 만듭니다", "Builds the opening statistics on the phone", "在本机生成开局统计"),
                )
            }
        }
    }
    if (prefs == null) return
    section("curriculum") {
        val curriculum by prefs.curriculumAutoUpdate.state.collectAsStateWithLifecycle()
        ConnectedGroup(title = tr("정석", "Joseki", "定式")) {
            row {
                SwitchRow(
                    tr("정석 커리큘럼 자동 업데이트", "Update the joseki curriculum", "自动更新定式课程"),
                    tr("rapfiweb.pages.dev · 보내는 정보 없음", "rapfiweb.pages.dev · nothing is sent", "rapfiweb.pages.dev · 不发送任何信息"),
                    curriculum,
                ) { prefs.curriculumAutoUpdate.set(it) }
            }
        }
    }
}
// ---- PC 설정 ---------------------------------------------------------------------------

private fun SectionsBuilder.desktopSections(ui: SettingsUiState, actions: SettingsActions) {
    desktopGroups(ui, actions)
    section("files") {
        ConnectedGroup(title = tr("데스크톱 설정 파일", "Desktop settings files", "桌面版设置文件")) {
            SettingsFile.entries.forEach { file ->
                row {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(file.fileName, style = RowTitleStyle, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { actions.onImport(file) }) { Text(tr("가져오기", "Import", "导入")) }
                        OutlinedButton(onClick = { actions.onExport(file) }) { Text(tr("내보내기", "Export", "导出")) }
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
                        Text(tr("툴바 · 핫키 · 언어 폴더", "Toolbar, hotkeys, language folder", "工具栏 · 快捷键 · 语言文件夹"), style = RowTitleStyle)
                        Hint(ui.appearanceSource ?: tr("기본값", "Defaults", "默认值"))
                    }
                    OutlinedButton(onClick = actions.onFolder) { Text(tr("폴더", "Folder", "文件夹")) }
                    if (ui.appearanceSource != null) {
                        TextButton(onClick = actions.onResetAppearance) { Text(tr("기본값", "Defaults", "默认值")) }
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
                            style = RowTitleStyle,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = actions.onExportDebugLog, enabled = ui.debugLogBytes > 0) {
                            Text(tr("내보내기", "Export", "导出"))
                        }
                        TextButton(onClick = actions.onClearDebugLog, enabled = ui.debugLogBytes > 0) { Text(tr("지우기", "Clear", "清除")) }
                    }
                }
            }
            row { ResetRow(onReset = actions.onReset) }
        }
    }
}
