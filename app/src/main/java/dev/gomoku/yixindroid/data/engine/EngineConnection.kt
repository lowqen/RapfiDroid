package dev.gomoku.yixindroid.data.engine

import dev.gomoku.yixindroid.core.common.EngineDispatcher
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.ConnectionState
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Line framing and session state for one engine, whichever end it is: it opens
 * an [EngineTransport] and moves piskvork **lines** in both directions. Reads
 * run on the IO dispatcher (blocking `readUtf8Line`); writes are serialized
 * through a single-consumer channel so byte order to the engine is
 * deterministic (full-duplex, so read and write proceed concurrently).
 *
 * Nothing here knows whether the far end is `rapfi-server` across Tailscale or
 * a child process on this phone — that is [TcpTransport] versus
 * [LocalEngineTransport], and it is the only difference between the two.
 *
 * State transitions this class owns: Disconnected -> Connecting -> Handshaking,
 * and Error/Disconnected when the session ends. The piskvork-level
 * Ready/Thinking are driven by the repository via [markReady]/[markThinking].
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

    private var channel: EngineChannel? = null
    private var scope: CoroutineScope? = null
    private var outbox: Channel<String>? = null

    @Volatile
    private var closing = false

    /** Open the session and start the reader/writer loops. Throws when the far
     *  end cannot be opened (state left at [ConnectionState.Error]). */
    suspend fun open(transport: EngineTransport) {
        close()
        closing = false
        _state.value = ConnectionState.Connecting

        val ch = try {
            // Both transports block while opening — a TCP connect, or spawning
            // a process after writing 40 MB of weights out of the APK.
            withContext(ioDispatcher) { transport.open() }
        } catch (e: Exception) {
            _state.value = ConnectionState.Error(e.message ?: "connect failed")
            throw e
        }

        val source: BufferedSource = ch.source
        val sink: BufferedSink = ch.sink
        val sc = CoroutineScope(SupervisorJob())
        val out = Channel<String>(Channel.BUFFERED)
        channel = ch
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
     * Tear down a session that is open as far as the kernel is concerned but has
     * stopped answering. Unlike [close] this reports an error, so the layer
     * above treats it as a drop worth reconnecting from rather than a hang-up.
     */
    fun dropAsDead(reason: String) {
        scope?.cancel()
        scope = null
        outbox?.close()
        outbox = null
        channel?.close()
        channel = null
        _state.value = ConnectionState.Error(reason)
    }

    fun close() {
        closing = true
        scope?.cancel()
        scope = null
        outbox?.close()
        outbox = null
        channel?.close()
        channel = null
        _state.value = ConnectionState.Disconnected
    }

    private fun onEnded(error: Throwable?) {
        if (closing) return
        _state.value = error
            ?.let { ConnectionState.Error(it.message ?: "connection lost") }
            ?: ConnectionState.Disconnected
        channel?.close()
    }

    private fun fail(e: Throwable) {
        if (closing) return
        onEnded(e)
    }
}
