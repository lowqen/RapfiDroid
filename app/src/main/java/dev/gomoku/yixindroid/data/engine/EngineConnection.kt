package dev.gomoku.yixindroid.data.engine

import dev.gomoku.yixindroid.core.common.EngineDispatcher
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level transport that replaces engine.exe (a transparent relay): opens one
 * TCP socket to the Rapfi server and moves piskvork **lines** in both
 * directions. Reads run on the IO dispatcher (blocking `readUtf8Line`); writes
 * are serialized through a single-consumer channel so byte order to the server
 * is deterministic (full-duplex, so read and write proceed concurrently).
 *
 * State transitions this class owns: Disconnected -> Connecting -> Handshaking,
 * and Error/Disconnected on socket end. The piskvork-level Ready/Thinking are
 * driven by the repository via [markReady]/[markThinking].
 */
@Singleton
class EngineConnection @Inject constructor(
    @EngineDispatcher private val writeDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    private var socket: Socket? = null
    private var scope: CoroutineScope? = null
    private var outbox: Channel<String>? = null

    @Volatile
    private var closing = false

    /** Open the socket and start the reader/writer loops. Throws on connect
     *  failure (state left at [ConnectionState.Error]). */
    suspend fun open(endpoint: EngineEndpoint) {
        close()
        closing = false
        _state.value = ConnectionState.Connecting

        val s = try {
            withContext(ioDispatcher) {
                Socket().apply {
                    tcpNoDelay = true
                    // The far end is on-demand and the path crosses a VPN and a
                    // phone radio. Keepalive lets the kernel notice a peer that
                    // vanished without a FIN, which is how these links usually
                    // die; the repository's own idle ping covers the rest.
                    keepAlive = true
                    connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
                }
            }
        } catch (e: Exception) {
            _state.value = ConnectionState.Error(e.message ?: "connect failed")
            throw e
        }

        val source: BufferedSource = s.source().buffer()
        val sink: BufferedSink = s.sink().buffer()
        val sc = CoroutineScope(SupervisorJob())
        val out = Channel<String>(Channel.BUFFERED)
        socket = s
        scope = sc
        outbox = out
        _state.value = ConnectionState.Handshaking

        // Writer: one consumer preserves order regardless of thread.
        sc.launch(writeDispatcher) {
            try {
                for (line in out) {
                    sink.writeUtf8(line)
                    sink.writeUtf8("\n")
                    sink.flush()
                }
            } catch (e: Exception) {
                fail(e)
            }
        }

        // Reader: blocking line reads on the IO pool.
        sc.launch(ioDispatcher) {
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    _incoming.emit(line)
                }
                onEnded(null)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    suspend fun writeLine(line: String) {
        outbox?.send(line)
    }

    fun markReady() {
        if (_state.value is ConnectionState.Handshaking) _state.value = ConnectionState.Ready
    }

    fun markThinking() {
        if (_state.value is ConnectionState.Ready) _state.value = ConnectionState.Thinking
    }

    fun markSettled() {
        if (_state.value is ConnectionState.Thinking) _state.value = ConnectionState.Ready
    }

    /**
     * Tear down a socket that is open as far as the kernel is concerned but has
     * stopped answering. Unlike [close] this reports an error, so the layer
     * above treats it as a drop worth reconnecting from rather than a hang-up.
     */
    fun dropAsDead(reason: String) {
        scope?.cancel()
        scope = null
        outbox?.close()
        outbox = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        _state.value = ConnectionState.Error(reason)
    }

    fun close() {
        closing = true
        scope?.cancel()
        scope = null
        outbox?.close()
        outbox = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        _state.value = ConnectionState.Disconnected
    }

    private fun onEnded(error: Throwable?) {
        if (closing) return
        _state.value = error
            ?.let { ConnectionState.Error(it.message ?: "connection lost") }
            ?: ConnectionState.Disconnected
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    private fun fail(e: Throwable) {
        if (closing) return
        onEnded(e)
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
    }
}
