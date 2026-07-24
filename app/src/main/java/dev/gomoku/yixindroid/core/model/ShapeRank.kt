package dev.gomoku.yixindroid.core.model

/**
 * One row of the theoretical 5-move ranking (`rank5.csv` → bundled `rank5.db`).
 * Pure computation (rule-based enumeration), RenjuNet-free.
 *
 * `countRaw` is the shape's placement multiplicity and takes one of a small set
 * of values (32/16/8/4) — the "group" a shape belongs to. `countStd = countRaw/4`.
 */
data class ShapeRank(
    val rankStd: Int,
    val rankRaw: Int,
    val countStd: Int,
    val countRaw: Int,
    val perPlacement: Int,
    val placements: Int,
    val stabilizer: Int,
    val opening: String,
    val repMoves: String,
    val m5Dist: Int,
) {
    /** Placement-multiplicity class (32/16/8/4). */
    val group: Int get() = countRaw

    val openingIndex: Int get() = Opening26.indexOfAbbr(opening)

    fun moves(size: Int = Move.DEFAULT_SIZE): List<Move> =
        repMoves.split(' ').mapNotNull { Move.fromLabel(it, size) }
}
