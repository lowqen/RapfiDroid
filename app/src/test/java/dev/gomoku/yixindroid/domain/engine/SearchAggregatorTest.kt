package dev.gomoku.yixindroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.StoneColor
import org.junit.Test

class SearchAggregatorTest {

    private val coord = CoordMapper()

    private fun line(s: String) = ResponseParser.parse(s, coord)

    @Test
    fun assemblesOnePvBlock() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.consume(line("INFO PV 0"))
        agg.consume(line("INFO DEPTH 12"))
        agg.consume(line("INFO EVAL 40"))
        agg.consume(line("INFO WINRATE 0.62"))
        agg.consume(line("INFO BESTLINE 7,7 6,8"))
        val snap = agg.consume(line("INFO PV DONE"))

        assertThat(snap).isNotNull()
        val best = snap!!.best!!
        assertThat(best.index).isEqualTo(0)
        assertThat(best.depth).isEqualTo(12)
        assertThat(best.winRate).isWithin(1e-9).of(0.62)
        assertThat(best.line).containsExactly(Move(x = 7, y = 7), Move(x = 8, y = 6)).inOrder()
        // Black to move -> black win rate equals side-to-move win rate
        assertThat(snap.blackWinRate()).isWithin(1e-9).of(0.62)
    }

    /**
     * A PV block owns none of the previous block's numbers. The desktop kept
     * these in globals that only `INFO EVAL` ever cleared, so a block without an
     * EVAL of its own inherited the last block's mate — and because the desktop
     * also flipped that global's sign to print an `L` tag, a *lost* line came out
     * of the next block as a *won* one and was written into the shared database
     * as a mate. main.c now clears the three at the block start, the way this has
     * always done; the invariant is pinned here because this is the port both
     * programs are checked against.
     */
    @Test
    fun aBlockWithoutAnEvalInheritsNoMate() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.consume(line("INFO PV 0"))
        agg.consume(line("INFO DEPTH 20"))
        agg.consume(line("INFO EVAL -M7"))
        agg.consume(line("INFO WINRATE 0.01"))
        agg.consume(line("INFO BESTLINE 7,7"))
        val lost = agg.consume(line("INFO PV DONE"))
        assertThat(lost!!.pvs.first { it.index == 0 }.mate).isEqualTo(-7)

        // Second block: no EVAL at all.
        agg.consume(line("INFO PV 1"))
        agg.consume(line("INFO DEPTH 20"))
        agg.consume(line("INFO WINRATE 0.44"))
        agg.consume(line("INFO BESTLINE 6,8"))
        val snap = agg.consume(line("INFO PV DONE"))

        val second = snap!!.pvs.first { it.index == 1 }
        assertThat(second.mate).isNull()
        assertThat(second.winRate).isWithin(1e-9).of(0.44)
    }

    @Test
    fun whiteToMoveFlipsBlackWinRate() {
        val agg = SearchAggregator(StoneColor.WHITE)
        agg.consume(line("INFO PV 0"))
        agg.consume(line("INFO WINRATE 0.70"))
        val snap = agg.consume(line("INFO PV DONE"))
        assertThat(snap!!.blackWinRate()).isWithin(1e-9).of(0.30)
    }

    @Test
    fun mateIsCarried() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.consume(line("INFO PV 0"))
        agg.consume(line("INFO EVAL +M5"))
        val snap = agg.consume(line("INFO PV DONE"))
        assertThat(snap!!.blackMate()).isEqualTo(5)
    }
}
