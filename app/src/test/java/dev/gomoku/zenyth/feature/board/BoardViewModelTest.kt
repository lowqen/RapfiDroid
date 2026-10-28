package dev.gomoku.zenyth.feature.board

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import dev.gomoku.zenyth.core.model.AppSettings
import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.BoardTransform
import dev.gomoku.zenyth.core.model.DbPositionValue
import dev.gomoku.zenyth.core.model.DbState
import dev.gomoku.zenyth.core.model.GameFileFormat
import dev.gomoku.zenyth.core.model.GameState
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.OpeningName
import dev.gomoku.zenyth.core.model.PositionLink
import dev.gomoku.zenyth.core.model.ProveProgress
import dev.gomoku.zenyth.core.model.ReviewProgress
import dev.gomoku.zenyth.core.model.TapResult
import dev.gomoku.zenyth.domain.engine.CoordMapper
import dev.gomoku.zenyth.domain.engine.EngineResponse
import dev.gomoku.zenyth.domain.engine.ResponseParser
import dev.gomoku.zenyth.domain.engine.SearchAggregator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What [BoardViewModel] promises the board screen, pinned before the
 * performance work (docs/리팩토링-성능-위생-계획.md, Phase 0): when a stopped
 * search plays its move and when it does not, how a restart swallows the
 * cancelled search's reply, what the card and the board show, and which actions
 * a running review or proof locks.
 *
 * The fakes are in `BoardFakes.kt`; the engine one runs the real
 * [SearchAggregator], so a snapshot here is the one the app would build.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app that starts the engine watcher.
// Korean, because the notices asserted below are `tr()` text.
@Config(sdk = [35], application = Application::class, qualifiers = "ko-rKR")
class BoardViewModelTest {

    private val main = StandardTestDispatcher()
    private val coord = CoordMapper()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Harness(rig: BoardRig, val scope: TestScope) {
        val vm = rig.vm
        val engine = rig.engine
        val game = rig.game
        val settings = rig.settings
        val database = rig.database
        val review = rig.review
        val prove = rig.prove
        val links = rig.links

        val ui: BoardUiState get() = vm.uiState.value

        suspend fun receive(vararg lines: String) {
            lines.forEach { engine.responseFlow.emit(ResponseParser.parse(it, CoordMapper())) }
            scope.runCurrent()
        }

        /** One `INFO PV <index>` … `INFO PV DONE` block. */
        suspend fun pv(index: Int, winRate: String, bestline: String) = receive(
            "INFO PV $index",
            "INFO DEPTH 14",
            "INFO WINRATE $winRate",
            "INFO BESTLINE $bestline",
            "INFO PV DONE",
        )

        /** The engine settles: a bare `y,x`. */
        suspend fun settle(move: Move) {
            engine.responseFlow.emit(EngineResponse.BestMove(listOf(move), raw = ""))
            scope.runCurrent()
        }

        suspend fun board(vararg moves: Move) {
            game.replaceLine(moves.toList())
            scope.runCurrent()
        }

        fun act(block: BoardViewModel.() -> Unit) {
            vm.block()
            scope.runCurrent()
        }
    }

    private fun boardTest(
        settings: AppSettings = AppSettings(),
        body: suspend TestScope.(Harness) -> Unit,
    ) = runTest(main) {
        val rig = boardRig(settings, io = StandardTestDispatcher(testScheduler))
        backgroundScope.launch { rig.vm.uiState.collect {} }
        runCurrent()
        try {
            body(Harness(rig, this))
        } finally {
            rig.vm.viewModelScope.cancel()
        }
    }

    private fun line(text: String): List<Move> =
        (ResponseParser.parse("INFO BESTLINE $text", coord) as EngineResponse.InfoBestline).line

    private val h8 = Move.fromLabel("H8")!!
    private val g7 = Move.fromLabel("G7")!!
    private val i9 = Move.fromLabel("I9")!!

    // ---- the analysis and its card -----------------------------------------

