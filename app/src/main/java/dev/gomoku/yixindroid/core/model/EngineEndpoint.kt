package dev.gomoku.yixindroid.core.model

/**
 * Where the Rapfi engine listens. Confirmed from engine.exe: the desktop proxy
 * is a transparent relay to the Tailscale node `rapfi-server` (100.111.248.44)
 * on TCP 7669. The Android client connects here directly and speaks piskvork.
 */
data class EngineEndpoint(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
) {
    val display: String get() = "$host:$port"

    companion object {
        const val DEFAULT_HOST = "100.111.248.44" // rapfi-server (Tailscale)
        const val DEFAULT_PORT = 7669
    }
}
