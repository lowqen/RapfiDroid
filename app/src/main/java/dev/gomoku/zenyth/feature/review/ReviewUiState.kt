package dev.gomoku.zenyth.feature.review

import dev.gomoku.zenyth.core.model.GameReport
import dev.gomoku.zenyth.core.model.GradingPreset
import dev.gomoku.zenyth.core.model.QueueEntry
import dev.gomoku.zenyth.core.model.ReviewBudget
import dev.gomoku.zenyth.core.model.ReviewProgress

data class ReviewUiState(
    val progress: ReviewProgress = ReviewProgress(),
    val budget: ReviewBudget = ReviewBudget(),
    val report: GameReport? = null,
    val queueReports: List<GameReport> = emptyList(),
    val queue: List<QueueEntry> = emptyList(),
    val log: List<String> = emptyList(),
    val preset: GradingPreset = GradingPreset.DEFAULT,
    val skipOpening: Boolean = true,
    val showBadges: Boolean = true,
    /** Moves on the board plus the redo tail — what a review would work on. */
    val lineLength: Int = 0,
    val connected: Boolean = false,
    val notice: String? = null,
) {
    val running: Boolean get() = progress.running
    val canReview: Boolean get() = !running && connected && lineLength > 0
    val canQueue: Boolean get() = !running && connected && queue.isNotEmpty()
}
