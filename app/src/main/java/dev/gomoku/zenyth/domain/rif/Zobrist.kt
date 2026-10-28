package dev.gomoku.zenyth.domain.rif

import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.PosKey

/**
 * A position's **64-bit canonical hash** — what the fast pack build counts
 * positions by (RapfiWeb `core/rif/zobrist.ts`).
 *
 * It names the same equivalence classes as [PosKey]: two move lists hash alike
 * exactly when they have the same canonical key. Only the shape differs: the
 * key is the smallest of eight serialisations, this is the smallest of eight
 * Zobrist hashes (XOR of a random number per cell and colour). So
 *
 *  - adding a stone is one XOR per symmetry — a game is hashed **incrementally**
 *    along its moves, where the string key re-serialised every stone of every
 *    prefix. That is the difference between minutes and seconds;
 *  - XOR does not know order: the same stones in another order are the same
 *    value (a transposition). Colour comes from move parity, as in the key.
 *
 * A move list that repeats a cell is never fed in — XOR would cancel the two
 * stones. The parser drops such games, as the desktop's importer does.
 *
 * Collisions: a million distinct positions in 64 bits is a birthday chance of
 * about 3e-8. `ZobristTest` checks "same hash iff same key" over random positions.
 *
 * Symmetry numbering is [PosKey.tf]'s, so the stabiliser bits mean the same and
 * [foldCell] / the inverse transform are shared. The representative transform
 * the hash picks may differ from the key's; inside a pack only consistency matters.
 */
class ZobristScanner(private val size: Int = Move.DEFAULT_SIZE) {
    private val t = tables(size)
    private val h = LongArray(SYMS)
    private var ply = 0

    var hash = 0L
        private set
    var transform = 0
        private set
    var stabiliser = 1
        private set

    fun reset() {
        h.fill(0L)
        ply = 0
    }

    /** Board index `y * size + x`; colour from the number of stones so far. */
    fun push(cell: Int) {
        val base = (ply and 1) * t.cells
        val sym = t.sym
        val z = t.z
        for (s in 0 until SYMS) {
            h[s] = h[s] xor z[base + sym[s * t.cells + cell]]
        }
        ply++
    }

    /** Computes [hash], [transform] and [stabiliser] for the current position. */
    fun canonical(): ZobristScanner {
        var best = h[0]
        var bt = 0
        var stab = 1
        for (s in 1 until SYMS) {
            val v = h[s]
            if (v < best) {
                best = v
                bt = s
                stab = 1 shl s
            } else if (v == best) {
                stab = stab or (1 shl s)
            }
        }
        hash = best
        transform = bt
        stabiliser = stab
        return this
    }

    internal class Tables(val cells: Int, val sym: IntArray, val z: LongArray)

    companion object {
        private const val SYMS = 8
        private val cache = HashMap<Int, Tables>()

        @Synchronized
        internal fun tables(size: Int): Tables = cache.getOrPut(size) {
            val cells = size * size
            val sym = IntArray(SYMS * cells)
            for (t in 0 until SYMS) for (y in 0 until size) for (x in 0 until size) {
                val p = PosKey.tf(t, size, x, y)
                sym[t * cells + y * size + x] = p.y * size + p.x
            }
            // A fixed seed: the machine that builds a pack and the one that reads it
            // must agree. Changing it invalidates every stored pack (bump the version).
            var s = 0x2545F4914F6CDD1DL
            val z = LongArray(2 * cells) {
                s += -0x61c8864680b583ebL
                var v = s
                v = (v xor (v ushr 30)) * -0x40a7b892e31b1a47L
                v = (v xor (v ushr 27)) * -0x6b2fb644ecceee15L
                v xor (v ushr 31)
            }
            Tables(cells, sym, z)
        }

        /**
         * Fold [cell] into the canonical frame: the smallest board index among its
         * images under the stabiliser's symmetries ([PosKey.canonNext], without
         * allocating).
         */
        fun foldCell(stabiliser: Int, cell: Int, size: Int = Move.DEFAULT_SIZE): Int {
            val t = tables(size)
            var best = Int.MAX_VALUE
            for (s in 0 until SYMS) {
                if (stabiliser and (1 shl s) == 0) continue
                val c = t.sym[s * t.cells + cell]
                if (c < best) best = c
            }
            return best
        }

        /** The canonical hash of a move list — lookups use it. */
        fun of(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): ZobristScanner {
            val z = ZobristScanner(size)
            for (m in moves) z.push(m.y * size + m.x)
            return z.canonical()
        }
    }
}
