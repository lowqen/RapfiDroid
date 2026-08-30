package dev.gomoku.rapfidroid.core.model

import dev.gomoku.rapfidroid.core.i18n.tr

/**
 * Move-order (transposition) enumeration — a port of the desktop
 * `Yixin-Board/moveorder.h` (880 lines), the core behind 수순 탐색기.
 *
 * Answers "which move orders produce exactly the stones now on the board?".
 * Orders explode as `b! * w!` (a 20-stone position has ~13 trillion), but the
 * intermediate positions — the nodes of the DAG — are only
 * `C(n,w) + C(n,w+1)` = 352,716. So nothing is ever enumerated: we walk the
 * DAG and hang a memoised path count on every branch.
 *
 * Legality layers, exactly as the desktop applies them:
 * - **L0** alternation — structural (black on even plies).
 * - **L1** no premature five ([noFive]) — a five before the last move would
 *   have ended the game.
 * - **L3** opening rule ([openingRule]) — move *p+1* within Chebyshev
 *   distance *p* of tengen, for the first four moves.
 * - **L3b** canonical move 2 ([move2Fix]) — H9 / I9, the two symmetry classes
 *   of the 3×3 ring.
 *
 * Renju forbidden points (3-3 / 4-4 / overline) are **not** judged: the GUI has
 * no local judge and asking the engine costs one round trip per node, which is
 * hopeless over millions (개발_핸드북.md §8.5). Same limitation on mobile,
 * where the engine is across a VPN — so the same honest gap.
 */

/** Max stones per colour: the per-colour bitmask width (`MO_MAXSTONES`). */
const val MO_MAX_STONES = 30

/** Interactive node budget shared by the whole variant set (`MO_GUI_NODES`). */
const val MO_GUI_NODES = 300_000L

/** One branch of the DAG: a stone that may be played next. */
data class MoveOrderChild(
    val cell: Int,
    /** 1 = black, 2 = white, like the desktop's `col`. */
    val color: Int,
    /** Complete legal orders passing through this branch. */
    val count: Double,
) {
    val isBlack: Boolean get() = color == 1
}

/** A stone still to be placed, as the mini board draws it. */
data class MoveOrderGhost(
    val cell: Int,
    val color: Int,
    /**
     * True when every surviving placement puts this stone here; the desktop
     * draws those as translucent stones with a ply badge and the rest as a
     * small neutral dot (main.c:6365-6409).
     */
    val common: Boolean,
    /** Bit *p* set = this stone can be move *p+1*. 0 when unknown. */
    val plies: Long,
)

/**
 * A `uint64 -> double` open-addressing memo. Hand-rolled for the same reason
 * the desktop hand-rolls it: a `HashMap<Long, Double>` boxes both halves of
 * every one of ~300k entries, which on a phone costs more than the search.
 */
private class MoMemo {
    private var keys = LongArray(1024)
    private var vals = DoubleArray(1024)
    private var used = BooleanArray(1024)
    private var mask = 1023
    private var count = 0

    fun get(k: Long): Double? {
        var i = hash(k) and mask
        while (used[i]) {
            if (keys[i] == k) return vals[i]
            i = (i + 1) and mask
        }
        return null
    }

    fun put(k: Long, v: Double) {
        if ((count + 1) * 10 >= keys.size * 7) grow()
        var i = hash(k) and mask
        while (used[i]) {
            if (keys[i] == k) {
                vals[i] = v
                return
            }
            i = (i + 1) and mask
        }
        used[i] = true
        keys[i] = k
        vals[i] = v
        count++
    }

    private fun grow() {
        val ok = keys
        val ov = vals
        val ou = used
        keys = LongArray(ok.size * 2)
        vals = DoubleArray(ok.size * 2)
        used = BooleanArray(ok.size * 2)
        mask = keys.size - 1
        count = 0
        for (i in ok.indices) if (ou[i]) put(ok[i], ov[i])
    }

    private fun hash(key: Long): Int {
        var k = key
        k = k xor (k ushr 33)
        k *= -0x00ae502812aa7333L   // 0xff51afd7ed558ccd
        k = k xor (k ushr 33)
        k *= -0x3b314601e57a13adL   // 0xc4ceb9fe1a85ec53
        k = k xor (k ushr 33)
        return (k.toInt()) and Int.MAX_VALUE
    }
}

/**
 * One placement of the shape (one D4 variant) plus its DP. Port of `MoCtx`.
 * Mutable and not thread-safe — drive it from a single coroutine.
 */