    @Test
    fun `an analysis asks for the settings' multi-PV and fills the card and the board`() =
        boardTest(AppSettings(multiPv = 2)) { h ->
            h.board(h8)
            h.act { onStartAnalyze() }
            assertThat(h.engine.analyses).hasSize(1)
            assertThat(h.engine.analyses[0].first.moves).containsExactly(h8)
            assertThat(h.engine.analyses[0].second.multiPv).isEqualTo(2)
            assertThat(h.engine.analyses[0].second.defend).isFalse()

            h.pv(0, "0.62", "6,8 8,6")
            val ui = h.ui
            assertThat(ui.analyzing).isTrue()
            assertThat(ui.stale).isFalse()
            assertThat(ui.shown!!.pvs.map { it.line }).containsExactly(line("6,8 8,6"))
            assertThat(ui.render.bestMark).isEqualTo(line("6,8").single())
            assertThat(ui.render.ghosts).isEqualTo(line("6,8 8,6"))
            assertThat(ui.render.tags.keys).containsExactly(line("6,8").single())
            assertThat(ui.eval).isNotNull()
            assertThat(ui.eval!!.fromDb).isFalse()
        }

    @Test
    fun `a previewed line replaces the best line's ghosts and hides the values`() =
        boardTest(AppSettings(multiPv = 2)) { h ->
            h.board(h8)
            h.act { onStartAnalyze() }
            h.pv(0, "0.62", "6,8 8,6")
            h.pv(1, "0.55", "8,8 6,6")
            assertThat(h.ui.render.ghosts).isEqualTo(line("6,8 8,6"))

            h.act { onPreviewPv(1) }
            assertThat(h.ui.previewPv).isEqualTo(1)
            assertThat(h.ui.render.ghosts).isEqualTo(line("8,8 6,6"))
            assertThat(h.ui.render.tags).isEmpty()
        }

    @Test
    fun `the game tab shows none of the analysis on the board`() = boardTest { h ->
        h.board(h8)
        h.act { onStartAnalyze() }
        h.pv(0, "0.62", "6,8 8,6")
        assertThat(h.ui.render.tags).isNotEmpty()

        h.game.modeFlow.value = BoardMode.GAME
        h.scope.runCurrent()
        assertThat(h.ui.render.tags).isEmpty()
        assertThat(h.ui.render.ghosts).isEmpty()
        assertThat(h.ui.render.bestMark).isNull()
        assertThat(h.ui.eval).isNull()
        assertThat(h.ui.moveGrade).isNull()
    }

    @Test
    fun `switching to the game tab stops the analysis`() = boardTest { h ->
        h.board(h8)
        h.act { onStartAnalyze() }
        h.act { onMode(BoardMode.GAME) }
        assertThat(h.ui.analyzing).isFalse()
        assertThat(h.game.modeFlow.value).isEqualTo(BoardMode.GAME)
    }

    @Test
    fun `searchdefend is an analysis that asks for every defence`() = boardTest { h ->
        h.board(h8)
        h.act { onSearchDefend() }
        assertThat(h.engine.analyses.single().second.defend).isTrue()
        assertThat(h.ui.defending).isTrue()
    }

    @Test
    fun `changing the multi-PV writes the setting, capped, and restarts a live analysis`() = boardTest { h ->
        h.board(h8)
        h.act { onStartAnalyze() }
        h.act { onMultiPvChange(12) }
        assertThat(h.settings.writes).contains("multiPv" to "8")
        assertThat(h.engine.analyses).hasSize(2)
        assertThat(h.engine.analyses[1].second.multiPv).isEqualTo(8)
    }

    @Test
    fun `after the board moves on the card keeps the last reading, marked stale`() = boardTest { h ->
        h.act { onPlayBestOnStop(false) }
        h.board(h8)
        h.act { onStartAnalyze() }
        h.pv(0, "0.62", "6,8 8,6")
        h.act { onStopAnalyze() }
        h.settle(line("6,8").single())
        assertThat(h.ui.analyzing).isFalse()

        h.board(h8, g7)
        val ui = h.ui
        assertThat(ui.stale).isTrue()
        assertThat(ui.staleAt).isEqualTo(1)
        assertThat(ui.shown!!.pvs.map { it.line }).containsExactly(line("6,8 8,6"))
        assertThat(ui.render.tags).isEmpty()
        assertThat(ui.render.ghosts).isEmpty()
        assertThat(ui.render.bestMark).isNull()
    }

