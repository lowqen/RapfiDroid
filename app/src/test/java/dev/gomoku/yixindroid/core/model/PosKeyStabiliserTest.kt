package dev.gomoku.yixindroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

/**
 * The stabiliser and [PosKey.canonNext], which aggregation needs and lookup does
 * not. `PosKeyTest` guards the key itself against the golden vectors the
 * four-way cross-check emits; this guards what was added beside it.
 */
class PosKeyStabiliserTest {

    private val size = Move.DEFAULT_SIZE
    private val centre = Move(7, 7)

    @Test
    fun `canonical agrees with the existing key and transform`() {
        val random = Random(20260830)
        repeat(200) {
            val moves = (0 until random.nextInt(0, 8)).map {
                Move(random.nextInt(size), random.nextInt(size))
            }.distinct()
            val old = PosKey.of(moves, size)
            val new = PosKey.canonical(moves, size)

            assertThat(new.key).isEqualTo(old.key)
            assertThat(new.transform).isEqualTo(old.transform)
            // The adopted transform is always in the stabiliser, and the
            // stabiliser is never empty — identity ties with itself at worst.
            assertThat(new.stabiliser and (1 shl new.transform)).isNotEqualTo(0)
            assertThat(new.stabiliser).isNotEqualTo(0)
        }
    }

    @Test
    fun `an empty board is invariant under everything`() {
        assertThat(PosKey.canonical(emptyList(), size).stabiliser).isEqualTo(0xFF)
    }

    @Test
    fun `a single centre stone is invariant under everything`() {
        assertThat(PosKey.canonical(listOf(centre), size).stabiliser).isEqualTo(0xFF)
    }

    @Test
    fun `an asymmetric shape is invariant under the identity alone`() {
        // Not (7,7) (8,6) (9,5): those three sit on one anti-diagonal through
        // the centre, so the anti-diagonal mirror fixes every one of them and
        // the stabiliser has two elements. Moving the last stone off that line
        // is what makes the shape generic.
        val moves = listOf(centre, Move(8, 6), Move(9, 4))
        val canonical = PosKey.canonical(moves, size)

        assertThat(Integer.bitCount(canonical.stabiliser)).isEqualTo(1)
        assertThat(canonical.stabiliser).isEqualTo(1 shl canonical.transform)
    }

    @Test
    fun `every symmetry in the stabiliser really produces the same key`() {
        val moves = listOf(centre, Move(7, 6))   // a mirror axis survives
        val canonical = PosKey.canonical(moves, size)
        assertThat(Integer.bitCount(canonical.stabiliser)).isGreaterThan(1)

        for (t in 0 until 8) {
            if (canonical.stabiliser and (1 shl t) == 0) continue
            val image = moves.map { PosKey.tf(t, size, it) }
            assertThat(PosKey.keyOf(image, size)).isEqualTo(canonical.key)
        }
    }

    @Test
    fun `mirrored replies to a centre stone share one representative`() {
        // The board is symmetric after one central stone, so "directly above"
        // and "directly below" are the same continuation. Aggregation counts
        // them together only because of this.
        val stabiliser = PosKey.canonical(listOf(centre), size).stabiliser
        val above = PosKey.canonNext(stabiliser, Move(7, 6), size)
        val below = PosKey.canonNext(stabiliser, Move(7, 8), size)
        val left = PosKey.canonNext(stabiliser, Move(6, 7), size)

        assertThat(above).isEqualTo(below)
        assertThat(above).isEqualTo(left)
        // The representative is the smallest *board index*, not the smallest
        // key string — index order is what `rifkey.canon_next` uses.
        assertThat(above.y * size + above.x).isEqualTo(6 * size + 7)
    }

    @Test
    fun `a trivial stabiliser leaves the move alone`() {
        val move = Move(9, 5)
        assertThat(PosKey.canonNext(1 shl 0, move, size)).isEqualTo(move)
    }
}
