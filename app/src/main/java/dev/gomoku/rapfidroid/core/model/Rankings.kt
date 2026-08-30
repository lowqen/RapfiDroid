package dev.gomoku.rapfidroid.core.model

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

    /** Games that reached a result. `unknown` is not one, so it is left out. */
    val decided: Int get() = blackWins + whiteWins + draws

    /**
     * Score for [side] with draws counting half — the chess/renju convention, so
     * a drawn game is half a point rather than a discarded one. Null when nothing
     * was decided, which is not the same as 0.
     */
    fun score(side: RankSide): Double? {
        if (decided == 0) return null
        val wins = if (side == RankSide.WHITE) whiteWins else blackWins
        return (wins + draws * 0.5) / decided
    }

    /**
     * What "best win rate" sorts on: the lower end of the 95 % Wilson interval
     * around [score].
     *
     * The plain rate cannot be the sort key. Filter to one player under one rule
     * and most openings are left holding a handful of games, where a single win
     * is 100 % — so a raw sort answers "which opening did they play once and
     * win", which is never the question being asked. Wilson asks instead how low
     * the true rate could plausibly be given this many games: one win from one
     * game scores 0.21, while a merely good 11 from 20 scores 0.34 and ranks
     * above it. Depth of evidence beats a lucky sample, without hiding either.
     *
     * Draws-as-half breaks the binomial assumption slightly; the ordering it
     * produces is still the one that matters, and the row shows the real rate
     * and the real game count next to it.
     */
    fun rankingScore(side: RankSide): Double {
        val n = decided
        val p = score(side) ?: return 0.0
        val z = 1.96
        val z2 = z * z
        val centre = p + z2 / (2 * n)
        val margin = z * kotlin.math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n))
        return ((centre - margin) / (1 + z2 / n)).coerceIn(0.0, 1.0)
    }
}

/**
 * Which side of the board a ranking question is about.
 *
 * It does two jobs, because they are the same question asked once: with players
 * selected it narrows the games to the ones they held that colour in, and it
 * picks whose score the win-rate sort reads. [EITHER] keeps both colours and
 * falls back to Black, which is the side an opening is normally judged from.
 */
enum class RankSide { EITHER, BLACK, WHITE }

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

/** The player/rule/side filter shared by both ranking tabs (mirrors the dashboard). */
data class RankingFilter(
    val playerQuery: String = "",
    val playerIndices: Set<Int> = emptySet(),  // resolved selection; empty = all
    val ruleIndices: Set<Int> = emptySet(),    // empty = all rules
    val side: RankSide = RankSide.EITHER,
) {
    val isActive: Boolean
        get() = playerIndices.isNotEmpty() || ruleIndices.isNotEmpty() || side != RankSide.EITHER
}

/** Aggregated 3-move ranking output for a filter. */
data class OpeningRanking(
    val totalGames: Int,
    val rows: List<OpeningRankRow>,   // sorted by total desc
)
