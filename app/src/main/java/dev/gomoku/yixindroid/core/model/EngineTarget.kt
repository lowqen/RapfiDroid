package dev.gomoku.yixindroid.core.model

/**
 * Which Rapfi a session talks to.
 *
 * The protocol is the same piskvork either way — this only says where the other
 * end lives, and the two ends are deliberately not interchangeable in what they
 * are good for:
 *
 *  - [Remote] is the on-demand `rapfi-server` across Tailscale (EPYC, 8 GB hash,
 *    the real database). Deep analysis, position proving, batch work.
 *  - [Local] is Rapfi running on this phone as a child process. Always there,
 *    no VPN, no waking a server — but a small hash and no database, so it is for
 *    games and quick reading.
 *
 * Because Rapfi's search is bit-identical across instruction sets (upstream
 * forces `-ffp-contract=off` for exactly that reason), the two ends pick the
 * *same move given the same nodes*. Only depth-per-second differs.
 */
sealed interface EngineTarget {

    data class Remote(val endpoint: EngineEndpoint = EngineEndpoint()) : EngineTarget

    data object Local : EngineTarget

    val isLocal: Boolean get() = this is Local

    /** Short label for the console and the notification. */
    val display: String
        get() = when (this) {
            is Remote -> endpoint.display
            Local -> "on-device"
        }
}
