package dev.gomoku.yixindroid.feature.connection

import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.core.model.FontSpec
import dev.gomoku.yixindroid.core.model.LinkHealth

data class ConnectionUiState(
    val host: String = EngineEndpoint.DEFAULT_HOST,
    val port: String = EngineEndpoint.DEFAULT_PORT.toString(),
    /** True while the on-device engine is chosen; the address fields go quiet. */
    val localMode: Boolean = false,
    val state: ConnectionState = ConnectionState.Disconnected,
    /** Drop / reconnect status; [LinkHealth.idle] while nothing is wrong. */
    val health: LinkHealth = LinkHealth(),
    val console: List<ConsoleLine> = emptyList(),
    val commandDraft: String = "",
    /** settings.txt line 13 — the desktop's "show log". */
    val showLog: Boolean = true,
    /** settings.txt line 37 — console text scale in percent. */
    val logScalePercent: Int = 140,
    /** settings.txt line 45 — "Text Log Font"; only its size travels. */
    val logFont: FontSpec = FontSpec.DEFAULT,
) {
    /**
     * Console font multiplier; 100 % = the app's default size. Two settings feed
     * it because the desktop has two: the log area scale (line 37) and the log
     * font itself (line 45). Multiplying keeps both live instead of letting one
     * silently win.
     */
    val logScale: Float get() =
        (logScalePercent.coerceIn(50, 300)) / 100f * logFont.scale

    /** While a reconnect is pending the endpoint is spoken for; don't offer it. */
    val canConnect: Boolean
        get() = !health.reconnecting &&
            (state is ConnectionState.Disconnected || state is ConnectionState.Error)

    val canSend: Boolean
        get() = state.isLive && commandDraft.isNotBlank()
}
