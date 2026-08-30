package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The toolbar's shape tools, checked against the desktop console commands
 * `rotate` / `flip` / `move` / `getpos` / `putpos` (main.c:10194-10405).
 */
class BoardTransformTest {

    private val size = 15
    private fun move(label: String) = Move.fromLabel(label, size)!!

    // ---- rotate ------------------------------------------------------------

    /**
     * main.c's 90° step is `y = _x; x = size - 1 - _y`. H8 is the centre and must
     * stay put; A1 (bottom-left) becomes A15 (top-left).
     */
    @Test
    fun rotate90MatchesTheDesktopStep() {
        val moves = listOf(move("H8"), move("A1"))
        val turned = BoardTransform.symmetry(moves, size, BoardSymmetry.ROTATE_90)
        assertThat(turned[0]).isEqualTo(move("H8"))
        assertThat(turned[1]).isEqualTo(move("A15"))
    }

    @Test
    fun fourQuarterTurnsAreTheIdentity() {
        val moves = listOf(move("H8"), move("I9"), move("A1"), move("O15"), move("B7"))
        var out = moves
        repeat(4) { out = BoardTransform.symmetry(out, size, BoardSymmetry.ROTATE_90) }
        assertThat(out).isEqualTo(moves)
    }

    @Test
    fun rotate180IsTwoQuarterTurns() {
        val moves = listOf(move("B7"), move("O1"))
        val twice = BoardTransform.symmetry(
            BoardTransform.symmetry(moves, size, BoardSymmetry.ROTATE_90),
            size, BoardSymmetry.ROTATE_90,
        )
        assertThat(BoardTransform.symmetry(moves, size, BoardSymmetry.ROTATE_180)).isEqualTo(twice)
    }

    @Test
    fun rotate270IsThreeQuarterTurns() {
        val moves = listOf(move("B7"), move("O1"), move("H8"))
        var thrice = moves
        repeat(3) { thrice = BoardTransform.symmetry(thrice, size, BoardSymmetry.ROTATE_90) }
        assertThat(BoardTransform.symmetry(moves, size, BoardSymmetry.ROTATE_270)).isEqualTo(thrice)
    }

    // ---- flip --------------------------------------------------------------

    @Test
    fun mirrorsMatchTheDesktopAxes() {
        val a1 = move("A1")
        // `flip |` mirrors the columns, `flip -` the rows, `flip \` transposes.
        assertThat(BoardTransform.symmetry(listOf(a1), size, BoardSymmetry.MIRROR_LEFT_RIGHT))
            .containsExactly(move("O1"))
        assertThat(BoardTransform.symmetry(listOf(a1), size, BoardSymmetry.MIRROR_UP_DOWN))
            .containsExactly(move("A15"))
        assertThat(BoardTransform.symmetry(listOf(move("B3")), size, BoardSymmetry.MIRROR_DIAGONAL))
            .containsExactly(Move(x = move("B3").y, y = move("B3").x))
    }

    @Test
    fun everyMirrorIsItsOwnInverse() {
        val moves = listOf(move("H8"), move("I9"), move("A1"), move("C12"))
        listOf(
            BoardSymmetry.MIRROR_LEFT_RIGHT,
            BoardSymmetry.MIRROR_UP_DOWN,
            BoardSymmetry.MIRROR_DIAGONAL,
            BoardSymmetry.MIRROR_ANTI_DIAGONAL,
        ).forEach { mirror ->
            val there = BoardTransform.symmetry(moves, size, mirror)
            assertThat(BoardTransform.symmetry(there, size, mirror)).isEqualTo(moves)
        }
    }

    /**
     * The one place the app leaves the desktop: main.c's `flip /` is a 180°
     * rotation, so its anti-diagonal mirror is missing. Ours is a real mirror —
     * it keeps the anti-diagonal fixed, which the rotation does not.
     */
    @Test
    fun antiDiagonalMirrorIsAMirrorAndNotTheDesktopRotation() {
        val onAntiDiagonal = move("A1")   // x = 0, y = 14
        assertThat(BoardTransform.symmetry(listOf(onAntiDiagonal), size, BoardSymmetry.MIRROR_ANTI_DIAGONAL))
            .containsExactly(onAntiDiagonal)
        val off = move("B7")
        assertThat(BoardTransform.symmetry(listOf(off), size, BoardSymmetry.MIRROR_ANTI_DIAGONAL))
            .isNotEqualTo(BoardTransform.symmetry(listOf(off), size, BoardSymmetry.ROTATE_180))
    }

