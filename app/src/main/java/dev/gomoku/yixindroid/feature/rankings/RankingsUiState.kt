package dev.gomoku.yixindroid.feature.rankings

import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.RankingFilter
import dev.gomoku.yixindroid.core.model.ResultSplit

enum class RankTab { THREE_MOVE, FIVE_MOVE }

/** 3-move opening filter by opening kind (直/間). */
enum class DirectFilter { ALL, DIRECT, INDIRECT }

/** 5-move ordering: theoretical rank5 order, or empirical (freq) play count. */
enum class FiveSort { THEORY, EMPIRICAL }

/** Board-extent scope for the 5-move list. */
enum class BoardScope(val label: String, val m5Max: Int?) {
    FULL("15×15", null),
    NINE("9×9", 4),
    SEVEN("7×7", 3),
}

/** A 26주형 card: static identity (always shown) + optional empirical split. */
data class OpeningCard(
    val index: Int,
    val abbr: String,
    val korean: String,
    val romaji: String,
    val direct: Boolean,
    val moves: List<Move>,
    val theoryShapeCount: Int,       // rank5 shapes under this opening
    val split: ResultSplit? = null,  // freq split when a dataset is loaded
)

/** A 5-move ranking row (theoretical, optionally annotated with play count). */
data class FiveRow(
    val rankRaw: Int,
    val opening: String,
    val openingIndex: Int,
    val moves: List<Move>,
    val repMoves: String,
    val group: Int,                  // 32/16/8/4 multiplicity class
    val empiricalCount: Int? = null, // play count when freq loaded
    val split: ResultSplit? = null,  // result split (empirical mode)
)

data class RankingsUiState(
    val tab: RankTab = RankTab.THREE_MOVE,

    // dataset status
    val freqLoaded: Boolean = false,
    val freqGenerated: String? = null,
    val freqGameCount: Int = 0,
    val importing: Boolean = false,
    val error: String? = null,
    // Non-fatal failure loading the bundled rank5 dataset (e.g. asset missing
    // from the APK — usually a stale build). Shown as a persistent banner.
    val dataError: String? = null,

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
    val fiveSort: FiveSort = FiveSort.THEORY,
    val fiveQuery: String = "",
    val boardScope: BoardScope = BoardScope.FULL,
    val fiveRows: List<FiveRow> = emptyList(),
    val groupDist: List<Pair<Int, Int>> = emptyList(),
    val shapeTotal: Int = 0,
) {
    val filterActive: Boolean get() = filter.isActive
}
