package dev.gomoku.yixindroid.feature.settings

import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.DesktopSettings
import dev.gomoku.yixindroid.core.model.EngineCapabilities
import dev.gomoku.yixindroid.core.model.SettingCategory
import dev.gomoku.yixindroid.core.model.SettingEditor
import dev.gomoku.yixindroid.core.model.SettingSpec

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val query: String = "",
    val category: SettingCategory? = null,
    val capabilities: EngineCapabilities = EngineCapabilities(),
    val connected: Boolean = false,
    /** Transient result of an import/export/reset, shown as a banner. */
    val message: String? = null,
) {
    /** The specs to show, always in file order so line numbers read top-down. */
    val visible: List<SettingSpec>
        get() {
            val q = query.trim()
            return DesktopSettings.ALL.filter { spec ->
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
