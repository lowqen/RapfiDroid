package dev.gomoku.yixindroid.core.model

/**
 * Navigation along a game line.
 *
 * The desktop keeps the whole line in `movepath` and treats `piecenum` as a
 * cursor: undo/redo replay the line up to it (main.c `change_piece`), and
 * playing the move that is *already stored* at the cursor keeps the tail while
 * any other move discards it (main.c:2182). Both rules live here — a redo tail
 * that quietly disappears is the kind of thing no screenshot shows.
 */
object MoveCursor {

    /** The redo tail that survives playing [move] at the cursor. */
    fun tailAfter(future: List<Move>, move: Move): List<Move> =
        if (future.firstOrNull() == move) future.drop(1) else emptyList()

    /** Split [whole] so the played part has [target] moves (clamped). */
    fun splitAt(whole: List<Move>, target: Int): Pair<List<Move>, List<Move>> {
        val played = target.coerceIn(0, whole.size)
        return whole.take(played) to whole.drop(played)
    }
}
