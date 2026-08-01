package dev.gomoku.yixindroid.domain.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The clamp that decides whether a database reply may set the position value —
 * main.c's `dbqueryseq`/`dbdoneseq`, plus the deadline this port needs because
 * its replies cross a VPN and can go missing entirely.
 */
class DbQueryPairingTest {

    private val pairing = DbQueryPairing(lostAfterMs = 1_000)

    @Test
    fun anAnsweredQueryIsPaired() {
        pairing.onQuery(now = 0)
        assertThat(pairing.paired).isFalse()
        pairing.onDone()
        assertThat(pairing.paired).isTrue()
    }

    /** The case it exists for: the user moved on before the reply arrived. */
    @Test
    fun anOlderPositionsReplyIsNotTakenForTheNewOne() {
        pairing.onQuery(now = 0)      // position A
        pairing.onQuery(now = 100)    // position B, A still unanswered
        pairing.onDone()              // A's reply lands
        assertThat(pairing.paired).isFalse()
        pairing.onDone()              // B's reply lands
        assertThat(pairing.paired).isTrue()
    }

    /** `dbval`, `dbtext` and edit acks also end in DONE without a query of ours. */
    @Test
    fun anUnsolicitedDoneCannotRunTheCounterPastTheQueries() {
        pairing.onQuery(now = 0)
        pairing.onDone()
        pairing.onDone()
        pairing.onDone()
        assertThat(pairing.paired).isTrue()

        pairing.onQuery(now = 10)
        assertThat(pairing.paired).isFalse() // and not "already answered twice over"
    }

    /**
     * Without the deadline this is the state the board got stuck in: one reply
     * lost over the VPN and no later answer could ever set the value again.
     */
    @Test
    fun aReplyThatNeverArrivesStopsBlockingTheNextOne() {
        pairing.onQuery(now = 0)      // lost
        pairing.onQuery(now = 500)    // still within the deadline of the first
        pairing.onDone()              // this is the second query's reply
        assertThat(pairing.paired).isFalse()

        pairing.onQuery(now = 5_000)  // long past it: the first is gone, not in flight
        pairing.onDone()
        assertThat(pairing.paired).isTrue()
    }

    @Test
    fun aReplyStillInFlightKeepsItsGuard() {
        pairing.onQuery(now = 0)
        pairing.onQuery(now = 900)    // inside the deadline
        pairing.onDone()
        assertThat(pairing.paired).isFalse()
    }
}
