package dev.gomoku.rapfidroid.domain.rif

import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.Opening26
import dev.gomoku.rapfidroid.core.model.PosKey
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * The opening statistics built on the device from the user's `.rif` — one file,
 * a few seconds (RapfiWeb `core/rif/pack.ts`).
 *
 * The same positions as the desktop's `renju_stats.pack` (prefixes of at most
 * [maxPlies] moves reached by at least [minGames] games — its defaults 20 and 2),
 * in another shape:
 *
 *  - positions are keyed by the 64-bit canonical hash ([ZobristScanner]) and
 *    kept **sorted**, so a lookup is a binary search over a `LongArray`;
 *  - a position does not carry its game ids (most of the desktop pack's size):
 *    the game list is recounted from the move column on demand, tens of ms;
 *  - the rankings' rows are the game columns themselves.
 *
 * **RenjuNet-derived.** It lives in app-private storage and never leaves it.
 */
class RenjuPack(
    val size: Int,
    val maxPlies: Int,
    val minGames: Int,
    /** `<database date>` of the file it came from. */
    val date: String,
    val builtAt: Long,
    val players: Array<String>,
    val playerCountry: Array<String>,
    val rules: Array<String>,
    val openings: Array<String>,
    val tournaments: Array<String>,
    val tourYear: IntArray,
    val tourRated: BooleanArray,
    val tourCountry: Array<String>,
    val tourStart: Array<String>,
    val tourEnd: Array<String>,
    // ---- games, one index per game -----------------------------------------
    val gameId: IntArray,
    /** Dense indices into the tables above (not the file's ids); -1 unknown. */
    val gameBlack: IntArray,
    val gameWhite: IntArray,
    val gameRule: IntArray,
    val gameTour: IntArray,
    val gameOpening: IntArray,
    /** 2 black win, 1 draw, 0 white win. */
    val gameRes: ByteArray,
    /** 26주형 of the first three moves, 0..25, or [Opening26] non-standard. */
    val gameO3: ByteArray,
    /** Index into [shapes] of the first five moves, -1 when shorter. */
    val gameK5: IntArray,
    val moveStart: IntArray,
    val moveLen: ByteArray,
    val moves: ByteArray,
    /** One representative line per 5-move shape, `"H8 I9 …"`. */
    val shapes: Array<String>,
    // ---- positions, sorted by hash -----------------------------------------
    val posHash: LongArray,
    val posN: IntArray,
    val posB: IntArray,
    val posD: IntArray,
    val posW: IntArray,
    /** Start of each position's next moves; one longer than the positions. */
    val edgeStart: IntArray,
    /** Next moves in the canonical frame, most played first. */
    val edgeCell: ByteArray,
    val edgeN: IntArray,
    val edgeB: IntArray,
    val edgeD: IntArray,
    val edgeW: IntArray,
) {
    val gameCount: Int get() = gameId.size
    val positionCount: Int get() = posHash.size

    fun cell(game: Int, i: Int): Int = moves[moveStart[game] + i].toInt() and 0xff
    fun length(game: Int): Int = moveLen[game].toInt() and 0xff

    fun gameMoves(game: Int): List<Move> = (0 until length(game)).map {
        val c = cell(game, it)
        Move(c % size, c / size)
    }

    // ---- lookup ------------------------------------------------------------

    class Split(val n: Int, val b: Int, val d: Int, val w: Int)

    class NextMove(val move: Move, val n: Int, val b: Int, val d: Int, val w: Int)

    class Stat(val n: Int, val b: Int, val d: Int, val w: Int, val next: List<NextMove>)

    fun findPosition(hash: Long): Int {
        var a = 0
        var b = posHash.size - 1
        while (a <= b) {
            val m = (a + b) ushr 1
            val h = posHash[m]
            when {
                h == hash -> return m
                h < hash -> a = m + 1
                else -> b = m - 1
            }
        }
        return -1
    }

    /**
     * This position's numbers and next moves, most played first, mapped from
     * the canonical frame back onto the board. Null when the pack never saw it.
     */
    fun lookup(line: List<Move>): Stat? {
        if (line.size > maxPlies) return null
        val z = ZobristScanner.of(line, size)
        val i = findPosition(z.hash)
        if (i < 0) return null
        val inv = PosKey.inverse(z.transform)
        val next = (edgeStart[i] until edgeStart[i + 1]).map { k ->
            val c = edgeCell[k].toInt() and 0xff
            NextMove(PosKey.tf(inv, size, c % size, c / size), edgeN[k], edgeB[k], edgeD[k], edgeW[k])
        }
        return Stat(posN[i], posB[i], posD[i], posW[i], next)
    }

    /**
     * Games through this position — the move column scanned in place, so a
     * transposition counts too. [accept] filters (player names); up to [limit]
     * are returned while the count stays exact.
     */
    fun gamesThrough(line: List<Move>, limit: Int, accept: (Int) -> Boolean = { true }): Pair<List<Int>, Int> {
        val n = line.size
        val want = ZobristScanner.of(line, size).hash
        val z = ZobristScanner(size)
        val out = ArrayList<Int>()
        var matched = 0
        for (g in 0 until gameCount) {
            if (length(g) < n) continue
            z.reset()
            for (i in 0 until n) z.push(cell(g, i))
            if (z.canonical().hash != want) continue
            if (!accept(g)) continue
            matched++
            if (out.size < limit) out.add(g)
        }
        return out to matched
    }

    // ---- file --------------------------------------------------------------

    fun write(output: OutputStream) {
        val out = DataOutputStream(output.buffered(1 shl 16))
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeInt(size); out.writeInt(maxPlies); out.writeInt(minGames)
        out.writeUTF(date); out.writeLong(builtAt)
        for (a in listOf(players, playerCountry, rules, openings, tournaments, tourCountry, tourStart, tourEnd, shapes)) {
            out.strings(a)
        }
        out.ints(tourYear)
        out.writeInt(tourRated.size); tourRated.forEach { out.writeBoolean(it) }
        for (a in listOf(gameId, gameBlack, gameWhite, gameRule, gameTour, gameOpening, gameK5, moveStart)) out.ints(a)
        for (a in listOf(gameRes, gameO3, moveLen, moves)) out.bytes(a)
        out.longs(posHash)
        for (a in listOf(posN, posB, posD, posW, edgeStart)) out.ints(a)
        out.bytes(edgeCell)
        for (a in listOf(edgeN, edgeB, edgeD, edgeW)) out.ints(a)
        out.flush()
    }

    companion object {
        const val MAGIC = 0x524A504B // "RJPK"
        const val VERSION = 1
        const val DEFAULT_MAX_PLIES = 20
        const val DEFAULT_MIN_GAMES = 2
        const val SHAPE_PLIES = 5
        private const val NO_NEXT = 255

        fun read(input: InputStream): RenjuPack? {
            val inp = DataInputStream(input.buffered(1 shl 16))
            if (inp.readInt() != MAGIC || inp.readInt() != VERSION) return null
            val size = inp.readInt(); val maxPlies = inp.readInt(); val minGames = inp.readInt()
            val date = inp.readUTF(); val builtAt = inp.readLong()
            val s = Array(9) { inp.strings() }
            val tourYear = inp.ints()
            val rated = BooleanArray(inp.readInt()) { inp.readBoolean() }
            val gi = Array(8) { inp.ints() }
            val gb = Array(4) { inp.bytes() }
            val posHash = inp.longs()
            val pi = Array(5) { inp.ints() }
            val edgeCell = inp.bytes()
            val ei = Array(4) { inp.ints() }
            return RenjuPack(
                size, maxPlies, minGames, date, builtAt,
                players = s[0], playerCountry = s[1], rules = s[2], openings = s[3], tournaments = s[4],
                tourYear = tourYear, tourRated = rated, tourCountry = s[5], tourStart = s[6], tourEnd = s[7],
                gameId = gi[0], gameBlack = gi[1], gameWhite = gi[2], gameRule = gi[3], gameTour = gi[4],
                gameOpening = gi[5], gameRes = gb[0], gameO3 = gb[1], gameK5 = gi[6], moveStart = gi[7],
                moveLen = gb[2], moves = gb[3], shapes = s[8],
                posHash = posHash, posN = pi[0], posB = pi[1], posD = pi[2], posW = pi[3], edgeStart = pi[4],
                edgeCell = edgeCell, edgeN = ei[0], edgeB = ei[1], edgeD = ei[2], edgeW = ei[3],
            )
        }

        private fun DataOutputStream.strings(a: Array<String>) {
            writeInt(a.size)
            a.forEach { writeUTF(it.take(10_000)) }
        }

        private fun DataInputStream.strings(): Array<String> = Array(readInt()) { readUTF() }

        private fun DataOutputStream.ints(a: IntArray) {
            writeInt(a.size)
            val bb = ByteBuffer.allocate(a.size * 4)
            bb.asIntBuffer().put(a)
            write(bb.array())
        }

        private fun DataInputStream.ints(): IntArray {
            val n = readInt()
            val raw = ByteArray(n * 4)
            readFully(raw)
            return IntArray(n).also { ByteBuffer.wrap(raw).asIntBuffer().get(it) }
        }

        private fun DataOutputStream.longs(a: LongArray) {
            writeInt(a.size)
            val bb = ByteBuffer.allocate(a.size * 8)
            bb.asLongBuffer().put(a)
            write(bb.array())
        }

        private fun DataInputStream.longs(): LongArray {
            val n = readInt()
            val raw = ByteArray(n * 8)
            readFully(raw)
            return LongArray(n).also { ByteBuffer.wrap(raw).asLongBuffer().get(it) }
        }

        private fun DataOutputStream.bytes(a: ByteArray) {
            writeInt(a.size)
            write(a)
        }

        private fun DataInputStream.bytes(): ByteArray = ByteArray(readInt()).also { readFully(it) }

        // ---- build ---------------------------------------------------------

        /** Where a build has got to: [phase] 0 index, 1 sort, 2 count; [fraction] of it. */
        fun interface Progress {
            fun onStep(phase: Int, fraction: Float)
        }

        /**
         * Parsed `.rif` → pack, sort-based: no hash table, only primitive arrays.
         *
         *  1. one row per prefix position: (hash, folded next move, result)
         *  2. LSD radix sort by (hash, next)
         *  3. one pass counting positions and their next moves; positions seen
         *     in fewer than [minGames] games are dropped
         *
         * Positions with different stone counts are never equal, so the prefixes
         * are split into ply ranges counted separately and merged at the end —
         * the work arrays shrink by the number of ranges.
         */
        fun build(
            t: RifTables,
            size: Int = Move.DEFAULT_SIZE,
            maxPlies: Int = DEFAULT_MAX_PLIES,
            minGames: Int = DEFAULT_MIN_GAMES,
            partitions: Int = 2,
            progress: Progress = Progress { _, _ -> },
        ): RenjuPack {
            val g = t.games
            val count = g.count

            // ---- tables with dense indices --------------------------------
            val players = ArrayList<String>()
            val playerCountry = ArrayList<String>()
            val playerIdx = HashMap<Int, Int>()
            fun pidx(id: Int) = playerIdx.getOrPut(id) {
                val p = t.players[id]
                players += p?.display ?: "?"
                playerCountry += p?.let { t.countries[it.country] }.orEmpty()
                players.size - 1
            }
            val rules = ArrayList<String>()
            val ruleIdx = HashMap<Int, Int>()
            fun ridx(id: Int) = ruleIdx.getOrPut(id) { rules += t.rules[id] ?: "?"; rules.size - 1 }
            val openings = ArrayList<String>()
            val openIdx = HashMap<Int, Int>()
            fun oidx(id: Int) = if (id < 0 || id !in t.openings) -1 else openIdx.getOrPut(id) {
                openings += t.openings[id].orEmpty(); openings.size - 1
            }
            val tours = ArrayList<String>()
            val tourYear = ArrayList<Int>()
            val tourRated = ArrayList<Boolean>()
            val tourCountry = ArrayList<String>()
            val tourStart = ArrayList<String>()
            val tourEnd = ArrayList<String>()
            val tourIdx = HashMap<Int, Int>()
            fun tidx(id: Int) = tourIdx.getOrPut(id) {
                val tour = t.tournaments[id]
                tours += tour?.name.orEmpty()
                tourYear += tour?.year ?: 0
                tourRated += (tour?.rated ?: 0) == 1
                tourCountry += tour?.let { t.countries[it.country] }.orEmpty()
                tourStart += tour?.start.orEmpty()
                tourEnd += tour?.end.orEmpty()
                tours.size - 1
            }

            val black = IntArray(count)
            val white = IntArray(count)
            val rule = IntArray(count)
            val tour = IntArray(count)
            val opening = IntArray(count)
            val o3 = ByteArray(count)
            val k5 = IntArray(count)
            val shapes = ArrayList<String>()
            val shapeIdx = HashMap<Long, Int>()
            val z = ZobristScanner(size)
            for (i in 0 until count) {
                black[i] = pidx(g.black[i])
                white[i] = pidx(g.white[i])
                rule[i] = ridx(g.rule[i])
                tour[i] = tidx(g.tournament[i])
                opening[i] = oidx(g.opening[i])
                val len = g.length(i)
                val first = (0 until minOf(len, SHAPE_PLIES)).map { k -> g.cell(i, k).let { Move(it % size, it / size) } }
                o3[i] = Opening26.classify(first.take(3)).toByte()
                k5[i] = if (len >= SHAPE_PLIES) {
                    z.reset()
                    first.forEach { z.push(it.y * size + it.x) }
                    shapeIdx.getOrPut(z.canonical().hash) {
                        shapes += first.joinToString(" ") { it.label(size) }
                        shapes.size - 1
                    }
                } else {
                    -1
                }
            }

            // ---- per ply range: prefix rows, sort, count -------------------
            val ranges = plyRanges(maxPlies, partitions.coerceAtLeast(1))
            val sizes = ranges.map { (a, b) ->
                var e = 0L
                for (i in 0 until count) {
                    val l = minOf(g.length(i), maxPlies)
                    if (l >= a) e += minOf(l, b) - a + 1
                }
                e.toInt()
            }
            val cap = sizes.maxOrNull() ?: 0
            val hashA = LongArray(cap)
            val payA = IntArray(cap)
            val hashB = LongArray(cap)
            val payB = IntArray(cap)
            val parts = ArrayList<Part>()
            ranges.forEachIndexed { pi, (a, b) ->
                var e = 0
                val tick = maxOf(1, count / 50)
                for (i in 0 until count) {
                    val l = minOf(g.length(i), maxPlies)
                    if (l < a) continue
                    val top = minOf(l, b)
                    val res = g.res[i].toInt()
                    z.reset()
                    for (ply in 0..top) {
                        val c = if (ply < l) g.cell(i, ply) else -1
                        if (ply >= a) {
                            z.canonical()
                            hashA[e] = z.hash
                            val next = if (c >= 0) foldCellFast(z.stabiliser, c, size) else NO_NEXT
                            payA[e] = (next shl 2) or res
                            e++
                        }
                        if (c >= 0 && ply < top) z.push(c)
                    }
                    if (i % tick == 0) progress.onStep(0, (pi + i.toFloat() / count) / ranges.size)
                }
                progress.onStep(1, pi.toFloat() / ranges.size)
                val sorted = radixSort(hashA, payA, hashB, payB, e)
                progress.onStep(2, pi.toFloat() / ranges.size)
                parts += countSorted(sorted.first, sorted.second, e, minGames)
            }
            val merged = merge(parts)
            progress.onStep(2, 1f)

            return RenjuPack(
                size = size, maxPlies = maxPlies, minGames = minGames, date = t.date,
                builtAt = System.currentTimeMillis(),
                players = players.toTypedArray(), playerCountry = playerCountry.toTypedArray(),
                rules = rules.toTypedArray(), openings = openings.toTypedArray(),
                tournaments = tours.toTypedArray(), tourYear = tourYear.toIntArray(),
                tourRated = tourRated.toBooleanArray(), tourCountry = tourCountry.toTypedArray(),
                tourStart = tourStart.toTypedArray(), tourEnd = tourEnd.toTypedArray(),
                gameId = g.id, gameBlack = black, gameWhite = white, gameRule = rule, gameTour = tour,
                gameOpening = opening, gameRes = g.res, gameO3 = o3, gameK5 = k5,
                moveStart = g.moveStart, moveLen = g.moveLen, moves = g.moves,
                shapes = shapes.toTypedArray(),
                posHash = merged.hash, posN = merged.n, posB = merged.b, posD = merged.d, posW = merged.w,
                edgeStart = merged.edgeStart, edgeCell = merged.eCell, edgeN = merged.eN, edgeB = merged.eB,
                edgeD = merged.eD, edgeW = merged.eW,
            )
        }

        private fun foldCellFast(stabiliser: Int, cell: Int, size: Int): Int =
            if (stabiliser == 1) cell else ZobristScanner.foldCell(stabiliser, cell, size)

        /** 0..maxPlies split into [p] ranges, ends included. */
        internal fun plyRanges(maxPlies: Int, p: Int): List<Pair<Int, Int>> {
            val n = minOf(p, maxPlies + 1)
            return (0 until n).map { k ->
                val a = Math.round(k * (maxPlies + 1).toDouble() / n).toInt()
                val b = Math.round((k + 1) * (maxPlies + 1).toDouble() / n).toInt() - 1
                a to b
            }
        }

        /**
         * LSD radix sort of (hash, payload) by hash then by the next move: the
         * next move first (stable), then the hash in four 16-bit digits, the top
         * one with its sign bit flipped so the order is signed `Long` order.
         * Returns whichever pair of buffers holds the result.
         */
        internal fun radixSort(
            hashA: LongArray, payA: IntArray, hashB: LongArray, payB: IntArray, n: Int,
        ): Pair<LongArray, IntArray> {
            var sh = hashA
            var sp = payA
            var dh = hashB
            var dp = payB
            val counts = IntArray(1 shl 16)
            val digits = IntArray(n)
            for (pass in 0 until 5) {
                counts.fill(0)
                when (pass) {
                    0 -> for (i in 0 until n) digits[i] = (sp[i] ushr 2) and 0xff
                    4 -> for (i in 0 until n) digits[i] = ((sh[i] ushr 48).toInt() xor 0x8000) and 0xffff
                    else -> {
                        val shift = 16 * (pass - 1)
                        for (i in 0 until n) digits[i] = (sh[i] ushr shift).toInt() and 0xffff
                    }
                }
                for (i in 0 until n) counts[digits[i]]++
                var sum = 0
                for (d in counts.indices) {
                    val c = counts[d]
                    counts[d] = sum
                    sum += c
                }
                for (i in 0 until n) {
                    val at = counts[digits[i]]++
                    dh[at] = sh[i]
                    dp[at] = sp[i]
                }
                val th = sh
                sh = dh
                dh = th
                val tp = sp
                sp = dp
                dp = tp
            }
            return sh to sp
        }

        internal class Part(
            val hash: LongArray, val n: IntArray, val b: IntArray, val d: IntArray, val w: IntArray,
            val edgeStart: IntArray, val eCell: ByteArray, val eN: IntArray, val eB: IntArray,
            val eD: IntArray, val eW: IntArray,
        )

        private fun countSorted(hash: LongArray, pay: IntArray, entries: Int, minGames: Int): Part {
            // First pass sizes the arrays, the second fills them.
            var nPos = 0
            var nEdge = 0
            run {
                var i = 0
                while (i < entries) {
                    var j = i
                    while (j < entries && hash[j] == hash[i]) j++
                    if (j - i >= minGames) {
                        nPos++
                        var k = i
                        while (k < j) {
                            val nx = pay[k] ushr 2
                            while (k < j && pay[k] ushr 2 == nx) k++
                            if (nx != NO_NEXT) nEdge++
                        }
                    }
                    i = j
                }
            }
            val out = Part(
                LongArray(nPos), IntArray(nPos), IntArray(nPos), IntArray(nPos), IntArray(nPos),
                IntArray(nPos + 1), ByteArray(nEdge), IntArray(nEdge), IntArray(nEdge), IntArray(nEdge), IntArray(nEdge),
            )
            var p = 0
            var e = 0
            var i = 0
            while (i < entries) {
                var j = i
                while (j < entries && hash[j] == hash[i]) j++
                if (j - i >= minGames) {
                    out.hash[p] = hash[i]
                    out.edgeStart[p] = e
                    var pb = 0; var pd = 0; var pw = 0
                    val from = e
                    var k = i
                    while (k < j) {
                        val nx = pay[k] ushr 2
                        var kb = 0; var kd = 0; var kw = 0
                        var m = k
                        while (m < j && pay[m] ushr 2 == nx) {
                            when (pay[m] and 3) {
                                RifStreamParser.RES_BLACK -> kb++
                                RifStreamParser.RES_DRAW -> kd++
                                RifStreamParser.RES_WHITE -> kw++
                            }
                            m++
                        }
                        pb += kb; pd += kd; pw += kw
                        if (nx != NO_NEXT) {
                            out.eCell[e] = nx.toByte()
                            out.eN[e] = m - k
                            out.eB[e] = kb; out.eD[e] = kd; out.eW[e] = kw
                            e++
                        }
                        k = m
                    }
                    sortEdges(out, from, e)
                    out.n[p] = j - i
                    out.b[p] = pb; out.d[p] = pd; out.w[p] = pw
                    p++
                }
                i = j
            }
            out.edgeStart[nPos] = nEdge
            return out
        }

        /** One position's next moves, most played first, then by cell; a few rows only. */
        private fun sortEdges(o: Part, from: Int, to: Int) {
            for (i in from + 1 until to) {
                val c = o.eCell[i]; val n = o.eN[i]; val b = o.eB[i]; val d = o.eD[i]; val w = o.eW[i]
                var j = i - 1
                while (j >= from && (o.eN[j] < n || (o.eN[j] == n && (o.eCell[j].toInt() and 0xff) > (c.toInt() and 0xff)))) {
                    o.eCell[j + 1] = o.eCell[j]; o.eN[j + 1] = o.eN[j]; o.eB[j + 1] = o.eB[j]
                    o.eD[j + 1] = o.eD[j]; o.eW[j + 1] = o.eW[j]
                    j--
                }
                o.eCell[j + 1] = c; o.eN[j + 1] = n; o.eB[j + 1] = b; o.eD[j + 1] = d; o.eW[j + 1] = w
            }
        }

        /** Ranges never share a position (different stone counts): merge by hash. */
        private fun merge(parts: List<Part>): Part {
            if (parts.size == 1) return parts[0]
            val nPos = parts.sumOf { it.hash.size }
            val nEdge = parts.sumOf { it.eCell.size }
            val out = Part(
                LongArray(nPos), IntArray(nPos), IntArray(nPos), IntArray(nPos), IntArray(nPos),
                IntArray(nPos + 1), ByteArray(nEdge), IntArray(nEdge), IntArray(nEdge), IntArray(nEdge), IntArray(nEdge),
            )
            val head = IntArray(parts.size)
            var e = 0
            for (k in 0 until nPos) {
                var best = -1
                for (q in parts.indices) {
                    val i = head[q]
                    if (i >= parts[q].hash.size) continue
                    if (best < 0 || parts[q].hash[i] < parts[best].hash[head[best]]) best = q
                }
                val src = parts[best]
                val i = head[best]++
                out.hash[k] = src.hash[i]; out.n[k] = src.n[i]; out.b[k] = src.b[i]
                out.d[k] = src.d[i]; out.w[k] = src.w[i]
                out.edgeStart[k] = e
                for (x in src.edgeStart[i] until src.edgeStart[i + 1]) {
                    out.eCell[e] = src.eCell[x]; out.eN[e] = src.eN[x]; out.eB[e] = src.eB[x]
                    out.eD[e] = src.eD[x]; out.eW[e] = src.eW[x]
                    e++
                }
            }
            out.edgeStart[nPos] = e
            return out
        }
    }
}
