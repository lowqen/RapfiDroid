package dev.gomoku.zenyth.core.model

/**
 * Where a server Rapfi listens: a piskvork relay on the user's own machine,
 * reached over their Tailscale network (the desktop's engine.exe relay listens on
 * **TCP 5050**, so that is the default port).
 *
 * There is no default host. The address is the user's own server and nobody
 * else's — it used to default to the maintainer's tailnet node, which put a
 * private address in public source and pointed every install at a machine it
 * could not reach (docs/보안-강화-계획.md, Phase 5). Until one is typed the
 * server engine cannot be started.
 */
data class EngineEndpoint(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
) {
    val display: String get() = "$host:$port"

    /** A host has been typed; a blank one points nowhere (and would resolve to this phone). */
    val isConfigured: Boolean get() = host.isNotBlank()

    companion object {
        const val DEFAULT_HOST = ""
        const val DEFAULT_PORT = 5050
    }
}
