package dev.gomoku.yixindroid.domain.rankings

import dev.gomoku.yixindroid.core.model.OpeningRankRow
import dev.gomoku.yixindroid.core.model.OpeningRanking
import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.RankSide
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
     * Whether a game belongs to the selection. With a side chosen the player has
     * to have held *that* colour: "Alice as Black" is a different set of games
     * from "Alice", and asking which openings she scores best with as Black is
     * meaningless if her games as White are mixed in.
     *
     * Side alone narrows nothing — every game has a black and a white player —
     * so it only reaches this test once players are named. On its own it just
     * tells the sort whose score to read.
     */
    private fun matches(game: IntArray, players: Set<Int>, side: RankSide): Boolean = when (side) {
        RankSide.BLACK -> game[BLACK] in players
        RankSide.WHITE -> game[WHITE] in players
        RankSide.EITHER -> game[BLACK] in players || game[WHITE] in players
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
            if (players != null && !matches(g, players, filter.side)) continue
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
            if (players != null && !matches(g, players, filter.side)) continue
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

}
