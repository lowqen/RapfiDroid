package dev.gomoku.rapfidroid.core.model

/**
 * The opening explorer's lookup identity — a port of the desktop
 * `web_poskey_n()` / `web_tf()` / `web_tf_inv()` (main.c:4670-4736).
 *
 * The key is the lexicographically smallest serialization of the position over
 * the 8 board symmetries: `"<size>"` then `"<col><row><b|w>"` per stone in
 * transformed board-index order, colours by move parity. Every rotation and
 * mirror of a line therefore hashes to one key, which is what lets the packs
 * merge transpositions.
 *
 * **This is the fourth implementation** of that logic — the others are
 * `main.c web_poskey()`, `Yixin-Board/tests/test_webkey.c` and
 * `rifdb/rifkey.py`. They are held together by `rifdb/rif_crosscheck.py`,
 * which emits the golden vectors that `PosKeyTest` reads. Change one and the
 * cross-check must be re-run (CLAUDE.md / 개발_핸드북.md §2-6).
 *
 * Two subtleties the port keeps deliberately:
 * 1. Candidates are compared as **strings**, not by board index — `"10" < "2"`,
 *    so an index-order comparison would pick a different symmetry.
 * 2. Ties keep the **smallest** transform id (the desktop only replaces `best`
 *    on a strict `<`), and [transform] is what maps a stored next move back
 *    onto the board, so a tie broken the other way would draw it in the wrong
 *    place while the key still matched.
 */
object PosKey {

    /** Key of the empty board — `rif_pack` stores the totals under this. */
    fun emptyKey(size: Int = Move.DEFAULT_SIZE): String = size.toString()

    /**
     * Apply board symmetry [t] (0..7), the same table the key search runs over.
     * Port of `web_tf` — note this is **not** the `moveorder.h` transform
     * numbering, which orders the group differently.
     */
    fun tf(t: Int, size: Int, x: Int, y: Int): Move {
        val s = size - 1
        return when (t) {
            0 -> Move(x, y)
            1 -> Move(s - x, y)
            2 -> Move(x, s - y)
            3 -> Move(s - x, s - y)
            4 -> Move(y, x)
            5 -> Move(s - y, x)
            6 -> Move(y, s - x)
            else -> Move(s - y, s - x)
        }
    }

    fun tf(t: Int, size: Int, move: Move): Move = tf(t, size, move.x, move.y)

    /** The transform that undoes [t]: all are self-inverse except 5 and 6. */
    fun inverse(t: Int): Int = when (t) {
        5 -> 6
        6 -> 5
        else -> t
    }

    /**
     * Key + the symmetry that produced it. Suggested moves come out of the pack
     * in that canonical frame, so put them back on the board with
     * `tf(inverse(result.transform), size, cell)`.
     */
    fun of(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): Result {
        val c = canonical(moves, size)
        return Result(c.key, c.transform)
    }

    /**
     * [of] plus the **stabiliser** — every symmetry that produces the same key,
     * not just the first one.
     *
     * Aggregation needs it and lookup does not, which is why it was not here
     * before: to merge transpositions, the next move played from a position has
     * to be named by one representative of its orbit under the symmetries the
     * position itself is invariant under (`rifdb/rifkey.py canon_next`). Take
     * only [Result.transform] and a position with a symmetric shape counts the
     * same continuation under two different names.
     *
     * The key and the adopted transform come out of the same loop as [of], so
     * the two cannot drift apart — [of] is defined in terms of this.
     */
    fun canonical(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): Canonical {
        var best: String? = null
        var bestT = 0
        var stabiliser = 0
        for (t in 0 until 8) {
            val cur = serialize(t, moves, size)
            val cmp = if (best == null) -1 else cur.compareTo(best)
            if (cmp < 0) {
                best = cur
                bestT = t
                stabiliser = 1 shl t
            } else if (cmp == 0) {
                stabiliser = stabiliser or (1 shl t)
            }
        }
        return Canonical(best ?: emptyKey(size), bestT, stabiliser)
    }

    /**
     * The representative of [move]'s orbit under [stabiliser]: the image with
     * the smallest **board index**, not the smallest string.
     *
     * Index order, deliberately — the key compares serialisations as strings
     * (`"10" < "2"`), but a next move is a cell and `rifkey.canon_next` orders
     * cells by `y * size + x`. Using the string rule here would pick a
     * different representative and split one continuation into two.
     */
    fun canonNext(stabiliser: Int, move: Move, size: Int = Move.DEFAULT_SIZE): Move {
        var best: Move? = null
        var bestIdx = Int.MAX_VALUE
        for (t in 0 until 8) {
            if (stabiliser and (1 shl t) == 0) continue
            val p = tf(t, size, move)
            val idx = p.y * size + p.x
            if (idx < bestIdx) {
                bestIdx = idx
                best = p
            }
        }
        return best ?: move
    }

    /** Key, the symmetry that produced it, and every symmetry that ties with it. */
    data class Canonical(
        val key: String,
        val transform: Int,
        /** Bit *t* set when symmetry *t* serialises to [key]. Never zero. */
        val stabiliser: Int,
    )

    /** Just the key, for callers that never map a move back. */
    fun keyOf(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): String =
        of(moves, size).key

    private fun serialize(t: Int, moves: List<Move>, size: Int): String {
        // (board index, colour) sorted by index — the desktop's insertion sort.
        // sortedBy is stable, but the keys are distinct board indices (a cell
        // holds one stone), so stability never decides anything here.
        val stones = moves.mapIndexed { i, m ->
            val p = tf(t, size, m)
            (p.y * size + p.x) to (if (i % 2 == 1) 'w' else 'b')
        }.sortedBy { it.first }
        val sb = StringBuilder(2 + 4 * stones.size)
        sb.append(size)
        for ((idx, col) in stones) {
            sb.append('a' + idx % size).append(size - idx / size).append(col)
        }
        return sb.toString()
    }

    data class Result(val key: String, val transform: Int) {
        /** Map a cell from the key's canonical frame back onto the board. */
        fun toBoard(cell: Move, size: Int = Move.DEFAULT_SIZE): Move =
            tf(inverse(transform), size, cell)
    }
}
