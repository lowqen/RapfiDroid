package dev.gomoku.rapfidroid.domain.repository

import dev.gomoku.rapfidroid.core.model.AnalysisSnapshot
import dev.gomoku.rapfidroid.core.model.AnalyzeParams
import dev.gomoku.rapfidroid.core.model.ConnectionState
import dev.gomoku.rapfidroid.core.model.ConsoleLine
import dev.gomoku.rapfidroid.core.model.EngineCapabilities
import dev.gomoku.rapfidroid.core.model.EngineParams
import dev.gomoku.rapfidroid.core.model.EngineTarget
import dev.gomoku.rapfidroid.core.model.LinkHealth
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.Position
import dev.gomoku.rapfidroid.domain.engine.EngineCommand
import dev.gomoku.rapfidroid.domain.engine.EngineResponse
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

    /** Whether the link dropped and what the repository is doing about it. */
    val health: StateFlow<LinkHealth>

    /** Open a session with [target] — the server across Tailscale, or this phone. */
    suspend fun connect(target: EngineTarget)
    suspend fun send(command: EngineCommand)

    /**
     * Hang up for good. Unlike a dropped socket this is the user's decision, so
     * no reconnect is attempted until [connect] is called again.
     */
    fun disconnect()

    /** Give up waiting for the backoff and try the endpoint again right now. */
    suspend fun retryNow()

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
