package dev.gomoku.rapfidroid.core.model

/**
 * How the link to the engine is holding up — the part of P11 the desktop never
 * needed.
 *
 * engine.exe is a child process on the same machine: if it dies, Yixin has
 * bigger problems than reconnecting. Here the same relay is a TCP socket over a
 * VPN to an on-demand server, crossing a phone radio that sleeps, a NAT that
 * forgets idle flows, and a tailnet that drops when the screen goes off. A
 * session that survives a walk between rooms has to expect the socket to die
 * and to come back on its own.
 */
data class LinkHealth(
    /** A reconnect is scheduled or in flight. */
    val reconnecting: Boolean = false,
    /** Attempts since the link was last healthy; 1 is the first retry. */
    val attempt: Int = 0,
    /** Seconds until the next attempt, counted down for the UI. */
    val retryInSeconds: Int = 0,
    /** Why the link dropped, as the socket reported it. */
    val lastError: String? = null,
    /** Reconnects that succeeded this session — the honest "it happened" count. */
    val recovered: Int = 0,
) {
    val idle: Boolean get() = !reconnecting && lastError == null

    /**
     * Backoff for [attempt]: 2, 4, 8, 16, 32, then a minute. Retrying harder
     * does not wake an on-demand server any faster, and each attempt costs a
     * full engine spawn plus a database load on the far end.
     */
    companion object {
        fun delaySecondsFor(attempt: Int): Int =
            when {
                attempt <= 1 -> 2
                attempt >= 6 -> 60
                else -> 1 shl attempt
            }

        /**
         * Nothing heard for this long on a settled link means it is worth
         * poking. Long enough that an idle session is not chatty, short enough
         * that the user is not staring at a dead socket.
         */
        const val IDLE_PING_SECONDS = 150

        /** A poke that gets no answer in this long means the link is gone. */
        const val PING_REPLY_SECONDS = 20
    }
}
