package dev.gomoku.rapfidroid.data.review

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.AnalysisSnapshot
import dev.gomoku.rapfidroid.core.model.AnalyzeParams
import dev.gomoku.rapfidroid.core.model.AppSettings
import dev.gomoku.rapfidroid.core.model.ComputerSide
import dev.gomoku.rapfidroid.core.model.ConnectionState
import dev.gomoku.rapfidroid.core.model.ConsoleLine
import dev.gomoku.rapfidroid.core.model.EngineCapabilities
import dev.gomoku.rapfidroid.core.model.EngineBusy
import dev.gomoku.rapfidroid.core.model.EngineTarget
import dev.gomoku.rapfidroid.core.model.EngineParams
import dev.gomoku.rapfidroid.core.model.LinkHealth
import dev.gomoku.rapfidroid.core.model.GameFile
import dev.gomoku.rapfidroid.core.model.GameState
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.MoveQuality
import dev.gomoku.rapfidroid.core.model.Position
import dev.gomoku.rapfidroid.core.model.ProveOptions
import dev.gomoku.rapfidroid.core.model.ProveOutcome
import dev.gomoku.rapfidroid.core.model.ProveOverlay
import dev.gomoku.rapfidroid.core.model.ProveProgress
import dev.gomoku.rapfidroid.core.model.QueueEntry
import dev.gomoku.rapfidroid.core.model.QueueStatus
import dev.gomoku.rapfidroid.core.model.ReviewBudget
import dev.gomoku.rapfidroid.core.model.SettingsFile
import dev.gomoku.rapfidroid.core.model.Swap2Choice
import dev.gomoku.rapfidroid.core.model.TapResult
import dev.gomoku.rapfidroid.domain.engine.CoordMapper
import dev.gomoku.rapfidroid.domain.engine.EngineCommand
import dev.gomoku.rapfidroid.domain.engine.EngineResponse
import dev.gomoku.rapfidroid.domain.engine.ResponseParser
import dev.gomoku.rapfidroid.domain.repository.EngineRepository
import dev.gomoku.rapfidroid.domain.repository.GameFileReader
import dev.gomoku.rapfidroid.domain.repository.GameRepository
import dev.gomoku.rapfidroid.domain.repository.ProveRepository
import dev.gomoku.rapfidroid.domain.repository.ProveStart
import dev.gomoku.rapfidroid.domain.repository.ReviewStart
import dev.gomoku.rapfidroid.domain.repository.SettingsRepository
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
import javax.inject.Provider

