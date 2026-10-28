package dev.gomoku.zenyth.domain.rif

import dev.gomoku.zenyth.core.model.Move
import java.io.InputStream

/**
 * Games as columns — one index per game, moves concatenated as board indices.
 * A few int arrays instead of 150k objects: this is most of why the fast build
 * fits in tens of megabytes (Zenyth Web `GameColumns`).
 */
class GameColumns(rows: Int = 1024, moveBytes: Int = 1 shl 16) {
    var count = 0
        private set
    var id = IntArray(rows)
        private set
    var tournament = IntArray(rows)
        private set
    var rule = IntArray(rows)
        private set
    var black = IntArray(rows)
        private set
    var white = IntArray(rows)
        private set
    var opening = IntArray(rows)
        private set
    /** 2 black win, 1 draw, 0 white win (freq35's code). */
    var res = ByteArray(rows)
        private set
    var moveStart = IntArray(rows)
        private set
    var moveLen = ByteArray(rows)
        private set
    var moves = ByteArray(moveBytes)
        private set
    var movesUsed = 0
        private set

    fun add(
        gameId: Int, tour: Int, ruleId: Int, blackId: Int, whiteId: Int, openingId: Int, result: Int,
        cells: ByteArray, n: Int,
    ) {
        if (count == id.size) grow(id.size * 2)
        if (movesUsed + n > moves.size) moves = moves.copyOf(maxOf(moves.size * 2, movesUsed + n))
        val i = count++
        id[i] = gameId
        tournament[i] = tour
        rule[i] = ruleId
        black[i] = blackId
        white[i] = whiteId
        opening[i] = openingId
        res[i] = result.toByte()
        moveStart[i] = movesUsed
        moveLen[i] = n.toByte()
        System.arraycopy(cells, 0, moves, movesUsed, n)
        movesUsed += n
    }

    /** Moves of game [g] as unsigned board indices. */
    fun cell(g: Int, i: Int): Int = moves[moveStart[g] + i].toInt() and 0xff
    fun length(g: Int): Int = moveLen[g].toInt() and 0xff

    private fun grow(n: Int) {
        id = id.copyOf(n); tournament = tournament.copyOf(n); rule = rule.copyOf(n)
        black = black.copyOf(n); white = white.copyOf(n); opening = opening.copyOf(n)
        res = res.copyOf(n); moveStart = moveStart.copyOf(n); moveLen = moveLen.copyOf(n)
    }

    fun trim(): GameColumns {
        grow(count)
        moves = moves.copyOf(movesUsed)
        return this
    }
}

class RifTables(
    val date: String,
    val countries: Map<Int, String>,
    val rules: Map<Int, String>,
    val openings: Map<Int, String>,
    val players: Map<Int, RifPlayer>,
    val tournaments: Map<Int, RifTournament>,
    val games: GameColumns,
    val skipped: Map<String, Int>,
)

/**
 * RenjuNet `.rif` (XML) read **as bytes**, streaming (Zenyth Web `rif-parse.ts`).
 *
 * The old reader built a String per tag and an object per game: 43 MB of file
 * became hundreds of megabytes of short-lived garbage and minutes of work. Here
 * a fixed working buffer holds a chunk and the tail of the last element; a game
 * tag and its moves are read straight out of the bytes (they are ASCII) into
 * [GameColumns]. Only the ~20k table tags (players, tournaments …) are decoded
 * as UTF-8.
 *
 * Accepted games are the old reader's, which are `rifdb/rif_import.py`'s: an id
 * seen for the first time, a non-empty move list inside the board with **no
 * repeated cell**, and a black result of 0, 0.5 or 1. The rest are counted by
 * reason.
 */
class RifStreamParser(private val size: Int = Move.DEFAULT_SIZE, expectedBytes: Long = 0) {
    private var buf = ByteArray(1 shl 20)
    private var len = 0
    private var date = ""
    private val countries = HashMap<Int, String>()
    private val rules = HashMap<Int, String>()
    private val openings = HashMap<Int, String>()
    private val players = HashMap<Int, RifPlayer>()
    private val tournaments = HashMap<Int, RifTournament>()
    private val games = if (expectedBytes > 0) {
        GameColumns((expectedBytes / 260).toInt().coerceAtLeast(1024), (expectedBytes * 0.105).toInt().coerceAtLeast(1 shl 16))
    } else {
        GameColumns()
    }
    private var seen = ByteArray(1 shl 15)
    private val skipped = HashMap<String, Int>()
    private val cells = ByteArray(size * size)
    private val taken = BooleanArray(size * size)

    val gameCount: Int get() = games.count

