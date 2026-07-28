package dev.gomoku.yixindroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Win detection against `make_move` (main.c:2211): five wins everywhere, an
 * overline wins under every rule except standard gomoku.
 */
class RefereeTest {

    private val size = 15

    /** Black plays [black], White fills harmless points far away. */
    private fun line(black: List<Move>, white: List<Move> = filler(black.size - 1)): List<Move> {
        val out = ArrayList<Move>()
        for (i in black.indices) {
            out += black[i]
            if (i < white.size) out += white[i]
        }
        return out
    }

    /** Stones on the top row, out of the way of anything built near the centre. */
    private fun filler(count: Int): List<Move> = (0 until count).map { Move(x = it, y = 0) }

    @Test
    fun fiveInARowWinsOnEveryAxis() {
        val axes = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
        for ((dx, dy) in axes) {
            val black = (0..4).map { Move(x = 5 + dx * it, y = 7 + dy * it) }
            val result = Referee.result(line(black), size, allowOverline = true)
            assertThat(result).isEqualTo(GameResult(GameEnd.FIVE, StoneColor.BLACK))
        }
    }

    @Test
    fun fourIsNotAWin() {
        val black = (0..3).map { Move(x = 5 + it, y = 7) }
        assertThat(Referee.result(line(black), size, allowOverline = true)).isNull()
    }

    @Test
    fun whiteCanWinToo() {
        // White's five, with Black's stones parked on the top row.
        val moves = ArrayList<Move>()
        val white = (0..4).map { Move(x = 5 + it, y = 7) }
        for (i in 0..4) {
            moves += Move(x = i, y = 0)
            moves += white[i]
        }
        assertThat(Referee.result(moves, size, allowOverline = true))
            .isEqualTo(GameResult(GameEnd.FIVE, StoneColor.WHITE))
    }

    /** `k > 5 && inforule != 1`: an overline is a win unless the rule is standard. */
    @Test
    fun overlineWinsExceptUnderStandard() {
        val black = (0..5).map { Move(x = 4 + it, y = 7) }
        val moves = line(black)
        assertThat(Referee.result(moves, size, allowOverline = true))
            .isEqualTo(GameResult(GameEnd.FIVE, StoneColor.BLACK))
        assertThat(Referee.result(moves, size, allowOverline = false)).isNull()
    }

    /** main.c:2195 ends the game when `piecenum == boardsize * boardsize`. */
    @Test
    fun aFullBoardIsADraw() {
        // Four wide: every point can be taken and five in a row is impossible.
        val moves = ArrayList<Move>()
        for (y in 0 until 4) for (x in 0 until 4) moves += Move(x, y)
        assertThat(Referee.result(moves, size = 4, allowOverline = true))
            .isEqualTo(GameResult(GameEnd.BOARD_FULL, null))
    }

    @Test
    fun anEmptyBoardHasNoResult() {
        assertThat(Referee.result(emptyList(), size, allowOverline = true)).isNull()
    }

    @Test
    fun theWinningLineIsReported() {
        val black = (0..4).map { Move(x = 5 + it, y = 7) }
        val won = Referee.winningLine(line(black), size, allowOverline = true)
        assertThat(won).containsExactlyElementsIn(black)
    }

    // ---- opening area (RIF / Soosorv) ---------------------------------------

    @Test
    fun theOpeningAreaMatchesTheDesktopBox() {
        val c = size / 2
        val ok = listOf(Move(c, c), Move(c + 1, c - 1), Move(c + 2, c + 2))
        assertThat(Referee.openingAreaOk(ok, size)).isTrue()

        // move 1 off centre
        assertThat(Referee.openingAreaOk(listOf(Move(c + 1, c), Move(c, c + 1), Move(c, c + 2)), size))
            .isFalse()
        // move 2 two points away
        assertThat(Referee.openingAreaOk(listOf(Move(c, c), Move(c + 2, c), Move(c, c + 2)), size))
            .isFalse()
        // move 3 three points away
        assertThat(Referee.openingAreaOk(listOf(Move(c, c), Move(c + 1, c), Move(c + 3, c)), size))
            .isFalse()
    }

    @Test
    fun fewerThanThreeMovesIsNotYetJudged() {
        assertThat(Referee.openingAreaOk(listOf(Move(0, 0)), size)).isTrue()
    }

    // ---- swap after first move ----------------------------------------------

    /**
     * `(_x > 1 && _y > 1 && _x + _y > 5)` on the *distance to the nearest edge* —
     * a central opening is strong, so the engine takes it; an edge opening is not
     * worth swapping for.
     */
    @Test
    fun aCentralFirstMoveMakesTheEngineTakeOver() {
        assertThat(Referee.swapAfterFirstMove(Move(x = 7, y = 7), size, coinFlip = false)).isTrue()
        assertThat(Referee.swapAfterFirstMove(Move(x = 3, y = 4), size, coinFlip = false)).isTrue()
        assertThat(Referee.swapAfterFirstMove(Move(x = 0, y = 0), size, coinFlip = false)).isFalse()
        assertThat(Referee.swapAfterFirstMove(Move(x = 1, y = 9), size, coinFlip = false)).isFalse()
    }

    /** The two borderline openings are a coin flip in the desktop. */
    @Test
    fun borderlineOpeningsFollowTheCoin() {
        val borderline = Move(x = 2, y = 3)
        assertThat(Referee.swapAfterFirstMove(borderline, size, coinFlip = true)).isTrue()
        assertThat(Referee.swapAfterFirstMove(borderline, size, coinFlip = false)).isFalse()
    }
}
