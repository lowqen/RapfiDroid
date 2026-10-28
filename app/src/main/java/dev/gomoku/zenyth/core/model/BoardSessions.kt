package dev.gomoku.zenyth.core.model

import kotlinx.serialization.Serializable

/**
 * The board's two tabs (Zenyth Web `BoardMode`).
 *
 *  - [ANALYSIS]: the engine reads the position; the user places stones freely.
 *  - [GAME]: human against the engine, which moves on its own turns.
 *
 * **Each tab has its own board.** Analysis forks from the game record, so every
 * game move is there, but what is explored in analysis never leaks into the
 * game's record (three game moves plus two explored ones used to come back as a
 * five-move game).
 */
enum class BoardMode { ANALYSIS, GAME }

/** One tab's board as it is written to disk. Cells are `y * size + x`. */
@Serializable
data class SavedBoard(
    val size: Int = Move.DEFAULT_SIZE,
    val moves: List<Int> = emptyList(),
    val future: List<Int> = emptyList(),
    /** [GameEnd] name, when the game was decided. */
    val end: String? = null,
    /** [StoneColor] name of the winner; null for a draw. */
    val winner: String? = null,
    /** The game file this line came from, if any. */
    val name: String? = null,
) {
    fun moves(): List<Move> = moves.map { Move(it % size, it / size) }
    fun future(): List<Move> = future.map { Move(it % size, it / size) }
    fun result(): GameResult? = end?.let { e ->
        val ending = runCatching { GameEnd.valueOf(e) }.getOrNull() ?: return null
        GameResult(ending, winner?.let { w -> runCatching { StoneColor.valueOf(w) }.getOrNull() })
    }

    companion object {
        fun of(size: Int, moves: List<Move>, future: List<Move>, result: GameResult?, name: String?) =
            SavedBoard(
                size = size,
                moves = moves.map { it.y * size + it.x },
                future = future.map { it.y * size + it.x },
                end = result?.end?.name,
                winner = result?.winner?.name,
                name = name,
            )
    }
}

/** Both tabs, plus the game line the analysis board forked from. */
@Serializable
data class BoardSessions(
    val mode: String = BoardMode.ANALYSIS.name,
    val game: SavedBoard = SavedBoard(),
    val analysis: SavedBoard? = null,
    val base: List<Int>? = null,
)
