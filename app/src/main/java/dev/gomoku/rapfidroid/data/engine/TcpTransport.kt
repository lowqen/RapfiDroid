package dev.gomoku.rapfidroid.data.engine

import dev.gomoku.rapfidroid.core.model.EngineEndpoint
import okio.buffer
import okio.sink
import okio.source
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The server engine: one TCP socket to `rapfi-server`, which is what engine.exe
 * (a transparent relay) used to be on the desktop.
 */
class TcpTransport(private val endpoint: EngineEndpoint) : EngineTransport {

    override val label: String get() = endpoint.display

    override suspend fun open(): EngineChannel {
        val socket = Socket().apply {
            tcpNoDelay = true
            // The far end is on-demand and the path crosses a VPN and a phone
            // radio. Keepalive lets the kernel notice a peer that vanished
            // without a FIN, which is how these links usually die; the
            // repository's own idle ping covers the rest.
            keepAlive = true
            connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
        }
        return EngineChannel(
            source = socket.source().buffer(),
            sink = socket.sink().buffer(),
        ) { socket.close() }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
    }
}
