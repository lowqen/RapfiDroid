package dev.gomoku.rapfidroid.feature.settings

import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.AppSettings
import dev.gomoku.rapfidroid.core.model.DesktopSettings
import dev.gomoku.rapfidroid.core.model.EngineCapabilities
import dev.gomoku.rapfidroid.core.model.LocalEngineProfile
import dev.gomoku.rapfidroid.core.model.SettingCategory
import dev.gomoku.rapfidroid.core.model.SettingEditor
import dev.gomoku.rapfidroid.core.model.SettingSpec

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val query: String = "",
    val category: SettingCategory? = null,
    val capabilities: EngineCapabilities = EngineCapabilities(),
    val connected: Boolean = false,
    /** The on-device engine's own limits — no line in either desktop file. */
    val localProfile: LocalEngineProfile = LocalEngineProfile(),
    /** Thread counts to offer — the fixed set plus this phone's own default. */
    val threadChoices: List<Int> = LocalEngineProfile.THREAD_CHOICES,
    /** True while the on-device engine is the chosen one (connection tab). */
    val localMode: Boolean = true,
    /** Whether the server engine is offered at all; advanced switch turns it on. */
    val serverEnabled: Boolean = false,
    /** Absolute path of the device's own `rapfi.db`; empty until resolved. */
    val localDbPath: String = "",
    /** Size of that file, null when there is none yet. Held whole in the engine's memory. */
    val localDbBytes: Long? = null,
    /** From this size on the database is worth a warning ([MemoryTier.databaseWarnBytes]). */
    val localDbWarnBytes: Long = Long.MAX_VALUE,
    /** The table ceiling the phone imposed after a kill for memory (T3), or null. */
    val memoryCapMb: Int? = null,
    /** Show every desktop setting, not just the everyday ones. */
    val advanced: Boolean = false,
    /** Where the imported toolbar/hotkeys/labels came from; null = defaults. */
    val appearanceSource: String? = null,
    /** Size of the recorded debug log; 0 when there is nothing to hand over. */
    val debugLogBytes: Long = 0,
    /** Transient result of an import/export/reset, shown as a banner. */
    val message: String? = null,
) {
    /**
     * The specs to show, always in file order so line numbers read top-down.
     *
     * `by lazy`, not `get()`: this walks all 67 specs and does up to four
     * case-insensitive `contains` on each, and the screen reads it twice per
     * composition — once for the count in the subtitle, once for the list
     * itself. As a getter that was the whole filter run twice on every
     * keystroke in the search box. The state is immutable and a new instance is
     * built for every change, so caching it on the instance is exactly as fresh
     * as recomputing it.
     */
    val visible: List<SettingSpec> by lazy {
        val q = query.trim()
        DesktopSettings.ALL.filter { spec ->
            // A search looks through everything: hiding a setting the user
            // is explicitly asking for by name would be worse than a long list.
            // RapfiWeb: the desktop entries are the advanced layer; the common
            // ones live in their own card above.
            (advanced || q.isNotEmpty()) &&
                (category == null || spec.category == category) &&
                (
                    q.isEmpty() ||
                        spec.label.contains(q, ignoreCase = true) ||
                        spec.comment.contains(q, ignoreCase = true) ||
                        spec.id.contains(q, ignoreCase = true) ||
                        spec.engineKey?.contains(q, ignoreCase = true) == true
                    )
        }
    }

    val total: Int get() = DesktopSettings.ALL.size

    /** How many entries the advanced switch would add right now. */
    val hidden: Int
        get() = if (advanced || query.isNotBlank()) 0 else
            DesktopSettings.ALL.count { category == null || it.category == category }

    /**
     * What the on-device engine uses **instead of** this desktop value, or null
     * when the row reaches whichever engine is connected unchanged.
     *
     * Three of the 67 do not survive the trip to a phone: the desktop asks for 4
     * threads, 8192 MB and the database, and those numbers are the server's.
     * Without this line the screen would show 8192 MB while the engine ran on
     * 128 — a settings screen that reports a value the engine never received is
     * worse than one that admits the difference.
     */
    fun overrideFor(spec: SettingSpec): String? {
        if (!localMode) return null
        return when (spec.id) {
            "threadNum" -> tr(
                "기기 내 엔진에서는 ${localProfile.threads} 스레드",
                "On-device: ${localProfile.threads} threads",
                "本机引擎: ${localProfile.threads} 线程",
            )
            "hashSizeMb" -> tr(
                "기기 내 엔진에서는 ${localProfile.hashMb} MB",
                "On-device: ${localProfile.hashMb} MB",
                "本机引擎: ${localProfile.hashMb} MB",
            )
            "useDatabase" -> tr(
                if (localProfile.useDatabase) "기기 내 엔진에서는 기기의 rapfi.db 를 씁니다"
                else "기기 내 엔진에서는 데이터베이스를 쓰지 않습니다",
                if (localProfile.useDatabase) "On-device: uses this phone's rapfi.db"
                else "On-device: no database",
                if (localProfile.useDatabase) "本机引擎: 使用本机的 rapfi.db"
                else "本机引擎: 不使用数据库",
            )
            else -> null
        }
    }

    /**
     * The editor to actually offer: thread and hash maxima are tightened to what
     * the engine reported (`MESSAGE INFO MAX_THREAD_NUM` / `MAX_HASH_SIZE`), so
     * the user cannot ask for more than the server has.
     */
    fun editorFor(spec: SettingSpec): SettingEditor {
        val editor = spec.editor
        if (editor !is SettingEditor.Number) return editor
        val cap = when (spec.id) {
            "threadNum" -> capabilities.maxThreadNum?.toLong()
            "hashSizeMb" -> capabilities.maxHashSizeMb?.toLong()
            else -> null
        } ?: return editor
        return editor.copy(max = minOf(editor.max, cap.coerceAtLeast(editor.min)))
    }
}
