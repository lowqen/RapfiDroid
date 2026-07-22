package dev.gomoku.yixindroid.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "engine")

/** Persists the server endpoint the user last used (defaults = rapfi-server). */
@Singleton
class EndpointStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val hostKey = stringPreferencesKey("host")
    private val portKey = intPreferencesKey("port")

    val endpoint: Flow<EngineEndpoint> = context.dataStore.data.map { prefs ->
        EngineEndpoint(
            host = prefs[hostKey] ?: EngineEndpoint.DEFAULT_HOST,
            port = prefs[portKey] ?: EngineEndpoint.DEFAULT_PORT,
        )
    }

    suspend fun save(endpoint: EngineEndpoint) {
        context.dataStore.edit { prefs ->
            prefs[hostKey] = endpoint.host
            prefs[portKey] = endpoint.port
        }
    }
}
