package dev.gomoku.rapfidroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.StoneColor
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

    /** One `INFO PV <i>` … `INFO PV DONE` block, with only the fields given. */
    private fun SearchAggregator.block(
        index: Int,
        depth: Int,
        bestline: String,
        eval: String? = null,
        winRate: String? = null,
    ) {
        consume(line("INFO PV $index"))
        consume(line("INFO DEPTH $depth"))
        eval?.let { consume(line("INFO EVAL $it")) }
        winRate?.let { consume(line("INFO WINRATE $it")) }
        consume(line("INFO BESTLINE $bestline"))
        consume(line("INFO PV DONE"))
    }

    private val h8 = Move(x = 7, y = 7)
    private val i7 = Move(x = 8, y = 6)

    /**
     * A tag dies when its round ends, not when a deeper one starts. main.c
     * compares depths, and two deepening rounds can report the same depth — so a
     * cell that had dropped out of the candidate set kept its old label right
     * next to the current round's percentages, which reads as one more move that
     * loses. Rounds cannot tie with themselves.
     */
    @Test
    fun aTagFromAnEarlierRoundDoesNotSurviveAtTheSameDepth() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.consume(line("INFO NUMPV 2"))
        agg.block(0, depth = 20, bestline = "7,7", winRate = "0.55")
        agg.block(1, depth = 20, bestline = "6,8", eval = "-M40")

        // Next round, same depth, and 6,8 is no longer among the candidates.
        agg.consume(line("INFO NUMPV 1"))
        agg.block(0, depth = 20, bestline = "7,7", winRate = "0.58")

        assertThat(agg.snapshot().tags.keys).containsExactly(h8)
        assertThat(agg.snapshot().tags[h8]!!.label).isEqualTo("58%")
    }

    /**
     * `INFO NUMPV` is the only thing that can end a round early, and it is not
     * resent by every search. When it overstates the round the sweep never runs,
     * so the next round's `INFO PV 0` has to do it instead.
     */
    @Test
    fun aRoundThatNeverReachesItsNumPvIsStillSweptEventually() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.consume(line("INFO NUMPV 5")) // more than the engine will actually send
        agg.block(0, depth = 18, bestline = "7,7", winRate = "0.51")
        agg.block(1, depth = 18, bestline = "6,8", eval = "-M40")

        agg.block(0, depth = 19, bestline = "7,7", winRate = "0.53")
        // Round 2 has not ended yet, so round 1's leftovers are still shown …
        assertThat(agg.snapshot().tags.keys).contains(i7)
        // … and are gone once round 3 opens.
        agg.consume(line("INFO PV 0"))
        assertThat(agg.snapshot().tags.keys).containsExactly(h8)
    }

    /**
     * What a finished search established is the *last* round, whatever the
     * rounds before it reported. A deeper round may return fewer PVs than a
     * shallower one, and the leftovers kept a valid coordinate with a stale
     * value — which the prove pipeline then read as one of this round's answers.
     */
    @Test
    fun finalPvsAreTheLastRoundOnly() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.block(0, depth = 18, bestline = "7,7", winRate = "0.51")
        agg.block(1, depth = 18, bestline = "6,8", eval = "-M40")
        agg.block(2, depth = 18, bestline = "5,9", eval = "-M20")
        // The deeper round finds only one line worth reporting.
        agg.block(0, depth = 22, bestline = "7,7", winRate = "0.10")

        val last = agg.finalPvs()
        assertThat(last.map { it.index }).containsExactly(0)
        assertThat(last.single().winRate).isWithin(1e-9).of(0.10)
        assertThat(last.single().mate).isNull()
    }
}
