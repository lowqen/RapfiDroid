package dev.gomoku.yixindroid.data.engine

import android.content.Context
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AnalyzeParams
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.DatabaseAttachGuard
import dev.gomoku.yixindroid.core.model.EngineCapabilities
import dev.gomoku.yixindroid.core.model.EngineParams
import dev.gomoku.yixindroid.core.model.EngineTarget
import dev.gomoku.yixindroid.core.model.LinkHealth
import dev.gomoku.yixindroid.core.model.LocalEngineProfile
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.data.prefs.LocalEngineStore
import dev.gomoku.yixindroid.domain.engine.CoordMapper
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.engine.ResponseParser
import dev.gomoku.yixindroid.domain.engine.SearchAggregator
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngineRepositoryImpl @Inject constructor(
    private val connection: EngineConnection,
    private val settingsRepository: SettingsRepository,
    private val localEngine: LocalEngineInstaller,
    private val localEngineStore: LocalEngineStore,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : EngineRepository {

    private val scope = CoroutineScope(SupervisorJob() + io)

    // One coordinate convention for now; becomes a setting in a later phase.
    private val coord = CoordMapper()

    /** Mirror of the settings' engine subset; pushed on connect and on change. */
    @Volatile
    private var engineParams = EngineParams()

    /**
     * Whether *this* engine process already holds the database — per-connection
     * state, reset by the lifecycle collector below rather than by any one call
     * site, so a reconnect attaches again. See [DatabaseAttachGuard].
     */
    private val databaseGuard = DatabaseAttachGuard()

    private val _health = MutableStateFlow(LinkHealth())
    override val health: StateFlow<LinkHealth> = _health.asStateFlow()

    /** The engine to go back to; a reconnect has nowhere to aim without it. */
    @Volatile
    private var lastTarget: EngineTarget? = null

    /**
     * Resource limits for the on-device engine, mirrored from [LocalEngineStore]
     * by the collector below. Its default has to be usable before that first
     * emission arrives: see [LocalEngineProfile] for what the desktop's own
     * numbers would do to a phone.
     */
    @Volatile
    private var localProfile = LocalEngineProfile()

    private fun transportFor(target: EngineTarget): EngineTransport = when (target) {
        is EngineTarget.Remote -> TcpTransport(target.endpoint)
        EngineTarget.Local -> LocalEngineTransport(localEngine, localProfile)
    }

    /**
     * The parameters actually sent to the engine in front of us. The user's
     * settings are the desktop's settings — 4 threads, 8192 MB, database on —
     * and they stay untouched in [engineParams] so switching back to the server
     * restores them exactly. Only what leaves for a local engine is clamped.
     */
    private fun effective(params: EngineParams): EngineParams =
        if (lastTarget?.isLocal == true) localProfile.clamp(params) else params

    /**
     * The user's intent, not the socket's state: true from [connect] until
     * [disconnect]. Everything the reconnect loop does is gated on it, so a
     * deliberate hang-up is never undone by the retry.
     */
    @Volatile
    private var wantsConnection = false

    @Volatile
    private var lastLineAt = 0L

    private var retryJob: Job? = null

    override val state: StateFlow<ConnectionState> = connection.state

    private val _capabilities = MutableStateFlow(EngineCapabilities())
    override val capabilities: StateFlow<EngineCapabilities> = _capabilities.asStateFlow()

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
        // A dead socket means a dead engine process, and the next one starts
        // with no database. Tying the reset to the state rather than to
        // disconnect() covers the paths that reconnect on their own.
        scope.launch {
            connection.state.collect { state ->
                if (state is ConnectionState.Disconnected || state is ConnectionState.Error) {
                    databaseGuard.reset()
                    // The socket died on its own. If the user still wants to be
                    // connected, that is a problem to solve, not to report.
                    if (wantsConnection) {
                        scheduleReconnect((state as? ConnectionState.Error)?.reason)
                    }
                }
            }
        }
        scope.launch { livenessLoop() }
        scope.launch {
            localEngineStore.profile.collect { profile ->
                // Computed against the old profile before it is replaced — the
                // whole point of `effective` is that it reads this field.
                val before = effective(engineParams)
                localProfile = profile
                if (lastTarget?.isLocal != true) return@collect
                val live = connection.state.value
                val settled = live is ConnectionState.Ready || live is ConnectionState.Thinking
                val after = effective(engineParams)
                if (!settled || before == after) return@collect
                // hash_size and usedatabase are live options, so this reaches the
                // running engine; only what config.toml sizes at startup would
                // need a new process, and INFO overrides that anyway.
                runCatching {
                    if (after.needsRestart(before)) handshake() else pushChanges(before, after)
                }
            }
        }
        scope.launch {
            connection.incoming.collect { line ->
                lastLineAt = System.currentTimeMillis()
                _console.emit(ConsoleLine(outbound = false, text = line))
                val response = ResponseParser.parse(line, coord)
                if (response is EngineResponse.Capability) {
                    _capabilities.update { it.with(response.key, response.value) }
                }
                // A bare `y,x` is the end of a search (main.c:13930): the engine
                // has settled on a move and is listening again. Marking it idle
                // *here* rather than when we tear the analysis flow down is what
                // keeps the next database query out of a search that is still
                // running — the engine would only answer it once the search
                // ended, which is exactly the delay the board values had.
                if (response is EngineResponse.BestMove) connection.markSettled()
                _responses.emit(response)
            }
        }
        // Settings own the engine parameters. Pushing only what changed keeps the
        // console readable; rule and board size are baked in by START, so those
        // need the whole handshake again.
        scope.launch {
            settingsRepository.settings.collect { settings ->
                val previous = engineParams
                engineParams = settings.toEngineParams()
                // Only once the session is settled: pushing mid-handshake would
                // interleave with the connect sequence.
                val live = connection.state.value
                val settled = live is ConnectionState.Ready || live is ConnectionState.Thinking
                // Compared after clamping, so a hash change the local profile
                // swallows (8192 -> 4096 MB, both 128 on device) is not a
                // needless restart.
                val before = effective(previous)
                val after = effective(engineParams)
                if (!settled || before == after) return@collect
                runCatching {
                    if (after.needsRestart(before)) handshake() else pushChanges(before, after)
                }
            }
        }
    }

    override suspend fun connect(target: EngineTarget) {
        lastTarget = target
        wantsConnection = true
        EngineService.start(context)
        try {
            connection.open(transportFor(target))
            handshake()
            _health.value = LinkHealth()
        } catch (e: Exception) {
            EngineService.stop(context)
            wantsConnection = false
            throw e
        }
    }

    /**
     * The reconnect loop. A dropped socket is not an error the user should have
     * to answer: the server is on-demand and the phone's radio sleeps, so the
     * link goes away routinely and the only sensible response is to keep asking.
     *
     * It runs only while [wantsConnection] — set by [connect], cleared by
     * [disconnect] — so hanging up stays hung up.
     */
    private suspend fun reconnectLoop() {
        val target = lastTarget ?: return
        while (wantsConnection && !connection.state.value.isLive) {
            val attempt = _health.value.attempt + 1
            for (left in LinkHealth.delaySecondsFor(attempt) downTo 1) {
                if (!wantsConnection) return
                _health.update { it.copy(reconnecting = true, attempt = attempt, retryInSeconds = left) }
                delay(1_000)
            }
            if (!wantsConnection) return
            _health.update { it.copy(retryInSeconds = 0) }
            val ok = runCatching {
                // For a local target this respawns the engine process, which is
                // the right answer to it having died: it starts from nothing and
                // the handshake puts the position back.
                connection.open(transportFor(target))
                handshake()
            }.isSuccess
            if (ok) {
                _health.update {
                    LinkHealth(recovered = it.recovered + 1)
                }
                return
            }
        }
    }

    override suspend fun retryNow() {
        retryJob?.cancel()
        val target = lastTarget ?: return
        wantsConnection = true
        _health.update { it.copy(reconnecting = true, retryInSeconds = 0) }
        runCatching {
            connection.open(transportFor(target))
            handshake()
        }.onSuccess { _health.update { LinkHealth(recovered = it.recovered) } }
            .onFailure { scheduleReconnect(it.message) }
    }

    private fun scheduleReconnect(reason: String?) {
        if (!wantsConnection) return
        if (retryJob?.isActive == true) return
        _health.update { it.copy(reconnecting = true, lastError = reason ?: it.lastError) }
        retryJob = scope.launch { reconnectLoop() }
    }

    /**
     * Liveness. A TCP socket over a VPN can be dead for minutes without either
     * end noticing, and the symptom — an analysis that never answers — reads as
     * a hung engine. So when a settled link has been silent for a while, ask it
     * something harmless: `ABOUT` is answered by every piskvork engine at any
     * time and costs the search nothing. No answer means the link is gone,
     * which the reader loop then reports like any other drop.
     */
    private suspend fun livenessLoop() {
        while (true) {
            delay(1_000)
            if (connection.state.value !is ConnectionState.Ready) continue
            val silentFor = (System.currentTimeMillis() - lastLineAt) / 1000
            if (silentFor < LinkHealth.IDLE_PING_SECONDS) continue
            val before = lastLineAt
            runCatching { dispatch(EngineCommand.About, echo = false) }
            withTimeoutOrNull(LinkHealth.PING_REPLY_SECONDS * 1000L) {
                while (lastLineAt == before) delay(200)
            } ?: connection.dropAsDead("응답 없음 (${LinkHealth.IDLE_PING_SECONDS}초)")
        }
    }

    override suspend fun send(command: EngineCommand) = dispatch(command)

    /** @param echo false for the liveness ping, which is noise in the console. */
    private suspend fun dispatch(command: EngineCommand, echo: Boolean = true) {
        // One choke point for the whole app: the handshake, a settings push and
        // the `dbrefresh` console command all come through here, and any of them
        // repeating `usedatabase 1` would cost a second copy of the database.
        if (command is EngineCommand.Info && !databaseGuard.allow(command.key, command.value)) {
            return
        }
        val text = command.serialize(coord)
        if (echo) _console.emit(ConsoleLine(outbound = true, text = text))
        for (line in text.split('\n')) connection.writeLine(line)
    }

    override fun disconnect() {
        // Order matters: clear the intent before closing, or the state collector
        // sees the drop and starts reconnecting to the session just ended.
        wantsConnection = false
        retryJob?.cancel()
        retryJob = null
        _health.value = LinkHealth()
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
            // settings.txt line 25: drop the TT first when the user asked for it,
            // so a search cannot be biased by the previous position's entries.
            if (settingsRepository.settings.value.hashAutoClear) {
                dispatch(EngineCommand.YxHashClear)
            }
            // No START here: the desktop sends it once in init_engine and then
            // only `yxboard` per analysis (main.c send_board). A START mid-session
            // resets the engine and cost us a redundant round trip.
            dispatch(EngineCommand.YxBoard(position.placements()))
            // `searchdefend` is an ordinary analysis with a different opening
            // command — main.c:10883 pushes the board and sends `yxsearchdefend`
            // where `nbest` sends `yxnbest`, and reads the answer the same way.
            if (params.defend) {
                dispatch(EngineCommand.YxSearchDefend)
            } else {
                dispatch(EngineCommand.YxNbest(params.multiPv.coerceAtLeast(1)))
            }
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
        // yxboard alone is enough; a START here would reset the session mid-game.
        dispatch(EngineCommand.YxBoard(position.placements()))
        dispatch(EngineCommand.YxShowForbid)
        return withTimeoutOrNull(FORBID_TIMEOUT_MS) { deferred.await() }
            ?: emptyList<Move>().also { deferred.cancel() }
    }

    /**
     * Balance search. The desktop does exactly three things (main.c:10862):
     * push the board, mark itself as thinking, send `yxbalanceone|two <bias>`.
     * The answer arrives on the ordinary best-move channel — one coordinate, or
     * two for `balancetwo`, which is why the parser accepts a pair.
     *
     * There is no time limit of our own: the engine stops when its own budget
     * runs out or when the user presses stop. The cap below only keeps a lost
     * reply from pinning the UI in "thinking" forever.
     */
    override suspend fun balance(position: Position, two: Boolean, bias: Int): List<Move> {
        val deferred = scope.async {
            responses.filterIsInstance<EngineResponse.BestMove>().first().moves
        }
        dispatch(EngineCommand.YxBoard(position.placements()))
        dispatch(EngineCommand.YxBalance(two = two, bias = bias))
        connection.markThinking()
        return try {
            withTimeoutOrNull(BALANCE_TIMEOUT_MS) { deferred.await() }
                ?: emptyList<Move>().also { deferred.cancel() }
        } finally {
            connection.markSettled()
        }
    }

    override suspend fun stop() = dispatch(EngineCommand.YxStop)

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
        val params = effective(engineParams)
        dispatch(EngineCommand.ShowDetail(SHOW_DETAIL_LEVEL))
        dispatch(EngineCommand.YxShowInfo)
        dispatch(EngineCommand.DatabaseReadonly(params.databaseReadonly))
        // Rule first, then START, then the rest — exactly the desktop's order.
        // Skipping these left Rapfi on its own config (freestyle rule, default
        // threads/hash), so no score or best move matched the PC.
        val pairs = params.infoPairs()
        for ((key, value) in pairs) {
            if (key == RULE_KEY) dispatch(EngineCommand.Info(key, value))
        }
        dispatch(EngineCommand.Start(params.boardSize))
        for ((key, value) in pairs) {
            if (key !in PRE_START_KEYS) dispatch(EngineCommand.Info(key, value))
        }
        connection.markReady()
    }

    override suspend fun applyParams(params: EngineParams) {
        val previous = effective(engineParams)
        engineParams = params
        val next = effective(params)
        if (next.needsRestart(previous)) handshake() else pushChanges(previous, next)
    }

    /** Only the `INFO` pairs whose value actually changed (all of them on first push). */
    private suspend fun pushChanges(previous: EngineParams, next: EngineParams) {
        val before = previous.infoPairs().toMap()
        for ((key, value) in next.infoPairs()) {
            if (before[key] != value) dispatch(EngineCommand.Info(key, value))
        }
    }

    private companion object {
        const val CONSOLE_REPLAY = 300
        const val FORBID_TIMEOUT_MS = 3_000L

        /** Balance searches are user-budgeted; this is only a leak guard. */
        const val BALANCE_TIMEOUT_MS = 15 * 60_000L
        const val SHOW_DETAIL_LEVEL = 3
        const val RULE_KEY = "rule"

        /** Sent before START: the rule is baked in by it, and the desktop pushes
         *  the read-only flag there too (as lowercase `info database_readonly`). */
        val PRE_START_KEYS = setOf(RULE_KEY, "database_readonly")
    }
}
