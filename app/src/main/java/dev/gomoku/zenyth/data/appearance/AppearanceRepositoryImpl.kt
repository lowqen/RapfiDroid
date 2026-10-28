package dev.gomoku.zenyth.data.appearance

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.zenyth.core.common.IoDispatcher
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.FunctionScripts
import dev.gomoku.zenyth.core.model.LngTable
import dev.gomoku.zenyth.domain.repository.AppearanceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the desktop's `function/` and `language/` folders through SAF.
 *
 * The folder to pick is the Yixin deployment directory — the one holding
 * `Yixin.exe` — because that is what a user can point at without knowing the
 * layout. Both subfolders are optional: importing only `function/` gives buttons
 * with fallback labels, importing only `language/` renames the built-in ones.
 */
@Singleton
class AppearanceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val store: AppearanceStore,
) : AppearanceRepository {

    private val _toolbar = MutableStateFlow(FunctionScripts.DEFAULT_TOOLBAR)
    override val toolbar: StateFlow<List<FunctionScripts.ToolbarItem>> = _toolbar.asStateFlow()

    private val _hotkeys = MutableStateFlow(FunctionScripts.DEFAULT_HOTKEYS)
    override val hotkeys: StateFlow<List<FunctionScripts.HotkeyItem>> = _hotkeys.asStateFlow()

    private val _language = MutableStateFlow(LngTable.EMPTY)
    override val language: StateFlow<LngTable> = _language.asStateFlow()

    private val _source = MutableStateFlow<String?>(null)
    override val source: StateFlow<String?> = _source.asStateFlow()

    override suspend fun restore() {
        val saved = withContext(io) { store.load() } ?: return
        if (saved.toolbar.isNotEmpty()) _toolbar.value = saved.toolbar
        if (saved.hotkeys.isNotEmpty()) _hotkeys.value = saved.hotkeys
        _language.value = LngTable(saved.labels)
        _source.value = saved.source
    }

    override suspend fun importFrom(tree: Uri, languageIndex: Int): Result<String> =
        withContext(io) {
            runCatching {
                val saf = SafTree(context.contentResolver, tree)
                val found = ArrayList<String>()

                val functionId = saf.folder("function")
                val toolbar = functionId?.let { id ->
                    // The desktop stops at the first missing file — the list is
                    // contiguous by construction (main.c:14301 `toolbarnum = i`).
                    readSeries(FunctionScripts.MAX_TOOLBAR_ITEMS) { i ->
                        saf.readFile(id, FunctionScripts.toolbarFileName(i))
                    }.mapNotNull { FunctionScripts.parseToolbar(it) }
                }.orEmpty()
                val hotkeys = functionId?.let { id ->
                    readSeries(FunctionScripts.MAX_HOTKEY_ITEMS) { i ->
                        saf.readFile(id, FunctionScripts.hotkeyFileName(i))
                    }.mapNotNull { FunctionScripts.parseHotkey(it) }
                }.orEmpty()

                // The desktop ships seven `.lng` files; this app speaks Korean,
                // English and Chinese, so a PC set to Japanese, Russian or
                // Vietnamese would otherwise put those labels on buttons in an
                // app that speaks none of them. Those fall back to English, which
                // is what the desktop's own `TL(idx, "…")` defaults are written in;
                // both Chinese files (1 简体, 2 繁體) are kept as the PC chose.
                val lngIndex = if (languageIndex in LNG_SUPPORTED) languageIndex else LNG_ENGLISH
                val lng = saf.folder("language")
                    ?.let { saf.readFile(it, "$lngIndex.lng") }
                    ?.let { LngTable.parse(it) }

                if (toolbar.isNotEmpty()) {
                    _toolbar.value = toolbar
                    found += tr("툴바 ${toolbar.size}개", "${toolbar.size} toolbar items", "工具栏 ${toolbar.size} 项")
                }
                if (hotkeys.isNotEmpty()) {
                    _hotkeys.value = hotkeys
                    found += tr("핫키 ${hotkeys.size}개", "${hotkeys.size} hotkeys", "快捷键 ${hotkeys.size} 个")
                }
                if (lng != null && lng.size > 0) {
                    _language.value = lng
                    val name = lng.languageName.ifEmpty { "$lngIndex.lng" }
                    found += tr("언어 $name (${lng.size}개)", "language $name (${lng.size})", "语言 $name (${lng.size} 条)")
                }
                if (found.isEmpty()) {
                    error(
                        tr("function/toolbar1.txt 도 language/$lngIndex.lng 도 없습니다 — ", "Neither function/toolbar1.txt nor language/$lngIndex.lng is there — ", "既没有 function/toolbar1.txt 也没有 language/$lngIndex.lng，") +
                            tr("Yixin.exe 가 있는 폴더를 선택하세요", "pick the folder Yixin.exe is in", "请选择 Yixin.exe 所在的文件夹"),
                    )
                }
                val label = saf.displayName() ?: tr("선택한 폴더", "the chosen folder", "所选文件夹")
                _source.value = label
                store.save(
                    AppearanceStore.Saved(
                        toolbar = _toolbar.value,
                        hotkeys = _hotkeys.value,
                        labels = _language.value.entriesForStorage(),
                        source = label,
                    ),
                )
                found.joinToString(" · ") + tr(" 불러옴", " loaded", " 已载入")
            }
        }

    override suspend fun reset() {
        _toolbar.value = FunctionScripts.DEFAULT_TOOLBAR
        _hotkeys.value = FunctionScripts.DEFAULT_HOTKEYS
        _language.value = LngTable.EMPTY
        _source.value = null
        withContext(io) { store.clear() }
    }

    /** `toolbar1.txt`, `toolbar2.txt`, … stopping at the first one that is absent. */
    private inline fun readSeries(max: Int, read: (Int) -> String?): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until max) out += read(i) ?: break
        return out
    }

    private companion object {
        /** `language/<n>.lng` indices this app will read (main.c:14278). */
        const val LNG_ENGLISH = 0
        const val LNG_KOREAN = 3
        val LNG_SUPPORTED = setOf(1, 2, LNG_KOREAN)
    }
}
