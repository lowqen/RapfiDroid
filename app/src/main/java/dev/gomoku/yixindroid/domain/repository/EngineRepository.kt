package dev.gomoku.yixindroid.domain.repository

import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AnalyzeParams
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.EngineCapabilities
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.core.model.EngineParams
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

    /** Limits the engine reported (thread/hash maxima); empty until it does. */
    val capabilities: StateFlow<EngineCapabilities>

    suspend fun connect(endpoint: EngineEndpoint)
    suspend fun send(command: EngineCommand)
    fun disconnect()

    /**
     * Push engine parameters (rule, level, threads, hash, …) and remember them
     * for later reconnects. They are also sent automatically on connect and
     * whenever the settings change, since without them Rapfi analyses with its
     * own config instead of the user's.
     */
    suspend fun applyParams(params: EngineParams)

    /**
     * Analyze [position]: streams [AnalysisSnapshot]s as the search deepens.
     * Collecting starts the search (yxboard + yxnbest); cancelling the
     * collection sends YXSTOP.
     */
    fun analyze(position: Position, params: AnalyzeParams): Flow<AnalysisSnapshot>

    /** Renju forbidden points for [position] (empty if none / not renju). */
    suspend fun forbidden(position: Position): List<Move>

    /**
     * Balance search (desktop `balance1` / `balance2`): the move — or move pair,
     * when [two] — that brings the position closest to [bias]. Suspends until the
     * engine answers; [stop] makes it answer with its current best, and the
     * result is empty if it never does.
     */
    suspend fun balance(position: Position, two: Boolean, bias: Int = 0): List<Move>

    /** YXSTOP: ends the running search, which then reports its best move. */
    suspend fun stop()
}
