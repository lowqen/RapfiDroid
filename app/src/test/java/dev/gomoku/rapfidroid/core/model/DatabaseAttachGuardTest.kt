package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The client-side stand-in for a guard the engine does not have.
 *
 * Every case here is a real path that reached the engine before: the connect
 * handshake, the re-handshake a rule change triggers, the `dbrefresh` console
 * command, and a settings push. Each repeat used to cost a whole second copy of
 * a 47.7-million-record database.
 */
class DatabaseAttachGuardTest {

    private val guard = DatabaseAttachGuard()

    private fun attach() = guard.allow("usedatabase", "1")
    private fun detach() = guard.allow("usedatabase", "0")

    @Test
    fun theFirstAttachGoesThroughAndTheRepeatDoesNot() {
        assertThat(attach()).isTrue()
        assertThat(attach()).isFalse()
        assertThat(attach()).isFalse()
        assertThat(guard.isAttached).isTrue()
    }

    /** A rule change re-runs the whole handshake; only the first attach is real. */
    @Test
    fun aSecondHandshakeOnTheSameConnectionAttachesNothing() {
        val handshake = { listOf("rule" to "2", "hash_size" to "8388608", "usedatabase" to "1") }
        val first = handshake().filter { (k, v) -> guard.allow(k, v) }
        val second = handshake().filter { (k, v) -> guard.allow(k, v) }
        assertThat(first).hasSize(3)
        assertThat(second.map { it.first }).containsExactly("rule", "hash_size")
    }

    /** Detaching frees the copy, so it always goes through — and re-arms attach. */
    @Test
    fun detachAlwaysGoesThroughAndReArmsTheNextAttach() {
        assertThat(attach()).isTrue()
        assertThat(detach()).isTrue()
        assertThat(guard.isAttached).isFalse()
        assertThat(attach()).isTrue()
    }

    /** Off-then-on is the safe reload: the `0` frees before the `1` builds. */
    @Test
    fun repeatedDetachIsHarmlessAndStaysDetached() {
        assertThat(detach()).isTrue()
        assertThat(detach()).isTrue()
        assertThat(guard.isAttached).isFalse()
    }

    /** A new socket is a new engine process, holding nothing. */
    @Test
    fun aReconnectHasToAttachAgain() {
        assertThat(attach()).isTrue()
        guard.reset()
        assertThat(guard.isAttached).isFalse()
        assertThat(attach()).isTrue()
    }

    @Test
    fun everyOtherInfoKeyPassesThroughUntouched() {
        assertThat(guard.allow("hash_size", "1")).isTrue()
        assertThat(guard.allow("hash_size", "1")).isTrue()
        assertThat(guard.allow("database_readonly", "1")).isTrue()
        assertThat(guard.allow("database_readonly", "1")).isTrue()
        assertThat(guard.isAttached).isFalse()
    }

    /** The desktop writes it lowercase, the engine matches uppercase. */
    @Test
    fun theKeyIsMatchedWithoutRegardToCase() {
        assertThat(guard.allow("USEDATABASE", "1")).isTrue()
        assertThat(guard.allow("usedatabase", "1")).isFalse()
    }

    @Test
    fun surroundingSpaceDoesNotHideARepeat() {
        assertThat(guard.allow("usedatabase", " 1 ")).isTrue()
        assertThat(guard.allow("usedatabase", "1")).isFalse()
    }
}
