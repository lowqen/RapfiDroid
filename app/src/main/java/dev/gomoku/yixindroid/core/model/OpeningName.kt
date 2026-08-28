package dev.gomoku.yixindroid.core.model

import dev.gomoku.yixindroid.core.i18n.tr

/**
 * The opening-name identity — a port of the desktop `Yixin-Board/openname.h`.
 *
 * **This is not [PosKey].** The explorer's position key deliberately drops move
 * order so transpositions merge into one row; names cannot work that way,
 * because *which white stone was move 2* is exactly what makes a line 화월
 * rather than something else. Measured on RenjuNet (108,490 standard-opening
 * games): at ply 4 there are 577 stone sets but 649 move orders, so 72 keys
 * would merge two differently-named lines. Names therefore get their own,
 * order-preserving key.
 *
 * **The frame.** Of the 8 D4 placements of a line, keep the ones that put move
 * 1 on tengen and move 2 on `h9` (direct) or `i9` (indirect) — the frame renju
 * diagrams are drawn in, and the same normalisation [Opening26] already uses.
 * Exactly two survive (the stabiliser of h9 / i9 has order 2), and the tie goes
 * to the smallest move sequence read as board indices in play order.
 *
 * The key is that frame's move list, e.g. `"h8 h9 g9 g8"` — human-readable on
 * purpose, since it doubles as the caption under the worksheet diagrams the
 * 4th-move names are collected with.
 *
 * Two things to keep straight against [PosKey]:
 * 1. Candidates are compared as **board indices**, not as serialised strings.
 *    Index order is what `mo_opening26` minimises, and matching it keeps this
 *    key's 3-move prefix and the 26주형 classifier from ever disagreeing.
 * 2. The transform numbering is `web_tf`'s (same as [PosKey.tf]), *not*
 *    `moveorder.h`'s — which is what [Opening26] uses internally.
 *
 * **Third implementation** of this logic, after `openname.h` and
 * `rifdb/rifkey.py`; `rifdb/rif_crosscheck.py` holds the three together and
 * emits the golden vectors `OpeningNameTest` reads.
 */
object OpeningName {

    /** Names exist for the first four moves only — 렌주 has no per-shape name
     *  at 5수/6수, so the chain simply stops here. */
    const val MAX_PLY = 4

    /**
     * The *frame* reaches one ply further than the *name*. 5수 has no name, but
     * it does have an evaluation (흑 5수 유불리표), and pointing at a row of that
     * table needs an identity for the 5-move line.
     *
     * Safe because the key has the prefix property: minimising a 5-long
     * sequence only reaches index 4 when the first four are equal, so the
     * 4-move prefix of a 5-move key is always the 4-move key. [MAX_PLY] still
     * bounds names — only [frameTransform] and [frameOf] see this one.
     */
    const val FRAME_PLY = 5

    enum class Move2Kind { NONE, DIRECT, INDIRECT }

    /** Frame transform + the framed line, or `null` when the line has no frame. */
    data class Frame(val transform: Int, val key: String)

    /**
     * Apply symmetry [t] (0..7) — the `web_tf` table, shared with [PosKey.tf].
     * Nothing couples the two keys; the shared table is only to spare a reader
     * one more numbering to hold in their head.
     */
    fun tf(t: Int, size: Int, move: Move): Move = PosKey.tf(t, size, move)

    private fun cellIndex(m: Move, size: Int) = m.y * size + m.x

    private fun framed(t: Int, size: Int, moves: List<Move>): List<Int> =
        moves.map { cellIndex(tf(t, size, it), size) }

    /** The frame transform, or -1 when the line has no frame. */
    fun frameTransform(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): Int {
        if (size % 2 == 0 || size < 5 || moves.size !in 1..FRAME_PLY) return -1
        val centre = (size / 2) * size + size / 2
        if (cellIndex(moves[0], size) != centre) return -1
        if (moves.size == 1) return 0   // tengen alone is fixed by all 8
        val direct = (size / 2 - 1) * size + size / 2       // h9
        val indirect = direct + 1                           // i9
        var best = -1
        var bestSeq: List<Int> = emptyList()
        for (t in 0 until 8) {
            val seq = framed(t, size, moves)
            if (seq[1] != direct && seq[1] != indirect) continue
            if (best < 0 || less(seq, bestSeq)) {
                best = t
                bestSeq = seq
            }
        }
        return best
    }

    private fun less(a: List<Int>, b: List<Int>): Boolean {
        for (i in a.indices) if (a[i] != b[i]) return a[i] < b[i]
        return false
    }

    /** `"h8 h9 g9 g8"` + its transform, or `null` when the line has no frame. */
    fun frameOf(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): Frame? {
        val t = frameTransform(moves, size)
        if (t < 0) return null
        val key = framed(t, size, moves).joinToString(" ") { c ->
            "${'a' + c % size}${size - c / size}"
        }
        return Frame(t, key)
    }

    /** The key alone, or `null`. Lookups in `opening_names.txt` use this. */
    fun keyOf(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): String? =
        frameOf(moves, size)?.key

