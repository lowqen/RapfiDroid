package dev.gomoku.rapfidroid.domain.rif

import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.PosKey

/** One continuation from a position, already merged across symmetries. */
data class NextStat(
    val x: Int,
    val y: Int,
    val games: Int,
    val blackWins: Int,
    val draws: Int,
    val whiteWins: Int,
)

/** One position record, in the order the pack writes it. */
class PosStat(
    val key: String,
    val games: Int,
    val blackWins: Int,
    val draws: Int,
    val whiteWins: Int,
    /** Descending by games, then by the remaining fields — `rif_pack`'s order. */
    val next: List<NextStat>,
    /** Every game through this position, rated first, then newest, then by id. */
    val gameIds: IntArray,
)

class Aggregate(
    val positions: List<PosStat>,
    val totalGames: Int,
    val maxPlies: Int,
    val minGames: Int,
)

/**
 * Turns parsed games into the opening explorer's position statistics — the port
 * of `rifdb/rif_aggregate.py`.
 *
 * Two passes, for memory rather than speed. The long tail of positions seen in
 * a single game is most of the distinct positions and none of the useful ones,
 * so the first pass counts visits by key hash and the second only builds records
 * for keys that clear [minGames]. Doing it in one pass means holding a record
 * for every position anyone ever played.
 *
 * The subtle part is not the counting, it is **naming the continuation**. A move
 * is recorded under the smallest image of itself over the symmetries the
 * *parent* position is invariant under ([PosKey.canonNext]); without that, a
 * symmetric opening splits one continuation into two that never add up.
 */
class PackAggregator(
    private val boardSize: Int = Move.DEFAULT_SIZE,
    val maxPlies: Int = DEFAULT_MAX_PLIES,
    val minGames: Int = DEFAULT_MIN_GAMES,
) {

    fun aggregate(db: RifDatabase, onProgress: Progress = Progress { _, _, _ -> }): Aggregate {
        val games = db.games
        val meta = LongArray(games.size)
        for ((i, g) in games.withIndex()) {
            val t = db.tournaments[g.tournament]
            meta[i] = pack(rated = t?.rated ?: 0, year = t?.year ?: 0, id = g.id)
        }

        val visits = LongIntOpenMap(expected = games.size * 8)
        for ((i, game) in games.withIndex()) {
            forEachPrefixKey(game) { key, _ -> visits.increment(Fnv.hash64(key)) }
            if (i % PROGRESS_EVERY == 0) onProgress.onStep(1, i, games.size)
        }
        onProgress.onStep(1, games.size, games.size)

        val slots = HashMap<String, Int>(1 shl 16)
        val keys = ArrayList<String>()
        val counts = IntBag()
        val nexts = ArrayList<HashMap<Int, IntArray>>()
        val samples = ArrayList<LongBag>()

        for ((i, game) in games.withIndex()) {
            val resultIndex = game.resultIndex
            val sample = meta[i]
            forEachPrefixKey(game) { key, ctx ->
                if (visits.get(Fnv.hash64(key)) < minGames) return@forEachPrefixKey
                val slot = slots.getOrPut(key) {
                    keys += key
                    counts.addFour()
                    nexts += HashMap()
                    samples += LongBag()
                    keys.size - 1
                }
                counts.bump(slot * 4)
                counts.bump(slot * 4 + 1 + resultIndex)
                samples[slot].add(sample)
                val played = ctx.played
                if (played != null) {
                    val rep = PosKey.canonNext(ctx.stabiliser, played, boardSize)
                    val cell = rep.y * boardSize + rep.x
                    val row = nexts[slot].getOrPut(cell) { IntArray(4) }
                    row[0]++
                    row[1 + resultIndex]++
                }
            }
            if (i % PROGRESS_EVERY == 0) onProgress.onStep(2, i, games.size)
        }
        onProgress.onStep(2, games.size, games.size)

        val positions = ArrayList<PosStat>(keys.size)
        for (slot in keys.indices) {
            val next = nexts[slot].map { (cell, v) ->
                NextStat(cell % boardSize, cell / boardSize, v[0], v[1], v[2], v[3])
            }.sortedWith(NEXT_ORDER)
            positions += PosStat(
                key = keys[slot],
                games = counts[slot * 4],
                blackWins = counts[slot * 4 + 1],
                draws = counts[slot * 4 + 2],
                whiteWins = counts[slot * 4 + 3],
                next = next,
                gameIds = samples[slot].sortedDescendingIds(),
            )
        }
        val total = positions.firstOrNull { it.key == PosKey.emptyKey(boardSize) }?.games ?: games.size
        return Aggregate(positions, total, maxPlies, minGames)
    }

    /** Position context handed to the walk: what was played next, and from where. */
    class PrefixContext(var stabiliser: Int = 0, var played: Move? = null)

    /**
     * Every prefix of [game] up to [maxPlies] inclusive — the empty board first,
     * then one position per move played.
     */
    private inline fun forEachPrefixKey(game: RifGame, body: (String, PrefixContext) -> Unit) {
        val moves = ArrayList<Move>(game.cells.size)
        for (cell in game.cells) moves += Move(cell % boardSize, cell / boardSize)
        val top = minOf(moves.size, maxPlies)
        val ctx = PrefixContext()
        for (length in 0..top) {
            val canonical = PosKey.canonical(moves.subList(0, length), boardSize)
            ctx.stabiliser = canonical.stabiliser
            ctx.played = if (length < moves.size && length < maxPlies) moves[length] else null
            body(canonical.key, ctx)
        }
    }

    fun interface Progress {
        /** [pass] is 1 or 2; [done] of [total] games. */
        fun onStep(pass: Int, done: Int, total: Int)
    }

    companion object {
        const val DEFAULT_MAX_PLIES = 20
        const val DEFAULT_MIN_GAMES = 2
        private const val PROGRESS_EVERY = 2_000

        /** `sorted(entries, reverse=True)` over `(n, bwin, dwin, wwin, x, y)`. */
        private val NEXT_ORDER = compareByDescending<NextStat> { it.games }
            .thenByDescending { it.blackWins }
            .thenByDescending { it.draws }
            .thenByDescending { it.whiteWins }
            .thenByDescending { it.x }
            .thenByDescending { it.y }

        /**
         * `(rated, year, id)` in one long so the per-position lists sort with a
         * primitive sort and cost 8 bytes an entry — there are millions of them.
         * Descending order over this is descending over the triple.
         */
        fun pack(rated: Int, year: Int, id: Int): Long =
            ((rated.coerceIn(0, 1).toLong()) shl 48) or
                ((year.coerceIn(0, 0xFFFF).toLong()) shl 32) or
                (id.toLong() and 0xFFFFFFFFL)
    }
}

