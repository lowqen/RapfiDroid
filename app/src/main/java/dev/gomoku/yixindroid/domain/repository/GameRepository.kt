package dev.gomoku.yixindroid.domain.repository

import dev.gomoku.yixindroid.core.model.ComputerSide
import dev.gomoku.yixindroid.core.model.GameState
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.Swap2Choice
import dev.gomoku.yixindroid.core.model.TapResult
import kotlinx.coroutines.flow.StateFlow

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
}