    // ---- how a search ends -------------------------------------------------

    @Test
    fun `stop plays the engine's settled move when play-best-on-stop is on`() = boardTest { h ->
        h.board(h8)
        h.act { onStartAnalyze() }
        h.pv(0, "0.62", "6,8 8,6")
        h.act { onStopAnalyze() }
        assertThat(h.engine.stops).isEqualTo(1)
        assertThat(h.ui.analyzing).isTrue()

        h.settle(line("6,8").single())
        assertThat(h.game.positionFlow.value.moves).containsExactly(h8, line("6,8").single()).inOrder()
        assertThat(h.ui.analyzing).isFalse()
        // The new position does not restart the stopped analysis.
        assertThat(h.engine.analyses).hasSize(1)
    }

    @Test
    fun `stop keeps the board when play-best-on-stop is off, and re-reads the database`() = boardTest { h ->
        h.act { onPlayBestOnStop(false) }
        assertThat(h.ui.playBestOnStop).isFalse()
        h.board(h8)
        h.act { onStartAnalyze() }
        h.pv(0, "0.62", "6,8 8,6")
        h.act { onStopAnalyze() }
        val before = h.database.refreshes

        h.settle(line("6,8").single())
        assertThat(h.game.positionFlow.value.moves).containsExactly(h8)
        assertThat(h.ui.analyzing).isFalse()
        assertThat(h.database.refreshes).isEqualTo(before + 1)
    }

    @Test
    fun `a search whose time ran out just ends — its move is not played`() = boardTest { h ->
        h.board(h8)
        h.act { onStartAnalyze() }
        h.pv(0, "0.62", "6,8 8,6")
        val before = h.database.refreshes

        h.settle(line("6,8").single())
        assertThat(h.game.positionFlow.value.moves).containsExactly(h8)
        assertThat(h.ui.analyzing).isFalse()
        assertThat(h.database.refreshes).isEqualTo(before + 1)
        // The reading stays on the card.
        assertThat(h.ui.shown!!.pvs).isNotEmpty()
    }

    @Test
    fun `a move settled with no line behind it is played at once`() = boardTest { h ->
        h.board(h8)
        h.act { onStartAnalyze() }
        h.settle(g7)
        assertThat(h.game.positionFlow.value.moves).containsExactly(h8, g7).inOrder()
        assertThat(h.ui.analyzing).isFalse()
    }

    @Test
    fun `a restart swallows exactly the cancelled search's move`() = boardTest { h ->
        h.board(h8)
        h.act { onStartAnalyze() }
        h.board(h8, g7)
        // The board moved under a live analysis: it restarts on the new position.
        assertThat(h.engine.analyses).hasSize(2)
        assertThat(h.engine.analyses[1].first.moves).containsExactly(h8, g7).inOrder()

        // The cancelled search's reply is swallowed…
        h.settle(i9)
        assertThat(h.game.positionFlow.value.moves).containsExactly(h8, g7).inOrder()
        assertThat(h.ui.analyzing).isTrue()

        // …and the next one is the new search's own.
        h.settle(i9)
        assertThat(h.game.positionFlow.value.moves).containsExactly(h8, g7, i9).inOrder()
        assertThat(h.ui.analyzing).isFalse()
    }

    @Test
    fun `stop on an empty board places the centre without asking the engine`() = boardTest { h ->
        h.act { onStartAnalyze() }
        h.act { onStopAnalyze() }
        assertThat(h.engine.stops).isEqualTo(0)
        assertThat(h.game.positionFlow.value.moves).containsExactly(Move(7, 7))
        assertThat(h.ui.analyzing).isFalse()
        assertThat(h.engine.analyses).hasSize(1)
    }

    @Test
    fun `the engine thinking on its game move refuses an analysis`() = boardTest { h ->
        h.game.stateFlow.value = GameState(thinking = true)
        h.act { onStartAnalyze() }
        assertThat(h.engine.analyses).isEmpty()
        assertThat(h.ui.notice).contains("대국 착수를 계산하는 중")
    }

