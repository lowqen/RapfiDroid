package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.exp

/**
 * The grader against `classify_moves` / `mq_tally` (main.c:1398 / 1585). Every
 * case here is a threshold the desktop draws, so a drift shows up as a
 * different grade for the same review data.
 */
class MoveQualityTest {

    private val size = 15

    /** A line of harmless moves; only the records matter for grading. */
    private fun line(n: Int) = (0 until n).map { Move(x = it % size, y = it / size) }

    private fun data(vararg records: PositionRecord) =
        ReviewData(moves = line(records.size - 1), size = size, records = records.toList())

    private fun rec(wr: Double?, mate: Int = 0, best: Move? = null, gap: Double? = null) =
        PositionRecord(blackWinRate = wr, blackMate = mate, best = best, gap = gap)

    private fun gradeOf(data: ReviewData, index: Int, preset: GradingPreset = GradingPreset.DEFAULT) =
        MoveGrader.grade(data, preset, skipOpening = false)[index - 1]

    // ---- dWR thresholds ----------------------------------------------------

    @Test
    fun deltaThresholdsMatchTheDesktopScale() {
        // Black plays move 1: 60% -> the given black winrate after it.
        val cases = listOf(
            0.60 to MoveQuality.BEST,        // 0 %p
            0.585 to MoveQuality.EXCELLENT,  // 1.5 %p
            0.55 to MoveQuality.GOOD,        // 5 %p
            0.49 to MoveQuality.INACCURACY,  // 11 %p
            0.40 to MoveQuality.MISTAKE,     // 20 %p
            0.20 to MoveQuality.BLUNDER,     // 40 %p
        )
        for ((after, expected) in cases) {
            val d = data(rec(0.60), rec(after))
            assertThat(gradeOf(d, 1).quality).isEqualTo(expected)
        }
    }

    @Test
    fun theStrictPresetGradesTheSameLossHarder() {
        val d = data(rec(0.60), rec(0.55))       // 5 %p
        assertThat(gradeOf(d, 1, GradingPreset.DEFAULT).quality).isEqualTo(MoveQuality.GOOD)
        assertThat(gradeOf(d, 1, GradingPreset.STRICT).quality).isEqualTo(MoveQuality.INACCURACY)
        assertThat(gradeOf(d, 1, GradingPreset.LENIENT).quality).isEqualTo(MoveQuality.GOOD)
    }

    /**
     * A win turned into a loss is a blunder whatever the delta says — visible
     * only under the lenient preset, where 36 %p would otherwise be a mistake.
     */
    @Test
    fun aWinTurnedLossIsAlwaysABlunder() {
        val d = data(rec(0.70), rec(0.34))
        assertThat(gradeOf(d, 1, GradingPreset.LENIENT).quality).isEqualTo(MoveQuality.BLUNDER)
        // The same loss without the reversal stays a mistake there.
        val milder = data(rec(0.60), rec(0.24))
        assertThat(gradeOf(milder, 1, GradingPreset.LENIENT).quality).isEqualTo(MoveQuality.MISTAKE)
    }

    @Test
    fun theEnginesOwnMoveIsBestEvenWithALoss() {
        val played = line(1).first()
        val d = data(rec(0.60, best = played), rec(0.40))
        assertThat(gradeOf(d, 1).quality).isEqualTo(MoveQuality.BEST)
    }

    // ---- special grades ----------------------------------------------------

    /** Mate established where none was known, from a position that was not won. */
    @Test
    fun aNewForcedWinIsBrilliant() {
        val d = data(rec(0.50), rec(1.0, mate = 3))
        assertThat(gradeOf(d, 1).quality).isEqualTo(MoveQuality.BRILLIANT)
    }

    /** The engine's move, a big gap to the second best, and it clinches the win. */
    @Test
    fun theOnlyWinningBlowIsBrilliant() {
        val played = line(1).first()
        val d = data(rec(0.65, best = played, gap = 0.30), rec(0.95))
        assertThat(gradeOf(d, 1).quality).isEqualTo(MoveQuality.BRILLIANT)
    }

