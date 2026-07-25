package dev.gomoku.yixindroid.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.yixindroid.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Persistence for [AppSettings]. Stored as one JSON blob rather than 67 keys:
 * updates are atomic, and a missing field simply takes its desktop default, so
 * adding a setting never needs a migration.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("app_settings_json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[key] ?: return@map AppSettings()
        // A corrupt or older blob must not brick the app: fall back to defaults.
        runCatching { json.decodeFromString(AppSettings.serializer(), stored) }
            .getOrElse { AppSettings() }
    }

    suspend fun save(settings: AppSettings) {
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        context.settingsDataStore.edit { it[key] = encoded }
    }
}
