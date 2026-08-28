package dev.gomoku.yixindroid.core.model

import dev.gomoku.yixindroid.core.i18n.tr
enum class StoneColor {
    BLACK,
    WHITE,
    ;

    fun other(): StoneColor = if (this == BLACK) WHITE else BLACK

    val label: String get() = if (this == BLACK) tr("흑", "Black") else tr("백", "White")
}

/** A placed stone as the protocol needs it: cell + whether it is the engine's
 *  own stone (BOARD line field 3 = 1) or the opponent's (= 2). */
data class Placement(val move: Move, val own: Boolean)

/**
 * A game line: ordered moves, black first. Immutable; [play]/[undo] return new
 * instances so the UI can treat positions as values.
 */
data class Position(
    val size: Int = Move.DEFAULT_SIZE,
    val moves: List<Move> = emptyList(),
) {
    val sideToMove: StoneColor
        get() = if (moves.size % 2 == 0) StoneColor.BLACK else StoneColor.WHITE

    /**
     * Empty intersections. An upper bound on the moves the side to move has —
     * renju's forbidden points are the engine's to know, not ours — which is the
     * useful direction for anything asking "have I accounted for every move?".
     */
    val emptyPoints: Int get() = size * size - moves.size

    fun colorAt(index: Int): StoneColor =
        if (index % 2 == 0) StoneColor.BLACK else StoneColor.WHITE

    fun play(move: Move): Position = copy(moves = moves + move)

    fun undo(): Position =
        if (moves.isEmpty()) this else copy(moves = moves.dropLast(1))

    /**
     * Placements for a BOARD/YXBOARD command: "own" = stones of the side that
     * is to move. NOTE: the own/opponent split is finalized in P2 against the
     * real server (tied to which side the engine is asked to play).
     */
    fun placements(): List<Placement> =
        moves.mapIndexed { i, m -> Placement(m, own = colorAt(i) == sideToMove) }
}
