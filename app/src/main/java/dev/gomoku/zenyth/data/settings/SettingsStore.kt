package dev.gomoku.zenyth.data.settings

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.zenyth.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

private val SETTINGS_KEY = stringPreferencesKey("app_settings_json")

/** Which one-off default changes this install has been through. */
private val DEFAULTS_REV = intPreferencesKey("defaults_rev")

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(DefaultsMigration) },
)

/**
 * Defaults that changed after release. The blob stores every field
 * (`encodeDefaults`), so an install keeps the default it was first written
 * with; a value still equal to the **old** default is taken as never chosen and
 * moved to the new one, once.
 *
 *  1. multi-PV 3 → 1 (0.15.0): analysis opens on the best move alone, as Zenyth Web.
 */
internal object DefaultsMigration : DataMigration<Preferences> {
    private const val REV = 1

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[DEFAULTS_REV] ?: 0) < REV

    override suspend fun migrate(currentData: Preferences): Preferences {
        val out = currentData.toMutablePreferences()
        currentData[SETTINGS_KEY]?.let { out[SETTINGS_KEY] = moveOldMultiPv(it) }
        out[DEFAULTS_REV] = REV
        return out.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    /** The stored blob with `multiPv` 3 (the old default) turned into 1. Anything else is left as it is. */
    fun moveOldMultiPv(json: String): String {
        val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return json
        if ((obj["multiPv"] as? JsonPrimitive)?.intOrNull != 3) return json
        return JsonObject(obj + ("multiPv" to JsonPrimitive(1))).toString()
    }
}

/**
 * Persistence for [AppSettings]. Stored as one JSON blob rather than 67 keys:
 * updates are atomic, and a missing field simply takes its desktop default, so
 * adding a setting never needs a migration (changing a default does —
 * [DefaultsMigration]).
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[SETTINGS_KEY] ?: return@map AppSettings()
        // A corrupt or older blob must not brick the app: fall back to defaults.
        runCatching { json.decodeFromString(AppSettings.serializer(), stored) }
            .getOrElse { AppSettings() }
    }

    suspend fun save(settings: AppSettings) {
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        context.settingsDataStore.edit { it[SETTINGS_KEY] = encoded }
    }
}