    /** Reads the whole stream; [onProgress] gets the bytes read so far. */
    fun parse(input: InputStream, onProgress: (Long) -> Unit = {}): RifTables {
        val chunk = ByteArray(1 shl 16)
        var total = 0L
        var lastReport = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            pushBytes(chunk, n)
            total += n
            if (total - lastReport >= (1 shl 20)) {
                lastReport = total
                onProgress(total)
            }
        }
        onProgress(total)
        return finish()
    }

    fun pushBytes(chunk: ByteArray, n: Int = chunk.size) {
        if (len + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, len + n))
        System.arraycopy(chunk, 0, buf, len, n)
        len += n
        val used = process(false)
        System.arraycopy(buf, used, buf, 0, len - used)
        len -= used
    }

    fun finish(): RifTables {
        process(true)
        len = 0
        return RifTables(date, countries, rules, openings, players, tournaments, games.trim(), HashMap(skipped))
    }

    private fun indexOf(b: Byte, from: Int, to: Int = len): Int {
        var i = from
        while (i < to) {
            if (buf[i] == b) return i
            i++
        }
        return -1
    }

    private fun find(p: ByteArray, from: Int, to: Int = len): Int {
        var i = from
        val first = p[0]
        while (i + p.size <= to) {
            if (buf[i] == first) {
                var k = 1
                while (k < p.size && buf[i + k] == p[k]) k++
                if (k == p.size) return i
            }
            i++
        }
        return -1
    }

    private fun startsAt(p: ByteArray, i: Int): Boolean {
        if (i + p.size > len) return false
        for (k in p.indices) if (buf[i + k] != p[k]) return false
        return true
    }

    /** Consumes every finished element; returns how many bytes were used. */
    private fun process(final: Boolean): Int {
        var pos = 0
        while (true) {
            val lt = indexOf(LT, pos)
            if (lt < 0) return len
            if (!final && lt + LOOKAHEAD > len) return lt
            if (startsAt(P_GAME, lt)) {
                val end = find(P_GAME_END, lt)
                if (end < 0) return if (final) len else lt
                game(lt, end)
                pos = end + P_GAME_END.size
                continue
            }
            if (startsAt(P_COMMENT, lt)) {
                val end = find(P_COMMENT_END, lt)
                if (end < 0) return if (final) len else lt
                pos = end + P_COMMENT_END.size
                continue
            }
            val gt = indexOf(GT, lt)
            if (gt < 0) return if (final) len else lt
            var k = lt + 1
            while (k < gt && buf[k] != SPACE && buf[k] != SLASH && buf[k] != NL && buf[k] != CR) k++
            if (k - lt - 1 in 1..10) {
                val name = String(buf, lt + 1, k - lt - 1, Charsets.US_ASCII)
                if (name in TABLE_TAGS) table(name, String(buf, lt, gt + 1 - lt, Charsets.UTF_8))
            }
            pos = gt + 1
        }
    }

    private fun skip(reason: String) {
        skipped[reason] = (skipped[reason] ?: 0) + 1
    }

    private fun table(name: String, tag: String) {
        val a = attrs(tag)
        val id = a["id"]?.toIntOrNull() ?: -1
        when (name) {
            "database" -> date = a["date"].orEmpty()
            "country" -> if (id >= 0) countries[id] = a["name"] ?: a["abbr"].orEmpty()
            "rule" -> if (id >= 0) rules[id] = a["name"].orEmpty()
            "opening" -> if (id >= 0) openings[id] = a["abbr"].orEmpty()
            "player" -> if (id >= 0) {
                players[id] = RifPlayer(id, a["name"].orEmpty(), a["surname"].orEmpty(), a["country"]?.toIntOrNull() ?: -1)
            }
            "tournament" -> if (id >= 0) {
                tournaments[id] = RifTournament(
                    id = id,
                    name = a["name"].orEmpty(),
                    country = a["country"]?.toIntOrNull() ?: -1,
                    year = a["year"]?.toIntOrNull() ?: 0,
                    start = a["start"].orEmpty(),
                    end = a["end"].orEmpty(),
                    rated = a["rated"]?.toIntOrNull() ?: 0,
                )
            }
        }
    }

    private fun hasSeen(id: Int): Boolean =
        (id shr 3) < seen.size && (seen[id shr 3].toInt() and (1 shl (id and 7))) != 0

    private fun markSeen(id: Int) {
        if ((id shr 3) >= seen.size) seen = seen.copyOf(maxOf(seen.size * 2, (id shr 3) + 1))
        seen[id shr 3] = (seen[id shr 3].toInt() or (1 shl (id and 7))).toByte()
    }

    private fun attrInt(p: ByteArray, from: Int, to: Int, def: Int = -1): Int {
        val at = find(p, from, to)
        if (at < 0) return def
        var i = at + p.size
        var n = 0
        var digits = 0
        var neg = false
        if (i < to && buf[i] == MINUS) {
            neg = true
            i++
        }
        while (i < to && buf[i] != QUOTE) {
            val d = buf[i] - ZERO
            if (d < 0 || d > 9) return def
            n = n * 10 + d
            digits++
            i++
        }
        return if (digits == 0) def else if (neg) -n else n
    }

    /** `bresult`: "1" black, "0" white, "0.5" draw; anything else -1. */
    private fun result(from: Int, to: Int): Int {
        val at = find(A_BRESULT, from, to)
        if (at < 0) return -1
        val i = at + A_BRESULT.size
        fun b(k: Int): Byte = if (i + k < len) buf[i + k] else 0
        if (b(0) == ONE && b(1) == QUOTE) return RES_BLACK
        if (b(0) == ZERO && b(1) == QUOTE) return RES_WHITE
        if (b(0) == ZERO && b(1) == DOT && b(2) == FIVE && b(3) == QUOTE) return RES_DRAW
        return -1
    }

    private fun game(start: Int, end: Int) {
        val tagEnd = indexOf(GT, start, end)
        if (tagEnd < 0) return skip("dup_or_bad_id")
        val id = attrInt(A_ID, start, tagEnd)
        if (id < 0 || hasSeen(id)) return skip("dup_or_bad_id")
        val mOpen = find(P_MOVE, tagEnd, end)
        val mClose = if (mOpen >= 0) find(P_MOVE_END, mOpen, end) else -1
        val n = if (mClose >= 0) moves(mOpen + P_MOVE.size, mClose) else 0
        if (n <= 0) return skip(if (n == 0) "empty_moves" else "bad_moves")
        val res = result(start, tagEnd)
        if (res < 0) return skip("bad_bresult")
        markSeen(id)
        games.add(
            id,
            attrInt(A_TOURNAMENT, start, tagEnd),
            attrInt(A_RULE, start, tagEnd),
            attrInt(A_BLACK, start, tagEnd),
            attrInt(A_WHITE, start, tagEnd),
            attrInt(A_OPENING, start, tagEnd),
            res,
            cells,
            n,
        )
    }

    /** `"h8 h9 …"` in [from, to) into [cells]: the count, 0 if empty, -1 if bad. */
    private fun moves(from: Int, to: Int): Int {
        var n = 0
        var i = from
        var ok = true
        while (i < to) {
            val c = buf[i].toInt() and 0xff
            if (c <= 32) {
                i++
                continue
            }
            val x = (c or 32) - 'a'.code
            var j = i + 1
            var row = 0
            var digits = 0
            while (j < to) {
                val d = buf[j] - ZERO
                if (d < 0 || d > 9) break
                row = row * 10 + d
                digits++
                j++
            }
            if (j < to && (buf[j].toInt() and 0xff) > 32) ok = false
            if (x < 0 || x >= size || digits == 0 || row < 1 || row > size) ok = false
            if (!ok || n >= MAX_MOVES) {
                ok = false
                break
            }
            val cell = (size - row) * size + x
            if (taken[cell]) {
                ok = false
                break
            }
            taken[cell] = true
            cells[n++] = cell.toByte()
            i = j
        }
        for (k in 0 until n) taken[cells[k].toInt() and 0xff] = false
        return if (ok) n else -1
    }

    companion object {
        const val RES_WHITE = 0
        const val RES_DRAW = 1
        const val RES_BLACK = 2

        private const val MAX_MOVES = 225
        private const val LT = '<'.code.toByte()
        private const val GT = '>'.code.toByte()
        private const val QUOTE = '"'.code.toByte()
        private const val SPACE = ' '.code.toByte()
        private const val SLASH = '/'.code.toByte()
        private const val NL = '\n'.code.toByte()
        private const val CR = '\r'.code.toByte()
        private const val MINUS = '-'.code.toByte()
        private const val ZERO = '0'.code.toByte()
        private const val ONE = '1'.code.toByte()
        private const val FIVE = '5'.code.toByte()
        private const val DOT = '.'.code.toByte()
        private const val LOOKAHEAD = 8

        private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)
        private val P_GAME = ascii("<game ")
        private val P_GAME_END = ascii("</game>")
        private val P_COMMENT = ascii("<!--")
        private val P_COMMENT_END = ascii("-->")
        private val P_MOVE = ascii("<move>")
        private val P_MOVE_END = ascii("</move>")
        private val A_ID = ascii(" id=\"")
        private val A_TOURNAMENT = ascii(" tournament=\"")
        private val A_RULE = ascii(" rule=\"")
        private val A_BLACK = ascii(" black=\"")
        private val A_WHITE = ascii(" white=\"")
        private val A_OPENING = ascii(" opening=\"")
        private val A_BRESULT = ascii(" bresult=\"")
        private val TABLE_TAGS = setOf("database", "country", "rule", "opening", "player", "tournament")

        private val ATTR = Regex("([\\w:-]+)=\"([^\"]*)\"")
        private val ENTITY = Regex("&(\\w+);")
        private val ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "apos" to "'", "quot" to "\"", "copy" to "©")

        private fun decode(s: String) =
            if ('&' !in s) s else ENTITY.replace(s) { m -> ENTITIES[m.groupValues[1]] ?: m.value }

        private fun attrs(tag: String): Map<String, String> =
            ATTR.findAll(tag).associate { it.groupValues[1] to decode(it.groupValues[2]) }
    }
}
