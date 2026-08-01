package dev.gomoku.yixindroid.data.appearance

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.FunctionScripts
import dev.gomoku.yixindroid.core.model.LngTable
import dev.gomoku.yixindroid.domain.repository.AppearanceRepository
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

                val lng = saf.folder("language")
                    ?.let { saf.readFile(it, "$languageIndex.lng") }
                    ?.let { LngTable.parse(it) }

                if (toolbar.isNotEmpty()) {
                    _toolbar.value = toolbar
                    found += "툴바 ${toolbar.size}개"
                }
                if (hotkeys.isNotEmpty()) {
                    _hotkeys.value = hotkeys
                    found += "핫키 ${hotkeys.size}개"
                }
                if (lng != null && lng.size > 0) {
                    _language.value = lng
                    val name = lng.languageName.ifEmpty { "$languageIndex.lng" }
                    found += "언어 $name (${lng.size}개)"
                }
                if (found.isEmpty()) {
                    error(
                        "function/toolbar1.txt 도 language/$languageIndex.lng 도 없습니다 — " +
                            "Yixin.exe 가 있는 폴더를 선택하세요",
                    )
                }
                val label = saf.displayName() ?: "선택한 폴더"
                _source.value = label
                store.save(
                    AppearanceStore.Saved(
                        toolbar = _toolbar.value,
                        hotkeys = _hotkeys.value,
                        labels = _language.value.entriesForStorage(),
                        source = label,
                    ),
                )
                found.joinToString(" · ") + " 불러옴"
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
}
