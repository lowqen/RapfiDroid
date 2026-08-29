package dev.gomoku.yixindroid.feature.rankings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.Opening26
import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.RankSide
import dev.gomoku.yixindroid.core.model.RankingFilter
import dev.gomoku.yixindroid.domain.repository.RankingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RankingsViewModel @Inject constructor(
    private val repo: RankingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RankingsUiState())
    val uiState: StateFlow<RankingsUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(openingCards = baseCards()) }
        viewModelScope.launch { repo.restoreFreq() }
        viewModelScope.launch {
            repo.freq.collect { bundle ->
                _state.update {
                    it.copy(
                        freqLoaded = bundle != null,
                        freqGenerated = bundle?.generated,
                        freqGameCount = bundle?.gameCount ?: 0,
                        ruleOptions = bundle?.rules?.mapIndexed { i, n -> i to n } ?: emptyList(),
                    ).withValidSort()
                }
                refresh3()
                refresh5()
            }
        }
    }

    // ---- intents ----

    fun onSelectTab(tab: RankTab) = _state.update { it.copy(tab = tab) }

    fun onOpenFilter() = _state.update { it.copy(filterSheetOpen = true) }
    fun onCloseFilter() = _state.update { it.copy(filterSheetOpen = false) }
    fun onDismissError() = _state.update { it.copy(error = null) }

    fun onImport(uri: Uri) {
        _state.update { it.copy(importing = true, error = null) }
        viewModelScope.launch {
            val result = repo.importFreq(uri)
            _state.update {
                it.copy(
                    importing = false,
                    error = result.exceptionOrNull()?.let { e -> tr("임포트 실패: ${e.message}", "Import failed: ${e.message}") },
                )
            }
        }
    }

    fun onClearFreq() {
        viewModelScope.launch {
            repo.clearFreq()
            _state.update {
                it.copy(
                    filter = RankingFilter(),
                    selectedPlayers = emptyList(),
                    playerQuery = "",
                    playerSuggestions = emptyList(),
                ).withValidSort()
            }
        }
    }

    fun onPlayerQueryChange(q: String) {
        _state.update { it.copy(playerQuery = q) }
        viewModelScope.launch {
            val hits = repo.matchPlayers(q).take(15)
            _state.update { it.copy(playerSuggestions = hits) }
        }
    }

    fun onSelectPlayer(player: PlayerRef) {
        _state.update {
            if (it.selectedPlayers.any { p -> p.index == player.index }) return@update it
            val selected = it.selectedPlayers + player
            it.copy(
                selectedPlayers = selected,
                filter = it.filter.copy(playerIndices = selected.map { p -> p.index }.toSet()),
                playerQuery = "",
                playerSuggestions = emptyList(),
            )
        }
        refresh3(); refresh5()
    }

    fun onRemovePlayer(player: PlayerRef) {
        _state.update {
            val selected = it.selectedPlayers.filterNot { p -> p.index == player.index }
            it.copy(
                selectedPlayers = selected,
                filter = it.filter.copy(playerIndices = selected.map { p -> p.index }.toSet()),
            ).withValidSort()
        }
        refresh3(); refresh5()
    }

    fun onToggleRule(index: Int) {
        _state.update {
            val cur = it.filter.ruleIndices
            val next = if (index in cur) cur - index else cur + index
            it.copy(filter = it.filter.copy(ruleIndices = next)).withValidSort()
        }
        refresh3(); refresh5()
    }

    fun onClearFilter() {
        _state.update {
            it.copy(
                filter = RankingFilter(),
                selectedPlayers = emptyList(),
                playerQuery = "",
                playerSuggestions = emptyList(),
            ).withValidSort()
        }
        refresh3(); refresh5()
    }

    fun onDirectFilter(df: DirectFilter) {
        _state.update { it.copy(directFilter = df) }
        refresh3()
    }

    fun onThreeSort(sort: RankSort) {
        _state.update { it.copy(threeSort = sort) }
        refresh3()
    }

    fun onFiveSort(sort: RankSort) {
        _state.update { it.copy(fiveSort = sort) }
        refresh5()
    }

    /**
     * Picking a side re-runs both tabs, not only the sort: once players are
     * selected it changes which games are counted at all — "Alice as Black" is a
     * different set from "Alice".
     */
    fun onSide(side: RankSide) {
        _state.update { it.copy(filter = it.filter.copy(side = side)).withValidSort() }
        refresh3(); refresh5()
    }

    /**
     * Keep the ordering to one the user can still see.
     *
     * Win rate is offered only while the filter narrows something, and both
     * empirical orderings need the dataset. Clearing either can therefore leave
     * a list sorted by a rule whose chip has just disappeared — an order nobody
     * chose and nobody can undo. Every intent that can shrink the filter runs
     * this, so the sort falls back the moment its option does.
     */
    private fun RankingsUiState.withValidSort(): RankingsUiState = copy(
        threeSort = threeSort.takeIf { it in sortOptions(RankTab.THREE_MOVE) } ?: RankSort.NUMBER,
        fiveSort = fiveSort.takeIf { it in sortOptions(RankTab.FIVE_MOVE) } ?: RankSort.GAMES,
    )

    fun onFiveQueryChange(q: String) {
        _state.update { it.copy(fiveQuery = q) }
        refresh5()
    }

    // ---- recompute ----

    private fun baseCards(): List<OpeningCard> = (0 until Opening26.COUNT).map { i ->
        OpeningCard(
            index = i,
            abbr = Opening26.abbr[i],
            korean = Opening26.name(i),
            romaji = Opening26.romaji[i],
            direct = Opening26.isDirect(i),
            moves = Opening26.representative(i),
        )
    }

    private fun refresh3() {
        viewModelScope.launch {
            val s = _state.value
            val ranking = repo.openingRanking(s.filter)
            val splitByIndex = ranking?.rows?.associate { it.openingIndex to it.split }.orEmpty()
            var cards = baseCards().map { it.copy(split = splitByIndex[it.index]) }
            cards = when (s.directFilter) {
                DirectFilter.ALL -> cards
                DirectFilter.DIRECT -> cards.filter { it.direct }
                DirectFilter.INDIRECT -> cards.filter { !it.direct }
            }
            // An opening with no games under this filter has nothing to rank, so
            // it sinks rather than sorting as a zero among real results — the 26
            // cards are always all present, and most of them are empty once a
            // single player is selected.
            cards = when {
                ranking == null -> cards.sortedBy { it.index }
                s.threeSort == RankSort.WIN_RATE -> cards.sortedByDescending { card ->
                    card.split?.takeIf { it.decided > 0 }?.rankingScore(s.scoringSide) ?: -1.0
                }
                s.threeSort == RankSort.GAMES -> cards.sortedByDescending { it.split?.total ?: 0 }
                else -> cards.sortedBy { it.index }
            }
            _state.update {
                it.copy(openingCards = cards, threeTotalGames = ranking?.totalGames ?: 0)
            }
        }
    }

    /**
     * The 5-move list is empirical only: how often each distinct shape was
     * actually played, in the user's own dataset. There is no theoretical list
     * behind it, so with no dataset imported the tab is simply empty.
     */
    private fun refresh5() {
        viewModelScope.launch {
            val s = _state.value
            val q = s.fiveQuery.trim().lowercase()
            // The repository caps at the 250 most-played shapes before anything
            // here runs, so a win-rate sort reorders *those* rather than trawling
            // a tail of shapes played twice. That cap is doing useful work, not
            // just bounding the list: it is what keeps the top of a win-rate sort
            // from being a wall of one-game shapes.
            var rows = repo.fiveMoveRanking(s.filter, top = 250)
                .filter { q.isEmpty() || it.shape.repMoves.lowercase().contains(q) }
                .map { row ->
                    val moves = row.shape.moves()
                    FiveRow(
                        openingIndex = Opening26.classify(moves),
                        moves = moves,
                        repMoves = row.shape.repMoves,
                        count = row.count,
                        split = row.split,
                    )
                }
            if (s.fiveSort == RankSort.WIN_RATE) {
                rows = rows.sortedByDescending { row ->
                    row.split?.takeIf { it.decided > 0 }?.rankingScore(s.scoringSide) ?: -1.0
                }
            }
            _state.update { it.copy(fiveRows = rows) }
        }
    }
}
