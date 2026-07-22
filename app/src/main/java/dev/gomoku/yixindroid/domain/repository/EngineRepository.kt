package dev.gomoku.yixindroid.domain.repository

import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-facing engine access. The implementation owns the socket + parser and
 * survives configuration changes (process-scoped singleton, kept alive by the
 * foreground service). P1 exposes connect / send / a parsed response stream /
 * a raw console stream; higher-level analysis use cases land in P2.
 */
interface EngineRepository {
    val state: StateFlow<ConnectionState>
    val responses: SharedFlow<EngineResponse>
    val console: SharedFlow<ConsoleLine>

    suspend fun connect(endpoint: EngineEndpoint)
    suspend fun send(command: EngineCommand)
    fun disconnect()
}