private class MoCtx(
    val boardSize: Int,
    val rule: Int,
    val openingRule: Boolean,
    val noFive: Boolean,
    val move2Fix: Boolean,
    cells: IntArray,
) {
    val b = IntArray(MO_MAX_STONES)
    val w = IntArray(MO_MAX_STONES)
    var nb = 0
    var nw = 0
    val n: Int get() = nb + nw

    private val occ = IntArray(boardSize * boardSize)
    private val memo = MoMemo()

    var nodes = 0L
    var maxNodes = Long.MAX_VALUE
    var overflow = false

    init {
        for (i in cells.indices) {
            if (i % 2 == 0) b[nb++] = cells[i] else w[nw++] = cells[i]
        }
    }

    fun yOf(cell: Int) = cell / boardSize
    fun xOf(cell: Int) = cell % boardSize

    /** Does the stone just placed at [cell] complete a five (or an overline
     *  where the rule counts it)? Mirrors the win check at the end of
     *  `make_move`; only same-colour stones extend a run, so opponent stones
     *  block exactly like empty points. */
    private fun makesFive(cell: Int): Boolean {
        val col = occ[cell]
        val y0 = yOf(cell)
        val x0 = xOf(cell)
        for (d in 0 until 4) {
            var k = 1
            var ny = y0
            var nx = x0
            for (j in 1 until 6) {
                ny += DY[d]; nx += DX[d]
                if (nx < 0 || ny < 0 || nx >= boardSize || ny >= boardSize) break
                if (occ[ny * boardSize + nx] != col) break
                k++
            }
            ny = y0; nx = x0
            for (j in 1 until 6) {
                ny -= DY[d]; nx -= DX[d]
                if (nx < 0 || ny < 0 || nx >= boardSize || ny >= boardSize) break
                if (occ[ny * boardSize + nx] != col) break
                k++
            }
            if (k == 5 || (k > 5 && rule != 1)) return true
        }
        return false
    }

    /** Move *p+1* must sit within a `(2p+1)²` box around tengen. Only
     *  meaningful on an odd board, which is where a true centre exists. */
    private fun openingOk(p: Int, cell: Int): Boolean {
        if (!openingRule || p > 3) return true
        if (boardSize % 2 == 0) return true
        val ctr = boardSize / 2
        val y = yOf(cell)
        val x = xOf(cell)
        // Canonical move 2 is displayed H9 / I9. Internal y runs downward
        // (row = boardSize - y), so "one row above tengen" is y = ctr - 1.
        if (p == 1 && move2Fix) return y == ctr - 1 && (x == ctr || x == ctr + 1)
        val dy = if (y > ctr) y - ctr else ctr - y
        val dx = if (x > ctr) x - ctr else ctr - x
        return maxOf(dy, dx) <= p
    }

    /** May this stone be the move played at ply [p]? The caller must not have
     *  placed it yet. */
    fun edgeOk(p: Int, color: Int, cell: Int): Boolean {
        if (!openingOk(p, cell)) return false
        if (!noFive) return true
        if (p + 1 >= n) return true   // the final move may end the game
        occ[cell] = color
        val ok = !makesFive(cell)
        occ[cell] = 0
        return ok
    }

    fun place(cell: Int, color: Int) { occ[cell] = color }
    fun lift(cell: Int) { occ[cell] = 0 }
    fun clearBoard() { occ.fill(0) }

    /** Complete legal orders that finish the position from the partial
     *  placement (bm, wm) at ply p. Memoised on (bm, wm), which determines p. */
    fun count(bm: Int, wm: Int, p: Int): Double {
        if (p >= n) return 1.0
        if (overflow) return 0.0

        val key = key(bm, wm)
        memo.get(key)?.let { return it }
        if (nodes >= maxNodes) {
            overflow = true
            return 0.0
        }
        nodes++

        var total = 0.0
        val black = p % 2 == 0
        val list = if (black) b else w
        val used = if (black) bm else wm
        val col = if (black) 1 else 2
        for (i in 0 until (if (black) nb else nw)) {
            if (used and (1 shl i) != 0) continue
            val cell = list[i]
            if (!edgeOk(p, col, cell)) continue
            occ[cell] = col
            total += if (black) count(bm or (1 shl i), wm, p + 1)
            else count(bm, wm or (1 shl i), p + 1)
            occ[cell] = 0
            if (overflow) return 0.0
        }

        memo.put(key, total)
        return total
    }

    fun total(): Double {
        clearBoard()
        return count(0, 0, 0)
    }

    /** Legal next stones from (bm, wm) at ply p with their path counts.
     *  [occ] must already reflect (bm, wm). */
    fun children(bm: Int, wm: Int, p: Int): List<MoveOrderChild> {
        val out = ArrayList<MoveOrderChild>()
        val black = p % 2 == 0
        val list = if (black) b else w
        val used = if (black) bm else wm
        val col = if (black) 1 else 2
        for (i in 0 until (if (black) nb else nw)) {
            if (used and (1 shl i) != 0) continue
            val cell = list[i]
            if (!edgeOk(p, col, cell)) continue
            occ[cell] = col
            val c = if (black) count(bm or (1 shl i), wm, p + 1)
            else count(bm, wm or (1 shl i), p + 1)
            occ[cell] = 0
            if (c <= 0.0) continue   // legal step, but no legal completion past it
            out.add(MoveOrderChild(cell, col, c))
        }
        return out
    }

    /** Extend (bm, wm) to a full legal order, writing plies p..n-1 into [out].
     *  Greedy with backtracking, so it always finds one when count > 0. */
    fun complete(bm: Int, wm: Int, p: Int, out: IntArray): Boolean {
        if (p >= n) return true
        val black = p % 2 == 0
        val list = if (black) b else w
        val used = if (black) bm else wm
        val col = if (black) 1 else 2
        for (i in 0 until (if (black) nb else nw)) {
            if (used and (1 shl i) != 0) continue
            val cell = list[i]
            if (!edgeOk(p, col, cell)) continue
            occ[cell] = col
            out[p] = cell
            val ok = if (black) complete(bm or (1 shl i), wm, p + 1, out)
            else complete(bm, wm or (1 shl i), p + 1, out)
            occ[cell] = 0
            if (ok) return true
        }
        return false
    }

    /**
     * Which plies can each stone occupy? A placement is *useful* iff some
     * complete order goes through it — reachable and `count(after) > 0`. One
     * DFS over the reachable states with the count memo already warm from
     * [total] marks every useful (stone, ply) pair.
     */
    fun markPlies(bPly: LongArray, wPly: LongArray) {
        val seen = MoMemo()
        clearBoard()
        markRec(seen, 0, 0, 0, bPly, wPly)
    }

    private fun markRec(
        seen: MoMemo, bm: Int, wm: Int, p: Int, bPly: LongArray, wPly: LongArray,
    ) {
        if (p >= n) return
        val k = key(bm, wm)
        if (seen.get(k) != null) return
        seen.put(k, 1.0)
        val black = p % 2 == 0
        val list = if (black) b else w
        val used = if (black) bm else wm
        val col = if (black) 1 else 2
        for (i in 0 until (if (black) nb else nw)) {
            if (used and (1 shl i) != 0) continue
            val cell = list[i]
            if (!edgeOk(p, col, cell)) continue
            occ[cell] = col
            val c = if (black) count(bm or (1 shl i), wm, p + 1)
            else count(bm, wm or (1 shl i), p + 1)
            if (c > 0.0) {
                if (black) bPly[i] = bPly[i] or (1L shl p) else wPly[i] = wPly[i] or (1L shl p)
                markRec(
                    seen,
                    if (black) bm or (1 shl i) else bm,
                    if (black) wm else wm or (1 shl i),
                    p + 1, bPly, wPly,
                )
            }
            occ[cell] = 0
        }
    }

    private fun key(bm: Int, wm: Int): Long =
        (bm.toLong() shl 32) or (wm.toLong() and 0xffffffffL)

    private companion object {
        val DY = intArrayOf(1, 0, 1, 1)
        val DX = intArrayOf(0, 1, 1, -1)
    }
}

