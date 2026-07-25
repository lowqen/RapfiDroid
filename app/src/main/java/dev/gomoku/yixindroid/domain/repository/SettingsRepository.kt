package dev.gomoku.yixindroid.domain.repository

import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.SettingsFile
import kotlinx.coroutines.flow.StateFlow

/**
 * The single source of truth for the 67 desktop settings. Everything that reads
 * a setting (board, engine, theme) reads it from here, so a change lands in one
 * place and propagates.
 */
interface SettingsRepository {

    /** Current settings — starts at the desktop defaults, then the stored values. */
    val settings: StateFlow<AppSettings>

    /** True once the persisted values have been loaded (avoids a flash of defaults). */
    val loaded: StateFlow<Boolean>

    suspend fun update(transform: (AppSettings) -> AppSettings)

    /** Set one setting by its [dev.gomoku.yixindroid.core.model.SettingSpec] id. */
    suspend fun set(id: String, raw: String)

    suspend fun resetToDefaults()

    /** The desktop file text for export (identical layout to the PC's). */
    fun export(file: SettingsFile): String

    /** Apply a desktop file's text; returns the number of lines consumed. */
    suspend fun import(text: String, file: SettingsFile): Int
}
