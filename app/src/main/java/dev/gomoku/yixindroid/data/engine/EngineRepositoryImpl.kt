package dev.gomoku.yixindroid.data.engine

import android.content.Context
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.domain.engine.CoordMapper
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.engine.ResponseParser
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped engine access. Owns the [EngineConnection], parses incoming
 * lines into [EngineResponse]s, and mirrors both directions into a console
 * stream for the P1 debug screen. Kept alive by [EngineService] so an analysis
 * session survives Activity recreation.
 */
@Singleton
class EngineRepositoryImpl @Inject constructor(
    private val connection: EngineConnection,
    @ApplicationContext private val context: Context,
    @IoDispatcher io: CoroutineDispatcher,
) : EngineRepository {

    private val scope = CoroutineScope(SupervisorJob() + io)

    // One coordinate convention for now; becomes a setting in a later phase.
    private val coord = CoordMapper()

    override val state: StateFlow<ConnectionState> = connection.state

    private val _responses = MutableSharedFlow<EngineResponse>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val responses: SharedFlow<EngineResponse> = _responses.asSharedFlow()

    private val _console = MutableSharedFlow<ConsoleLine>(
        replay = CONSOLE_REPLAY,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val console: SharedFlow<ConsoleLine> = _console.asSharedFlow()

    init {
        scope.launch {
            connection.incoming.collect { line ->
                _console.emit(ConsoleLine(outbound = false, text = line))
                _responses.emit(ResponseParser.parse(line, coord))
            }
        }
    }

    override suspend fun connect(endpoint: EngineEndpoint) {
        EngineService.start(context)
        try {
            connection.open(endpoint) // Connecting -> Handshaking, or throws
            handshake()
        } catch (e: Exception) {
            EngineService.stop(context) // don't leave a foreground service on failure
            throw e
        }
    }

    override suspend fun send(command: EngineCommand) {
        val text = command.serialize(coord)
        _console.emit(ConsoleLine(outbound = true, text = text))
        // BOARD/YXBOARD blocks embed '\n'; write each physical line in order.
        for (line in text.split('\n')) {
            connection.writeLine(line)
        }
    }

    override fun disconnect() {
        connection.close()
        EngineService.stop(context)
    }

    /**
     * P1 handshake: drive the piskvork START, then optimistically mark Ready.
     * The server may print config/DB load noise first (tolerated — it lands in
     * the console). A socket failure flips state to Error via the reader loop.
     * P2 will subscribe-before-send to await an explicit OK and push INFO
     * config defaults (timeouts, rule, threads).
     */
    private suspend fun handshake() {
        send(EngineCommand.Start(Move.DEFAULT_SIZE))
        connection.markReady()
    }

    private companion object {
        const val CONSOLE_REPLAY = 300
    }
}
