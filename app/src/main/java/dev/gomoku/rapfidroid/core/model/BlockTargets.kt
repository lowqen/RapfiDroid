package dev.gomoku.rapfidroid.core.model

/**
 * Which points `block compare` and `blockpath except` actually block.
 *
 * Both commands are inversions — "block everything **but** these" — so the
 * desktop walks the whole board and emits one command per remaining point
 * (main.c:10490-10510 and 10601-10640). That derivation is the only real logic
 * behind the two commands, so it lives here where it can be checked without an
 * engine.
 */
object BlockTargets {

    /**
     * Points to block so that only [keep] (and the stones already played) stay
     * available. Occupied points are skipped: the engine cannot play there
     * anyway, and the desktop clears them from `boardblock` before sending.
     */
    fun complement(keep: Collection<Move>, occupied: Collection<Move>, size: Int): List<Move> {
        val spare = keep.toSet() + occupied.toSet()
        val out = ArrayList<Move>((size * size - spare.size).coerceAtLeast(0))
        for (y in 0 until size) {
            for (x in 0 until size) {
                val m = Move(x, y)
                if (m !in spare) out.add(m)
            }
        }
        return out
    }
}
