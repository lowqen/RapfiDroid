package dev.gomoku.zenyth.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.gomoku.zenyth.core.model.LocalEngineProfile
import dev.gomoku.zenyth.core.model.MemoryTier
import dev.gomoku.zenyth.data.engine.DeviceSpecReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.localEngineDataStore by preferencesDataStore(name = "local_engine")

/**
 * What the on-device engine is allowed to take. Deliberately **not** part of
 * [dev.gomoku.zenyth.core.model.AppSettings]: that model is pinned
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

    /** Absent on everything written before R3 — see [LocalEngineProfile.fromStored]. */
    private val versionKey = intPreferencesKey("version")

    /**
     * The table ceiling the phone itself imposed (T3): set one step lower each time
     * the system killed the engine for memory, cleared when the user picks a table
     * size. Not part of the profile — it is the phone's answer, not a choice.
     */
    private val memoryCapKey = intPreferencesKey("memory_cap_mb")
    /** True while an on-device session is open; left true by a process that was killed. */
    private val engineOpenKey = booleanPreferencesKey("engine_open")
    /** The newest process exit already looked at, so one kill lowers the table once. */
    private val exitSeenKey = longPreferencesKey("exit_seen_ms")

    /** This phone's out-of-box numbers: every core, a table sized by its RAM (R3). */
    val defaults: LocalEngineProfile by lazy { LocalEngineProfile.forDevice(DeviceSpecReader.read(context)) }

    /** The RAM the system reports, 0 when it cannot be read — for the database warning (T3). */
    val totalRamMb: Long by lazy { DeviceSpecReader.read(context)?.totalRamMb ?: 0L }

    val profile: Flow<LocalEngineProfile> = context.localEngineDataStore.data.map { prefs ->
        LocalEngineProfile.fromStored(
            threads = prefs[threadsKey],
            hashMb = prefs[hashKey],
            useDatabase = prefs[databaseKey],
            legacy = prefs[versionKey] == null,
            defaults = defaults,
        )
    }

    val memoryCapMb: Flow<Int?> = context.localEngineDataStore.data.map { it[memoryCapKey] }

    /**
     * One step down from [currentMb] after a kill for memory; returns the new
     * ceiling, or null when the table is already at the bottom.
     */
    suspend fun lowerMemoryCap(currentMb: Int): Int? {
        val next = MemoryTier.stepDown(currentMb) ?: return null
        context.localEngineDataStore.edit { it[memoryCapKey] = next }
        return next
    }

    suspend fun clearMemoryCap() {
        context.localEngineDataStore.edit { it.remove(memoryCapKey) }
    }

    suspend fun setEngineOpen(open: Boolean) {
        context.localEngineDataStore.edit { it[engineOpenKey] = open }
    }

    suspend fun engineWasOpen(): Boolean = context.localEngineDataStore.data.first()[engineOpenKey] == true

    suspend fun exitSeenMs(): Long = context.localEngineDataStore.data.first()[exitSeenKey] ?: 0L

    suspend fun setExitSeenMs(ms: Long) {
        context.localEngineDataStore.edit { it[exitSeenKey] = ms }
    }

    suspend fun save(profile: LocalEngineProfile) {
        context.localEngineDataStore.edit { prefs ->
            // Stored already clamped: what is written here is what the engine is
            // told, and a value that only becomes safe on the way out is a value
            // that will one day leave by another door.
            //
            // A number equal to the device default is *not* stored: the screen
            // saves all three when any one changes, and pinning the others would
            // freeze today's default into a choice nobody made.
            if (profile.threads == defaults.threads) {
                prefs.remove(threadsKey)
            } else {
                prefs[threadsKey] = profile.threads
            }
            if (profile.hashMb == defaults.hashMb) {
                prefs.remove(hashKey)
            } else {
                prefs[hashKey] = profile.hashMb
            }
            prefs[databaseKey] = profile.useDatabase
            prefs[versionKey] = VERSION
        }
    }

    private companion object {
        /** 2 = R3: unset means the device default, and 3/128 mean what they say. */
        const val VERSION = 2
    }
}
