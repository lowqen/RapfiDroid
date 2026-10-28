package dev.gomoku.zenyth.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * An engine session is hung up only when nobody is looking and nothing is running:
 * a search in the background is exactly what the foreground service is there for.
 */
class EngineIdlePolicyTest {

    private val calm = LinkHealth()
    private val retrying = LinkHealth(reconnecting = true)

    @Test
    fun `nothing is idle while the app is on screen`() {
        for (state in listOf(ConnectionState.Ready, ConnectionState.Error("x"), ConnectionState.Thinking)) {
            assertThat(EngineIdlePolicy.isIdle(foreground = true, state, calm)).isFalse()
        }
        assertThat(EngineIdlePolicy.isIdle(foreground = true, ConnectionState.Disconnected, retrying)).isFalse()
    }

    @Test
    fun `a connected engine waiting in the background is idle`() {
        assertThat(EngineIdlePolicy.isIdle(foreground = false, ConnectionState.Ready, calm)).isTrue()
        assertThat(EngineIdlePolicy.isIdle(foreground = false, ConnectionState.Error("x"), calm)).isTrue()
    }

    @Test
    fun `a search in the background is never cut short`() {
        assertThat(EngineIdlePolicy.isIdle(foreground = false, ConnectionState.Thinking, calm)).isFalse()
        assertThat(EngineIdlePolicy.isIdle(foreground = false, ConnectionState.Connecting, calm)).isFalse()
        assertThat(EngineIdlePolicy.isIdle(foreground = false, ConnectionState.Handshaking, calm)).isFalse()
    }

    @Test
    fun `a reconnect loop keeps the notification up, so it counts as a session`() {
        assertThat(EngineIdlePolicy.isIdle(foreground = false, ConnectionState.Disconnected, retrying)).isTrue()
        // Already off: nothing left to hang up.
        assertThat(EngineIdlePolicy.isIdle(foreground = false, ConnectionState.Disconnected, calm)).isFalse()
    }

    @Test
    fun `the wait is a few minutes`() {
        assertThat(EngineIdlePolicy.PARK_AFTER_MS).isEqualTo(3 * 60_000L)
    }
}
