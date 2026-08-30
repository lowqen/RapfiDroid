package dev.gomoku.yixindroid.data.engine

import okio.BufferedSink
import okio.BufferedSource

/**
 * Where the piskvork lines go. [EngineConnection] owns the framing, the state
 * machine and the reader/writer coroutines; it does not care whether the bytes
 * cross a VPN or a pipe to a child process on this phone.
 *
 * That split is the whole of the on-device work at this layer: the parser, the
 * aggregator, the handshake and the reconnect loop above it are unchanged.
 */
interface EngineTransport {

    /** For the console banner and the notification — `100.x.y.z:5050` or `on-device`. */
    val label: String

    /**
     * Open one session. Called on the IO dispatcher, so blocking here is fine.
     * Throws if the far end cannot be reached (or, locally, cannot be started).
     */
    suspend fun open(): EngineChannel
}

/**
 * One open session's two pipes plus the way to tear it down. [close] must be
 * safe to call twice — the connection closes on hang-up, on a read error and on
 * the next open, and those races are normal.
 */
class EngineChannel(
    val source: BufferedSource,
    val sink: BufferedSink,
    private val onClose: () -> Unit,
) {
    fun close() {
        runCatching { onClose() }
    }
}
