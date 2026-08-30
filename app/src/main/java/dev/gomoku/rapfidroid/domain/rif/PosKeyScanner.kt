package dev.gomoku.rapfidroid.domain.rif

import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.PosKey

/**
 * [PosKey.canonical], made incremental — the aggregation's hot loop and nothing
 * else.
 *
 * The build computes a key for every prefix of every game: 150k games × 21 plies
 * ≈ 3.3M positions, and the straightforward way costs, at each one, eight list
 * builds, eight sorts and eight `String`s. That is the whole of the build's time
 * and most of its garbage.
 *
 * What this does instead, following `rifdb/rifkey.py`'s `KeyScanner`:
 *
 *  - the eight transformed board indices of a cell come from a table, computed
 *    once per board size rather than per stone;
 *  - each transform keeps its stones in a **sorted int array** that one stone is
 *    inserted into per ply, instead of a list that is rebuilt and re-sorted;
 *  - the eight candidate serialisations are written into reused `char` buffers
 *    and compared as buffers, so a `String` is allocated only when the caller
 *    actually asks for the key;
 *  - [hash64] hashes the winning buffer directly, which is what the first pass
 *    wants — it only needs to count visits, never to name them.
 *
 * **It must agree with [PosKey.canonical] exactly**, including the two subtle
 * rules that class documents: candidates compare as *strings* (so `"10" < "2"`),
 * and a tie keeps the smallest transform id. `PosKeyScannerTest` holds the two
 * together over random positions; the key itself stays where the four-way
 * cross-check can see it.
 */
internal class PosKeyScanner(private val size: Int = Move.DEFAULT_SIZE) {

    private val cells = size * size

    /** `tfIdx[t][idx]` — where board index `idx` lands under symmetry `t`. */
    private val tfIdx: Array<IntArray> = Array(8) { t ->
        IntArray(cells) { idx ->
            val p = PosKey.tf(t, size, idx % size, idx / size)
            p.y * size + p.x
        }
    }

    /** `frag[colour][idx]` — the `<col><row>` characters, minus the colour. */
    private val fragment: Array<CharArray> = Array(cells) { idx ->
        val column = 'a' + idx % size
        val row = (size - idx / size).toString()
        CharArray(1 + row.length) { i -> if (i == 0) column else row[i - 1] }
    }

    /** Per transform, the stones so far as `(transformedIndex shl 1) or colour`,
     *  kept ascending. Board indices are distinct within a position, so ordering
     *  by the packed value is ordering by the index. */
    private val stones = Array(8) { IntArray(MAX_STONES) }
    private var count = 0

    /** Reused output: `buffer[t]` holds transform `t`'s serialisation. */
    private val buffer = Array(8) { CharArray(2 + 4 * MAX_STONES) }
    private val length = IntArray(8)

    private var best = 0
    private var stabiliserMask = 0
    private var dirty = true

    fun reset() {
        count = 0
        dirty = true
    }

    /** Add the stone played on board index [cell]; colour follows move parity. */
    fun push(cell: Int) {
        require(count < MAX_STONES) { "position longer than $MAX_STONES plies" }
        val colour = count and 1   // 0 = black, 1 = white, as the key writes them
        for (t in 0 until 8) {
            val packed = (tfIdx[t][cell] shl 1) or colour
            val row = stones[t]
            var i = count
            while (i > 0 && row[i - 1] > packed) {
                row[i] = row[i - 1]
                i--
            }
            row[i] = packed
        }
        count++
        dirty = true
    }

    /** The canonical key. Allocates — call it only for positions being kept. */
    fun key(): String {
        resolve()
        return String(buffer[best], 0, length[best])
    }

    /** Bit *t* set when symmetry *t* ties for the key. */
    fun stabiliser(): Int {
        resolve()
        return stabiliserMask
    }

    /** Whether the key equals [other], without building it. */
    fun matches(other: String): Boolean {
        resolve()
        if (other.length != length[best]) return false
        val buf = buffer[best]
        for (i in other.indices) if (buf[i] != other[i]) return false
        return true
    }

    /** FNV-1a of the key, without building the key. */
    fun hash64(): Long {
        resolve()
        val buf = buffer[best]
        val end = length[best]
        var h = FNV_OFFSET
        for (i in 0 until end) {
            h = h xor (buf[i].code.toLong() and 0xff)
            h *= FNV_PRIME
        }
        return h
    }

    /**
     * The representative of [cell]'s orbit under the stabiliser — the image with
     * the smallest board index, as `rifkey.canon_next` picks it.
     */
    fun canonNext(cell: Int): Int {
        resolve()
        var bestIdx = Int.MAX_VALUE
        for (t in 0 until 8) {
            if (stabiliserMask and (1 shl t) == 0) continue
            val idx = tfIdx[t][cell]
            if (idx < bestIdx) bestIdx = idx
        }
        return bestIdx
    }

    private fun resolve() {
        if (!dirty) return
        for (t in 0 until 8) {
            val buf = buffer[t]
            var at = 0
            // The head is the board size, exactly as PosKey.serialize writes it.
            for (c in sizeChars) buf[at++] = c
            val row = stones[t]
            for (i in 0 until count) {
                val packed = row[i]
                val frag = fragment[packed ushr 1]
                for (c in frag) buf[at++] = c
                buf[at++] = if (packed and 1 == 1) 'w' else 'b'
            }
            length[t] = at
        }
        best = 0
        stabiliserMask = 1
        for (t in 1 until 8) {
            val cmp = compare(t, best)
            if (cmp < 0) {
                best = t
                stabiliserMask = 1 shl t
            } else if (cmp == 0) {
                stabiliserMask = stabiliserMask or (1 shl t)
            }
        }
        dirty = false
    }

    /**
     * `String.compareTo` over two buffers: common prefix first, then length.
     * Candidates really can differ in length — a row number is one digit or two,
     * so `"a9b"` and `"a10b"` are both possible serialisations of one stone.
     */
    private fun compare(a: Int, b: Int): Int {
        val x = buffer[a]
        val y = buffer[b]
        val n = minOf(length[a], length[b])
        for (i in 0 until n) {
            val d = x[i].code - y[i].code
            if (d != 0) return d
        }
        return length[a] - length[b]
    }

    private val sizeChars = size.toString().toCharArray()

    private companion object {
        /** `MAX_PLIES` is 20; a little headroom costs 8 ints per transform. */
        const val MAX_STONES = 32
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L
    }
}
