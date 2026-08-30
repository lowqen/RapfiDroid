package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The arithmetic the "best win rate" ordering rests on.
 *
 * `res` codes come from freq35: 2 = black won, 1 = draw, 0 = white won, 3 = the
 * dataset does not say.
 */
class ResultSplitTest {

    private fun split(vararg results: Int) =
        results.fold(ResultSplit()) { acc, r -> acc.plus(r) }

    @Test
    fun countsEachResultAndLeavesUnknownOutOfDecided() {
        val s = split(2, 2, 0, 1, 3)
        assertThat(s.total).isEqualTo(5)
        assertThat(s.blackWins).isEqualTo(2)
        assertThat(s.whiteWins).isEqualTo(1)
        assertThat(s.draws).isEqualTo(1)
        assertThat(s.unknown).isEqualTo(1)
        // The unknown game is counted in `total` but cannot be scored.
        assertThat(s.decided).isEqualTo(4)
    }

    @Test
    fun scoreCountsDrawsAsHalfAndMirrorsBetweenSides() {
        val s = split(2, 2, 0, 1)                      // 2 black, 1 white, 1 draw
        assertThat(s.score(RankSide.BLACK)).isWithin(1e-9).of(2.5 / 4)
        assertThat(s.score(RankSide.WHITE)).isWithin(1e-9).of(1.5 / 4)
        // Both sides split the whole point, so the two scores sum to 1.
        assertThat(s.score(RankSide.BLACK)!! + s.score(RankSide.WHITE)!!).isWithin(1e-9).of(1.0)
    }

    @Test
    fun eitherReadsAsBlack() {
        val s = split(2, 0)
        assertThat(s.score(RankSide.EITHER)).isEqualTo(s.score(RankSide.BLACK))
    }

    @Test
    fun nothingDecidedHasNoScore() {
        assertThat(ResultSplit().score(RankSide.BLACK)).isNull()
        assertThat(split(3, 3).score(RankSide.BLACK)).isNull()   // unknown results only
        assertThat(split(3, 3).rankingScore(RankSide.BLACK)).isWithin(1e-9).of(0.0)
    }

    @Test
    fun oneWinFromOneGameDoesNotOutrankTwentyGamesOfWinning() {
        val lucky = split(2)                                        // 100 %, n = 1
        val proven = List(11) { 2 } + List(9) { 0 }                 // 55 %, n = 20
        val real = split(*proven.toIntArray())

        assertThat(lucky.score(RankSide.BLACK)).isWithin(1e-9).of(1.0)
        assertThat(real.score(RankSide.BLACK)).isWithin(1e-9).of(0.55)

        // …and yet the ranking key puts the twenty-game record first. This is the
        // whole reason the sort does not use the plain rate.
        assertThat(lucky.rankingScore(RankSide.BLACK)).isWithin(1e-3).of(0.207)
        assertThat(real.rankingScore(RankSide.BLACK)).isWithin(1e-3).of(0.342)
        assertThat(real.rankingScore(RankSide.BLACK))
            .isGreaterThan(lucky.rankingScore(RankSide.BLACK))
    }

    @Test
    fun moreGamesAtTheSameRateRanksHigher() {
        val few = split(2, 2, 0, 0)                                  // 50 %, n = 4
        val many = split(*(List(50) { 2 } + List(50) { 0 }).toIntArray())  // 50 %, n = 100
        assertThat(few.score(RankSide.BLACK)).isWithin(1e-9).of(0.5)
        assertThat(many.score(RankSide.BLACK)).isWithin(1e-9).of(0.5)
        assertThat(many.rankingScore(RankSide.BLACK))
            .isGreaterThan(few.rankingScore(RankSide.BLACK))
    }

    @Test
    fun rankingScoreStaysAProportion() {
        val perfect = split(*IntArray(30) { 2 })
        val hopeless = split(*IntArray(30) { 0 })
        assertThat(perfect.rankingScore(RankSide.BLACK)).isAtMost(1.0)
        assertThat(hopeless.rankingScore(RankSide.BLACK)).isAtLeast(0.0)
        assertThat(perfect.rankingScore(RankSide.BLACK)).isGreaterThan(0.8)
        assertThat(hopeless.rankingScore(RankSide.BLACK)).isLessThan(0.2)
        // White's view of a game Black never won is the mirror image.
        assertThat(hopeless.rankingScore(RankSide.WHITE))
            .isWithin(1e-9).of(perfect.rankingScore(RankSide.BLACK))
    }
}
