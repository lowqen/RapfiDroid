package dev.gomoku.yixindroid.domain.rankings

import dev.gomoku.yixindroid.core.model.OpeningRankRow
import dev.gomoku.yixindroid.core.model.OpeningRanking
import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.RankingFilter
import dev.gomoku.yixindroid.core.model.ResultSplit
import dev.gomoku.yixindroid.core.model.ShapeFreqRow

/**
 * The dashboard's filter + rank logic (port of `freq35.rankings`), as pure
 * functions over a [FreqBundle]. No Android, no coroutines — unit-tested with
 * the desktop engine as the oracle.
 *
 * Game tuple layout: `[black, white, rule, o3, k5, res]`.
 */
object FreqAnalyzer {

    private const val BLACK = 0
    private const val WHITE = 1
    private const val RULE = 2
    private const val O3 = 3
    private const val K5 = 4
    private const val RES = 5

    /** Players whose name or country contains [query] (case-insensitive). */
    fun matchPlayers(bundle: FreqBundle, query: String): List<PlayerRef> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return bundle.players
            .filter { it.name.lowercase().contains(q) || it.country.lowercase().contains(q) }
    }

    /** Rule indices whose name contains [query]. */
    fun matchRules(bundle: FreqBundle, query: String): List<Int> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return bundle.rules.indices.filter { bundle.rules[it].lowercase().contains(q) }
    }

    /**
     * 3-move opening ranking under [filter]. Rows carry the win/draw/loss split
     * and are sorted by total games descending.
     */
    fun openingRanking(bundle: FreqBundle, filter: RankingFilter): OpeningRanking {
        val players = filter.playerIndices.takeIf { it.isNotEmpty() }
        val rules = filter.ruleIndices.takeIf { it.isNotEmpty() }
        val splits = HashMap<Int, ResultSplit>()
        var total = 0
        for (g in bundle.games) {
            if (players != null && g[BLACK] !in players && g[WHITE] !in players) continue
            if (rules != null && g[RULE] !in rules) continue
            total++
            val o3 = g[O3]
            splits[o3] = (splits[o3] ?: ResultSplit()).plus(g[RES])
        }
        val rows = splits.entries
            .map { OpeningRankRow(it.key, it.value) }
            .sortedByDescending { it.split.total }
        return OpeningRanking(total, rows)
    }

    /**
     * Empirical 5-move shape ranking under [filter], most-played first, capped at
     * [top]. Only games whose first five moves formed a distinct shape (`k5 >= 0`)
     * are counted.
     */
    fun fiveMoveRanking(bundle: FreqBundle, filter: RankingFilter, top: Int = 40): List<ShapeFreqRow> {
        val players = filter.playerIndices.takeIf { it.isNotEmpty() }
        val rules = filter.ruleIndices.takeIf { it.isNotEmpty() }
        val splits = HashMap<Int, ResultSplit>()
        for (g in bundle.games) {
            val k5 = g[K5]
            if (k5 < 0) continue
            if (players != null && g[BLACK] !in players && g[WHITE] !in players) continue
            if (rules != null && g[RULE] !in rules) continue
            splits[k5] = (splits[k5] ?: ResultSplit()).plus(g[RES])
        }
        return splits.entries
            .sortedByDescending { it.value.total }
            .take(top)
            .mapNotNull { e ->
                bundle.shapes.getOrNull(e.key)?.let { ShapeFreqRow(it, e.value.total, e.value) }
            }
    }

    /**
     * Empirical play counts keyed by rank5 theory rank (`rankRaw`), for annotating
     * the bundled theoretical 5-move list. Shapes not matched to rank5 (rankRaw==0)
     * are skipped.
     */
    fun countsByTheoryRank(bundle: FreqBundle, filter: RankingFilter): Map<Int, Int> {
        val players = filter.playerIndices.takeIf { it.isNotEmpty() }
        val rules = filter.ruleIndices.takeIf { it.isNotEmpty() }
        val perShape = IntArray(bundle.shapes.size)
        for (g in bundle.games) {
            val k5 = g[K5]
            if (k5 < 0) continue
            if (players != null && g[BLACK] !in players && g[WHITE] !in players) continue
            if (rules != null && g[RULE] !in rules) continue
            perShape[k5]++
        }
        val out = HashMap<Int, Int>()
        bundle.shapes.forEachIndexed { i, s ->
            if (s.theoryRankRaw > 0 && perShape[i] > 0) {
                out[s.theoryRankRaw] = (out[s.theoryRankRaw] ?: 0) + perShape[i]
            }
        }
        return out
    }
}
