package dev.gomoku.yixindroid.data.engine

import android.content.Context
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AnalyzeParams
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.core.model.EngineParams
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.domain.engine.CoordMapper
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.engine.ResponseParser
import dev.gomoku.yixindroid.domain.engine.SearchAggregator
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngineRepositoryImpl @Inject constructor(
    private val connection: EngineConnection,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : EngineRepository {

    private val scope = CoroutineScope(SupervisorJob() + io)

    // One coordinate convention for now; becomes a setting in a later phase.
    private val coord = CoordMapper()

    /** Pushed on every connect; P4 will let the settings screen replace it. */
    @Volatile
    private var engineParams = EngineParams()

    override val state: StateFlow<ConnectionState> = connection.state

    private val _responses = MutableSharedFlow<EngineResponse>(
        replay = 0,
        extraBufferCapacity = 512,
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
            connection.open(endpoint)
            handshake()
        } catch (e: Exception) {
            EngineService.stop(context)
            throw e
        }
    }

    override suspend fun send(command: EngineCommand) = dispatch(command)

    private suspend fun dispatch(command: EngineCommand) {
        val text = command.serialize(coord)
        _console.emit(ConsoleLine(outbound = true, text = text))
        for (line in text.split('\n')) connection.writeLine(line)
    }

    override fun disconnect() {
        connection.close()
        EngineService.stop(context)
    }

    override fun analyze(position: Position, params: AnalyzeParams): Flow<AnalysisSnapshot> =
        channelFlow {
            val aggregator = SearchAggregator(position.sideToMove)
            val collector = launch {
                responses.collect { response ->
                    aggregator.consume(response)?.let { trySend(it) }
                }
            }
            // No START here: the desktop sends it once in init_engine and then
            // only `yxboard` per analysis (main.c send_board). A START mid-session
            // resets the engine and cost us a redundant round trip.
            dispatch(EngineCommand.YxBoard(position.placements()))
            dispatch(EngineCommand.YxNbest(params.multiPv.coerceAtLeast(1)))
            connection.markThinking()

            awaitClose {
                collector.cancel()
                scope.launch(NonCancellable) {
                    runCatching { dispatch(EngineCommand.YxStop) }
                    connection.markSettled()
                }
            }
        }

    override suspend fun forbidden(position: Position): List<Move> {
        val deferred = scope.async {
            responses.filterIsInstance<EngineResponse.Forbid>().first().cells
        }
        dispatch(EngineCommand.Start(position.size))
        dispatch(EngineCommand.YxBoard(position.placements()))
        dispatch(EngineCommand.YxShowForbid)
        return withTimeoutOrNull(FORBID_TIMEOUT_MS) { deferred.await() }
            ?: emptyList<Move>().also { deferred.cancel() }
    }

    /**
     * Handshake, ported from the desktop `init_engine()` (main.c:14460). The
     * order matters and the first two lines are **not optional**: they switch
     * Rapfi into the detailed output mode that emits `INFO PV/DEPTH/EVAL/
     * WINRATE/BESTLINE`. Without them the engine only prints
     * `MESSAGE Depth 2-3 | Eval 814 | …`, which is why analysis appeared in the
     * console but never on the board.
     *
     * The server may print config/DB noise before this — tolerated, it lands in
     * the console. A socket failure flips state to Error via the reader loop.
     */
    private suspend fun handshake() {
        val params = engineParams
        dispatch(EngineCommand.ShowDetail(SHOW_DETAIL_LEVEL))
        dispatch(EngineCommand.YxShowInfo)
        dispatch(EngineCommand.DatabaseReadonly(false))
        // Rule first, then START, then the rest — exactly the desktop's order.
        // Skipping these left Rapfi on its own config (freestyle rule, default
        // threads/hash), so no score or best move matched the PC.
        for ((key, value) in params.infoPairs()) {
            if (key == RULE_KEY) dispatch(EngineCommand.Info(key, value))
        }
        dispatch(EngineCommand.Start(params.boardSize))
        for ((key, value) in params.infoPairs()) {
            if (key != RULE_KEY) dispatch(EngineCommand.Info(key, value))
        }
        connection.markReady()
    }

    override suspend fun applyParams(params: EngineParams) {
        engineParams = params
        for ((key, value) in params.infoPairs()) {
            dispatch(EngineCommand.Info(key, value))
        }
    }

    private companion object {
        const val CONSOLE_REPLAY = 300
        const val FORBID_TIMEOUT_MS = 3_000L
        const val SHOW_DETAIL_LEVEL = 3
        const val RULE_KEY = "rule"
    }
}