/**
 * The review pipeline against the desktop's loop (main.c:7098 onwards): what it
 * sends per position, what it records from the answer, and how it finishes.
 *
 * `runCurrent()` throughout: the repository arms a watchdog for every search,
 * so advancing to idle would never return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewRepositoryTest {

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
        override val health: StateFlow<LinkHealth> = MutableStateFlow(LinkHealth())

        override suspend fun connect(target: EngineTarget) = Unit
        override suspend fun send(command: EngineCommand) {
            sent += command.serialize(CoordMapper())
        }

        override fun disconnect() = Unit
        override suspend fun retryNow() = Unit
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

    /** Just the board: the review only reads the line and replaces it for a queue. */
    private class FakeGame(size: Int = 15) : GameRepository {
        val positionFlow = MutableStateFlow(Position(size = size))
        val futureFlow = MutableStateFlow<List<Move>>(emptyList())
        val stateFlow = MutableStateFlow(GameState())
        var jumped: Int? = null

        override val position: StateFlow<Position> get() = positionFlow
        override val future: StateFlow<List<Move>> get() = futureFlow
        override val forbidden: StateFlow<List<Move>> = MutableStateFlow(emptyList())
        override val state: StateFlow<GameState> get() = stateFlow

        override suspend fun tap(cell: Move) = TapResult.Ignored
        override suspend fun engineMove() = TapResult.Ignored
        override suspend fun stopThinking() = Unit
        override suspend fun newGame(resetClock: Boolean) {
            positionFlow.value = positionFlow.value.copy(moves = emptyList())
            futureFlow.value = emptyList()
        }

        override suspend fun setComputerSide(side: ComputerSide) = Unit
        override suspend fun undo() = Unit
        override suspend fun redo() = Unit
        override suspend fun toStart() = Unit
        override suspend fun toEnd() = Unit
        override suspend fun jumpTo(index: Int) {
            jumped = index
        }

        override suspend fun replaceLine(moves: List<Move>) {
            positionFlow.value = positionFlow.value.copy(moves = moves)
            futureFlow.value = emptyList()
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

    private class FakeFiles(val contents: MutableMap<String, ByteArray> = mutableMapOf()) :
        GameFileReader {
        override suspend fun read(uri: String): ByteArray? = contents[uri]
    }

    /** Only the run flag matters: a review refuses to start while a prove runs. */
    private class FakeProve : ProveRepository {
        val progressFlow = MutableStateFlow(ProveProgress())
        override val progress: StateFlow<ProveProgress> get() = progressFlow
        override val overlay: StateFlow<ProveOverlay> = MutableStateFlow(ProveOverlay.EMPTY)
        override val outcome: StateFlow<ProveOutcome?> = MutableStateFlow(null)
        override val log: StateFlow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun start(options: ProveOptions) = ProveStart.Started
        override suspend fun cancel() = Unit
        override fun clearOutcome() = Unit
    }

    private class Harness(
        val engine: FakeEngine,
        val game: FakeGame,
        val files: FakeFiles,
        val prove: FakeProve,
        val review: ReviewRepositoryImpl,
    ) {
        suspend fun receive(line: String) {
            engine.responseFlow.emit(ResponseParser.parse(line, CoordMapper()))
        }

        /** One finished search: two PVs and the best move the engine settles on. */
        suspend fun answer(best: String, winRate: Double, second: Double? = null, mate: Int? = null) {
            receive("INFO PV 0")
            receive("INFO DEPTH 12")
            mate?.let { receive("INFO EVAL ${if (it > 0) "+M$it" else "-M${-it}"}") }
            receive("INFO WINRATE ${"%.4f".format(winRate)}")
            receive("INFO BESTLINE $best")
            receive("INFO PV DONE")
            if (second != null) {
                receive("INFO PV 1")
                receive("INFO DEPTH 12")
                receive("INFO WINRATE ${"%.4f".format(second)}")
                receive("INFO BESTLINE $best")
                receive("INFO PV DONE")
            }
            receive(best)
        }
    }

    private fun reviewTest(
        settings: AppSettings = AppSettings(skipOpening = false, rule = 0),
        body: suspend TestScope.(Harness) -> Unit,
    ) = runTest {
        val engine = FakeEngine()
        val game = FakeGame()
        val files = FakeFiles()
        val prove = FakeProve()
        val repository = ReviewRepositoryImpl(
            engine, game, FakeSettings(settings), files, Provider { prove }, EngineBusy(),
            StandardTestDispatcher(testScheduler),
        )
        val h = Harness(engine, game, files, prove, repository)
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

    // ---- guards -------------------------------------------------------------

    @Test
    fun `an empty board has nothing to review`() = reviewTest { h ->
        val result = h.review.start(ReviewBudget())
        assertThat(result).isInstanceOf(ReviewStart.Refused::class.java)
    }

    @Test
    fun `a disconnected engine refuses the run`() = reviewTest { h ->
        h.game.replaceLine(listOf(h8))
        h.engine.stateFlow.value = ConnectionState.Disconnected
        assertThat(h.review.start(ReviewBudget())).isInstanceOf(ReviewStart.Refused::class.java)
    }

    @Test
    fun `a game search blocks the review, like the desktop`() = reviewTest { h ->
        h.game.replaceLine(listOf(h8))
        h.game.stateFlow.value = GameState(thinking = true)
        assertThat(h.review.start(ReviewBudget())).isInstanceOf(ReviewStart.Refused::class.java)
    }

    /** main.c:7195 — the two pipelines would fight over the same engine. */
    @Test
    fun `a running prove blocks the review`() = reviewTest { h ->
        h.game.replaceLine(listOf(h8))
        h.prove.progressFlow.value = ProveProgress(running = true)
        val refusal = h.review.start(ReviewBudget()) as ReviewStart.Refused
        assertThat(refusal.reason).contains("증명")
    }

    // ---- the loop -----------------------------------------------------------

    @Test
    fun `the budget goes out once and every position is searched with yxnbest 2`() =
        reviewTest { h ->
            h.game.replaceLine(listOf(h8, i9))
            h.engine.sent.clear()

            assertThat(h.review.start(ReviewBudget(seconds = 3))).isEqualTo(ReviewStart.Started)
            runCurrent()
            // review_send_budget, then the first position (empty board).
            assertThat(h.engine.sent).containsExactly(
                "INFO timeout_turn 3000",
                "INFO timeout_match 2000000000",
                "INFO max_node -1",
                "INFO max_depth 225",
                "INFO time_left 2000000000",
                "START 15",
                "YXBOARD\nDONE",
                "YXNBEST 2",
            ).inOrder()
            assertThat(h.review.progress.value.running).isTrue()
            assertThat(h.review.progress.value.total).isEqualTo(2)
        }

    @Test
    fun `a depth budget opens the clock instead`() = reviewTest { h ->
        h.game.replaceLine(listOf(h8))
        h.engine.sent.clear()
        h.review.start(ReviewBudget(byDepth = true, depth = 20))
        runCurrent()
        assertThat(h.engine.sent).contains("INFO timeout_turn 1000000000")
        assertThat(h.engine.sent).contains("INFO max_depth 20")
    }

    @Test
    fun `each answer advances one position and the run ends with a report`() = reviewTest { h ->
        h.game.replaceLine(listOf(h8, i9))
        h.review.start(ReviewBudget(seconds = 1))
        runCurrent()

        h.answer("7,7", winRate = 0.55, second = 0.35)   // position 0
        runCurrent()
        assertThat(h.review.progress.value.index).isEqualTo(1)

        h.answer("6,8", winRate = 0.60)                  // position 1 (white to move)
        runCurrent()
        h.answer("8,6", winRate = 0.52)                  // position 2
        runCurrent()

        assertThat(h.review.progress.value.running).isFalse()
        val report = h.review.report.value!!
        assertThat(report.moveCount).isEqualTo(2)
        // Position 0: black to move, so the winrate is already black's.
        assertThat(report.data.record(0).blackWinRate).isWithin(1e-6).of(0.55)
        assertThat(report.data.record(0).best).isEqualTo(h8)
        assertThat(report.data.record(0).gap).isWithin(1e-6).of(0.20)
        // Position 1: white to move — the engine's number is mirrored.
        assertThat(report.data.record(1).blackWinRate).isWithin(1e-6).of(0.40)
    }

    /** A mate lands in the history as 1.0 / 0.0 (`evalbar_update_from_engine`). */
    @Test
    fun `a mate is recorded as a certain win`() = reviewTest { h ->
        h.game.replaceLine(listOf(h8))
        h.review.start(ReviewBudget(seconds = 1))
        runCurrent()
        h.answer("7,7", winRate = 0.98, mate = 3)
        runCurrent()

        h.answer("6,8", winRate = 0.10)
        runCurrent()
        val report = h.review.report.value!!
        assertThat(report.data.record(0).blackWinRate).isEqualTo(1.0)
        assertThat(report.data.record(0).blackMate).isEqualTo(3)
    }

    @Test
    fun `a decided position is recorded without a search`() =
        reviewTest(AppSettings(skipOpening = false, rule = 0)) { h ->
            // Black five in a row; white replies elsewhere.
            val black = (0..4).map { Move(x = 3 + it, y = 7) }
            val white = (0..3).map { Move(x = 3 + it, y = 0) }
            val line = ArrayList<Move>()
            for (i in 0..4) {
                line += black[i]
                if (i < 4) line += white[i]
            }
            h.game.replaceLine(line)
            h.review.start(ReviewBudget(seconds = 1))
            runCurrent()

            // Answer the eight positions before the win, then the loop should
            // finish on its own: position 9 is already decided.
            repeat(line.size) {
                h.answer("0,0", winRate = 0.5)
                runCurrent()
            }
            assertThat(h.review.progress.value.running).isFalse()
            val report = h.review.report.value!!
            assertThat(report.data.record(line.size).blackWinRate).isEqualTo(1.0)
        }

    @Test
    fun `opening positions are skipped when the setting says so`() =
        reviewTest(AppSettings(skipOpening = true, rule = 0)) { h ->
            h.game.replaceLine(listOf(h8, i9, g7, Move(x = 9, y = 9), Move(x = 10, y = 10)))
            h.engine.sent.clear()
            h.review.start(ReviewBudget(seconds = 1))
            runCurrent()
            // The first searched position is 5, so the board carries five stones.
            val board = h.engine.sent.first { it.startsWith("YXBOARD") }
            assertThat(board.lines()).hasSize(7)     // head + 5 stones + DONE
            assertThat(h.review.progress.value.index).isEqualTo(5)
        }

    // ---- interruptions ------------------------------------------------------

    @Test
    fun `cancelling stops the engine and restores the level`() = reviewTest { h ->
        h.game.replaceLine(listOf(h8))
        h.review.start(ReviewBudget(seconds = 1))
        runCurrent()
        h.engine.sent.clear()

        h.review.cancel()
        runCurrent()
        assertThat(h.review.progress.value.running).isFalse()
        assertThat(h.engine.sent.first()).isEqualTo("YXSTOP")
        // set_level(levelchoice): the user's own limits go back out.
        assertThat(h.engine.sent.any { it.startsWith("INFO timeout_turn") }).isTrue()
        assertThat(h.review.report.value).isNull()
    }

    /** The best move a `yxstop` produces belongs to the abandoned search. */
    @Test
    fun `the reply to a watchdog stop is swallowed`() = reviewTest { h ->
        h.game.replaceLine(listOf(h8))
        h.review.start(ReviewBudget(seconds = 1))
        runCurrent()

        advanceTimeBy((ReviewBudget(seconds = 1).watchdogSeconds + 1) * 1000L)
        runCurrent()
        assertThat(h.review.progress.value.index).isEqualTo(0)   // same position again

        h.answer("7,7", winRate = 0.5)      // the stale answer
        runCurrent()
        assertThat(h.review.progress.value.index).isEqualTo(0)

        h.answer("7,7", winRate = 0.5)      // the real one
        runCurrent()
        assertThat(h.review.progress.value.index).isEqualTo(1)
    }

    // ---- queue --------------------------------------------------------------

    @Test
    fun `the queue reviews each readable game and skips the rest`() = reviewTest { h ->
        h.files.contents["good"] = GameFile.writePsq(listOf(h8, i9), 15)
        h.files.contents["broken"] = "not a game".toByteArray()
        h.review.enqueue(
            listOf(
                QueueEntry(uri = "broken", name = "broken.psq"),
                QueueEntry(uri = "good", name = "good.psq"),
            ),
        )
        assertThat(h.review.startQueue(ReviewBudget(seconds = 1))).isEqualTo(ReviewStart.Started)
        runCurrent()

        // The unreadable file is marked and the second game is on the board.
        assertThat(h.review.queue.value[0].status).isEqualTo(QueueStatus.FAILED)
        assertThat(h.game.positionFlow.value.moves).containsExactly(h8, i9).inOrder()

        repeat(3) {
            h.answer("7,7", winRate = 0.5)
            runCurrent()
        }
        assertThat(h.review.queue.value[1].status).isEqualTo(QueueStatus.DONE)
        assertThat(h.review.queueReports.value).hasSize(1)
        assertThat(h.review.progress.value.running).isFalse()
    }

    @Test
    fun `an empty queue is refused`() = reviewTest { h ->
        assertThat(h.review.startQueue(ReviewBudget())).isInstanceOf(ReviewStart.Refused::class.java)
    }

    // ---- grading ------------------------------------------------------------

    @Test
    fun `changing the preset re-grades the finished report`() = reviewTest { h ->
        h.game.replaceLine(listOf(h8))
        h.review.start(ReviewBudget(seconds = 1))
        runCurrent()
        // The engine preferred i9, so the played h8 is graded on its loss alone.
        h.answer("6,8", winRate = 0.60)
        runCurrent()
        h.answer("6,8", winRate = 0.45)      // black lost 5 %p
        runCurrent()

        assertThat(h.review.report.value!!.moves.first().quality).isEqualTo(MoveQuality.GOOD)
        h.review.setPreset(dev.gomoku.rapfidroid.core.model.GradingPreset.STRICT)
        assertThat(h.review.report.value!!.moves.first().quality)
            .isEqualTo(MoveQuality.INACCURACY)
    }
}
