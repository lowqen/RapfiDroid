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
) {
    val canConnect: Boolean
        get() = state is ConnectionState.Disconnected || state is ConnectionState.Error

    val canSend: Boolean
        get() = state.isLive && commandDraft.isNotBlank()
}
