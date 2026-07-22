package dev.gomoku.yixindroid.core.model

/** Lifecycle of the engine socket + piskvork handshake (see plan §2.1). */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Handshaking : ConnectionState
    data object Ready : ConnectionState
    data object Thinking : ConnectionState
    data class Error(val reason: String) : ConnectionState

    val isLive: Boolean
        get() = this is Handshaking || this is Ready || this is Thinking
}
