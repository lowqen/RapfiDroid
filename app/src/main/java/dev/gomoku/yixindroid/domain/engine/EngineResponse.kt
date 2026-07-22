package dev.gomoku.yixindroid.domain.engine

import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.SearchInfo

/**
 * Parsed server output. P1 covers the unambiguous protocol tokens; realtime
 * INFO/PV parsing into [SearchInfo] is finalized in P2 from real captures, so
 * such lines currently surface as [Message]/[Unknown] and stay visible in the
 * raw console.
 */
sealed interface EngineResponse {
    val raw: String

    /** A coordinate line "x,y" = the engine's chosen move. */
    data class BestMove(val move: Move, override val raw: String) : EngineResponse

    /** Reply to START and other acknowledged commands. */
    data class Ok(override val raw: String) : EngineResponse

    data class Message(val text: String, override val raw: String) : EngineResponse
    data class Debug(val text: String, override val raw: String) : EngineResponse
    data class Error(val text: String, override val raw: String) : EngineResponse

    /** ABOUT reply: name="Rapfi", version="...", author="...", ... */
    data class About(val fields: Map<String, String>, override val raw: String) : EngineResponse

    /** Reserved for P2 realtime parsing. */
    data class Info(val info: SearchInfo, override val raw: String) : EngineResponse

    data class Unknown(override val raw: String) : EngineResponse
}
