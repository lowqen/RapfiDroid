package dev.gomoku.zenyth.feature.board

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The evaluation graph's arithmetic (R3): swings, changes, runs, touch to move. */
class EvalGraphMathTest {

    @Test
    fun aSwingIsFifteenPointsInOneMoveBetweenReadMoves() {
        val history = listOf(0.5, 0.52, 0.70, null, 0.2, 0.18)
        // 0.52 → 0.70 swings; across the unread move nothing is compared.
        assertThat(EvalGraphMath.swings(history)).containsExactly(2)
    }

    @Test
    fun theChangeReachesBackOverUnreadMoves() {
        val history = listOf(0.5, 0.6, null, 0.45)
        assertThat(EvalGraphMath.change(history, 1)!!).isWithin(1e-9).of(0.1)
        assertThat(EvalGraphMath.change(history, 3)!!).isWithin(1e-9).of(-0.15)
        assertThat(EvalGraphMath.change(history, 2)).isNull()
        assertThat(EvalGraphMath.change(history, 0)).isNull()
    }

    @Test
    fun readStretchesAreRunsOfTheirOwn() {
        val runs = EvalGraphMath.runs(listOf(null, 0.5, 0.6, null, null, 0.4))
        assertThat(runs.map { run -> run.map { it.first } }).containsExactly(listOf(1, 2), listOf(5)).inOrder()
    }

    @Test
    fun aTouchLandsOnTheNearestMove() {
        // 11 plies (0..10) over 100 px: one every 10 px.
        assertThat(EvalGraphMath.plyAt(0f, 100f, 11)).isEqualTo(0)
        assertThat(EvalGraphMath.plyAt(44f, 100f, 11)).isEqualTo(4)
        assertThat(EvalGraphMath.plyAt(46f, 100f, 11)).isEqualTo(5)
        assertThat(EvalGraphMath.plyAt(-30f, 100f, 11)).isEqualTo(0)
        assertThat(EvalGraphMath.plyAt(130f, 100f, 11)).isEqualTo(10)
    }

    @Test
    fun moveNumbersThinOutOnLongGames() {
        assertThat(EvalGraphMath.tickStep(20)).isEqualTo(5)
        assertThat(EvalGraphMath.tickStep(60)).isEqualTo(10)
        assertThat(EvalGraphMath.tickStep(150)).isEqualTo(20)
    }
}
