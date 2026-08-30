package dev.gomoku.rapfidroid.domain.rif

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.PosKey
import org.junit.Test
import kotlin.random.Random

/**
 * The scanner is an optimisation of [PosKey.canonical], and an optimisation of
 * the position key is exactly the kind of change that is wrong in a way nothing
 * notices for months — a slightly different canonical choice merges two openings
 * or splits one, and the statistics are quietly off.
 *
 * So it is checked against the slow one it replaces, over thousands of random
 * positions, on every property the aggregation uses: the key, the stabiliser,
 * the hash, and the representative of the next move.
 */
class PosKeyScannerTest {

    private val size = Move.DEFAULT_SIZE

    private fun cellsOf(moves: List<Move>) = moves.map { it.y * size + it.x }

    private fun scannerFor(moves: List<Move>): PosKeyScanner =
        PosKeyScanner(size).apply {
            reset()
            for (cell in cellsOf(moves)) push(cell)
        }

    /** Random distinct cells — the shapes the aggregation actually walks. */
    private fun randomMoves(random: Random, count: Int): List<Move> {
        val seen = LinkedHashSet<Int>()
        while (seen.size < count) seen += random.nextInt(size * size)
        return seen.map { Move(it % size, it / size) }
    }

    @Test
    fun `key and stabiliser match the reference on random positions`() {
        val random = Random(20260830)
        repeat(400) {
            val moves = randomMoves(random, random.nextInt(0, 21))
            val expected = PosKey.canonical(moves, size)
            val scanner = scannerFor(moves)

            assertThat(scanner.key()).isEqualTo(expected.key)
            assertThat(scanner.stabiliser()).isEqualTo(expected.stabiliser)
        }
    }

    @Test
    fun `every prefix matches, which is what the walk actually asks for`() {
        val random = Random(7)
        repeat(60) {
            val moves = randomMoves(random, 20)
            val scanner = PosKeyScanner(size)
            scanner.reset()
            for (length in 0..moves.size) {
                if (length > 0) {
                    val m = moves[length - 1]
                    scanner.push(m.y * size + m.x)
                }
                val expected = PosKey.canonical(moves.take(length), size)
                assertThat(scanner.key()).isEqualTo(expected.key)
                assertThat(scanner.stabiliser()).isEqualTo(expected.stabiliser)
            }
        }
    }

    @Test
    fun `positions that stress the string comparison agree`() {
        // Rows 10..15 are two digits and 1..9 are one, so serialisations differ
        // in length and `"a10b" < "a9b"`. An index-order comparison would pick a
        // different symmetry here; these positions are chosen to expose that.
        val cases = listOf(
            listOf(Move(0, 0)),
            listOf(Move(0, 14)),
            listOf(Move(7, 7), Move(0, 0), Move(14, 14)),
            listOf(Move(1, 5), Move(9, 13), Move(13, 1), Move(5, 9)),
            listOf(Move(0, 5), Move(0, 13)),
        )
        for (moves in cases) {
            val expected = PosKey.canonical(moves, size)
            val scanner = scannerFor(moves)
            assertThat(scanner.key()).isEqualTo(expected.key)
            assertThat(scanner.stabiliser()).isEqualTo(expected.stabiliser)
        }
    }

    @Test
    fun `hash is the hash of the key it would have built`() {
        val random = Random(99)
        repeat(200) {
            val moves = randomMoves(random, random.nextInt(0, 12))
            val scanner = scannerFor(moves)
            assertThat(scanner.hash64()).isEqualTo(Fnv.hash64(scanner.key()))
        }
    }

    @Test
    fun `matches recognises its own key and nothing else`() {
        val moves = listOf(Move(7, 7), Move(7, 6))
        val scanner = scannerFor(moves)

        assertThat(scanner.matches(scanner.key())).isTrue()
        assertThat(scanner.matches(scanner.key() + "x")).isFalse()
        assertThat(scanner.matches(PosKey.emptyKey(size))).isFalse()
    }

    @Test
    fun `canonNext agrees with the reference`() {
        val random = Random(4242)
        repeat(200) {
            val moves = randomMoves(random, random.nextInt(1, 8))
            val scanner = scannerFor(moves)
            val stabiliser = PosKey.canonical(moves, size).stabiliser

            repeat(10) {
                val cell = random.nextInt(size * size)
                val expected = PosKey.canonNext(stabiliser, Move(cell % size, cell / size), size)
                assertThat(scanner.canonNext(cell)).isEqualTo(expected.y * size + expected.x)
            }
        }
    }

    @Test
    fun `reset returns it to the empty board`() {
        val scanner = scannerFor(listOf(Move(7, 7), Move(3, 4)))
        scanner.reset()

        assertThat(scanner.key()).isEqualTo(PosKey.emptyKey(size))
        assertThat(scanner.stabiliser()).isEqualTo(0xFF)
    }
}
