package dev.gomoku.yixindroid.domain.engine

import kotlin.math.min

/**
 * The desktop's `dbqueryseq` / `dbdoneseq` pair (main.c:1613 and 13550).
 *
 * A database refresh answers with a stream of cell values ending in `DONE`.
 * Over the VPN the reply for a position the user has already left can still be
 * in flight, and taking its value would flip the mate parity of the board on
 * screen — so the desktop counts queries and DONEs and lets only the newest
 * query's reply set the position value.
 *
 * The counting has one failure the desktop never meets on localhost: a reply
 * that never arrives at all leaves the two counters apart **forever**, and with
 * them the position value frozen on whatever it last held. So an unanswered
 * query is given a deadline — past it the reply is not in flight any more, it is
 * gone, and holding the gate open for it only costs the next answer.
 */
class DbQueryPairing(private val lostAfterMs: Long = LOST_AFTER_MS) {

    private var sent = 0
    private var answered = 0
    private var lastSentAt = 0L

    /** A refresh query is going out now. */
    fun onQuery(now: Long) {
        if (sent > answered && now - lastSentAt > lostAfterMs) answered = sent
        sent++
        lastSentAt = now
    }

    /**
     * A `DONE` arrived. Clamped exactly like main.c: a DONE from something that
     * was not a refresh — `dbval`, `dbtext`, the ack of an edit — must not push
     * the counter past the queries actually sent.
     */
    fun onDone() {
        answered = min(answered + 1, sent)
    }

    /** True when every query sent has been answered: the reply is the current one. */
    val paired: Boolean get() = sent == answered

    private companion object {
        const val LOST_AFTER_MS = 8_000L
    }
}
