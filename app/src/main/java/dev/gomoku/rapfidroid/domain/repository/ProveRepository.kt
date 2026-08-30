package dev.gomoku.rapfidroid.domain.repository

import dev.gomoku.rapfidroid.core.model.ProveOptions
import dev.gomoku.rapfidroid.core.model.ProveOutcome
import dev.gomoku.rapfidroid.core.model.ProveOverlay
import dev.gomoku.rapfidroid.core.model.ProveProgress
import kotlinx.coroutines.flow.StateFlow

/** Outcome of asking for a run — the desktop logs the same refusals (main.c:9867). */
sealed interface ProveStart {
    data object Started : ProveStart
    data class Refused(val reason: String) : ProveStart
}

/**
 * Position prove (main.c:8918-9979): does the side to move win here?
 *
 * Unlike the review this **writes to the database**. Every proven mate becomes a
 * `yxedittvddatabase` record in the engine's `rapfi.db`, so the run refuses to
 * start unless the database is on and read-only is off — a read-only engine would
 * throw the whole proof away silently.
 *
 * A singleton for the same reason the review is: a proof takes minutes and must
 * survive the screen that started it.
 */
interface ProveRepository {
    val progress: StateFlow<ProveProgress>

    /** Ghost stones and root-candidate markers for the board (display only). */
    val overlay: StateFlow<ProveOverlay>

    /** The last finished run, until the user dismisses it. */
    val outcome: StateFlow<ProveOutcome?>

    /** Status lines, the desktop's `printf_log` output for this run. */
    val log: StateFlow<List<String>>

    /** Prove the position on the board. */
    suspend fun start(options: ProveOptions): ProveStart

    /** Stop the run (the desktop's second click on the menu item). */
    suspend fun cancel()

    fun clearOutcome()
}
