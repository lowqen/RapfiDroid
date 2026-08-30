package dev.gomoku.rapfidroid.domain.rif

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Writes `renju_stats.pack` and `renju_games.pack` — the port of
 * `rifdb/rif_pack.py`, whose docstring is the format spec.
 *
 * The point of matching the PC's bytes rather than inventing a device format:
 * the reader already exists, is already tested against packs the PC built, and
 * is already what the desktop's C reader mirrors. A phone-built pack that the
 * desktop can also open costs nothing extra and keeps one format instead of two.
 */
class PackWriter(private val boardSize: Int = 15) {

    /**
     * `renju_stats.pack`. [date] is the build stamp as `YYYYMMDD`, which the
     * explorer shows so a user can tell how old their data is.
     */
    fun writeStats(out: OutputStream, aggregate: Aggregate, date: Int) {
        val positions = aggregate.positions
        // Records first: the index stores offsets into this block, and a record
        // at offset 0 could not be told from an empty slot — hence the 8-byte pad.
        val records = ByteArrayOutputStream(1 shl 20)
        val sink = LeSink(records)
        sink.bytes(ByteArray(8))
        val offsets = IntArray(positions.size)
        for ((i, pos) in positions.withIndex()) {
            offsets[i] = records.size()
            val key = pos.key.toByteArray(Charsets.US_ASCII)
            sink.u16(key.size)
            sink.bytes(key)
            sink.u32(pos.games)
            sink.u32(pos.blackWins)
            sink.u32(pos.draws)
            sink.u32(pos.whiteWins)
            // One byte for the count, so a position with 256 distinct
            // continuations cannot be written. On a 15x15 board with the moves
            // merged by symmetry that cannot arise; refuse rather than truncate.
            require(pos.next.size < 256) { "position ${pos.key} has ${pos.next.size} continuations" }
            sink.u8(pos.next.size)
            for (n in pos.next) {
                sink.u8(n.x)
                sink.u8(n.y)
                sink.u32(n.games)
                sink.u32(n.blackWins)
                sink.u32(n.draws)
                sink.u32(n.whiteWins)
            }
            sink.u32(pos.gameIds.size)
            for (id in pos.gameIds) sink.u32(id)
        }

        var slots = 1
        while (slots < positions.size / LOAD_FACTOR) slots = slots shl 1
        val hashes = LongArray(slots)
        val slotOffsets = IntArray(slots)
        val mask = slots - 1
        for ((i, pos) in positions.withIndex()) {
            val h = Fnv.hash64(pos.key)
            var s = (h and mask.toLong()).toInt()
            while (slotOffsets[s] != 0) s = (s + 1) and mask
            hashes[s] = h
            slotOffsets[s] = offsets[i]
        }

        val header = LeSink(out)
        header.bytes(STATS_MAGIC)
        header.u32(VERSION)
        header.u32(positions.size)
        header.u32(slots)
        header.u32(aggregate.totalGames)
        header.u32(aggregate.maxPlies)
        header.u32(aggregate.minGames)
        header.u32(date)
        header.bytes(ByteArray(HEADER_SIZE - 32))
        for (s in 0 until slots) {
            header.u64(hashes[s])
            header.u32(slotOffsets[s])
        }
        records.writeTo(out)
    }

