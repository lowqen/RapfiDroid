package dev.gomoku.yixindroid.core.model

/**
 * Win and draw detection, ported from the desktop's `make_move` (main.c:2211).
 *
 * The desktop scans the four axes from the stone just played, counting up to five
 * steps each way, and declares a win on `k == 5 || (k > 5 && inforule != 1)` —
 * i.e. an overline wins under every rule except **standard** gomoku. Renju's
 * overline ban is not here: there it is a *forbidden move* for Black, which the
 * engine reports through `FORBID`.
 */
object Referee {

    private val AXES = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)

    /**
     * The result after the whole line has been played, or null while the game is
     * still on. [allowOverline] is `inforule != 1`.
     */
    fun result(moves: List<Move>, size: Int, allowOverline: Boolean): GameResult? {
        val last = moves.lastOrNull() ?: return null
        val mover = if ((moves.size - 1) % 2 == 0) StoneColor.BLACK else StoneColor.WHITE
        if (isWin(moves, size, last, mover, allowOverline)) {
            return GameResult(GameEnd.FIVE, mover)
        }
        // main.c also ends the game when the board fills up (main.c:2195).
        if (moves.size >= size * size) return GameResult(GameEnd.BOARD_FULL, null)
        return null
    }

    /** The stones of [color] that would make five, for the win highlight. */
    fun winningLine(moves: List<Move>, size: Int, allowOverline: Boolean): List<Move> {
        val last = moves.lastOrNull() ?: return emptyList()
        val mover = if ((moves.size - 1) % 2 == 0) StoneColor.BLACK else StoneColor.WHITE
        val own = ownCells(moves, mover)
        for ((dx, dy) in AXES) {
            val line = ArrayList<Move>()
            line += last
            var step = 1
            while (step <= 5) {
                val m = Move(last.x + dx * step, last.y + dy * step)
                if (m !in own) break
                line += m
                step++
            }
            step = 1
            while (step <= 5) {
                val m = Move(last.x - dx * step, last.y - dy * step)
                if (m !in own) break
                line.add(0, m)
                step++
            }
            if (line.size == 5 || (line.size > 5 && allowOverline)) return line
        }
        return emptyList()
    }

    private fun isWin(
        moves: List<Move>,
        size: Int,
        last: Move,
        mover: StoneColor,
        allowOverline: Boolean,
    ): Boolean {
        val own = ownCells(moves, mover)
        for ((dx, dy) in AXES) {
            var count = 1
            // The desktop walks at most five steps in each direction (j < 6).
            for (step in 1..5) {
                val m = Move(last.x + dx * step, last.y + dy * step)
                if (!m.isInside(size) || m !in own) break
                count++
            }
            for (step in 1..5) {
                val m = Move(last.x - dx * step, last.y - dy * step)
                if (!m.isInside(size) || m !in own) break
                count++
            }
            if (count == 5 || (count > 5 && allowOverline)) return true
        }
        return false
    }

    private fun ownCells(moves: List<Move>, color: StoneColor): Set<Move> {
        val out = HashSet<Move>(moves.size)
        moves.forEachIndexed { i, m ->
            val c = if (i % 2 == 0) StoneColor.BLACK else StoneColor.WHITE
            if (c == color) out += m
        }
        return out
    }

    /**
     * The opening area RIF and Soosorv-8 require of the first three moves:
     * move 1 on the centre, move 2 within one point of it, move 3 within two —
     * the desktop's bounding-box check before `yxsoosorvstep2` (main.c:2786).
     */
    fun openingAreaOk(moves: List<Move>, size: Int): Boolean {
        if (moves.size < 3) return true
        val c = size / 2
        val (m1, m2, m3) = moves
        return m1.x == c && m1.y == c &&
            m2.x in (c - 1)..(c + 1) && m2.y in (c - 1)..(c + 1) &&
            m3.x in (c - 2)..(c + 2) && m3.y in (c - 2)..(c + 2)
    }

    /**
     * Whether the engine takes over after the human's first move under "swap
     * after 1st move". The desktop decides this itself instead of asking the
     * engine (main.c:2771): it folds the move into the top-left quadrant and
     * swaps on the far openings, with the two borderline ones a coin flip.
     */
    fun swapAfterFirstMove(move: Move, size: Int, coinFlip: Boolean): Boolean {
        val x = minOf(move.x, size - 1 - move.x)
        val y = minOf(move.y, size - 1 - move.y)
        val borderline = (x == 2 && y == 3) || (x == 3 && y == 2)
        return (borderline && coinFlip) || (x > 1 && y > 1 && x + y > 5)
    }
}
