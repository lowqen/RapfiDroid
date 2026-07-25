package dev.gomoku.yixindroid.feature.connection

import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.EngineEndpoint

data class ConnectionUiState(
    val host: String = EngineEndpoint.DEFAULT_HOST,
    val port: String = EngineEndpoint.DEFAULT_PORT.toString(),
    val state: ConnectionState = ConnectionState.Disconnected,
    val console: List<ConsoleLine> = emptyList(),
    val commandDraft: String = "",
    /** settings.txt line 13 — the desktop's "show log". */
    val showLog: Boolean = true,
    /** settings.txt line 37 — console text scale in percent. */
    val logScalePercent: Int = 140,
) {
    /** Console font multiplier; 100 % = the app's default size. */
    val logScale: Float get() = (logScalePercent.coerceIn(50, 300)) / 100f

    val canConnect: Boolean
        get() = state is ConnectionState.Disconnected || state is ConnectionState.Error

    val canSend: Boolean
        get() = state.isLive && commandDraft.isNotBlank()
}
