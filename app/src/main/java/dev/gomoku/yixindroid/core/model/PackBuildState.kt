package dev.gomoku.yixindroid.core.model

import dev.gomoku.yixindroid.core.i18n.tr

/**
 * Where the on-device pack build has got to.
 *
 * It is a long job — 150k games read, walked twice and written back out — so
 * "working" is not one state but four, and each one has a different unit of
 * progress. Showing a single indeterminate spinner for several minutes is how a
 * working build gets mistaken for a hung one.
 */
sealed interface PackBuildState {

    data object Idle : PackBuildState

    data class Running(val phase: Phase, val done: Int, val total: Int) : PackBuildState {
        /** 0..1, or null while the total is not yet known (the read phase). */
        val fraction: Float? get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
    }

    data class Done(val games: Int, val positions: Int, val skipped: Int) : PackBuildState

    data class Failed(val message: String) : PackBuildState

    enum class Phase {
        /** Streaming the `.rif` off storage and into memory. */
        Reading,

        /** First walk: which positions are worth keeping. */
        Counting,

        /** Second walk: the statistics themselves. */
        Aggregating,

        /** Writing the two packs and putting them in place. */
        Writing,
        ;

        val label: String
            get() = when (this) {
                Reading -> tr("기보 파일 읽는 중", "Reading the game file")
                Counting -> tr("국면 세는 중 (1/2)", "Counting positions (1 of 2)")
                Aggregating -> tr("통계 모으는 중 (2/2)", "Gathering statistics (2 of 2)")
                Writing -> tr("데이터 저장 중", "Saving the data")
            }
    }
}
