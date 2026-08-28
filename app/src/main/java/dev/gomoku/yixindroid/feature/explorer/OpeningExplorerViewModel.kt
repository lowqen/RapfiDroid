package dev.gomoku.yixindroid.feature.explorer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.ExplorerGames
import dev.gomoku.yixindroid.core.model.ExplorerPosition
import dev.gomoku.yixindroid.core.model.ExplorerStatus
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.OpeningName
import dev.gomoku.yixindroid.core.model.PackInfo
import dev.gomoku.yixindroid.core.model.RjGame
import dev.gomoku.yixindroid.domain.repository.ExplorerRepository
import dev.gomoku.yixindroid.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One 환원 row: another move order reaching the very same stones, with the name
 * chain that order earns. `["천원","간접막기","항성"]` next to `H8 I9 J8 …`.
 */
data class Transposition(val line: String, val chain: List<String>)

/** Everything the opening-explorer tab draws. */
data class OpeningExplorerUiState(
    val status: ExplorerStatus = ExplorerStatus.NO_PACKS,
    val packs: PackInfo? = null,
    val position: ExplorerPosition? = null,
    val games: ExplorerGames = ExplorerGames(),
    val filter: String = "",
    val selected: RjGame? = null,
    val selectedRule: String? = null,
    val selectedOpening: String? = null,
    /**
     * `["천원", "간접막기", "화월"]` for the line on the board. Tracks the board
     * and not the packs, so it is there with no packs imported at all — names
     * are pure computation and carry no RenjuNet licence.
     */
    val nameChain: List<String> = emptyList(),
    /** The line on the board, for the graded mini board — same reason as
     *  [nameChain]: it must survive NO_PACKS and NO_STATS. */
    val stones: List<Move> = emptyList(),
    /**
     * 환원 — the other move orders that reach these very stones, each with its
     * own name chain. Pure computation from the board, so it too survives
     * NO_PACKS. Empty for most positions, which is the normal case.
     */
    val transpositions: List<Transposition> = emptyList(),
    val importing: Boolean = false,
    val notice: String? = null,
) {
    /** All three result bars share one scale: the largest single result count
     *  on show (main.c:5350-5362). */
    val barScale: Int
        get() = position?.next?.maxOfOrNull {
            maxOf(it.blackWins, it.draws, it.whiteWins)
        }?.coerceAtLeast(1) ?: 1
}

/**
 * The opening-explorer tab. Nothing here is started or stopped: the repository
 * follows the board, so the numbers change when the line does — the desktop's
 * non-modal window discipline (`rjexp_schedule`).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class OpeningExplorerViewModel @Inject constructor(
    private val repository: ExplorerRepository,
    private val game: GameRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(OpeningExplorerUiState())
    val uiState: StateFlow<OpeningExplorerUiState> = _ui.asStateFlow()

    private val filter = MutableStateFlow("")

    init {
        viewModelScope.launch { repository.restore() }
        combine(repository.status, repository.packs, repository.position) { s, p, pos ->
            Triple(s, p, pos)
        }.onEach { (s, p, pos) ->
            // A new position invalidates the selected game and the list.
            _ui.update {
                it.copy(status = s, packs = p, position = pos, selected = null)
            }
            refreshGames()
        }.launchIn(viewModelScope)

        // Separate from the pack flow above on purpose: the name chain must
        // survive NO_PACKS and NO_STATS, where `position` is null.
        game.position.onEach { pos ->
            val chain = OpeningName.chain(pos.moves, pos.size)
            val self = OpeningName.keyOf(pos.moves, pos.size)
            val alts = OpeningName.transpositions(pos.moves, pos.size)
                .filter { OpeningName.keyOf(it, pos.size) != self }
                .map {
                    Transposition(
                        line = it.joinToString(" ") { m -> m.label(pos.size) },
                        chain = OpeningName.chain(it, pos.size),
                    )
                }
            _ui.update {
                if (it.nameChain == chain && it.stones == pos.moves &&
                    it.transpositions == alts
                ) it
                else it.copy(nameChain = chain, stones = pos.moves, transpositions = alts)
            }
        }.launchIn(viewModelScope)

        filter.debounce(200).onEach { text ->
            _ui.update { it.copy(filter = text) }
            refreshGames()
        }.launchIn(viewModelScope)
    }

    private suspend fun refreshGames() {
        val games = repository.games(filter.value)
        _ui.update { it.copy(games = games) }
    }

    fun onFilterChange(text: String) {
        _ui.update { it.copy(filter = text) }
        filter.value = text
    }

    fun onImport(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _ui.update { it.copy(importing = true) }
        viewModelScope.launch {
            val result = repository.importPacks(uris)
            _ui.update {
                it.copy(
                    importing = false,
                    notice = result.getOrElse { e -> e.message ?: tr("팩을 불러오지 못했습니다", "The packs could not be loaded") },
                )
            }
        }
    }

    fun onClearPacks() {
        viewModelScope.launch {
            repository.clearPacks()
            _ui.update { it.copy(notice = tr("팩을 지웠습니다", "Packs cleared"), selected = null) }
        }
    }

    fun onSelectGame(id: Int) {
        val g = repository.game(id)
        _ui.update {
            it.copy(
                selected = g,
                selectedRule = g?.let { r -> repository.ruleName(r.rule) },
                selectedOpening = g?.let { r -> repository.openingLabel(r.opening) },
            )
        }
    }

    fun onClearSelection() {
        _ui.update { it.copy(selected = null, selectedRule = null, selectedOpening = null) }
    }

    /** Play a listed next move (desktop: double-click the row). */
    fun onPlayNext(move: Move) {
        viewModelScope.launch {
            repository.playNext(move)?.let { reason ->
                _ui.update { it.copy(notice = reason) }
            }
        }
    }

    /** Replace the board with this game (desktop asks first; the screen does). */
    fun onLoadGame(id: Int) {
        viewModelScope.launch {
            val reason = repository.loadGame(id)
            _ui.update {
                it.copy(notice = reason ?: tr("기보 #$id 을(를) 보드에 올렸습니다", "Game #$id is on the board"))
            }
        }
    }

    fun onNoticeShown() {
        _ui.update { it.copy(notice = null) }
    }
}
