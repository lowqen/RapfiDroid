package dev.gomoku.yixindroid.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.core.model.EngineTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "engine")

/**
 * Which engine the user last connected to, and the server address they last
 * typed (defaults = rapfi-server).
 *
 * The address is kept even while the on-device engine is selected: switching to
 * local is not a decision to forget the server, and the field has to come back
 * filled in when they switch back.
 */
@Singleton
class EndpointStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val hostKey = stringPreferencesKey("host")
    private val portKey = intPreferencesKey("port")
    private val localKey = booleanPreferencesKey("local")

    val endpoint: Flow<EngineEndpoint> = context.dataStore.data.map { prefs ->
        EngineEndpoint(
            host = prefs[hostKey] ?: EngineEndpoint.DEFAULT_HOST,
            port = prefs[portKey] ?: EngineEndpoint.DEFAULT_PORT,
        )
    }

    /** Local or server. Defaults to the server — that is what existed first. */
    val localMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[localKey] ?: false
    }

    suspend fun save(target: EngineTarget, endpoint: EngineEndpoint) {
        context.dataStore.edit { prefs ->
            prefs[localKey] = target.isLocal
            prefs[hostKey] = endpoint.host
            prefs[portKey] = endpoint.port
        }
    }
}