    /** Best move, the alternative drops to clearly-losing but not hopeless. */
    @Test
    fun theOnlyPlayableMoveIsGreat() {
        val played = line(1).first()
        val d = data(rec(0.55, best = played, gap = 0.20), rec(0.60))
        assertThat(gradeOf(d, 1).quality).isEqualTo(MoveQuality.GREAT)
    }

    /** A huge gap with a dead-lost alternative is a routine block, not Great. */
    @Test
    fun aForcedBlockAgainstAHopelessAlternativeIsOnlyBest() {
        val played = line(1).first()
        val d = data(rec(0.55, best = played, gap = 0.52), rec(0.60))
        assertThat(gradeOf(d, 1).quality).isEqualTo(MoveQuality.BEST)
    }

    @Test
    fun losingAForcedWinIsAMissedWin() {
        val d = data(rec(1.0, mate = 4), rec(0.50))
        assertThat(gradeOf(d, 1).quality).isEqualTo(MoveQuality.MISSED_WIN)
    }

    @Test
    fun aDeadLostPositionWithoutMateDataIsForced() {
        val d = data(rec(0.03), rec(0.02))
        assertThat(gradeOf(d, 1).quality).isEqualTo(MoveQuality.FORCED)
    }

    /**
     * With exact mate distances on both sides the *resistance* is graded:
     * `kept = -ma - (-mb - 1)`, 0 = held the longest.
     */
    @Test
    fun resistanceIsGradedFromTheMateDistance() {
        // Black to move (move 1) and getting mated: mb/ma are black-perspective.
        // Mated in 10 before, so the longest defense leaves mate in 9.
        val cases = listOf(
            -9 to MoveQuality.BEST,          // kept 0
            -8 to MoveQuality.GOOD,          // kept -1
            -6 to MoveQuality.INACCURACY,    // kept -3
            -3 to MoveQuality.MISTAKE,       // kept -6
        )
        for ((after, expected) in cases) {
            val d = data(rec(0.0, mate = -10), rec(0.0, mate = after))
            assertThat(gradeOf(d, 1).quality).isEqualTo(expected)
        }
        // Shedding more than nine steps of resistance is a blunder.
        val collapse = data(rec(0.0, mate = -20), rec(0.0, mate = -9))
        assertThat(gradeOf(collapse, 1).quality).isEqualTo(MoveQuality.BLUNDER)
    }

    // ---- interpolation and N ----------------------------------------------

    @Test
    fun missingValuesAreInterpolatedForTheGraph() {
        val d = data(rec(0.20), rec(null), rec(null), rec(0.80))
        assertThat(d.winRateAt(1, 3)).isWithin(1e-9).of(0.40)
        assertThat(d.winRateAt(2, 3)).isWithin(1e-9).of(0.60)
    }

    @Test
    fun anEmptyReviewSitsAtFiftyPercent() {
        val d = data(rec(null), rec(null))
        assertThat(d.winRateAt(1, 1)).isEqualTo(0.5)
    }

    @Test
    fun openingMovesStayUngradedWhenSkipped() {
        val records = (0..6).map { rec(0.5) }
        val d = ReviewData(moves = line(6), size = size, records = records)
        val graded = MoveGrader.grade(d, GradingPreset.DEFAULT, skipOpening = true)
        assertThat(graded.take(5).map { it.quality })
            .containsExactlyElementsIn(List(5) { MoveQuality.NONE })
        assertThat(graded[5].quality).isNotEqualTo(MoveQuality.NONE)
    }

    // ---- tally -------------------------------------------------------------