    /** `renju_games.pack`. */
    fun writeGames(out: OutputStream, db: RifDatabase) {
        val pool = StringPool()
        pool.add("?")   // offset 0 is "unknown", which is what a missing id reads as

        val games = db.games.sortedBy { it.id.toLong() and 0xFFFFFFFFL }
        val section = ByteArrayOutputStream(1 shl 20)
        val sink = LeSink(section)
        val index = ArrayList<IntArray>(games.size)
        for (game in games) {
            val tour = db.tournaments[game.tournament]
            val black = db.players[game.black]
            val white = db.players[game.white]
            index += intArrayOf(game.id, section.size())
            sink.u32(game.id)
            sink.u16(minOf(tour?.year ?: 0, 0xFFFF))
            sink.u8(game.resultIndex)
            sink.u8(game.rule and 0xff)
            sink.u8(game.opening and 0xff)
            sink.u8((tour?.rated ?: 0) and 1)
            sink.u32(pool.add(black?.display ?: "?"))
            sink.u32(pool.add(white?.display ?: "?"))
            sink.u32(pool.add(tour?.name ?: "?"))
            sink.u32(pool.add(game.round))
            sink.u32(pool.add(game.swap))
            sink.u32(pool.add(game.alt))
            sink.u32(pool.add(game.info))
            sink.u32(pool.add(db.countryOf(black?.country)))
            sink.u32(pool.add(db.countryOf(white?.country)))
            sink.u32(pool.add(tour?.start ?: ""))
            sink.u32(pool.add(tour?.end ?: ""))
            sink.u32(pool.add(db.countryOf(tour?.country)))
            // One byte for the move count: a 15x15 board holds 225 stones.
            val cells = game.cells
            sink.u8(minOf(cells.size, 255))
            for (i in 0 until minOf(cells.size, 255)) sink.u8(cells[i])
        }

        val openings = db.openings.map {
            intArrayOf(it.id, pool.add(it.abbr.ifEmpty { "?" }), pool.add(it.name.ifEmpty { "?" }))
        }
        val rules = db.rules.map { intArrayOf(it.id, pool.add(it.name.ifEmpty { "?" })) }

        val gameSecOff = HEADER_SIZE
        val idxOff = gameSecOff + section.size()
        val openTblOff = idxOff + 8 * index.size
        val ruleTblOff = openTblOff + 4 + 9 * openings.size
        val poolOff = ruleTblOff + 4 + 5 * rules.size

        val header = LeSink(out)
        header.bytes(GAMES_MAGIC)
        header.u32(VERSION)
        header.u32(games.size)
        header.u32(gameSecOff)
        header.u32(idxOff)
        header.u32(openTblOff)
        header.u32(ruleTblOff)
        header.u32(poolOff)
        header.bytes(ByteArray(HEADER_SIZE - 32))
        section.writeTo(out)
        for ((id, off) in index.map { it[0] to it[1] }) {
            header.u32(id)
            header.u32(gameSecOff + off)
        }
        header.u32(openings.size)
        for (o in openings) {
            header.u8(o[0] and 0xff)
            header.u32(o[1])
            header.u32(o[2])
        }
        header.u32(rules.size)
        for (r in rules) {
            header.u8(r[0] and 0xff)
            header.u32(r[1])
        }
        pool.writeTo(out)
    }

    /** Deduplicating NUL-terminated UTF-8 pool; [add] returns the offset. */
    private class StringPool {
        private val buffer = ByteArrayOutputStream(1 shl 16)
        private val offsets = HashMap<String, Int>()

        fun add(s: String): Int = offsets.getOrPut(s) {
            val at = buffer.size()
            buffer.write(s.toByteArray(Charsets.UTF_8))
            buffer.write(0)
            at
        }

        fun writeTo(out: OutputStream) = buffer.writeTo(out)
    }

    /** Little-endian primitives, which is what every field in both packs is. */
    private class LeSink(private val out: OutputStream) {
        fun u8(v: Int) = out.write(v and 0xff)

        fun u16(v: Int) {
            out.write(v and 0xff)
            out.write((v ushr 8) and 0xff)
        }

        fun u32(v: Int) {
            u16(v and 0xffff)
            u16((v ushr 16) and 0xffff)
        }

        fun u64(v: Long) {
            u32(v.toInt())
            u32((v ushr 32).toInt())
        }

        fun bytes(b: ByteArray) = out.write(b)
    }

    private companion object {
        const val HEADER_SIZE = 64
        const val VERSION = 2
        const val LOAD_FACTOR = 0.7
        val STATS_MAGIC = "RJS1".toByteArray(Charsets.US_ASCII)
        val GAMES_MAGIC = "RJG1".toByteArray(Charsets.US_ASCII)
    }
}
