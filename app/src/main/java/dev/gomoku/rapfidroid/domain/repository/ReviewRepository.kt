package dev.gomoku.rapfidroid.domain.repository

import dev.gomoku.rapfidroid.core.model.GameFileContent
import dev.gomoku.rapfidroid.core.model.GameReport
import dev.gomoku.rapfidroid.core.model.GradingPreset
import dev.gomoku.rapfidroid.core.model.QueueEntry
import dev.gomoku.rapfidroid.core.model.ReviewBudget
import dev.gomoku.rapfidroid.core.model.ReviewProgress
import kotlinx.coroutines.flow.StateFlow

/** Outcome of asking for a run — the desktop logs the same refusals. */
sealed interface ReviewStart {
    data object Started : ReviewStart
    data class Refused(val reason: String) : ReviewStart
}

/**
 * Game review and the analysis queue (main.c `game_review` / `analysis_queue`):
 * search every position of the line on a fixed budget, grade the moves from
 * what came back, and hand out a report.
 *
 * A singleton for the same reason the game is: a review outlives the screen
 * that started it.
 */
interface ReviewRepository {
    val progress: StateFlow<ReviewProgress>

    /** The last finished review, re-graded whenever the preset changes. */
    val report: StateFlow<GameReport?>

    /** Reports of every game a queue run has finished, newest last. */
    val queueReports: StateFlow<List<GameReport>>

    val queue: StateFlow<List<QueueEntry>>

    /** Status lines, the desktop's `printf_log` output for these runs. */
    val log: StateFlow<List<String>>

    /** Review the line on the board. */
    suspend fun start(budget: ReviewBudget): ReviewStart

    /** Review every queued game in turn, unattended. */
    suspend fun startQueue(budget: ReviewBudget): ReviewStart

    /** Stop the run; a queued run stops the whole queue, as the desktop does. */
    suspend fun cancel()

    fun enqueue(entries: List<QueueEntry>)
    fun removeQueued(uri: String)
    fun clearQueue()

    /** Put a parsed game file on the board (`load_game_file`). */
    suspend fun loadGame(content: GameFileContent)

    /** Re-grade the current report with another preset (settings_dev line 3). */
    fun setPreset(preset: GradingPreset)

    fun clearReport()
}

/** Reads a queued game file; SAF in the app, a map in tests. */
fun interface GameFileReader {
    suspend fun read(uri: String): ByteArray?
}