/**
 * The merged DAG over deduped D4 variants — port of `MoVarSet`.
 *
 * A "shape" is direction-free: the dihedral group gives up to 8 placements of
 * the same shape. Transforms that coincide (self-symmetric shapes) are deduped,
 * and browsing merges the survivors: a branch is a cell that leads into **any**
 * alive variant, its count the sum over them. Distinct variants have distinct
 * final stone sets, so the sums never count an order twice.
 *
 * Mutable (the UI drills and steps back); drive it from one coroutine.
 */
class MoveOrderSet private constructor(
    val boardSize: Int,
    private val ctx: List<MoCtx>,
    /** `moveorder.h` transform id of each kept variant. */
    val transforms: List<Int>,
) {
    private val bm = IntArray(ctx.size)
    private val wm = IntArray(ctx.size)
    private val alive = BooleanArray(ctx.size)
    private val bPly = Array(ctx.size) { LongArray(MO_MAX_STONES) }
    private val wPly = Array(ctx.size) { LongArray(MO_MAX_STONES) }
    private val path = ArrayList<Int>()

    /** Total legal orders over every variant; 0 when [overflow]. */
    var total: Double = 0.0
        private set

    /** The node budget ran out: counts are not trustworthy and are reported as
     *  "not counted" rather than as a wrong number. */
    var overflow: Boolean = false
        private set

    /** [plies] data is valid (skipped after an overflow). */
    var plyOk: Boolean = false
        private set

    val variantCount: Int get() = ctx.size
    val stoneCount: Int get() = ctx[0].n
    val blackCount: Int get() = ctx[0].nb
    val whiteCount: Int get() = ctx[0].nw

    /** The drilled path, as board cells. */
    val prefix: List<Int> get() = path

    private fun build(maxNodes: Long) {
        var remaining = maxNodes
        val totals = DoubleArray(ctx.size)
        for ((v, c) in ctx.withIndex()) {
            // Budget is shared: a later variant may get nothing left, which
            // overflows it — the desktop does not break out of this loop either.
            c.maxNodes = if (remaining > 0) remaining else 0
            totals[v] = c.total()
            remaining -= c.nodes
            if (c.overflow) overflow = true
            total += totals[v]
        }
        if (overflow) {
            total = 0.0   // same honesty contract as the count itself
        } else {
            for ((v, c) in ctx.withIndex()) {
                if (totals[v] > 0.0) c.markPlies(bPly[v], wPly[v])
            }
            plyOk = true
        }
        applyPrefix()
    }

    /**
     * Rebuild per-variant masks / occupancy / liveness from [path]. One code
     * path for drill, back and root, so the state machine cannot drift.
     */
    private fun applyPrefix() {
        for (v in ctx.indices) {
            bm[v] = 0
            wm[v] = 0
            alive[v] = true
            ctx[v].clearBoard()
        }
        for (k in path.indices) {
            val cell = path[k]
            val black = k % 2 == 0
            val col = if (black) 1 else 2
            for (v in ctx.indices) {
                if (!alive[v]) continue
                val c = ctx[v]
                val list = if (black) c.b else c.w
                val used = if (black) bm[v] else wm[v]
                var found = -1
                for (i in 0 until (if (black) c.nb else c.nw)) {
                    if (list[i] == cell && used and (1 shl i) == 0) {
                        found = i
                        break
                    }
                }
                if (found < 0 || !c.edgeOk(k, col, cell)) {
                    alive[v] = false
                    continue
                }
                c.place(cell, col)
                if (black) bm[v] = bm[v] or (1 shl found) else wm[v] = wm[v] or (1 shl found)
            }
        }
    }

    fun aliveCount(): Int = alive.count { it }

    /** The **identity** placement is still in play. Only then does the real
     *  game's next move mean anything on the drilled path (main.c:5943-5949). */
    fun identityAlive(): Boolean =
        transforms.indexOf(0).let { it >= 0 && alive[it] }

    /** The transform id when exactly one placement is still in play. */
    fun soleAliveTransform(): Int? =
        alive.indices.filter { alive[it] }.singleOrNull()?.let { transforms[it] }

    /** Complete orders through the current prefix, summed over alive variants. */
    fun branchCount(): Double {
        var s = 0.0
        for (v in ctx.indices) if (alive[v]) s += ctx[v].count(bm[v], wm[v], path.size)
        return s
    }

    /** Merged next-move candidates: the same board cell across variants
     *  collapses into one row with the counts summed. Largest first, ties by
     *  cell (y then x) so the order is deterministic. */
    fun children(): List<MoveOrderChild> {
        val merged = LinkedHashMap<Int, MoveOrderChild>()
        for (v in ctx.indices) {
            if (!alive[v]) continue
            for (ch in ctx[v].children(bm[v], wm[v], path.size)) {
                val prev = merged[ch.cell]
                merged[ch.cell] =
                    if (prev == null) ch else prev.copy(count = prev.count + ch.count)
            }
        }
        return merged.values.sortedWith(
            compareByDescending<MoveOrderChild> { it.count }
                .thenBy { it.cell / boardSize }
                .thenBy { it.cell % boardSize },
        )
    }

    /** Stones not yet ordered, aggregated over the alive placements — what the
     *  mini board draws as ghosts (main.c:6341-6363). */
    fun ghosts(): List<MoveOrderGhost> {
        val black = HashMap<Int, Int>()
        val white = HashMap<Int, Int>()
        val plies = HashMap<Int, Long>()
        var nAlive = 0
        for (v in ctx.indices) {
            if (!alive[v]) continue
            nAlive++
            val c = ctx[v]
            for (i in 0 until c.nb) if (bm[v] and (1 shl i) == 0) {
                black[c.b[i]] = (black[c.b[i]] ?: 0) + 1
                if (plyOk) plies[c.b[i]] = (plies[c.b[i]] ?: 0L) or bPly[v][i]
            }
            for (i in 0 until c.nw) if (wm[v] and (1 shl i) == 0) {
                white[c.w[i]] = (white[c.w[i]] ?: 0) + 1
                if (plyOk) plies[c.w[i]] = (plies[c.w[i]] ?: 0L) or wPly[v][i]
            }
        }
        val out = ArrayList<MoveOrderGhost>()
        for (cell in black.keys + white.keys) {
            val nb = black[cell] ?: 0
            val nw = white[cell] ?: 0
            val common = (nb == nAlive && nw == 0) || (nw == nAlive && nb == 0)
            out.add(
                MoveOrderGhost(
                    cell = cell,
                    color = if (nb >= nw) 1 else 2,
                    common = common,
                    plies = if (common) plies[cell] ?: 0L else 0L,
                ),
            )
        }
        return out
    }

    /** Descend into a board cell. Returns false and leaves the state untouched
     *  when no variant accepts it. */
    fun drill(cell: Int): Boolean {
        if (ctx.isEmpty() || path.size >= stoneCount) return false
        path.add(cell)
        applyPrefix()
        if (alive.none { it }) {
            path.removeAt(path.size - 1)
            applyPrefix()
            return false
        }
        return true
    }

    fun back(): Boolean {
        if (path.isEmpty()) return false
        path.removeAt(path.size - 1)
        applyPrefix()
        return true
    }

    fun root() {
        path.clear()
        applyPrefix()
    }

    /**
     * Extend the prefix to a full legal order (prefix cells included). Prefers
     * the identity variant so a replay keeps the board orientation; returns
     * null when no completion exists.
     */
    fun complete(): Completion? {
        for (pass in 0 until 2) {
            for (v in ctx.indices) {
                if (!alive[v]) continue
                if ((pass == 0) != (transforms[v] == 0)) continue
                val out = IntArray(ctx[v].n)
                for (k in path.indices) out[k] = path[k]
                if (ctx[v].complete(bm[v], wm[v], path.size, out)) {
                    return Completion(transforms[v], out.toList())
                }
            }
        }
        return null
    }

    /** A full legal order plus the placement it lives in. */
    data class Completion(val transform: Int, val order: List<Int>)

    companion object {
        /** The D4 group in `moveorder.h` numbering, on internal (y, x).
         *  **Not** the `PosKey`/`web_tf` numbering — the groups are the same,
         *  the indices are not. */
        fun xform(t: Int, size: Int, cell: Int): Int {
            val y = cell / size
            val x = cell % size
            val b = size - 1
            val (oy, ox) = when (t) {
                0 -> y to x                    // identity
                1 -> x to (b - y)              // rot 90 cw
                2 -> (b - y) to (b - x)        // rot 180
                3 -> (b - x) to y              // rot 270 cw
                4 -> y to (b - x)              // mirror l-r
                5 -> (b - y) to x              // mirror u-d
                6 -> x to y                    // transpose
                else -> (b - x) to (b - y)     // anti-transpose
            }
            return oy * size + ox
        }

        /** Orientation names, keyed by transform id (`mo_xform_name`). */
        fun xformName(t: Int): String = when (t) {
            0 -> tr("원래 방향", "As played")
            1 -> tr("90° 회전", "Rotated 90°")
            2 -> tr("180° 회전", "Rotated 180°")
            3 -> tr("270° 회전", "Rotated 270°")
            4 -> tr("좌우 반전", "Mirrored ↔")
            5 -> tr("상하 반전", "Mirrored ↕")
            6 -> tr("\\ 대각 반전", "Mirrored \\")
            else -> tr("/ 대각 반전", "Mirrored /")
        }

        /** The two canonical move-2 cells (displayed h9 / i9), or null on an
         *  even board where there is no tengen (`mo_rule2_cells`). */
        fun rule2Cells(size: Int): Pair<Int, Int>? {
            if (size % 2 == 0) return null
            val ctr = size / 2
            return ((ctr - 1) * size + ctr) to ((ctr - 1) * size + ctr + 1)
        }

        /**
         * Build the deduped variants, count each under one shared node budget,
         * and mark the per-stone possible plies. Returns null when the position
         * is invalid or exceeds [MO_MAX_STONES] per colour.
         */
        fun create(
            cells: List<Int>,
            size: Int = Move.DEFAULT_SIZE,
            rule: Int = 2,
            openingRule: Boolean = true,
            noFive: Boolean = true,
            move2Fix: Boolean = true,
            withSymmetry: Boolean = true,
            maxNodes: Long = MO_GUI_NODES,
        ): MoveOrderSet? {
            if (cells.isEmpty() || cells.size > 2 * MO_MAX_STONES) return null
            if (cells.any { it < 0 || it >= size * size }) return null
            if ((cells.size + 1) / 2 > MO_MAX_STONES) return null

            val kept = ArrayList<MoCtx>()
            val ids = ArrayList<Int>()
            val seen = ArrayList<Pair<List<Int>, List<Int>>>()
            for (t in 0 until (if (withSymmetry) 8 else 1)) {
                val tc = cells.map { xform(t, size, it) }
                // canonical per-colour sorted cell lists, for the position dedupe
                val cb = tc.filterIndexed { i, _ -> i % 2 == 0 }.sorted()
                val cw = tc.filterIndexed { i, _ -> i % 2 == 1 }.sorted()
                if (seen.any { it.first == cb && it.second == cw }) {
                    continue   // self-symmetric: same position, count it once
                }
                seen.add(cb to cw)
                kept.add(MoCtx(size, rule, openingRule, noFive, move2Fix, tc.toIntArray()))
                ids.add(t)
            }
            return MoveOrderSet(size, kept, ids).also { it.build(maxNodes) }
        }
    }
}

