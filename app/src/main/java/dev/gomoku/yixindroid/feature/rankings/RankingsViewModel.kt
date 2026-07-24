package dev.gomoku.yixindroid.feature.rankings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.yixindroid.core.model.Opening26
import dev.gomoku.yixindroid.core.model.PlayerRef
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

    private var openingShapeCounts: Map<String, Int> = emptyMap()

    init {
        viewModelScope.launch {
            openingShapeCounts = repo.openingShapeCounts()
            val dist = repo.groupDistribution()
            val total = repo.shapeTotal()
            _state.update {
                it.copy(groupDist = dist, shapeTotal = total, openingCards = baseCards())
            }
            refresh3()
            refresh5()
        }
        viewModelScope.launch { repo.restoreFreq() }
        viewModelScope.launch {
            repo.freq.collect { bundle ->
                _state.update {
                    it.copy(
                        freqLoaded = bundle != null,
                        freqGenerated = bundle?.generated,
                        freqGameCount = bundle?.gameCount ?: 0,
                        ruleOptions = bundle?.rules?.mapIndexed { i, n -> i to n } ?: emptyList(),
                    )
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
                    error = result.exceptionOrNull()?.let { e -> "임포트 실패: ${e.message}" },
                )
            }
        }
    }

    fun onClearFreq() {
        viewModelScope.launch {
            repo.clearFreq()
            _state.update {
                it.copy(
                    filter = it.filter.copy(playerIndices = emptySet(), ruleIndices = emptySet()),
                    selectedPlayers = emptyList(),
                    playerQuery = "",
                    playerSuggestions = emptyList(),
                    fiveSort = FiveSort.THEORY,
                )
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
            )
        }
        refresh3(); refresh5()
    }

    fun onToggleRule(index: Int) {
        _state.update {
            val cur = it.filter.ruleIndices
            val next = if (index in cur) cur - index else cur + index
            it.copy(filter = it.filter.copy(ruleIndices = next))
        }
        refresh3(); refresh5()
    }

    fun onClearFilter() {
        _state.update {
            it.copy(
                filter = it.filter.copy(playerIndices = emptySet(), ruleIndices = emptySet()),
                selectedPlayers = emptyList(),
                playerQuery = "",
                playerSuggestions = emptyList(),
            )
        }
        refresh3(); refresh5()
    }

    fun onDirectFilter(df: DirectFilter) {
        _state.update { it.copy(directFilter = df) }
        refresh3()
    }

    fun onToggleThreeSort() {
        _state.update { it.copy(sortThreeByFreq = !it.sortThreeByFreq) }
        refresh3()
    }

    fun onFiveSort(sort: FiveSort) {
        _state.update { it.copy(fiveSort = sort) }
        refresh5()
    }

    fun onFiveQueryChange(q: String) {
        _state.update { it.copy(fiveQuery = q) }
        refresh5()
    }

    fun onBoardScope(scope: BoardScope) {
        _state.update { it.copy(boardScope = scope) }
        refresh5()
    }

    // ---- recompute ----

    private fun baseCards(): List<OpeningCard> = (0 until Opening26.COUNT).map { i ->
        OpeningCard(
            index = i,
            abbr = Opening26.abbr[i],
            korean = Opening26.korean[i],
            romaji = Opening26.romaji[i],
            direct = Opening26.isDirect(i),
            moves = Opening26.representative(i),
            theoryShapeCount = openingShapeCounts[Opening26.abbr[i]] ?: 0,
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
            cards = if (s.sortThreeByFreq && ranking != null) {
                cards.sortedByDescending { it.split?.total ?: 0 }
            } else {
                cards.sortedBy { it.index }
            }
            _state.update {
                it.copy(openingCards = cards, threeTotalGames = ranking?.totalGames ?: 0)
            }
        }
    }

    private fun refresh5() {
        viewModelScope.launch {
            val s = _state.value
            val rows: List<FiveRow> = if (s.fiveSort == FiveSort.EMPIRICAL && s.freqLoaded) {
                val q = s.fiveQuery.trim().lowercase()
                val empirical = repo.fiveMoveRanking(s.filter, top = 250)
                // Join matched shapes to rank5 for the authoritative opening +
                // canonical representative (a shape can be reached by several
                // move orders, so the shape's own rep opening is ambiguous).
                val byRank = repo.shapesByRank(
                    empirical.mapNotNull { it.shape.theoryRankRaw.takeIf { r -> r > 0 } }.toSet(),
                )
                empirical.map { row ->
                    val sr = byRank[row.shape.theoryRankRaw]
                    if (sr != null) {
                        FiveRow(
                            rankRaw = sr.rankRaw, opening = sr.opening,
                            openingIndex = sr.openingIndex, moves = sr.moves(),
                            repMoves = sr.repMoves, group = sr.group,
                            empiricalCount = row.count, split = row.split,
                        )
                    } else {
                        val moves = row.shape.moves()
                        FiveRow(
                            rankRaw = 0, opening = "—",
                            openingIndex = Opening26.classify(moves), moves = moves,
                            repMoves = row.shape.repMoves, group = row.shape.theoryCountRaw,
                            empiricalCount = row.count, split = row.split,
                        )
                    }
                }.filter { q.isEmpty() || it.repMoves.lowercase().contains(q) }
            } else {
                val counts = repo.countsByTheoryRank(s.filter)
                repo.searchShapes(
                    repContains = s.fiveQuery.ifBlank { null },
                    opening = null,
                    m5Max = s.boardScope.m5Max,
                    limit = 250,
                ).map { sr ->
                    FiveRow(
                        rankRaw = sr.rankRaw,
                        opening = sr.opening,
                        openingIndex = sr.openingIndex,
                        moves = sr.moves(),
                        repMoves = sr.repMoves,
                        group = sr.group,
                        empiricalCount = counts[sr.rankRaw],
                    )
                }
            }
            _state.update { it.copy(fiveRows = rows) }
        }
    }
}
