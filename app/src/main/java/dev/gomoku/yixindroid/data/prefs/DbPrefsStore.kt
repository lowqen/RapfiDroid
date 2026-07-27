package dev.gomoku.yixindroid.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.gomoku.yixindroid.domain.repository.DbPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dbDataStore by preferencesDataStore(name = "database")

/**
 * App-local database preferences. Deliberately **not** part of [dev.gomoku
 * .yixindroid.core.model.AppSettings]: that model is pinned line-for-line to the
 * desktop's `settings.txt`/`settings_dev.txt` (47 + 20), and adding a field
 * there would break the codec's round-trip.
 *
 * Holds the destructive-operation unlock (plan §7 decision 1) and the last
 * server-side paths, so the file dialogs do not start empty every time.
 */
@Singleton
class DbPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : DbPreferences {
    private val unlockKey = booleanPreferencesKey("destructive_unlocked")
    private val lastPathKey = stringPreferencesKey("last_path")

    override val destructiveUnlocked: Flow<Boolean> =
        context.dbDataStore.data.map { it[unlockKey] ?: false }

    override val lastPath: Flow<String> =
        context.dbDataStore.data.map { it[lastPathKey] ?: DEFAULT_PATH }

    override suspend fun setDestructiveUnlocked(on: Boolean) {
        context.dbDataStore.edit { it[unlockKey] = on }
    }

    override suspend fun setLastPath(path: String) {
        context.dbDataStore.edit { it[lastPathKey] = path }
    }

    private companion object {
        /** The engine's own database, as configured on the server. */
        const val DEFAULT_PATH = "rapfi.db"
    }
}