/** Growable int array; the counts are four per position and there are many. */
internal class IntBag(initial: Int = 1 shl 12) {
    private var data = IntArray(initial)
    private var size = 0

    operator fun get(index: Int): Int = data[index]

    fun bump(index: Int) {
        data[index]++
    }

    fun addFour() {
        if (size + 4 > data.size) data = data.copyOf(data.size * 2)
        size += 4
    }
}

/** Growable long array with a descending sort that yields the low 32 bits. */
internal class LongBag {
    private var data = LongArray(4)
    private var size = 0

    fun add(value: Long) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun sortedDescendingIds(): IntArray {
        val copy = data.copyOf(size)
        copy.sort()
        val out = IntArray(size)
        for (i in 0 until size) out[i] = copy[size - 1 - i].toInt()
        return out
    }
}

/**
 * Open-addressed `long -> int`, because the first pass holds one entry per
 * distinct position and a `HashMap<Long, Int>` would box both halves of every
 * one of the millions of them.
 */
internal class LongIntOpenMap(expected: Int) {
    private var mask: Int
    private var keys: LongArray
    private var values: IntArray
    private var size = 0

    init {
        var capacity = 16
        while (capacity < expected * 2) capacity = capacity shl 1
        mask = capacity - 1
        keys = LongArray(capacity)
        values = IntArray(capacity)
    }

    fun increment(key: Long) {
        // 0 marks a free slot, so the one key that is genuinely 0 is folded
        // into another. It costs one position out of millions a wrong count of
        // one, and buys not carrying an occupancy bitmap.
        val k = if (key == 0L) 1L else key
        var i = index(k)
        while (keys[i] != 0L && keys[i] != k) i = (i + 1) and mask
        if (keys[i] == 0L) {
            keys[i] = k
            values[i] = 1
            size++
            if (size * 10 > keys.size * 7) grow()
        } else {
            values[i]++
        }
    }

    fun get(key: Long): Int {
        val k = if (key == 0L) 1L else key
        var i = index(k)
        while (keys[i] != 0L) {
            if (keys[i] == k) return values[i]
            i = (i + 1) and mask
        }
        return 0
    }

    private fun index(k: Long): Int = ((k xor (k ushr 32)) * -0x61c8864680b583ebL).ushr(40).toInt() and mask

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        val capacity = keys.size shl 1
        mask = capacity - 1
        keys = LongArray(capacity)
        values = IntArray(capacity)
        for (j in oldKeys.indices) {
            val k = oldKeys[j]
            if (k == 0L) continue
            var i = index(k)
            while (keys[i] != 0L) i = (i + 1) and mask
            keys[i] = k
            values[i] = oldValues[j]
        }
    }
}
