package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The engine holds its table plus ~95 MB from the start (D5), so after a kill for
 * memory the table steps down one tier at a time and stops at the smallest.
 */
class MemoryTierTest {

    @Test
    fun `a kill steps the table down one tier`() {
        assertThat(MemoryTier.stepDown(512)).isEqualTo(256)
        assertThat(MemoryTier.stepDown(256)).isEqualTo(128)
        assertThat(MemoryTier.stepDown(128)).isEqualTo(64)
        // A size the user typed in between lands on the next tier below it.
        assertThat(MemoryTier.stepDown(300)).isEqualTo(256)
    }

    @Test
    fun `nothing lower than the smallest tier`() {
        assertThat(MemoryTier.stepDown(64)).isNull()
        assertThat(MemoryTier.stepDown(32)).isNull()
    }

    @Test
    fun `the ceiling changes only the table`() {
        val stored = LocalEngineProfile(threadNum = 8, hashSizeMb = 256)
        assertThat(stored.capHash(128)).isEqualTo(stored.copy(hashSizeMb = 128))
        assertThat(stored.capHash(null)).isSameInstanceAs(stored)
        assertThat(stored.capHash(512)).isSameInstanceAs(stored)
    }

    @Test
    fun `the database warning is 5 percent of RAM, never below 64 MB`() {
        assertThat(MemoryTier.databaseWarnBytes(5_500)).isEqualTo((5_500L shl 20) / 20)
        assertThat(MemoryTier.databaseWarnBytes(0)).isEqualTo(64L shl 20)
        assertThat(MemoryTier.databaseWarnBytes(1_000)).isEqualTo(64L shl 20)
    }

    @Test
    fun `SIGKILL is told apart from other exits`() {
        assertThat(MemoryTier.isKill(137)).isTrue()
        assertThat(MemoryTier.isKill(9)).isTrue()
        assertThat(MemoryTier.isKill(132)).isFalse() // SIGILL — the dotprod fallback
        assertThat(MemoryTier.isKill(0)).isFalse()
    }
}
