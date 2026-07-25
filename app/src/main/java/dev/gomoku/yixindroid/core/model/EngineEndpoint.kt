package dev.gomoku.yixindroid.core.model

/**
 * Where the Rapfi engine listens. Extracted from engine.exe's frozen `sengine`
 * module (`socket.connect((HOST, PORT))`): it is a transparent piskvork relay to
 * host `rapfi-server` on **TCP 5050**. On the desktop that name is resolved by
 * Tailscale MagicDNS to 100.111.248.44; the Android client connects to that IP
 * directly (no MagicDNS dependency) and speaks piskvork.
 */
data class EngineEndpoint(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
) {
    val display: String get() = "$host:$port"

    companion object {
        // Tailscale node `rapfi-server`; the engine.exe proxy connects here.
        const val DEFAULT_HOST = "100.111.248.44"
        const val DEFAULT_PORT = 5050
    }
}