    // ---- the database ------------------------------------------------------

    @Test
    fun `with no search running the eval bar reads the database`() = boardTest { h ->
        h.board(h8)
        h.database.stateFlow.value = DbState(
            enabled = true,
            value = DbPositionValue(blackWinRate = 0.7, blackMate = null, stmWinRate = 0.3, stmMate = null),
        )
        h.scope.runCurrent()
        val eval = h.ui.eval!!
        assertThat(eval.fromDb).isTrue()
        assertThat(eval.blackPct).isWithin(1e-9).of(70.0)
    }

    @Test
    fun `every position change is handed to the database`() = boardTest { h ->
        h.board(h8)
        h.board(h8, g7)
        assertThat(h.database.positions.last().moves).containsExactly(h8, g7).inOrder()
    }

    // ---- taps and locks ----------------------------------------------------

    @Test
    fun `a tap goes to the game, and a refusal becomes the notice`() = boardTest { h ->
        h.act { onTap(h8) }
        assertThat(h.game.taps).containsExactly(h8)
        assertThat(h.ui.moveCount).isEqualTo(1)

        h.game.tapResult = TapResult.Rejected("이미 돌이 있습니다")
        h.act { onTap(h8) }
        assertThat(h.ui.notice).isEqualTo("이미 돌이 있습니다")
        h.act { onNoticeShown() }
        assertThat(h.ui.notice).isNull()
    }

    @Test
    fun `a running review locks the board`() = boardTest { h ->
        h.review.progressFlow.value = ReviewProgress(running = true, index = 1, total = 5)
        h.scope.runCurrent()
        h.act { onTap(h8) }
        assertThat(h.game.taps).isEmpty()
        assertThat(h.ui.notice).contains("게임 리뷰")
        assertThat(h.ui.research).isNotNull()
    }

    @Test
    fun `a running proof locks navigation`() = boardTest { h ->
        h.board(h8)
        h.prove.progressFlow.value = ProveProgress(running = true)
        h.scope.runCurrent()
        h.act { onUndo() }
        assertThat(h.game.undos).isEqualTo(0)
        assertThat(h.ui.notice).contains("국면 증명")
        assertThat(h.ui.research!!.isProve).isTrue()
    }

    // ---- links and files ---------------------------------------------------

    @Test
    fun `a link over a board with stones keeps that board aside until decided`() = boardTest { h ->
        h.board(h8)
        h.links.offer(PositionLink.url(listOf(h8, g7, i9), 15))
        h.scope.runCurrent()
        assertThat(h.links.pending.value).isNull()
        assertThat(h.game.positionFlow.value.moves).containsExactly(h8, g7, i9).inOrder()
        assertThat(h.ui.linkBackup).isTrue()

        h.act { onRestoreLinkBackup() }
        assertThat(h.game.positionFlow.value.moves).containsExactly(h8)
        assertThat(h.ui.linkBackup).isFalse()
    }

    @Test
    fun `the position's link and its file name keep their shape`() = boardTest { h ->
        h.board(h8, g7)
        assertThat(h.vm.linkText()).isEqualTo(PositionLink.url(listOf(h8, g7), 15))
        val name = h.vm.saveName(GameFileFormat.PSQ)
        assertThat(name).startsWith("rapfi_2_")
        assertThat(name).endsWith(".psq")
    }

    @Test
    fun `the position card's fields follow the board`() = boardTest { h ->
        h.board(h8, g7, i9)
        val moves = listOf(h8, g7, i9)
        val ui = h.ui
        assertThat(ui.moveCount).isEqualTo(3)
        assertThat(ui.positionString).isEqualTo(BoardTransform.toPositionString(moves, 15))
        assertThat(ui.openingNames).isEqualTo((1..3).mapNotNull { OpeningName.nameAt(it, moves, 15) })
        assertThat(ui.openingNames).isNotEmpty()
        assertThat(ui.openingGrade).isEqualTo(OpeningName.gradeAt(moves, 15))
        assertThat(ui.render.stones).isEqualTo(moves)
        assertThat(ui.render.lastMove).isEqualTo(i9)
    }
}
