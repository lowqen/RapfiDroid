package dev.gomoku.yixindroid.data.game

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
import dev.gomoku.yixindroid.core.model.GamePrompt
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.SettingsFile
import dev.gomoku.yixindroid.core.model.StoneColor
import dev.gomoku.yixindroid.core.model.Swap2Choice
import dev.gomoku.yixindroid.core.model.TapResult
import dev.gomoku.yixindroid.domain.engine.CoordMapper
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.engine.ResponseParser
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5 state machine, checked against the desktop's three drivers:
 * `on_button_press_windowmain` (what a tap means), the best-move / SWAP2 /
 * SOOSORV branches of `iochannelout_watch`, and the swap dialogs.
 *
 * `runCurrent()` rather than `advanceUntilIdle()`: the repository owns an endless
 * clock ticker, so advancing to idle would never return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameRepositoryTest {

    private val coord = CoordMapper()

    private class FakeEngine : EngineRepository {
        val sent = mutableListOf<String>()
        val responseFlow = MutableSharedFlow<EngineResponse>(extraBufferCapacity = 64)
        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Ready)
        var forbidden: List<Move> = emptyList()

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

        override suspend fun forbidden(position: Position): List<Move> = forbidden
        override suspend fun balance(position: Position, two: Boolean, bias: Int): List<Move> =
            emptyList()

        override suspend fun stop() {
            sent += "YXSTOP"
        }
    }

    private class FakeSettings(initial: AppSettings) : SettingsRepository {
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

    private class Harness(
        val engine: FakeEngine,
        val settings: FakeSettings,
        val game: GameRepositoryImpl,
    ) {
        /** Feed a raw server line through the real parser, like the socket would. */
        suspend fun receive(line: String) {
            engine.responseFlow.emit(ResponseParser.parse(line, CoordMapper()))
        }

        fun labels(): List<String> = game.position.value.moves.map { it.label(game.position.value.size) }
    }

    /** Freestyle, engine plays white, custom level so the clocks are real. */
    private fun settings(
        rule: Int = 0,
        black: Boolean = false,
        white: Boolean = true,
        level: Int = 1,
        matchSec: Int = 300,
        incrementMs: Int = 0,
        showForbidden: Boolean = false,
    ) = AppSettings(
        rule = rule,
        computerBlack = black,
        computerWhite = white,
        level = level,
        timeoutTurnSec = 30,
        timeoutMatchSec = matchSec,
        incrementMs = incrementMs,
        showForbidden = showForbidden,
        useDatabase = false,
    )

    private fun gameTest(
        config: AppSettings = settings(),
        body: suspend TestScope.(Harness) -> Unit,
    ) = runTest {
        val engine = FakeEngine()
        val fakeSettings = FakeSettings(config)
        val repository = GameRepositoryImpl(
            engine, fakeSettings, StandardTestDispatcher(testScheduler),
        )
        val h = Harness(engine, fakeSettings, repository)
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

    // ---- ordinary play ------------------------------------------------------

    @Test
    fun `a human move is pushed as a whole board the first time and TURN after`() =
        gameTest { h ->
            h.engine.sent.clear()

            assertEquals(TapResult.Placed, h.game.tap(h8))
            runCurrent()
            // main.c: INFO time_left, then start + board (isneedrestart was set).
            assertEquals(
                listOf("INFO time_left 300000", "START 15", "BOARD\n7,7,2\nDONE"),
                h.engine.sent,
            )
            assertTrue(h.game.state.value.thinking)

            h.engine.sent.clear()
            h.receive("6,8")
            runCurrent()
            assertEquals(listOf("H8", "I9"), h.labels())
            assertTrue(!h.game.state.value.thinking)

            // Now the line is intact, so the desktop sends the single move.
            h.game.tap(g7)
            runCurrent()
            assertEquals(
                listOf("INFO time_left 300000", "TURN 8,6"),
                h.engine.sent,
            )
        }

    @Test
    fun `with both colours the engine keeps moving on its own`() =
        gameTest(settings(black = true, white = true)) { h ->
            h.game.tap(h8)          // a tap while the engine is on move starts it
            runCurrent()
            h.engine.sent.clear()

            h.receive("7,7")
            runCurrent()
            // Black's move is on the board and White (also the engine) is asked next.
            assertEquals(listOf("H8"), h.labels())
            assertEquals(
                listOf("INFO time_left 300000", "START 15", "BOARD\n7,7,2\nDONE"),
                h.engine.sent,
            )
            assertTrue(h.game.state.value.thinking)
        }

    @Test
    fun `taps are refused while the engine is thinking`() = gameTest { h ->
        h.game.tap(h8)
        runCurrent()
        val result = h.game.tap(i9)
        assertTrue(result is TapResult.Rejected)
        assertEquals(listOf("H8"), h.labels())
    }

    @Test
    fun `five in a row ends the game and blocks further moves`() =
        gameTest(settings(white = false)) { h ->
            // Human against human: black five on row 8 with white replies elsewhere.
            val black = (0..4).map { Move(x = 3 + it, y = 7) }
            val white = (0..3).map { Move(x = 3 + it, y = 0) }
            for (i in 0..4) {
                h.game.tap(black[i])
                if (i < 4) h.game.tap(white[i])
            }
            runCurrent()

            val result = h.game.state.value.result
            assertEquals(GameEnd.FIVE, result?.end)
            assertEquals(StoneColor.BLACK, result?.winner)
            assertTrue(h.game.tap(Move(x = 10, y = 10)) is TapResult.Rejected)
        }

    @Test
    fun `undoing the winning move puts the game back on`() = gameTest(settings(white = false)) { h ->
        val black = (0..4).map { Move(x = 3 + it, y = 7) }
        val white = (0..3).map { Move(x = 3 + it, y = 0) }
        for (i in 0..4) {
            h.game.tap(black[i])
            if (i < 4) h.game.tap(white[i])
        }
        runCurrent()
        assertTrue(h.game.state.value.over)

        h.game.undo()
        runCurrent()
        assertNull(h.game.state.value.result)
        // The undone move is kept for redo, like the desktop's movepath tail.
        assertEquals(1, h.game.future.value.size)
    }

    @Test
    fun `a forbidden point is refused with the desktop's dialog`() =
        gameTest(settings(rule = 2, white = false, showForbidden = true)) { h ->
            h.engine.forbidden = listOf(i9)
            // A move by white flips the turn back to black and refreshes the list.
            h.game.tap(h8)
            h.game.tap(g7)
            runCurrent()

            val result = h.game.tap(i9)
            assertTrue(result is TapResult.Rejected)
            assertEquals(GamePrompt.Forbidden(i9), h.game.state.value.prompt)
        }

    @Test
    fun `resign and draw send the desktop commands and end the game`() = gameTest { h ->
        h.engine.sent.clear()
        h.game.resign()
        runCurrent()
        assertEquals(listOf("yxresign"), h.engine.sent)
        assertEquals(GameEnd.RESIGNED, h.game.state.value.result?.end)

        h.game.newGame()
        h.engine.sent.clear()
        h.game.offerDraw()
        runCurrent()
        assertEquals(listOf("yxdraw"), h.engine.sent)
        assertEquals(GameEnd.DRAW_AGREED, h.game.state.value.result?.end)
    }

    /** The increment is credited per completed move and shows up in `time_left`. */
    @Test
    fun `the engine is told the match budget plus its increments`() =
        gameTest(settings(incrementMs = 5_000)) { h ->
            h.game.tap(h8)
            runCurrent()
            h.receive("6,8")
            runCurrent()
            h.engine.sent.clear()

            h.game.tap(g7)
            runCurrent()
            assertEquals("INFO time_left 305000", h.engine.sent.first())
        }

    // ---- RIF (rule 4): the first three moves are entered by hand -------------

    @Test
    fun `RIF does not ask the engine before the third move`() =
        gameTest(settings(rule = 4, white = true)) { h ->
            h.game.tap(h8)          // black, by hand
            runCurrent()
            h.engine.sent.clear()

            // White is the engine's colour, but the opening is entered by hand.
            assertEquals(TapResult.Placed, h.game.tap(i9))
            runCurrent()
            assertEquals(listOf("H8", "I9"), h.labels())
            assertTrue(h.engine.sent.isEmpty())

            // The third move completes the opening, so now the engine is asked.
            h.game.tap(g7)
            runCurrent()
            assertTrue(h.engine.sent.any { it.startsWith("BOARD") })
        }

    // ---- Swap2 (rule 6) -----------------------------------------------------

    @Test
    fun `swap2 with the engine opening runs step1 then asks the three-way question`() =
        gameTest(settings(rule = 6, black = true, white = false)) { h ->
            h.engine.sent.clear()
            h.game.newGame()
            runCurrent()
            assertEquals(listOf("yxswap2step1"), h.engine.sent)

            h.receive("MESSAGE SWAP2 MOVE1 7 7")
            h.receive("MESSAGE SWAP2 MOVE2 6 8")
            h.receive("MESSAGE SWAP2 MOVE3 8 6")
            runCurrent()
            assertEquals(listOf("H8", "I9", "G7"), h.labels())
            assertEquals(GamePrompt.Swap2, h.game.state.value.prompt)

            h.engine.sent.clear()
            h.game.answerSwap2(Swap2Choice.SWAP)
            runCurrent()
            // Swapping hands the engine the other colour and starts its search.
            assertEquals(ComputerSide.WHITE, h.game.state.value.computerSide)
            assertTrue(h.game.state.value.swapDone)
            assertTrue(h.engine.sent.any { it.startsWith("BOARD") })
        }

    @Test
    fun `swap2 with the human opening asks the engine after the third stone`() =
        gameTest(settings(rule = 6, black = false, white = true)) { h ->
            h.game.tap(h8)
            h.game.tap(i9)
            runCurrent()
            h.engine.sent.clear()

            h.game.tap(g7)
            runCurrent()
            // The desktop pushes the position as an analysis board, then step 2.
            // `send_board` colours from the side to move (main.c:2258), so with
            // three stones down the odd ones are 1 and the even ones 2.
            assertEquals(
                listOf(
                    "INFO time_left 300000",
                    "START 15",
                    "YXBOARD\n7,7,2\n6,8,1\n8,6,2\nDONE",
                    "yxswap2step2",
                ),
                h.engine.sent,
            )

            h.engine.sent.clear()
            h.receive("MESSAGE SWAP2 SWAP1 NO")
            runCurrent()
            // "No swap": the engine keeps white and plays on.
            assertTrue(h.game.state.value.swapDone)
            assertTrue(h.engine.sent.any { it.startsWith("BOARD") })
        }

    @Test
    fun `swap2 SWAP1 YES hands the colours over and tells the user`() =
        gameTest(settings(rule = 6, black = false, white = true)) { h ->
            h.game.tap(h8)
            h.game.tap(i9)
            h.game.tap(g7)
            runCurrent()

            h.receive("MESSAGE SWAP2 SWAP1 YES")
            runCurrent()
            assertEquals(GamePrompt.SwapInfo, h.game.state.value.prompt)
            assertEquals(ComputerSide.BLACK, h.game.state.value.computerSide)
            assertTrue(!h.game.state.value.thinking)
        }

    // ---- Soosorv-8 (rule 5) -------------------------------------------------

    @Test
    fun `soosorv walks the whole negotiation`() =
        gameTest(settings(rule = 5, black = false, white = true)) { h ->
            // 1-3 by hand, inside the desktop's opening box.
            h.game.tap(Move(x = 7, y = 7))
            h.game.tap(Move(x = 8, y = 6))
            runCurrent()
            h.engine.sent.clear()
            h.game.tap(Move(x = 9, y = 5))
            runCurrent()
            assertEquals(
                listOf("yxsoosorvstep2\n7,7\n6,8\n5,9\ndone"),
                h.engine.sent,
            )

            // The engine declines to swap, so the desktop asks for its fourth move.
            h.engine.sent.clear()
            h.receive("MESSAGE SOOSORV SWAP1 N")
            runCurrent()
            assertEquals(listOf("yxsoosorvstep3\n7,7\n6,8\n5,9\ndone"), h.engine.sent)

            // Move 4 arrives with N, and the fifth-move stage opens.
            h.receive("MESSAGE SOOSORV MOVE4 4 9 2")
            runCurrent()
            assertEquals(4, h.game.position.value.moves.size)
            assertEquals(2, h.game.state.value.fifthCount)
            assertEquals(GamePrompt.Swap(fifthCount = 2), h.game.state.value.prompt)

            // Keeping the colours, the human offers the two fifth moves.
            h.game.answerSwap(false)
            runCurrent()
            h.engine.sent.clear()
            h.game.tap(Move(x = 6, y = 6))
            runCurrent()
            assertTrue(h.game.state.value.offeringFifth)
            assertTrue(h.engine.sent.isEmpty())

            h.game.tap(Move(x = 10, y = 10))
            runCurrent()
            assertEquals(listOf("yxsoosorvstep6\n7,7\n6,8\n5,9\n4,9\n6,6\n10,10\ndone"), h.engine.sent)
            assertTrue(h.game.state.value.fifthStageDone)

            // The engine picks one: the board goes back to four stones plus it.
            h.engine.sent.clear()
            h.receive("MESSAGE SOOSORV MOVE5 C 10 10")
            runCurrent()
            assertEquals(5, h.game.position.value.moves.size)
            assertEquals("K5", h.game.position.value.moves.last().label(15))
            assertTrue(h.engine.sent.any { it.startsWith("BOARD") })
        }

    @Test
    fun `soosorv refuses an opening outside the standard box`() =
        gameTest(settings(rule = 5, black = false, white = true)) { h ->
            h.game.tap(Move(x = 7, y = 7))
            h.game.tap(Move(x = 8, y = 6))
            h.game.tap(Move(x = 12, y = 12))     // far outside the ±2 box
            runCurrent()

            assertEquals(GamePrompt.IllegalOpening, h.game.state.value.prompt)
            assertTrue(h.game.position.value.moves.isEmpty())
        }

    @Test
    fun `soosorv swap yes swaps sides and sends the next step`() =
        gameTest(settings(rule = 5, black = false, white = true)) { h ->
            h.game.tap(Move(x = 7, y = 7))
            h.game.tap(Move(x = 8, y = 6))
            h.game.tap(Move(x = 9, y = 5))
            runCurrent()
            h.engine.sent.clear()

            h.receive("MESSAGE SOOSORV MOVE4 4 9 1")
            runCurrent()
            h.engine.sent.clear()
            h.game.answerSwap(true)
            runCurrent()

            assertEquals(ComputerSide.BLACK, h.game.state.value.computerSide)
            assertEquals(
                // MOVE4 came in as `4 9` = (y=4, x=9), so it goes back out as 4,9.
                listOf("yxsoosorvstep5 1\n7,7\n6,8\n5,9\n4,9\ndone"),
                h.engine.sent,
            )
        }

    // ---- swap after first move (rule 3) ------------------------------------

    @Test
    fun `swap after first move opens with the desktop's canned stone`() =
        gameTest(settings(rule = 3, black = true, white = false)) { h ->
            h.engine.sent.clear()
            h.game.tap(Move(x = 3, y = 3))   // a tap while the engine is on move
            runCurrent()

            // main.c plays (y=1, x=7) itself and then asks whether to take over.
            assertEquals(listOf("H14"), h.labels())
            assertEquals(GamePrompt.Swap(), h.game.state.value.prompt)
            assertTrue(h.engine.sent.isEmpty())

            h.game.answerSwap(true)
            runCurrent()
            // The reply stone (y=4, x=5) goes down and the colours change hands.
            assertEquals(listOf("H14", "F11"), h.labels())
            assertEquals(ComputerSide.WHITE, h.game.state.value.computerSide)
        }
}
