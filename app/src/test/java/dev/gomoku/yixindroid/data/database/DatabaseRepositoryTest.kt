package dev.gomoku.yixindroid.data.database

import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AnalyzeParams
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.DbDeleteFilter
import dev.gomoku.yixindroid.core.model.DbDeleteScope
import dev.gomoku.yixindroid.core.model.DbOpResult
import dev.gomoku.yixindroid.core.model.EngineCapabilities
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.core.model.EngineParams
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.SettingsFile
import dev.gomoku.yixindroid.domain.engine.CoordMapper
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.engine.ResponseParser
import dev.gomoku.yixindroid.domain.repository.DatabaseRepository
import dev.gomoku.yixindroid.domain.repository.DbPreferences
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository-level P7 checks: the guards (read-only, database off, destructive
 * lock, not connected), the query pairing that protects the position value from
 * stale replies, and the fact that each API call puts the desktop's exact wire
 * text on the socket.
 *
 * Uses `runCurrent()` rather than `advanceUntilIdle()`: the repository owns an
 * endless auto-save loop, so advancing virtual time to idle would never return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseRepositoryTest {

    private val coord = CoordMapper()

    // ---- fakes --------------------------------------------------------------

    private class FakeEngine : EngineRepository {
        val sent = mutableListOf<String>()
        val responseFlow = MutableSharedFlow<EngineResponse>(extraBufferCapacity = 64)
        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Ready)

        override val state: StateFlow<ConnectionState> get() = stateFlow
        override val responses: SharedFlow<EngineResponse> get() = responseFlow
        override val console: SharedFlow<ConsoleLine> = MutableSharedFlow()
        override val capabilities: StateFlow<EngineCapabilities> =
            MutableStateFlow(EngineCapabilities())

        override suspend fun connect(endpoint: EngineEndpoint) = Unit
        override suspend fun send(command: EngineCommand) {
            sent += command.serialize(CoordMapper())
        }

        override fun disconnect() = Unit
        override suspend fun applyParams(params: EngineParams) = Unit
        override fun analyze(position: Position, params: AnalyzeParams): Flow<AnalysisSnapshot> =
            emptyFlow()

        override suspend fun forbidden(position: Position): List<Move> = emptyList()
        override suspend fun balance(position: Position, two: Boolean, bias: Int): List<Move> =
            emptyList()

        override suspend fun stop() = Unit
    }

    private class FakeSettings(
        initial: AppSettings = AppSettings(),
    ) : SettingsRepository {
        val flow = MutableStateFlow(initial)
        val writes = mutableListOf<Pair<String, String>>()

        override val settings: StateFlow<AppSettings> get() = flow
        override val loaded: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            flow.value = transform(flow.value)
        }

        override suspend fun set(id: String, raw: String) {
            writes += id to raw
        }

        override suspend fun resetToDefaults() = Unit
        override fun export(file: SettingsFile): String = ""
        override suspend fun import(text: String, file: SettingsFile): Int = 0
    }

    private class FakePrefs(unlocked: Boolean = false) : DbPreferences {
        override val destructiveUnlocked = MutableStateFlow(unlocked)
        override val lastPath = MutableStateFlow("rapfi.db")
        override suspend fun setDestructiveUnlocked(on: Boolean) {
            destructiveUnlocked.value = on
        }

        override suspend fun setLastPath(path: String) {
            lastPath.value = path
        }
    }

    private class Harness(
        val engine: FakeEngine,
        val settings: FakeSettings,
        val prefs: FakePrefs,
        val repository: DatabaseRepository,
    )

    private fun harness(
        settings: AppSettings = AppSettings(),
        unlocked: Boolean = false,
        state: ConnectionState = ConnectionState.Ready,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): Harness {
        val engine = FakeEngine().apply { stateFlow.value = state }
        val fakeSettings = FakeSettings(settings)
        val prefs = FakePrefs(unlocked)
        return Harness(
            engine, fakeSettings, prefs,
            DatabaseRepositoryImpl(engine, fakeSettings, prefs, dispatcher),
        )
    }

    /**
     * Builds a repository on the test scheduler, runs [body], then shuts the
     * repository's own scope down so `runTest` can finish (its cleanup advances
     * virtual time, and the auto-save loop would otherwise delay forever).
     */
    private fun dbTest(
        settings: AppSettings = AppSettings(),
        unlocked: Boolean = false,
        state: ConnectionState = ConnectionState.Ready,
        body: suspend kotlinx.coroutines.test.TestScope.(Harness) -> Unit,
    ) = runTest {
        val h = harness(settings, unlocked, state, StandardTestDispatcher(testScheduler))
        try {
            body(h)
        } finally {
            (h.repository as DatabaseRepositoryImpl).shutdown()
        }
    }

    private val path = listOf(Move(7, 7), Move(8, 6))

    // ---- happy paths --------------------------------------------------------

    @Test
    fun `setting the position queries the database with that path`() = dbTest { h ->
        runCurrent()
        h.engine.sent.clear()

        h.repository.setPosition(Position(moves = path))
        runCurrent()

        assertEquals(listOf("yxquerydatabaseallt\n7,7\n6,8\ndone"), h.engine.sent)
    }

    @Test
    fun `edits send the desktop's command and re-query`() = dbTest { h ->
        h.repository.setPosition(Position(moves = path))
        runCurrent()
        h.engine.sent.clear()

        assertEquals(DbOpResult.Sent, h.repository.editCellLabel(Move(9, 5), "A"))
        runCurrent()

        assertEquals(
            listOf(
                "yxeditlabeldatabase 5,9 A\n7,7\n6,8\ndone",
                "yxquerydatabaseallt\n7,7\n6,8\ndone",
            ),
            h.engine.sent,
        )
    }

    @Test
    fun `comment edit is followed by a text query, like main c`() = dbTest { h ->
        runCurrent()
        h.engine.sent.clear()

        h.repository.editComment("hello")
        runCurrent()

        assertEquals(
            listOf("yxedittextdatabase \"hello\"\ndone", "yxquerydatabasetext\ndone"),
            h.engine.sent,
        )
    }

    @Test
    fun `split sends the board first so the engine splits here`() = dbTest(unlocked = true) { h ->
        h.repository.setPosition(Position(moves = path))
        runCurrent()
        h.engine.sent.clear()

        assertEquals(DbOpResult.Sent, h.repository.split("part.db"))
        runCurrent()

        assertEquals(2, h.engine.sent.size)
        assertTrue(h.engine.sent[0].startsWith("YXBOARD\n"))
        assertEquals("yxdbsplit\npart.db", h.engine.sent[1])
    }

    // ---- guards -------------------------------------------------------------

    @Test
    fun `read-only refuses writes but allows queries`() = dbTest(settings = AppSettings(databaseReadonly = true)) { h ->
        runCurrent()
        h.engine.sent.clear()

        val refused = h.repository.editComment("x")
        assertTrue(refused is DbOpResult.Refused)
        assertEquals(DbOpResult.Sent, h.repository.queryValue())
        runCurrent()

        assertEquals(listOf("yxquerydatabaseone\ndone"), h.engine.sent)
    }

    @Test
    fun `database off refuses everything`() = dbTest(settings = AppSettings(useDatabase = false)) { h ->
        runCurrent()
        h.engine.sent.clear()

        assertTrue(h.repository.queryValue() is DbOpResult.Refused)
        assertTrue(h.repository.save() is DbOpResult.Refused)
        h.repository.setPosition(Position(moves = path))
        runCurrent()

        assertTrue(h.engine.sent.isEmpty())
    }

    @Test
    fun `bulk delete and split need the unlock`() = dbTest { h ->
        runCurrent()
        h.engine.sent.clear()

        val scope = DbDeleteScope(DbDeleteFilter.WL, recursive = true)
        assertTrue(h.repository.deleteAll(scope) is DbOpResult.Refused)
        assertTrue(h.repository.split("x.db") is DbOpResult.Refused)
        // a single-record delete is not gated
        assertEquals(DbOpResult.Sent, h.repository.deleteOne())
        runCurrent()

        h.prefs.destructiveUnlocked.value = true
        runCurrent()
        h.engine.sent.clear()
        assertEquals(DbOpResult.Sent, h.repository.deleteAll(scope))
        runCurrent()

        assertEquals("yxdeletedatabaseall wlrecursive\ndone", h.engine.sent.first())
    }

    @Test
    fun `nothing is sent while disconnected`() = dbTest(state = ConnectionState.Disconnected) { h ->
        runCurrent()

        assertTrue(h.repository.queryValue() is DbOpResult.Refused)
        h.repository.setPosition(Position(moves = path))
        runCurrent()
        assertTrue(h.engine.sent.isEmpty())
    }

    @Test
    fun `toggles go through the settings model so one sender pushes INFO`() = dbTest { h ->
        runCurrent()

        h.repository.setEnabled(false)
        h.repository.setReadOnly(true)
        runCurrent()

        assertEquals(
            listOf("useDatabase" to "0", "databaseReadonly" to "1"),
            h.settings.writes,
        )
    }

    // ---- query pairing ------------------------------------------------------

    @Test
    fun `a stale reply cannot change the position value`() = dbTest { h ->
        // Becoming Ready already triggers one query (like the desktop after
        // init_engine); answer it so the counters start balanced.
        runCurrent()
        emit(h, "MESSAGE DATABASE DONE")
        runCurrent()

        // Two queries in flight (the user moved on before the first reply landed).
        h.repository.setPosition(Position(moves = path))
        h.repository.setPosition(Position(moves = path + Move(9, 5)))
        runCurrent()

        // Reply #1 (stale): a win for the side to move — must NOT reach the state.
        emit(h, "MESSAGE DATABASE REFRESH")
        emit(h, "MESSAGE DATABASE 5 9 ${pack("w5")} 0 0 0 0")
        emit(h, "MESSAGE DATABASE DONE")
        runCurrent()
        assertNull(h.repository.state.value.value)

        // Reply #2 (current): now the value is adopted.
        emit(h, "MESSAGE DATABASE REFRESH")
        emit(h, "MESSAGE DATABASE 5 9 ${pack("63%")} 0 0 0 0")
        emit(h, "MESSAGE DATABASE DONE")
        runCurrent()
        val value = h.repository.state.value.value!!
        assertEquals(0.63, value.stmWinRate, 1e-9)
        // 3 stones on the board -> white to move, so black's bar is mirrored.
        assertEquals(0.37, value.blackWinRate, 1e-9)
    }

    @Test
    fun `cells and comment reach the state`() = dbTest { h ->
        runCurrent()
        emit(h, "MESSAGE DATABASE DONE") // answer the on-connect query
        h.repository.setPosition(Position(moves = path))
        runCurrent()

        emit(h, "MESSAGE DATABASE REFRESH")
        emit(h, "MESSAGE DATABASE 7 8 ${pack("w39")} 0 0 0 0 hot")
        emit(h, "MESSAGE DATABASE DONE")
        emit(h, """MESSAGE DATABASE TEXT "note here"""")
        runCurrent()

        val snapshot = h.repository.state.value.snapshot
        assertEquals("W39", snapshot.cells[Move(8, 7)]?.tagLabel)
        assertEquals("hot", snapshot.cells[Move(8, 7)]?.text)
        assertEquals("note here", snapshot.comment)
    }

    private suspend fun emit(h: Harness, line: String) {
        h.engine.responseFlow.emit(ResponseParser.parse(line, coord))
    }

    private fun pack(text: String): Int = text.fold(0) { acc, c -> (acc shl 8) or c.code }
}
