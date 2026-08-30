package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The board badge, against main.c:2114-2124.
 *
 * The rule that matters: a badge needs **an evaluation, not a review**. The
 * desktop's `wrhistory` is written by `evalbar_set_black_winrate`, whose only
 * two callers are the engine reply and the database reply — so stepping through
 * a game with the database on grades each move as you arrive at it. And only
 * the current move is painted; earlier ones live in the win-rate graph.
 */
class CurrentBadgeTest {

    private val size = 15
    private fun move(label: String) = Move.fromLabel(label, size)!!

    /** A line long enough to clear the ungraded opening (`SKIP_OPENING_N` = 5). */
    private val line = listOf("h8", "i9", "j10", "k11", "g7", "f6", "e5")
        .map { move(it) }

    private fun records(vararg rates: Double?): List<PositionRecord> =
        rates.map { r -> if (r == null) PositionRecord() else PositionRecord(blackWinRate = r) }

    @Test
    fun oneEvaluationOnEachSideIsEnoughToGradeWithoutAReview() {
        // black played move 7 and dropped from 60 % to 20 %: a clear blunder
        val r = records(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.60, 0.20)
        val q = MoveGrader.currentBadge(line, size, r)
        assertThat(q).isNotNull()
        assertThat(q).isEqualTo(MoveQuality.BLUNDER)
    }

    @Test
    fun aGoodMoveKeepsItsEvaluationAndGradesWell() {
        val r = records(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.60, 0.60)
        val q = MoveGrader.currentBadge(line, size, r)
        assertThat(q).isNotNull()
        assertThat(q).isNotEqualTo(MoveQuality.BLUNDER)
        assertThat(q).isNotEqualTo(MoveQuality.MISTAKE)
    }

    /** main.c:2117 — interpolation alone is not data. */
    @Test
    fun withNoRealRecordOnEitherSideThereIsNoBadge() {
        val r = records(0.5, null, null, null, null, null, null, null)
        assertThat(MoveGrader.currentBadge(line, size, r)).isNull()
        assertThat(MoveGrader.currentBadge(line, size, emptyList())).isNull()
    }

    /** One value is enough: the other side interpolates, as the graph does. */
    @Test
    fun aSingleFreshValueStillProducesABadge() {
        val r = records(0.5, null, null, null, null, null, null, 0.05)
        assertThat(MoveGrader.currentBadge(line, size, r)).isNotNull()
    }

    @Test
    fun theOpeningStaysUngradedWhileSkipOpeningIsOn() {
        val short = line.take(3)
        val r = records(0.5, 0.5, 0.6, 0.1)
        assertThat(MoveGrader.currentBadge(short, size, r, skipOpening = true)).isNull()
        assertThat(MoveGrader.currentBadge(short, size, r, skipOpening = false)).isNotNull()
    }

    @Test
    fun anEmptyBoardHasNothingToGrade() {
        assertThat(MoveGrader.currentBadge(emptyList(), size, records(0.5))).isNull()
    }

    /** The badge follows the cursor: stepping back grades the earlier move. */
    @Test
    fun theBadgeIsAlwaysAboutTheLastMoveOnTheBoard() {
        val r = records(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.60, 0.20)
        val blunderAt7 = MoveGrader.currentBadge(line, size, r)
        val stepBack = MoveGrader.currentBadge(line.dropLast(1), size, r)
        assertThat(blunderAt7).isEqualTo(MoveQuality.BLUNDER)
        // move 6 kept the evaluation, so it is not the blunder
        assertThat(stepBack).isNotEqualTo(MoveQuality.BLUNDER)
    }

    /**
     * Mate distances are what a database reply carries (`W5` / `L4`), and they
     * reach the grader intact: throwing away a forced win is not a plain
     * blunder but the specific "missed win" grade.
     */
    @Test
    fun mateRecordsFromTheDatabaseReachTheGrader() {
        val r = listOf(
            PositionRecord(0.5), PositionRecord(0.5), PositionRecord(0.5),
            PositionRecord(0.5), PositionRecord(0.5), PositionRecord(0.5),
            PositionRecord(blackWinRate = 1.0, blackMate = 5),
            PositionRecord(blackWinRate = 0.0, blackMate = -4),
        )
        assertThat(MoveGrader.currentBadge(line, size, r)).isEqualTo(MoveQuality.MISSED_WIN)
    }

    @Test
    fun theStricterPresetGradesAtLeastAsHarshly() {
        val r = records(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.60, 0.47)
        val strict = MoveGrader.currentBadge(line, size, r, GradingPreset.STRICT)
        val lenient = MoveGrader.currentBadge(line, size, r, GradingPreset.LENIENT)
        assertThat(strict).isNotNull()
        assertThat(lenient).isNotNull()
        assertThat(strict!!.ordinal).isAtLeast(lenient!!.ordinal)
    }
}