    @Test
    fun accuracyIsTheDesktopsExponentialAverage() {
        // One black move losing 10 %p, one white move losing nothing.
        val d = data(rec(0.60), rec(0.50), rec(0.50))
        val graded = MoveGrader.grade(d, GradingPreset.DEFAULT, skipOpening = false)
        val tally = MoveGrader.tally(graded)
        assertThat(tally.blackAccuracy!!).isWithin(1e-6).of(100.0 * exp(-0.10 * 100.0 / 40.0))
        assertThat(tally.whiteAccuracy!!).isWithin(1e-6).of(100.0)
    }

    @Test
    fun aSideWithoutGradedMovesHasNoAccuracy() {
        val d = data(rec(0.5), rec(0.5))
        val tally = MoveGrader.tally(MoveGrader.grade(d, GradingPreset.DEFAULT, skipOpening = true))
        assertThat(tally.blackAccuracy).isNull()
        assertThat(tally.whiteAccuracy).isNull()
    }

    @Test
    fun forcedMovesCountButDoNotAffectAccuracy() {
        val d = data(rec(0.02), rec(0.01))
        val graded = MoveGrader.grade(d, GradingPreset.DEFAULT, skipOpening = false)
        val tally = MoveGrader.tally(graded)
        assertThat(tally.counts[MoveQuality.FORCED]).isEqualTo(1 to 0)
        assertThat(tally.blackAccuracy).isNull()
    }

    // ---- worst -------------------------------------------------------------

    @Test
    fun theWorstListRanksByLoss() {
        val d = data(
            rec(0.50),
            rec(0.30),           // move 1 (black): -20 %p, mistake
            rec(0.35),           // move 2 (white): -5 %p, good — not in the pool
            rec(0.05),           // move 3 (black): -30 %p, mistake
        )
        val worst = MoveGrader.worst(MoveGrader.grade(d, GradingPreset.DEFAULT, skipOpening = false))
        assertThat(worst.map { it.index }).containsExactly(3, 1).inOrder()
    }

    /** `key = dWR + 0.5` for a missed win, so it outranks a bigger plain loss. */
    @Test
    fun aMissedWinOutranksABiggerLoss() {
        val d = data(
            rec(1.0, mate = 3),  // black to move with a forced win
            rec(0.75),           // move 1 (black): the win is gone, -25 %p
            rec(0.80),           // move 2 (white): -5 %p, not in the pool
            rec(0.10),           // move 3 (black): -70 %p blunder
        )
        val graded = MoveGrader.grade(d, GradingPreset.DEFAULT, skipOpening = false)
        val worst = MoveGrader.worst(graded)
        assertThat(worst.map { it.index }).containsExactly(1, 3).inOrder()
        assertThat(worst.first().quality).isEqualTo(MoveQuality.MISSED_WIN)
        // …even though move 3 lost far more.
        assertThat(worst[1].delta).isGreaterThan(worst[0].delta)
    }

    @Test
    fun aCleanGameHasNoWorstMoves() {
        val d = data(rec(0.50), rec(0.50), rec(0.50))
        val graded = MoveGrader.grade(d, GradingPreset.DEFAULT, skipOpening = false)
        assertThat(MoveGrader.worst(graded)).isEmpty()
    }

    // ---- cells -------------------------------------------------------------

    @Test
    fun coordinatesAreLowercaseWithBottomUpRows() {
        assertThat(MoveGrader.coord(Move(x = 7, y = 7), size)).isEqualTo("h8")
        assertThat(MoveGrader.coord(Move(x = 0, y = 0), size)).isEqualTo("a15")
        assertThat(MoveGrader.coord(null, size)).isEqualTo("-")
    }

    @Test
    fun theWinRateCellShowsMatesAndInterpolation() {
        val d = data(rec(0.50), rec(1.0, mate = 3), rec(null))
        val graded = MoveGrader.grade(d, GradingPreset.DEFAULT, skipOpening = false)
        assertThat(MoveGrader.winRateCell(graded[0])).isEqualTo("B M3")
        assertThat(MoveGrader.winRateCell(graded[1])).startsWith("(")
    }
}