    @Test
    fun transformsKeepMoveOrder() {
        val moves = listOf(move("H8"), move("I9"), move("G7"))
        val turned = BoardTransform.symmetry(moves, size, BoardSymmetry.ROTATE_90)
        assertThat(turned).hasSize(3)
        // Colours and numbering follow the index, so the order must survive.
        assertThat(turned[1]).isEqualTo(BoardTransform.symmetry(listOf(move("I9")), size, BoardSymmetry.ROTATE_90)[0])
    }

    // ---- move (shift) ------------------------------------------------------

    @Test
    fun shiftMovesEveryStoneOnePoint() {
        val moves = listOf(move("H8"), move("I9"))
        assertThat(BoardTransform.shift(moves, size, BoardShift.RIGHT))
            .containsExactly(move("I8"), move("J9")).inOrder()
        assertThat(BoardTransform.shift(moves, size, BoardShift.UP))
            .containsExactly(move("H9"), move("I10")).inOrder()
        assertThat(BoardTransform.shift(moves, size, BoardShift.DOWN))
            .containsExactly(move("H7"), move("I8")).inOrder()
        assertThat(BoardTransform.shift(moves, size, BoardShift.LEFT))
            .containsExactly(move("G8"), move("H9")).inOrder()
    }

    /** main.c checks the whole path first and then shifts nothing (flag `f`). */
    @Test
    fun aShiftThatWouldLeaveTheBoardIsRefusedWholesale() {
        val moves = listOf(move("A1"), move("H8"))
        assertThat(BoardTransform.shift(moves, size, BoardShift.LEFT)).isNull()
        assertThat(BoardTransform.shift(moves, size, BoardShift.DOWN)).isNull()
        assertThat(BoardTransform.shift(moves, size, BoardShift.RIGHT)).isNotNull()
    }

    // ---- getpos / putpos ---------------------------------------------------

    @Test
    fun positionStringUsesTheDesktopFormat() {
        // main.c:10401 prints a lower-case column and the 1-based row from the
        // bottom, with no separator.
        val moves = listOf(move("H8"), move("I9"), move("A15"))
        assertThat(BoardTransform.toPositionString(moves, size)).isEqualTo("h8i9a15")
    }

    @Test
    fun positionStringRoundTrips() {
        val moves = listOf(move("H8"), move("I9"), move("G10"), move("O1"), move("A15"))
        val text = BoardTransform.toPositionString(moves, size)
        assertThat(BoardTransform.fromPositionString(text, size)).isEqualTo(moves)
    }

    /** The second digit is taken when there is one: `h11` is row 11, not `h1`. */
    @Test
    fun twoDigitRowsAreParsedGreedily() {
        assertThat(BoardTransform.fromPositionString("h11", size)).containsExactly(move("H11"))
        assertThat(BoardTransform.fromPositionString("h1b3", size))
            .containsExactly(move("H1"), move("B3")).inOrder()
    }

    @Test
    fun parsingStopsAtTheFirstImpossibleToken() {
        // Off-board row, an occupied point, and a non-letter all end the line,
        // keeping what came before (main.c:10389-10393).
        assertThat(BoardTransform.fromPositionString("h8z9", size)).containsExactly(move("H8"))
        assertThat(BoardTransform.fromPositionString("h8h99", size)).containsExactly(move("H8"))
        assertThat(BoardTransform.fromPositionString("h8h8", size)).containsExactly(move("H8"))
        assertThat(BoardTransform.fromPositionString("h8 i9", size)).containsExactly(move("H8"))
        assertThat(BoardTransform.fromPositionString("", size)).isEmpty()
    }

    @Test
    fun uppercaseAndMixedCaseAreAccepted() {
        assertThat(BoardTransform.fromPositionString("H8I9", size))
            .containsExactly(move("H8"), move("I9")).inOrder()
    }
}
