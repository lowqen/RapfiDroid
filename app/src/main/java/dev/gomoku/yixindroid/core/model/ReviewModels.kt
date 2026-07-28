package dev.gomoku.yixindroid.core.model

/**
 * The per-position engine budget a review runs on (`review_send_budget`,
 * main.c:7073): either a wall-clock slice with the depth wide open, or a fixed
 * depth with the clock wide open.
 */
data class ReviewBudget(
    val seconds: Int = 3,
    val byDepth: Boolean = false,
    val depth: Int = 14,
) {
    /** `review_budget_str` — "3 s" or "depth 14", for logs and reports. */
    val label: String get() = if (byDepth) "depth $depth" else "$seconds s"

    /** Watchdog leash: a depth budget has no time bound (main.c:7131). */
    val watchdogSeconds: Int get() = if (byDepth) 1200 else seconds * 2 + 60

    fun sanitized(): ReviewBudget = copy(
        seconds = seconds.coerceIn(1, 120),
        depth = depth.coerceIn(4, 64),
    )
}

/** What the review pipeline is doing right now. */
data class ReviewProgress(
    val running: Boolean = false,
    /** Position being searched, 0..[total]. */
    val index: Int = 0,
    val total: Int = 0,
    val budget: ReviewBudget = ReviewBudget(),
    /** Queue position when the run came from the queue, else null. */
    val queue: QueueProgress? = null,
) {
    val fraction: Float get() = if (total <= 0) 0f else (index.toFloat() / (total + 1)).coerceIn(0f, 1f)
}

data class QueueProgress(val index: Int, val total: Int, val name: String)

/** One game waiting in the analysis queue. */
data class QueueEntry(
    val uri: String,
    val name: String,
    val status: QueueStatus = QueueStatus.PENDING,
    /** Summary line once the game has been reviewed or has failed. */
    val result: String = "",
)

enum class QueueStatus { PENDING, RUNNING, DONE, FAILED }

/**
 * A finished review: the graded line plus everything the report screen and the
 * exports show. Built purely from [ReviewData], so it can be rebuilt with a
 * different preset without touching the engine.
 */
data class GameReport(
    val title: String,
    val size: Int,
    val budget: ReviewBudget,
    val data: ReviewData,
    val preset: GradingPreset,
    val skipOpening: Boolean,
    val moves: List<GradedMove>,
    val tally: ReviewTally,
    val worst: List<GradedMove>,
    /** `rule_name` (main.c:8008) — the opening protocol wins over the base rule. */
    val ruleName: String = "Freestyle Gomoku",
    /** Millis since the epoch, for the report header. */
    val createdAt: Long = 0,
) {
    val moveCount: Int get() = moves.size

    companion object {
        fun of(
            title: String,
            data: ReviewData,
            budget: ReviewBudget,
            preset: GradingPreset,
            skipOpening: Boolean,
            createdAt: Long,
            ruleName: String = "Freestyle Gomoku",
        ): GameReport {
            val graded = MoveGrader.grade(data, preset, skipOpening)
            return GameReport(
                title = title,
                size = data.size,
                budget = budget,
                data = data,
                preset = preset,
                skipOpening = skipOpening,
                moves = graded,
                tally = MoveGrader.tally(graded),
                worst = MoveGrader.worst(graded),
                ruleName = ruleName,
                createdAt = createdAt,
            )
        }

        /** `rule_name`: the opening protocol names the rule when there is one. */
        fun ruleNameOf(settings: AppSettings): String = when (settings.opening) {
            OpeningProtocol.SWAP_FIRST -> "Swap after 1st move"
            OpeningProtocol.SOOSORV -> "Soosorv-8"
            OpeningProtocol.SWAP2 -> "Swap2"
            else -> when (settings.engineRule) {
                1 -> "Standard Gomoku"
                2 -> "Renju"
                else -> "Freestyle Gomoku"
            }
        }
    }
}
