package dev.gomoku.yixindroid.core.model

/** A player entry from the imported freq dataset. */
data class PlayerRef(val index: Int, val name: String, val country: String)

/** A distinct 5-move shape carried by the freq dataset: its representative
 *  move order, written in the canonical direction freq35 chose. */
data class ShapeRef(val repMoves: String) {
    fun moves(size: Int = Move.DEFAULT_SIZE): List<Move> =
        repMoves.split(' ').mapNotNull { Move.fromLabel(it, size) }
}

/**
 * A result split for one opening (or one shape). Result codes come straight from
 * freq35: 2 = black won, 1 = draw, 0 = white won, 3 = unknown.
 */
data class ResultSplit(
    val total: Int = 0,
    val blackWins: Int = 0,
    val whiteWins: Int = 0,
    val draws: Int = 0,
    val unknown: Int = 0,
) {
    fun plus(res: Int): ResultSplit = copy(
        total = total + 1,
        blackWins = blackWins + if (res == 2) 1 else 0,
        whiteWins = whiteWins + if (res == 0) 1 else 0,
        draws = draws + if (res == 1) 1 else 0,
        unknown = unknown + if (res !in 0..2) 1 else 0,
    )

    /** Decisive-game black score (draws count half), null when no decisive result. */
    val blackScore: Double?
        get() {
            val decided = blackWins + whiteWins + draws
            return if (decided == 0) null else (blackWins + draws * 0.5) / decided
        }
}

/** One row of the 3-move opening ranking (26 patterns + 기타). */
data class OpeningRankRow(
    val openingIndex: Int,     // 0..25, or Opening26.NONSTD
    val split: ResultSplit,
)

/** One row of the empirical 5-move shape ranking. */
data class ShapeFreqRow(
    val shape: ShapeRef,
    val count: Int,
    val split: ResultSplit,
)

/** The player/rule filter shared by both ranking tabs (mirrors the dashboard). */
data class RankingFilter(
    val playerQuery: String = "",
    val playerIndices: Set<Int> = emptySet(),  // resolved selection; empty = all
    val ruleIndices: Set<Int> = emptySet(),    // empty = all rules
) {
    val isActive: Boolean get() = playerIndices.isNotEmpty() || ruleIndices.isNotEmpty()
}

/** Aggregated 3-move ranking output for a filter. */
data class OpeningRanking(
    val totalGames: Int,
    val rows: List<OpeningRankRow>,   // sorted by total desc
)
