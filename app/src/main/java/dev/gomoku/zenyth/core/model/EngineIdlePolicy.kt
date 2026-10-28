package dev.gomoku.zenyth.core.model

/**
 * When an engine session the user has walked away from should be hung up.
 *
 * A connected engine that is not searching still costs: the on-device engine holds
 * its table and weights resident (0.3 ~ 0.5 GB on the Galaxy A16 — 256 MB table plus
 * the renju net), a server link keeps a remote process alive, and either keeps the
 * ongoing notification up. Starting again is cheap — the local engine is ready in
 * ~0.65 s (process 377 ms + weights 280 ms, docs/엔진-고도화-마스터플랜.md R2) — and
 * the session is brought back by itself when the app returns, so the wait can be
 * short: [PARK_AFTER_MS] covers a quick look at another app without paying for
 * the ones that never come back.
 *
 * A search is never cut short: an analysis left running in the background is what
 * the foreground service is for.
 */
object EngineIdlePolicy {

    const val PARK_AFTER_MS = 3 * 60_000L

    /**
     * Whether the session is idle and out of sight: the app is in the background and
     * the engine is up (or trying to come back) but not searching.
     */
    fun isIdle(foreground: Boolean, state: ConnectionState, health: LinkHealth): Boolean {
        if (foreground) return false
        return when (state) {
            ConnectionState.Ready, is ConnectionState.Error -> true
            // A reconnect loop waiting in the background is a session too — it is
            // what keeps the notification up after the link died.
            ConnectionState.Disconnected -> health.reconnecting
            ConnectionState.Thinking, ConnectionState.Connecting, ConnectionState.Handshaking -> false
        }
    }
}
