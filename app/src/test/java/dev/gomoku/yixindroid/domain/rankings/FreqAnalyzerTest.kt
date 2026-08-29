package dev.gomoku.yixindroid.domain.rankings

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.RankSide
import dev.gomoku.yixindroid.core.model.RankingFilter
import dev.gomoku.yixindroid.core.model.ShapeRef
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
            ShapeRef("h8 h9 h10 g8 g9"),
            ShapeRef("h8 h9 h10 h7 g9"),
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
    fun matchByCountryToo() {
        assertThat(FreqAnalyzer.matchPlayers(bundle, "kr").map { it.name })
            .containsExactly("Carl")
    }

    // Alice (0) held Black in games 0 and 2, White in games 1 and 3.

    @Test
    fun sideNarrowsAPlayerToTheGamesTheyHeldThatColour() {
        val asBlack = FreqAnalyzer.openingRanking(
            bundle, noFilter.copy(playerIndices = setOf(0), side = RankSide.BLACK),
        )
        assertThat(asBlack.totalGames).isEqualTo(2)          // games 0 and 2
        val asWhite = FreqAnalyzer.openingRanking(
            bundle, noFilter.copy(playerIndices = setOf(0), side = RankSide.WHITE),
        )
        assertThat(asWhite.totalGames).isEqualTo(2)          // games 1 and 3
        // Either side is still both, and still every Alice game.
        val either = FreqAnalyzer.openingRanking(bundle, noFilter.copy(playerIndices = setOf(0)))
        assertThat(either.totalGames).isEqualTo(4)
    }

    @Test
    fun sideSplitsTheResultsNotJustTheCount() {
        // Opening 0 with Alice as White: game 1 (she won) and game 3 (she lost).
        val asWhite = FreqAnalyzer.openingRanking(
            bundle, noFilter.copy(playerIndices = setOf(0), side = RankSide.WHITE),
        )
        val opening0 = asWhite.rows.single { it.openingIndex == 0 }
        assertThat(opening0.split.total).isEqualTo(2)
        assertThat(opening0.split.whiteWins).isEqualTo(1)
        assertThat(opening0.split.blackWins).isEqualTo(1)
    }

    @Test
    fun sideAloneNarrowsNothing() {
        // Every game has a black and a white player, so with nobody selected a
        // side cannot exclude anything — it only tells the sort what to read.
        val all = FreqAnalyzer.openingRanking(bundle, noFilter)
        for (side in RankSide.entries) {
            assertThat(FreqAnalyzer.openingRanking(bundle, noFilter.copy(side = side)).totalGames)
                .isEqualTo(all.totalGames)
        }
    }

    @Test
    fun sideAppliesToTheFiveMoveRankingToo() {
        // Shape 0 is in games 0, 2 and 4. Alice held Black in 0 and 2; game 4 is
        // Carl's, so as Black she brings two of the three.
        val asBlack = FreqAnalyzer.fiveMoveRanking(
            bundle, noFilter.copy(playerIndices = setOf(0), side = RankSide.BLACK),
        )
        assertThat(asBlack.single().shape.repMoves).isEqualTo("h8 h9 h10 g8 g9")
        assertThat(asBlack.single().count).isEqualTo(2)

        // As White she appears in game 1 only, which is shape 1.
        val asWhite = FreqAnalyzer.fiveMoveRanking(
            bundle, noFilter.copy(playerIndices = setOf(0), side = RankSide.WHITE),
        )
        assertThat(asWhite.single().shape.repMoves).isEqualTo("h8 h9 h10 h7 g9")
    }

    @Test
    fun sideIsPartOfWhetherTheFilterIsActive() {
        assertThat(noFilter.isActive).isFalse()
        assertThat(noFilter.copy(side = RankSide.BLACK).isActive).isTrue()
        assertThat(noFilter.copy(side = RankSide.EITHER).isActive).isFalse()
    }
}
