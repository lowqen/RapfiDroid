package dev.gomoku.yixindroid.data.prove

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.AnalyzeParams
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.ComputerSide
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.EngineCapabilities
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.core.model.EngineParams
import dev.gomoku.yixindroid.core.model.GameEnd
import dev.gomoku.yixindroid.core.model.GameReport
import dev.gomoku.yixindroid.core.model.GameResult
import dev.gomoku.yixindroid.core.model.GameState
import dev.gomoku.yixindroid.core.model.GradingPreset
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.ProveKind
import dev.gomoku.yixindroid.core.model.ProveMark
import dev.gomoku.yixindroid.core.model.ProveOptions
import dev.gomoku.yixindroid.core.model.ProvePhase
import dev.gomoku.yixindroid.core.model.QueueEntry
import dev.gomoku.yixindroid.core.model.ReviewBudget
import dev.gomoku.yixindroid.core.model.ReviewProgress
import dev.gomoku.yixindroid.core.model.SettingsFile
import dev.gomoku.yixindroid.core.model.StoneColor
import dev.gomoku.yixindroid.core.model.Swap2Choice
import dev.gomoku.yixindroid.core.model.TapResult
import dev.gomoku.yixindroid.core.model.GameFileContent
import dev.gomoku.yixindroid.domain.engine.CoordMapper
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.engine.ResponseParser
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.GameRepository
import dev.gomoku.yixindroid.domain.repository.ProveStart
import dev.gomoku.yixindroid.domain.repository.ReviewRepository
import dev.gomoku.yixindroid.domain.repository.ReviewStart
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The prove pipeline's engine conversation against main.c:9435-9859: which
 * command goes out in which phase, and — the part that touches shared data — what
 * ends up in a `yxedittvddatabase` line.
 *
 * `runCurrent()` throughout: the repository arms a watchdog and a 500 ms pulse,
 * so advancing to idle would never return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProveRepositoryTest {

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

        override suspend fun stop() {
            sent += "YXSTOP"
        }
    }

    /** The prove run only reads the board's line and its game state. */
    private class FakeGame(size: Int = 15) : GameRepository {
        val positionFlow = MutableStateFlow(Position(size = size))
        val stateFlow = MutableStateFlow(GameState())

        override val position: StateFlow<Position> get() = positionFlow
        override val future: StateFlow<List<Move>> = MutableStateFlow(emptyList())
        override val forbidden: StateFlow<List<Move>> = MutableStateFlow(emptyList())
        override val state: StateFlow<GameState> get() = stateFlow

        override suspend fun tap(cell: Move) = TapResult.Ignored
        override suspend fun engineMove() = TapResult.Ignored
        override suspend fun stopThinking() = Unit
        override suspend fun newGame(resetClock: Boolean) = Unit
        override suspend fun setComputerSide(side: ComputerSide) = Unit
        override suspend fun undo() = Unit
        override suspend fun redo() = Unit
        override suspend fun toStart() = Unit
        override suspend fun toEnd() = Unit
        override suspend fun jumpTo(index: Int) = Unit
        override suspend fun replaceLine(moves: List<Move>) {
            positionFlow.value = positionFlow.value.copy(moves = moves)
        }

        override suspend fun answerSwap(yes: Boolean) = Unit
        override suspend fun answerSwap2(choice: Swap2Choice) = Unit
        override suspend fun answerFifthCount(count: Int) = Unit
        override suspend fun offerDraw() = Unit
        override suspend fun resign() = Unit
        override fun setAnalyzing(on: Boolean) = Unit
        override fun dismissPrompt() = Unit
    }

    private class FakeSettings(initial: AppSettings) : SettingsRepository {
        val flow = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> get() = flow
        override val loaded: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            flow.value = transform(flow.value)
        }

        override suspend fun set(id: String, raw: String) = Unit
        override suspend fun resetToDefaults() = Unit
        override fun export(file: SettingsFile): String = ""
        override suspend fun import(text: String, file: SettingsFile): Int = 0
    }

    /** Only the run flag matters: a prove refuses to start while a review runs. */
    private class FakeReview : ReviewRepository {
        val progressFlow = MutableStateFlow(ReviewProgress())
        override val progress: StateFlow<ReviewProgress> get() = progressFlow
        override val report: StateFlow<GameReport?> = MutableStateFlow(null)
        override val queueReports: StateFlow<List<GameReport>> = MutableStateFlow(emptyList())
        override val queue: StateFlow<List<QueueEntry>> = MutableStateFlow(emptyList())
        override val log: StateFlow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun start(budget: ReviewBudget) = ReviewStart.Started
        override suspend fun startQueue(budget: ReviewBudget) = ReviewStart.Started
        override suspend fun cancel() = Unit
        override fun enqueue(entries: List<QueueEntry>) = Unit
        override fun removeQueued(uri: String) = Unit
        override fun clearQueue() = Unit
        override suspend fun loadGame(content: GameFileContent) = Unit
        override fun setPreset(preset: GradingPreset) = Unit
        override fun clearReport() = Unit
    }

    private class Harness(
        val engine: FakeEngine,
        val game: FakeGame,
        val review: FakeReview,
        val prove: ProveRepositoryImpl,
    ) {
        suspend fun receive(line: String) {
            engine.responseFlow.emit(ResponseParser.parse(line, CoordMapper()))
        }

        /** A `yxquerydatabaseone` reply. Tag 0 = the position has no record. */
        suspend fun record(tag: Char?, value: Int = 0, depth: Int = 0) {
            receive("MESSAGE DATABASE ONE ${tag?.code ?: 0} $value $depth 3")
        }

        /** One finished search: PVs, then the best move the engine settles on. */
        suspend fun answer(vararg pvs: Triple<String, Double, Int>) {
            pvs.forEachIndexed { index, (best, winRate, mate) ->
                receive("INFO PV $index")
                receive("INFO DEPTH 17")
                if (mate != 0) {
                    receive("INFO EVAL ${if (mate > 0) "+M$mate" else "-M${-mate}"}")
                }
                receive("INFO WINRATE ${"%.4f".format(winRate)}")
                receive("INFO BESTLINE ${best}")
                receive("INFO PV DONE")
            }
            receive(pvs.first().first)
        }
    }

    private fun proveTest(
        settings: AppSettings = AppSettings(useDatabase = true, databaseReadonly = false),
        body: suspend TestScope.(Harness) -> Unit,
    ) = runTest {
        val engine = FakeEngine()
        val game = FakeGame()
        val review = FakeReview()
        val repository = ProveRepositoryImpl(
            engine, game, review, FakeSettings(settings), StandardTestDispatcher(testScheduler),
        )
        val h = Harness(engine, game, review, repository)
        try {
            runCurrent()
            body(h)
        } finally {
            repository.shutdown()
        }
    }

    private val h8 = Move.fromLabel("H8")!!
    private val i9 = Move.fromLabel("I9")!!
    private val g7 = Move.fromLabel("G7")!!

    private val options = ProveOptions(budget0Sec = 5, budgetMaxSec = 320, nbest = 4)

    // ---- guards -------------------------------------------------------------

    @Test
    fun `a read-only database refuses the run — the proof would be discarded`() =
        proveTest(AppSettings(useDatabase = true, databaseReadonly = true)) { h ->
            val refusal = h.prove.start(options) as ProveStart.Refused
            assertThat(refusal.reason).contains("읽기 전용")
        }

    @Test
    fun `a disabled database refuses the run`() =
        proveTest(AppSettings(useDatabase = false)) { h ->
            assertThat(h.prove.start(options)).isInstanceOf(ProveStart.Refused::class.java)
        }

    @Test
    fun `a decided game has nothing to prove`() = proveTest { h ->
        h.game.stateFlow.value =
            GameState(result = GameResult(end = GameEnd.FIVE, winner = StoneColor.BLACK))
        val refusal = h.prove.start(options) as ProveStart.Refused
        assertThat(refusal.reason).contains("승부가 결정")
    }

    @Test
    fun `a running review blocks the prove`() = proveTest { h ->
        h.review.progressFlow.value = ReviewProgress(running = true)
        assertThat(h.prove.start(options)).isInstanceOf(ProveStart.Refused::class.java)
    }

    @Test
    fun `a game search blocks the prove`() = proveTest { h ->
        h.game.stateFlow.value = GameState(thinking = true)
        assertThat(h.prove.start(options)).isInstanceOf(ProveStart.Refused::class.java)
    }

    @Test
    fun `a second start while running is refused`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        assertThat(h.prove.start(options)).isEqualTo(ProveStart.Started)
        runCurrent()
        assertThat(h.prove.start(options)).isInstanceOf(ProveStart.Refused::class.java)
    }

    // ---- the conversation ---------------------------------------------------

    @Test
    fun `the run takes over the engine limits and queries the root first`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.engine.sent.clear()
        assertThat(h.prove.start(options)).isEqualTo(ProveStart.Started)
        runCurrent()
        assertThat(h.engine.sent).containsExactly(
            "INFO timeout_match 2000000000",
            "INFO max_node -1",
            "INFO max_depth 225",
            // The root is the line on the board, so its path is that line.
            "yxquerydatabaseone\n7,7\ndone",
        ).inOrder()
        assertThat(h.prove.progress.value.phase).isEqualTo(ProvePhase.QUERY)
    }

    @Test
    fun `a position without a record is searched with yxnbest k`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.engine.sent.clear()
        h.record(tag = null)
        runCurrent()
        assertThat(h.engine.sent).containsExactly(
            "START 15",
            "YXBOARD\n7,7,2\nDONE",
            "INFO time_left 2000000000",
            "INFO timeout_turn 5000",
            "YXNBEST 4",
        ).inOrder()
        assertThat(h.prove.progress.value.phase).isEqualTo(ProvePhase.SEARCH)
    }

    @Test
    fun `a depth budget opens the clock and caps the depth instead`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options.copy(byDepth = true, depth0 = 14))
        runCurrent()
        h.engine.sent.clear()
        h.record(tag = null)
        runCurrent()
        assertThat(h.engine.sent).contains("INFO timeout_turn 1000000000")
        assertThat(h.engine.sent).contains("INFO max_depth 14")
    }

    /**
     * yixindb labels are mover perspective, so at the root — where the side to
     * move is the attacker — `l` means *this* side wins.
     */
    @Test
    fun `a database label settles the root without a single search`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.engine.sent.clear()
        h.record(tag = 'l', value = 29990, depth = 12)
        runCurrent()

        val outcome = h.prove.outcome.value!!
        assertThat(outcome.resolved).isTrue()
        assertThat(outcome.win).isTrue()
        assertThat(outcome.kind).isEqualTo(ProveKind.DB)
        assertThat(outcome.searches).isEqualTo(0)
        // Nothing is written back: the record was already there.
        assertThat(outcome.dbWrites).isEqualTo(0)
        assertThat(h.engine.sent).doesNotContain("yxedittvddatabase")
        assertThat(h.engine.sent.last()).isEqualTo("yxsavedatabase")
        assertThat(h.prove.progress.value.running).isFalse()
    }

    @Test
    fun `a w label at the root means the side to move cannot win`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.record(tag = 'w', value = -29990, depth = 12)
        runCurrent()
        val outcome = h.prove.outcome.value!!
        assertThat(outcome.resolved).isTrue()
        assertThat(outcome.win).isFalse()
    }

    @Test
    fun `a draw record also refutes a win claim`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.record(tag = 'd')
        runCurrent()
        assertThat(h.prove.outcome.value!!.win).isFalse()
    }

    /**
     * The write is the whole reason this phase is separate: the record is stored
     * from the perspective of the side that moved *into* the position, so a mate
     * for the side to move is stored as `L` (76).
     */
    @Test
    fun `a proven mate is written back with the mover-perspective label`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.record(tag = null)
        runCurrent()
        h.engine.sent.clear()
        h.answer(Triple("6,8", 0.99, 4))
        runCurrent()

        assertThat(h.engine.sent.first())
            .isEqualTo("yxedittvddatabase 7 76 29996 17\n7,7\ndone")
        // The write is acknowledged with another DATABASE ONE; then the run ends.
        assertThat(h.prove.progress.value.phase).isEqualTo(ProvePhase.EDIT)
        h.record(tag = 'l', value = 29996, depth = 17)
        runCurrent()
        val outcome = h.prove.outcome.value!!
        assertThat(outcome.resolved).isTrue()
        assertThat(outcome.win).isTrue()
        assertThat(outcome.kind).isEqualTo(ProveKind.MATE)
        assertThat(outcome.dbWrites).isEqualTo(1)
    }

    @Test
    fun `a mate against the side to move is written as a loss for it`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.record(tag = null)
        runCurrent()
        h.engine.sent.clear()
        h.answer(Triple("6,8", 0.01, -6))
        runCurrent()
        // 87 = 'W': the previous mover wins, i.e. the side to move here loses.
        assertThat(h.engine.sent.first()).startsWith("yxedittvddatabase 7 87 -29994 17")
    }

    @Test
    fun `an unresolved search escalates and moves on to the best defense`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.record(tag = null)
        runCurrent()
        // No mate: the root expands, doubles its budget and the defense is next.
        h.answer(Triple("6,8", 0.60, 0), Triple("8,6", 0.40, 0))
        runCurrent()
        assertThat(h.prove.progress.value.searches).isEqualTo(1)
        assertThat(h.prove.progress.value.phase).isEqualTo(ProvePhase.QUERY)
        // The query now addresses the root line plus the attacker's best move.
        assertThat(h.engine.sent.last()).isEqualTo("yxquerydatabaseone\n7,7\n6,8\ndone")

        h.engine.sent.clear()
        h.record(tag = null)
        runCurrent()
        // A defender node enumerates every defense instead of the best move.
        assertThat(h.engine.sent).contains("yxsearchdefend")
        assertThat(h.engine.sent).doesNotContain("YXNBEST 4")
        assertThat(h.prove.progress.value.attack).isFalse()
    }

    @Test
    fun `the overlay marks the root candidates while the run goes on`() = proveTest { h ->
        h.game.replaceLine(listOf(h8, i9, g7))
        h.prove.start(options)
        runCurrent()
        h.record(tag = null)
        runCurrent()
        h.answer(Triple("6,8", 0.60, 0), Triple("8,6", 0.40, 0))
        runCurrent()

        val overlay = h.prove.overlay.value
        val best = Move(x = 8, y = 6)
        assertThat(overlay.marks[best]).isEqualTo(ProveMark.OPEN)
        assertThat(overlay.ghost[best]).isEqualTo(1)
        assertThat(overlay.rootLen).isEqualTo(3)
        // The second candidate is latent until the first one fails.
        assertThat(overlay.marks[Move(x = 6, y = 8)]).isEqualTo(ProveMark.LATENT)
    }

    // ---- watchdog and cancel ------------------------------------------------

    @Test
    fun `a silent engine gets the query again`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.engine.sent.clear()
        advanceTimeBy(61_000)
        runCurrent()
        assertThat(h.engine.sent).contains("yxquerydatabaseone\n7,7\ndone")
    }

    @Test
    fun `a stalled search is stopped, retried, and its late move ignored`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.record(tag = null)
        runCurrent()
        h.engine.sent.clear()

        advanceTimeBy(5 * 2 * 1000L + 61_000)
        runCurrent()
        assertThat(h.engine.sent).contains("YXSTOP")
        assertThat(h.engine.sent).contains("YXNBEST 4")
        assertThat(h.prove.progress.value.searches).isEqualTo(2)

        // The stopped search still reports one move; it must not be read as the
        // answer to the search that just started.
        h.receive("7,7")
        runCurrent()
        assertThat(h.prove.progress.value.phase).isEqualTo(ProvePhase.SEARCH)
    }

    @Test
    fun `cancelling stops the search and puts the user's limits back`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.record(tag = null)
        runCurrent()
        h.engine.sent.clear()
        h.prove.cancel()
        runCurrent()

        assertThat(h.engine.sent).contains("YXSTOP")
        assertThat(h.engine.sent).contains("yxsavedatabase")
        assertThat(h.engine.sent.any { it.startsWith("INFO max_node") }).isTrue()
        assertThat(h.prove.progress.value.running).isFalse()
        assertThat(h.prove.overlay.value.active).isFalse()
        assertThat(h.prove.outcome.value!!.cancelled).isTrue()
        assertThat(h.prove.outcome.value!!.resolved).isFalse()
    }

    @Test
    fun `the log records the start line and the conclusion`() = proveTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.start(options)
        runCurrent()
        h.record(tag = 'l', value = 29990, depth = 12)
        runCurrent()
        assertThat(h.prove.log.value.first()).isEqualTo("증명: 백 차례, 노드당 예산 5..320초, nbest 4")
        assertThat(h.prove.log.value).contains("증명: WIN db(db) (root)")
        assertThat(h.prove.log.value.last()).startsWith("증명 완료:")
    }
}
