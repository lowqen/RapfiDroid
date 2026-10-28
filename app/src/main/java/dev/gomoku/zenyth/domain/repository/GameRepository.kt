package dev.gomoku.zenyth.domain.repository

import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.ComputerSide
import dev.gomoku.zenyth.core.model.GameState
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.Position
import dev.gomoku.zenyth.core.model.Swap2Choice
import dev.gomoku.zenyth.core.model.TapResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The board and the game on it — the desktop's `movepath` + `piecenum` +
 * `computerside` + clocks, in one place.
 *
 * It lives in the data layer as a singleton because a game must survive the
 * board screen: switching tabs pops that destination, and an engine reply that
 * arrives meanwhile still has to land on the board.
 */
interface GameRepository {
    /** The stones currently on the board. */
    val position: StateFlow<Position>

    /** Moves undone but kept for redo (the desktop keeps the whole line). */
    val future: StateFlow<List<Move>>

    /** Renju forbidden points for the side to move, or empty. */
    val forbidden: StateFlow<List<Move>>

    val state: StateFlow<GameState>

    /**
     * A tap on the board: places a stone, answers an opening step, or asks the
     * engine to move — whichever the desktop would do in this situation.
     */
    suspend fun tap(cell: Move): TapResult

    /** Ask the engine to take the move that is due (desktop `thinking start`). */
    suspend fun engineMove(): TapResult

    /** `thinking stop` — the engine reports its current best move. */
    suspend fun stopThinking()

    /** New game; [resetClock] also restarts the clocks and the opening protocol. */
    suspend fun newGame(resetClock: Boolean = true)

    suspend fun setComputerSide(side: ComputerSide)

    suspend fun undo()
    suspend fun redo()
    suspend fun toStart()
    suspend fun toEnd()

    /** Park the cursor on [index] stones, keeping the rest of the line for redo. */
    suspend fun jumpTo(index: Int)

    /** Replace the whole line (shape transforms, pasted positions). */
    suspend fun replaceLine(moves: List<Move>)

    /** Yes/no answer to a swap question. */
    suspend fun answerSwap(yes: Boolean)

    /** Swap2's three-way answer. */
    suspend fun answerSwap2(choice: Swap2Choice)

    /** How many fifth moves to offer (Soosorv `move5N`). */
    suspend fun answerFifthCount(count: Int)

    suspend fun offerDraw()
    suspend fun resign()

    /** The analysis search is running; forbidden refreshes must not disturb it. */
    fun setAnalyzing(on: Boolean)

    fun dismissPrompt()

    /**
     * Which board tab is showing (Zenyth Web). Each tab keeps its own board; the
     * live flows above always describe the one on screen.
     */
    val mode: StateFlow<BoardMode> get() = FIXED_GAME_MODE

    /**
     * Switch tabs. Leaving the game abandons a move the engine was thinking
     * about; the analysis board forks from the game record, or resumes where it
     * was if the game has not moved since.
     */
    suspend fun setMode(mode: BoardMode) = Unit

    /** The game file the current line was opened from, if any. */
    val gameName: StateFlow<String?> get() = NO_NAME

    /** Replace the line with one read from a file called [name]. */
    suspend fun loadLine(moves: List<Move>, name: String?) = replaceLine(moves)

    companion object {
        internal val FIXED_GAME_MODE: StateFlow<BoardMode> = MutableStateFlow(BoardMode.GAME).asStateFlow()
        internal val NO_NAME: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
    }
}
