package dev.gomoku.rapfidroid.feature.prove

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.rapfidroid.core.designsystem.component.BoardRender
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.MoveGrader
import dev.gomoku.rapfidroid.core.model.ProveOptions
import dev.gomoku.rapfidroid.core.model.ProveOverlay
import dev.gomoku.rapfidroid.domain.repository.EngineRepository
import dev.gomoku.rapfidroid.domain.repository.GameRepository
import dev.gomoku.rapfidroid.domain.repository.ProveRepository
import dev.gomoku.rapfidroid.domain.repository.ProveStart
import dev.gomoku.rapfidroid.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The prove dialog and its live state (main.c `prove_start` / `prove_badge_lines`).
 * The options are settings_dev.txt lines 5, 6, 12, 13, 16, 17, 18 and 7, so every
 * change is written back through the settings repository and travels to the PC
 * with the rest of the file.
 */
@HiltViewModel
class ProveViewModel @Inject constructor(
    private val prove: ProveRepository,
    private val game: GameRepository,
    private val engine: EngineRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val notice = MutableStateFlow<String?>(null)

    private data class Board(
        val moves: List<Move>,
        val size: Int,
        val connected: Boolean,
        val over: Boolean,
        val dbWritable: Boolean,
        val showNumbers: Boolean,
    )

    private val board = combine(
        game.position, game.state, engine.state, settingsRepository.settings,
    ) { position, state, connection, settings ->
        Board(
            moves = position.moves,
            size = position.size,
            connected = connection.isLive,
            over = state.over,
            dbWritable = settings.useDatabase && !settings.databaseReadonly,
            showNumbers = settings.showNumber,
        )
    }

    private val options = settingsRepository.settings.map { ProveOptions.of(it) }

    val uiState: StateFlow<ProveUiState> = combine(
        prove.progress, prove.overlay, prove.outcome, prove.log,
        combine(board, options, notice) { board, options, notice -> Triple(board, options, notice) },
    ) { progress, overlay, outcome, log, rest ->
        val (board, options, notice) = rest
        ProveUiState(
            progress = progress,
            options = options,
            outcome = outcome,
            log = log,
            candidates = rows(overlay, board.size),
            render = BoardRender(
                size = board.size,
                stones = board.moves,
                lastMove = board.moves.lastOrNull(),
                showNumbers = board.showNumbers,
                prove = overlay.takeIf { it.active },
            ),
            moveCount = board.moves.size,
            blackToMove = board.moves.size % 2 == 0,
            connected = board.connected,
            dbWritable = board.dbWritable,
            gameOver = board.over,
            notice = notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProveUiState())

    private fun rows(overlay: ProveOverlay, size: Int): List<ProveCandidateRow> =
        overlay.marks.map { (move, mark) ->
            ProveCandidateRow(
                move = move,
                label = MoveGrader.coord(move, size),
                mark = mark,
                budget = overlay.budgetLabel(move),
            )
        }

    fun onOptions(next: ProveOptions) {
        val clean = next.sanitized()
        viewModelScope.launch {
            settingsRepository.set("proveBudget0Sec", clean.budget0Sec.toString())
            settingsRepository.set("proveBudgetMaxSec", clean.budgetMaxSec.toString())
            settingsRepository.set("proveByDepth", if (clean.byDepth) "1" else "0")
            settingsRepository.set("proveDepth0", clean.depth0.toString())
            settingsRepository.set("proveDepthMax", clean.depthMax.toString())
            settingsRepository.set("proveNbest", clean.nbest.toString())
            settingsRepository.set("proveBestFirst", if (clean.bestFirst) "1" else "0")
            settingsRepository.set("proveProbe", if (clean.probe) "1" else "0")
        }
    }

    fun onStart() {
        viewModelScope.launch {
            when (val result = prove.start(ProveOptions.of(settingsRepository.settings.value))) {
                is ProveStart.Refused -> notice.value = result.reason
                ProveStart.Started -> Unit
            }
        }
    }

    fun onCancel() {
        viewModelScope.launch { prove.cancel() }
    }

    fun onDismissOutcome() = prove.clearOutcome()

    fun onNoticeShown() {
        notice.value = null
    }
}
