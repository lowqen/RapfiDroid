package dev.gomoku.yixindroid.domain.rankings

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.RankingFilter
import dev.gomoku.yixindroid.core.model.ShapeTheory
import org.junit.Test

class FreqAnalyzerTest {

    // Players: 0 Alice(RU), 1 Bob(CN), 2 Carl(KR)
    // Rules:   0 Renju, 1 Gomoku
    // Shapes:  0 rankRaw1, 1 rankRaw2
    // Game tuple = [black, white, rule, o3, k5, res]  (res 2/1/0/3 = B/D/W/?)
    private val bundle = FreqBundle(
        generated = "test",
        players = listOf(
            PlayerRef(0, "Alice", "RU"),
            PlayerRef(1, "Bob", "CN"),
            PlayerRef(2, "Carl", "KR"),
        ),
        rules = listOf("Renju", "Gomoku"),
        shapes = listOf(
            ShapeTheory("h8 h9 h10 g8 g9", 1, 32, 8),
            ShapeTheory("h8 h9 h10 h7 g9", 2, 32, 8),
        ),
        games = listOf(
            intArrayOf(0, 1, 0, 0, 0, 2),   // Alice(B) Bob(W) Renju D1 shape0 blackwin
            intArrayOf(1, 0, 0, 0, 1, 0),   // Bob(B) Alice(W) Renju D1 shape1 whitewin
            intArrayOf(0, 1, 1, 3, 0, 1),   // Alice(B) Bob(W) Gomoku op3 shape0 draw
            intArrayOf(1, 0, 0, 0, -1, 2),  // Bob(B) Alice(W) Renju D1 noshape blackwin
            intArrayOf(2, 1, 0, 0, 0, 2),   // Carl(B) Bob(W) Renju D1 shape0 blackwin
        ),
    )

    private val noFilter = RankingFilter()

    @Test
    fun openingRankingCountsAndSplits() {
        val r = FreqAnalyzer.openingRanking(bundle, noFilter)
        assertThat(r.totalGames).isEqualTo(5)
        // opening 0 (D1) is most played: games 0,1,3,4 = 4
        val top = r.rows.first()
        assertThat(top.openingIndex).isEqualTo(0)
        assertThat(top.split.total).isEqualTo(4)
        assertThat(top.split.blackWins).isEqualTo(3)
        assertThat(top.split.whiteWins).isEqualTo(1)
        assertThat(top.split.draws).isEqualTo(0)
        // opening 3 appears once (the draw)
        assertThat(r.rows.last().openingIndex).isEqualTo(3)
        assertThat(r.rows.last().split.draws).isEqualTo(1)
    }

    @Test
    fun fiveMoveRankingIsMostPlayedFirst() {
        val rows = FreqAnalyzer.fiveMoveRanking(bundle, noFilter)
        assertThat(rows).hasSize(2)
        val first = rows.first()
        assertThat(first.shape.repMoves).isEqualTo("h8 h9 h10 g8 g9")
        assertThat(first.count).isEqualTo(3)          // games 0,2,4
        assertThat(first.split.blackWins).isEqualTo(2)
        assertThat(first.split.draws).isEqualTo(1)
        assertThat(rows[1].count).isEqualTo(1)
    }

    @Test
    fun playerFilterExcludesOtherGames() {
        val alice = FreqAnalyzer.matchPlayers(bundle, "ali")
        assertThat(alice.map { it.index }).containsExactly(0)
        val filter = noFilter.copy(playerIndices = setOf(0))
        val r = FreqAnalyzer.openingRanking(bundle, filter)
        assertThat(r.totalGames).isEqualTo(4)          // all but Carl-vs-Bob
        val five = FreqAnalyzer.fiveMoveRanking(bundle, filter)
        assertThat(five.first().count).isEqualTo(2)     // shape0 in games 0,2 only
    }

    @Test
    fun ruleFilterNarrowsToRule() {
        val gomoku = FreqAnalyzer.matchRules(bundle, "gom")
        assertThat(gomoku).containsExactly(1)
        val r = FreqAnalyzer.openingRanking(bundle, noFilter.copy(ruleIndices = setOf(1)))
        assertThat(r.totalGames).isEqualTo(1)
        assertThat(r.rows.single().openingIndex).isEqualTo(3)
    }

    @Test
    fun countsByTheoryRankKeyedByRankRaw() {
        val counts = FreqAnalyzer.countsByTheoryRank(bundle, noFilter)
        assertThat(counts[1]).isEqualTo(3)   // shape0 (rankRaw 1)
        assertThat(counts[2]).isEqualTo(1)   // shape1 (rankRaw 2)
    }

    @Test
    fun matchByCountryToo() {
        assertThat(FreqAnalyzer.matchPlayers(bundle, "kr").map { it.name })
            .containsExactly("Carl")
    }
}