/** Formatting helpers shared by the screen and its tests. */
object MoveOrderFormat {

    /** Group digits so six-figure counts stay readable; fall back to
     *  scientific notation once the value stops being a meaningful integer
     *  (`mo_fmt_count`). */
    fun count(v: Double): String {
        val x = if (v < 0.0) 0.0 else v
        // Locale.ROOT: a comma decimal separator would collide with the digit
        // grouping we add below.
        if (x >= 1e12) return String.format(java.util.Locale.ROOT, "%.3g", x)
        val digits = String.format(java.util.Locale.ROOT, "%.0f", x)
        if (digits.length <= 3) return digits
        val sb = StringBuilder()
        val k = digits.length % 3
        for (i in digits.indices) {
            if (i > 0 && (i - k) % 3 == 0 && i - k >= 0) sb.append(',')
            sb.append(digits[i])
        }
        return sb.toString()
    }

    /** "Which move numbers can this stone be?" — `0x15` → "1·3·5"; more than
     *  three collapse to "min~max" (`mo_fmt_plies`). */
    fun plies(mask: Long): String {
        if (mask == 0L) return ""
        var count = 0
        var first = -1
        var last = -1
        for (p in 0 until 2 * MO_MAX_STONES) {
            if (mask and (1L shl p) != 0L) {
                count++
                last = p
                if (first < 0) first = p
            }
        }
        if (count == 0) return ""
        if (count > 3) return "${first + 1}~${last + 1}"
        return (first..last).filter { mask and (1L shl it) != 0L }
            .joinToString("·") { (it + 1).toString() }
    }

    /** Cell code → "h8" (`mo_cell_name`). */
    fun cellName(cell: Int, size: Int = Move.DEFAULT_SIZE): String =
        "${'a' + cell % size}${size - cell / size}"

    /**
     * 주형 of a 3-ply order, or null (`mo_opening26_cells`). Either the first
     * or the third move must be tengen — different orders through the same
     * stones can realise different openings, which is exactly what the drilled
     * path is meant to reveal.
     */
    fun opening26(m1: Int, m2: Int, m3: Int, size: Int = Move.DEFAULT_SIZE): Int? {
        if (size % 2 == 0 || size != Move.DEFAULT_SIZE) return null
        val ctr = size / 2
        val tengen = ctr * size + ctr
        val other = when (tengen) {
            m1 -> m3
            m3 -> m1
            else -> return null
        }
        val idx = Opening26.classify(
            listOf(
                Move(ctr, ctr),
                Move(m2 % size, m2 / size),
                Move(other % size, other / size),
            ),
        )
        return if (idx == Opening26.NONSTD) null else idx
    }
}
