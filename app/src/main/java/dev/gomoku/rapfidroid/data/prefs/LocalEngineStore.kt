package dev.gomoku.rapfidroid.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.gomoku.rapfidroid.core.model.LocalEngineProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.localEngineDataStore by preferencesDataStore(name = "local_engine")

/**
 * What the on-device engine is allowed to take. Deliberately **not** part of
 * [dev.gomoku.rapfidroid.core.model.AppSettings]: that model is pinned
 * line-for-line to the desktop's `settings.txt`/`settings_dev.txt` (47 + 20),
 * and these three have no line there — the desktop has no on-device engine.
 *
 * Keeping them apart is also what lets the settings screen tell the truth about
 * which engine a number reaches: the desktop rows stay the server's, these stay
 * the phone's, and neither silently overwrites the other.
 */
@Singleton
class LocalEngineStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val threadsKey = intPreferencesKey("threads")
    private val hashKey = intPreferencesKey("hash_mb")
    private val databaseKey = booleanPreferencesKey("use_database")

    val profile: Flow<LocalEngineProfile> = context.localEngineDataStore.data.map { prefs ->
        val defaults = LocalEngineProfile()
        LocalEngineProfile(
            threadNum = prefs[threadsKey] ?: defaults.threadNum,
            hashSizeMb = prefs[hashKey] ?: defaults.hashSizeMb,
            useDatabase = prefs[databaseKey] ?: defaults.useDatabase,
        )
    }

    suspend fun save(profile: LocalEngineProfile) {
        context.localEngineDataStore.edit { prefs ->
            // Stored already clamped: what is written here is what the engine is
            // told, and a value that only becomes safe on the way out is a value
            // that will one day leave by another door.
            prefs[threadsKey] = profile.threads
            prefs[hashKey] = profile.hashMb
            prefs[databaseKey] = profile.useDatabase
        }
    }
}
