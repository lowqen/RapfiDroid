package dev.gomoku.yixindroid.domain.repository

import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AnalyzeParams
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-facing engine access. Owns the socket + parser; kept alive across
 * config changes by the foreground service.
 */
interface EngineRepository {
    val state: StateFlow<ConnectionState>
    val responses: SharedFlow<EngineResponse>
    val console: SharedFlow<ConsoleLine>

    suspend fun connect(endpoint: EngineEndpoint)
    suspend fun send(command: EngineCommand)
    fun disconnect()

    /**
     * Analyze [position]: streams [AnalysisSnapshot]s as the search deepens.
     * Collecting starts the search (yxboard + yxnbest); cancelling the
     * collection sends YXSTOP.
     */
    fun analyze(position: Position, params: AnalyzeParams): Flow<AnalysisSnapshot>

    /** Renju forbidden points for [position] (empty if none / not renju). */
    suspend fun forbidden(position: Position): List<Move>
}
