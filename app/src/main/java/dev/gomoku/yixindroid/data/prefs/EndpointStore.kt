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
    private val serverEnabledKey = booleanPreferencesKey("server_enabled")

    val endpoint: Flow<EngineEndpoint> = context.dataStore.data.map { prefs ->
        EngineEndpoint(
            host = prefs[hostKey] ?: EngineEndpoint.DEFAULT_HOST,
            port = prefs[portKey] ?: EngineEndpoint.DEFAULT_PORT,
        )
    }

    /**
     * Local or server. **Local by default**: the engine in the APK needs no
     * VPN, no server to wake and no address to type, so it is the one a new
     * install can actually use. It also stays local unless [serverEnabled] —
     * see there.
     */
    val localMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[serverEnabledKey] != true || (prefs[localKey] ?: true)
    }

    /**
     * Whether the server engine is offered at all. Off by default, behind the
     * settings screen's advanced switch.
     *
     * It is not a feature most installs can use: it needs a Tailscale node, a
     * machine to run Rapfi on and an address only its owner knows. Offering it
     * on the connection tab by default would put a dead option in front of
     * everyone, and a connection that fails for reasons the app cannot explain
     * reads as a broken app.
     */
    val serverEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[serverEnabledKey] ?: false
    }

    suspend fun save(target: EngineTarget, endpoint: EngineEndpoint) {
        context.dataStore.edit { prefs ->
            prefs[localKey] = target.isLocal
            prefs[hostKey] = endpoint.host
            prefs[portKey] = endpoint.port
        }
    }

    suspend fun setServerEnabled(on: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[serverEnabledKey] = on
            // Turning it off cannot leave the app pointed at an engine it will
            // no longer offer — the connection tab would have no way back.
            if (!on) prefs[localKey] = true
        }
    }
}
