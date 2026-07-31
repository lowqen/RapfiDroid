package dev.gomoku.yixindroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * `block compare` / `blockpath except` — the two inverted block commands.
 *
 * They are the only ones that derive their point list instead of taking it, and
 * they send one engine command per point, so an off-by-one here means hundreds
 * of wrong commands rather than one.
 */
class BlockTargetsTest {

    private val size = 15
    private fun move(label: String) = Move.fromLabel(label, size)!!

    @Test
    fun everythingButTheKeptPointsIsBlocked() {
        val keep = listOf(move("h8"), move("i9"))
        val target = BlockTargets.complement(keep, emptyList(), size)
        assertThat(target).hasSize(size * size - 2)
        assertThat(target).containsNoneIn(keep)
        assertThat(target).contains(move("a1"))
        assertThat(target).contains(move("o15"))
    }

    /** Occupied points are skipped — the engine cannot play there anyway. */
    @Test
    fun stonesOnTheBoardAreNotBlocked() {
        val played = listOf(move("h8"), move("i9"), move("j10"))
        val target = BlockTargets.complement(listOf(move("g7")), played, size)
        assertThat(target).hasSize(size * size - 4)
        assertThat(target).containsNoneIn(played)
        assertThat(target).doesNotContain(move("g7"))
    }

    @Test
    fun aKeptPointThatIsAlsoOccupiedIsCountedOnce() {
        val played = listOf(move("h8"))
        val target = BlockTargets.complement(listOf(move("h8")), played, size)
        assertThat(target).hasSize(size * size - 1)
        assertThat(target).doesNotContain(move("h8"))
    }

    @Test
    fun keepingNothingBlocksEveryEmptyPoint() {
        val target = BlockTargets.complement(emptyList(), listOf(move("h8")), size)
        assertThat(target).hasSize(size * size - 1)
    }

    @Test
    fun theOrderIsRowMajorLikeTheDesktopWalk() {
        val target = BlockTargets.complement(emptyList(), emptyList(), 3)
        assertThat(target.first()).isEqualTo(Move(0, 0))
        assertThat(target[1]).isEqualTo(Move(1, 0))
        assertThat(target[3]).isEqualTo(Move(0, 1))
        assertThat(target).hasSize(9)
    }
}