    /** Whether move 2 blocks orthogonally (직접막기) or diagonally (간접막기). */
    fun move2Kind(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): Move2Kind {
        if (moves.size < 2) return Move2Kind.NONE
        val t = frameTransform(moves.take(FRAME_PLY), size)
        if (t < 0) return Move2Kind.NONE
        val direct = (size / 2 - 1) * size + size / 2
        return if (framed(t, size, moves.take(2))[1] == direct) Move2Kind.DIRECT
        else Move2Kind.INDIRECT
    }

    // ---- the names themselves -------------------------------------------
    //
    // Sparse by design. 1·2·3수 are pure computation and always answer; 4수
    // comes from the user's opening_names.txt, so most fourth moves will never
    // have a name. That is the normal case — nothing may depend on a name
    // existing, and the chain simply stops where the names run out.

    /** Name of the position after exactly [ply] moves of [moves], or `null`. */
    fun nameAt(ply: Int, moves: List<Move>, size: Int = Move.DEFAULT_SIZE): String? {
        if (ply < 1 || ply > MAX_PLY || ply > moves.size) return null
        val line = moves.take(ply)
        if (frameTransform(line, size) < 0) return null
        return when (ply) {
            1 -> tr("천원", "Tengen")
            2 -> when (move2Kind(line, size)) {
                Move2Kind.DIRECT -> tr("직접막기", "Direct block")
                Move2Kind.INDIRECT -> tr("간접막기", "Indirect block")
                Move2Kind.NONE -> null
            }
            3 -> Opening26.classify(line).let {
                if (it == Opening26.NONSTD) null else Opening26.name(it)
            }
            else -> {      // 4수 — the user's table, keyed by the frame key
                val n = keyOf(line, size)?.let { OpeningTables.names[it] }
                n?.let { tr(it.ko, it.en ?: it.ko) }
            }
        }
    }

    /**
     * The 흑 5수 유불리 grade of the position after [moves], or null.
     *
     * Keyed by the *position* ([PosKey]) and not by the move order, which is
     * the opposite of the rule above: two lines that reach the same stones are
     * the same game and must show the same grade (환원). [OpeningEval] says why.
     */
    fun gradeAt(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): OpeningEval.Grade? {
        if (moves.size !in OpeningEval.PLIES || OpeningTables.evals.isEmpty()) return null
        return OpeningEval.of(OpeningTables.evals[PosKey.of(moves, size).key])
    }

    // ---- 환원 (transpositions) ------------------------------------------

    /** The central 5×5 that holds moves 1-3. A RULE of renju, binding the first
     *  three moves only — emphatically *not* a bound on the 4th, which is the
     *  mistake that once hid 한성's h11. Mirrors `ON_RULEBOX`. */
    const val RULE_BOX = 2

    /**
     * Every legal renju move order that reaches the **same stones in the same
     * colours** as [moves] — only the order changes. Returned as framed lines,
     * sorted by board index, including [moves]'s own frame.
     *
     * This is the list that explains the two keys: everything here is one
     * game, so the grade must be one number, while the names may legitimately
     * differ line by line ("이 자리는 한성 한교 = 어느 간접 주형의 4수").
     *
     * Nothing is stored — 3!×2! is twelve candidates and the filter does the
     * rest. Third implementation, after `openname.h on_transpositions` and
     * `rifkey.transpositions`; `rif_crosscheck.py` holds the first two together
     * and the golden holds this one.
     */
    fun transpositions(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): List<List<Move>> {
        if (moves.size !in 1..FRAME_PLY) return emptyList()
        val centre = size / 2
        val black = moves.filterIndexed { i, _ -> i % 2 == 0 }
        val white = moves.filterIndexed { i, _ -> i % 2 == 1 }
        val seen = LinkedHashMap<List<Int>, List<Move>>()
        for (pb in permutations(black)) for (pw in permutations(white)) {
            val seq = moves.indices.map { if (it % 2 == 0) pb[it / 2] else pw[it / 2] }
            if (seq.take(3).any {
                    kotlin.math.abs(it.x - centre) > RULE_BOX ||
                        kotlin.math.abs(it.y - centre) > RULE_BOX
                }
            ) continue
            val t = frameTransform(seq, size)
            if (t < 0) continue
            val framedCells = framed(t, size, seq)
            seen.getOrPut(framedCells) {
                framedCells.map { Move(it % size, it / size) }
            }
        }
        return seen.entries.sortedWith(compareBy(ListComparator) { it.key }).map { it.value }
    }

    private object ListComparator : Comparator<List<Int>> {
        override fun compare(a: List<Int>, b: List<Int>): Int {
            for (i in a.indices) {
                if (i >= b.size) return 1
                if (a[i] != b[i]) return a[i] - b[i]
            }
            return a.size - b.size
        }
    }

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        val out = ArrayList<List<T>>()
        for (i in items.indices) {
            val rest = items.toMutableList().also { it.removeAt(i) }
            for (p in permutations(rest)) out.add(listOf(items[i]) + p)
        }
        return out
    }

    /**
     * `["천원", "간접막기", "화월"]` — the named steps of this line, stopping at
     * the first unnamed ply because the chain is a path, not a set.
     */
    fun chain(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): List<String> {
        val out = mutableListOf<String>()
        for (ply in 1..minOf(MAX_PLY, moves.size))
            out += nameAt(ply, moves, size) ?: break
        return out
    }
}
