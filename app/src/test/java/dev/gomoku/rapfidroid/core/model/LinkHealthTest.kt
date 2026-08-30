package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinkHealthTest {

    /** 2, 4, 8, 16, 32, then a flat minute — never faster than the far end. */
    @Test
    fun theBackoffGrowsAndThenLevelsOff() {
        val delays = (1..8).map { LinkHealth.delaySecondsFor(it) }
        assertThat(delays).containsExactly(2, 4, 8, 16, 32, 60, 60, 60).inOrder()
    }

    @Test
    fun aFirstRetryNeverWaitsLessThanTwoSeconds() {
        assertThat(LinkHealth.delaySecondsFor(0)).isEqualTo(2)
        assertThat(LinkHealth.delaySecondsFor(-3)).isEqualTo(2)
    }

    @Test
    fun aFreshHealthIsIdleAndSaysNothing() {
        val health = LinkHealth()
        assertThat(health.idle).isTrue()
        assertThat(health.reconnecting).isFalse()
        assertThat(health.attempt).isEqualTo(0)
    }

    @Test
    fun aRememberedErrorIsNotIdleEvenAfterTheRetryStops() {
        val health = LinkHealth(reconnecting = false, lastError = "connection reset")
        assertThat(health.idle).isFalse()
    }

    /** The ping must be able to time out well inside one idle period. */
    @Test
    fun theReplyWindowFitsInsideTheIdlePeriod()  {
        assertThat(LinkHealth.PING_REPLY_SECONDS).isLessThan(LinkHealth.IDLE_PING_SECONDS)
    }
}
