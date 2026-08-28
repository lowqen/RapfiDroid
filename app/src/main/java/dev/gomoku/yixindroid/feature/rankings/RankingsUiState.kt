package dev.gomoku.yixindroid.feature.rankings

import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.RankingFilter
import dev.gomoku.yixindroid.core.model.ResultSplit

enum class RankTab { THREE_MOVE, FIVE_MOVE }

/** 3-move opening filter by opening kind (直/間). */
enum class DirectFilter { ALL, DIRECT, INDIRECT }

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
    val importing: Boolean = false,
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
    val sortThreeByFreq: Boolean = true,
    val openingCards: List<OpeningCard> = emptyList(),
    val threeTotalGames: Int = 0,

    // 5-move tab
    val fiveQuery: String = "",
    val fiveRows: List<FiveRow> = emptyList(),
) {
    val filterActive: Boolean get() = filter.isActive
}
