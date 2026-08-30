package dev.gomoku.rapfidroid.feature.rankings

import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.PlayerRef
import dev.gomoku.rapfidroid.core.model.RankSide
import dev.gomoku.rapfidroid.core.model.RankingFilter
import dev.gomoku.rapfidroid.core.model.ResultSplit

enum class RankTab { THREE_MOVE, FIVE_MOVE }

/** 3-move opening filter by opening kind (直/間). */
enum class DirectFilter { ALL, DIRECT, INDIRECT }

/**
 * How a ranking list is ordered.
 *
 * [NUMBER] is the 26 openings in their catalogue order and means nothing for
 * 5-move shapes, which have no catalogue; [WIN_RATE] is only offered once the
 * filter narrows something, because "the best-scoring opening" over every game
 * ever played is a fact about renju, not about anything the user asked.
 */
enum class RankSort { NUMBER, GAMES, WIN_RATE }

/** A 26주형 card: static identity (always shown) + optional empirical split. */
data class OpeningCard(
    val index: Int,
    val abbr: String,
    val korean: String,
    val romaji: String,
    val direct: Boolean,
    val moves: List<Move>,
    val split: ResultSplit? = null,  // freq split when a dataset is loaded
)

/** A 5-move ranking row: how often this shape was actually played. */
data class FiveRow(
    val openingIndex: Int,
    val moves: List<Move>,
    val repMoves: String,
    val count: Int,
    val split: ResultSplit? = null,
)

data class RankingsUiState(
    val tab: RankTab = RankTab.THREE_MOVE,

    // dataset status
    val freqLoaded: Boolean = false,
    val freqGenerated: String? = null,
    val freqGameCount: Int = 0,
    val error: String? = null,

    // shared filter
    val filter: RankingFilter = RankingFilter(),
    val filterSheetOpen: Boolean = false,
    val playerQuery: String = "",
    val playerSuggestions: List<PlayerRef> = emptyList(),
    val selectedPlayers: List<PlayerRef> = emptyList(),
    val ruleOptions: List<Pair<Int, String>> = emptyList(),

    // 3-move tab
    val directFilter: DirectFilter = DirectFilter.ALL,
    val threeSort: RankSort = RankSort.GAMES,
    val openingCards: List<OpeningCard> = emptyList(),
    val threeTotalGames: Int = 0,

    // 5-move tab
    val fiveQuery: String = "",
    val fiveSort: RankSort = RankSort.GAMES,
    val fiveRows: List<FiveRow> = emptyList(),
) {
    val filterActive: Boolean get() = filter.isActive

    /** Whose score the win-rate sort reads; [RankSide.EITHER] judges from Black. */
    val scoringSide: RankSide
        get() = if (filter.side == RankSide.WHITE) RankSide.WHITE else RankSide.BLACK

    /**
     * The orderings on offer for [tab]. Win rate appears only once the filter
     * narrows something — see [RankSort] — and both tabs need the dataset before
     * either empirical ordering means anything.
     */
    fun sortOptions(tab: RankTab): List<RankSort> = buildList {
        if (tab == RankTab.THREE_MOVE) add(RankSort.NUMBER)
        if (freqLoaded) {
            add(RankSort.GAMES)
            if (filterActive) add(RankSort.WIN_RATE)
        }
    }
}
