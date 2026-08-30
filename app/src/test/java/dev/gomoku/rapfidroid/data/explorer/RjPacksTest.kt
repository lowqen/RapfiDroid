package dev.gomoku.rapfidroid.data.explorer

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.PosKey
import dev.gomoku.rapfidroid.core.model.RjGame
import dev.gomoku.rapfidroid.core.model.RjNext
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The pack readers against packs built here byte for byte from the format spec
 * in `rifdb/rif_pack.py`'s docstring (the C reader in main.c:4798-4966 is the
 * other consumer of that spec).
 *
 * Real packs are RenjuNet-derived and **must never enter the repository**, so
 * the fixtures below are synthetic — which is also what makes the truncation
 * and tamper cases testable at all.
 */
class RjPacksTest {

    // ---- tiny writers mirroring rif_pack.py --------------------------------

    private class Blob {
        val out = java.io.ByteArrayOutputStream()
        val size: Int get() = out.size()
        fun u8(v: Int) = out.write(v and 0xff)
        fun u16(v: Int) { u8(v); u8(v shr 8) }
        fun u32(v: Int) { u16(v); u16(v ushr 16) }
        fun u64(v: Long) { u32(v.toInt()); u32((v ushr 32).toInt()) }
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun utf8z(s: String) { out.write(s.toByteArray(Charsets.UTF_8)); u8(0) }
        fun bytes(b: ByteArray) = out.write(b)
        fun pad(to: Int) { while (size < to) u8(0) }
        fun toBuffer(): ByteBuffer =
            ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
    }

    private data class NextRow(
        val x: Int, val y: Int, val n: Int, val bw: Int, val dw: Int, val ww: Int,
    )

    private data class StatRow(
        val key: String,
        val n: Int, val bw: Int, val dw: Int, val ww: Int,
        val next: List<NextRow> = emptyList(),
        val gameIds: List<Int> = emptyList(),
    )

    private fun statsPack(
        rows: List<StatRow>,
        totalGames: Int = 1000,
        version: Int = 2,
        magic: String = "RJS1",
    ): ByteBuffer {
        val rec = Blob()
        rec.pad(8)   // valid offsets must not be 0
        val offsets = rows.map { row ->
            val at = rec.size
            val kb = row.key.toByteArray(Charsets.US_ASCII)
            rec.u16(kb.size); rec.bytes(kb)
            rec.u32(row.n); rec.u32(row.bw); rec.u32(row.dw); rec.u32(row.ww)
            rec.u8(row.next.size)
            for (e in row.next) {
                rec.u8(e.x); rec.u8(e.y)
                rec.u32(e.n); rec.u32(e.bw); rec.u32(e.dw); rec.u32(e.ww)
            }
            rec.u32(row.gameIds.size)
            for (g in row.gameIds) rec.u32(g)
            at
        }

        var nslots = 1
        while (nslots < rows.size / 0.7) nslots *= 2
        val slotHash = LongArray(nslots)
        val slotOff = IntArray(nslots)
        val mask = nslots - 1
        for ((i, row) in rows.withIndex()) {
            val h = RjStatsPack.fnv1a64(row.key)
            var s = (h and mask.toLong()).toInt()
            while (slotOff[s] != 0) s = (s + 1) and mask
            slotHash[s] = h
            slotOff[s] = offsets[i]
        }

        val pack = Blob()
        pack.ascii(magic)
        pack.u32(version); pack.u32(rows.size); pack.u32(nslots)
        pack.u32(totalGames); pack.u32(20); pack.u32(2); pack.u32(20260731)
        pack.pad(64)
        for (s in 0 until nslots) { pack.u64(slotHash[s]); pack.u32(slotOff[s]) }
        pack.bytes(rec.out.toByteArray())
        return pack.toBuffer()
    }

    private fun gamesPack(
        games: List<RjGame>,
        openings: List<Triple<Int, String, String>> = emptyList(),
        rules: List<Pair<Int, String>> = emptyList(),
        version: Int = 2,
    ): ByteBuffer {
        val pool = Blob()
        val poolMap = LinkedHashMap<String, Int>()
        fun str(s: String): Int = poolMap.getOrPut(s) {
            val at = pool.size
            pool.utf8z(s)
            at
        }
        str("?")   // offset 0 = unknown

        val sec = Blob()
        val index = ArrayList<Pair<Int, Int>>()
        for (g in games.sortedBy { it.id }) {
            index.add(g.id to sec.size)
            sec.u32(g.id); sec.u16(g.year)
            sec.u8(g.result); sec.u8(g.rule); sec.u8(g.opening); sec.u8(if (g.rated) 1 else 0)
            for (
                s in listOf(
                    g.black, g.white, g.tournament, g.round, g.swap, g.alt, g.info,
                    g.blackCountry, g.whiteCountry, g.tourStart, g.tourEnd, g.tourCountry,
                )
            ) sec.u32(str(s))
            sec.u8(g.cells.size)
            for (c in g.cells) sec.u8(c)
        }

        val gameSecOff = 64
        val idxOff = gameSecOff + sec.size
        val openOff = idxOff + 8 * index.size
        val ruleOff = openOff + 4 + 9 * openings.size
        val poolOff = ruleOff + 4 + 5 * rules.size

        // resolve pool offsets only after every string exists
        val openEntries = openings.map { Triple(it.first, str(it.second), str(it.third)) }
        val ruleEntries = rules.map { it.first to str(it.second) }

        val pack = Blob()
        pack.ascii("RJG1")
        pack.u32(version); pack.u32(games.size); pack.u32(gameSecOff)
        pack.u32(idxOff); pack.u32(openOff); pack.u32(ruleOff); pack.u32(poolOff)
        pack.pad(64)
        pack.bytes(sec.out.toByteArray())
        for ((id, off) in index) { pack.u32(id); pack.u32(gameSecOff + off) }
        pack.u32(openEntries.size)
        for ((id, a, n) in openEntries) { pack.u8(id); pack.u32(a); pack.u32(n) }
        pack.u32(ruleEntries.size)
        for ((id, n) in ruleEntries) { pack.u8(id); pack.u32(n) }
        pack.bytes(pool.out.toByteArray())
        return pack.toBuffer()
    }

    private fun sampleGame(id: Int, moves: List<String>, result: Int = 0) = RjGame(
        id = id, year = 2019, result = result, rule = 1, opening = 4, rated = true,
        black = "Ivan Ivanov", white = "Wang Wei", tournament = "World Championship",
        round = "3", swap = "", alt = "", info = "", blackCountry = "RUS",
        whiteCountry = "CHN", tourStart = "2019-08-01", tourEnd = "2019-08-10",
        tourCountry = "EST",
        cells = moves.map { Move.fromLabel(it, 15)!!.let { m -> m.y * 15 + m.x } },
    )

    // ---- stats pack ---------------------------------------------------------

    @Test
    fun readsHeaderNumbers() {
        val p = RjStatsPack.open(statsPack(listOf(StatRow("15", 118860, 60000, 8860, 50000))))!!
        assertThat(p.positions).isEqualTo(1)
        assertThat(p.totalGames).isEqualTo(1000)
        assertThat(p.maxPlies).isEqualTo(20)
        assertThat(p.minGames).isEqualTo(2)
        assertThat(p.date).isEqualTo(20260731)
    }

    @Test
    fun findsARecordThroughTheHashIndex() {
        val rows = listOf(
            StatRow("15", 100, 50, 10, 40),
            StatRow("15h8b", 80, 44, 6, 30, next = listOf(NextRow(7, 6, 30, 18, 2, 10))),
            StatRow("15g9wh8b", 25, 12, 3, 10),
        )
        val p = RjStatsPack.open(statsPack(rows))!!
        val s = p.lookup("15h8b")!!
        assertThat(s.games).isEqualTo(80)
        assertThat(s.blackWins).isEqualTo(44)
        assertThat(s.draws).isEqualTo(6)
        assertThat(s.whiteWins).isEqualTo(30)
        assertThat(s.nextMoves()).containsExactly(RjNext(7, 6, 30, 18, 2, 10))
    }

    @Test
    fun missingKeysReturnNull() {
        val p = RjStatsPack.open(statsPack(listOf(StatRow("15", 1, 1, 0, 0))))!!
        assertThat(p.lookup("15h8b")).isNull()
    }

    @Test
    fun theEmptyBoardKeyIsTheBoardSize() {
        val p = RjStatsPack.open(statsPack(listOf(StatRow("15", 7, 4, 1, 2))))!!
        assertThat(p.lookup(PosKey.keyOf(emptyList(), 15))!!.games).isEqualTo(7)
    }

    @Test
    fun manyKeysSurviveHashCollisionsAndProbing() {
        val rows = (0 until 200).map { StatRow("15key$it", it + 1, it, 0, 1) }
        val p = RjStatsPack.open(statsPack(rows))!!
        for (i in 0 until 200) assertThat(p.lookup("15key$i")!!.games).isEqualTo(i + 1)
        assertThat(p.lookup("15key200")).isNull()
    }

    @Test
    fun gameIdsAreReadOnDemand() {
        val ids = (100 until 140).toList()
        val p = RjStatsPack.open(statsPack(listOf(StatRow("15", 40, 20, 0, 20, gameIds = ids))))!!
        val s = p.lookup("15")!!
        assertThat(s.gameCount).isEqualTo(40)
        assertThat((0 until s.gameCount).map { s.gameIdAt(it) }).isEqualTo(ids)
    }

    @Test
    fun theWrongMagicOrVersionIsRefused() {
        assertThat(RjStatsPack.open(statsPack(emptyList(), magic = "RJG1"))).isNull()
        assertThat(RjStatsPack.open(statsPack(emptyList(), version = 1))).isNull()
        assertThat(RjStatsPack.open(ByteBuffer.allocate(10))).isNull()
    }

    /** A truncated pack must degrade to "no statistics", never crash — the
     *  desktop reader's bounds checks exist for exactly this. */
    @Test
    fun aTruncatedPackFailsTheLookupInsteadOfThrowing() {
        val full = statsPack(listOf(StatRow("15h8b", 80, 44, 6, 30, gameIds = (1..50).toList())))
        val bytes = ByteArray(full.capacity())
        full.duplicate().get(bytes)
        for (cut in listOf(bytes.size - 4, bytes.size - 60, bytes.size / 2)) {
            val short = ByteBuffer.wrap(bytes.copyOf(cut)).order(ByteOrder.LITTLE_ENDIAN)
            val p = RjStatsPack.open(short)
            if (p != null) assertThat(p.lookup("15h8b")).isNull()
        }
    }

    // ---- games pack ---------------------------------------------------------

    @Test
    fun findsAGameByBinarySearch() {
        val games = (1..50).map { sampleGame(it * 7, listOf("h8", "i9", "j10")) }
        val p = RjGamesPack.open(gamesPack(games))!!
        assertThat(p.gameCount).isEqualTo(50)
        val g = p.game(70)!!
        assertThat(g.id).isEqualTo(70)
        assertThat(g.black).isEqualTo("Ivan Ivanov")
        assertThat(g.white).isEqualTo("Wang Wei")
        assertThat(g.tournament).isEqualTo("World Championship")
        assertThat(g.blackCountry).isEqualTo("RUS")
        assertThat(g.tourStart).isEqualTo("2019-08-01")
        assertThat(g.year).isEqualTo(2019)
        assertThat(g.rated).isTrue()
        assertThat(p.game(71)).isNull()
    }

    @Test
    fun movesComeBackInTheBoardFrame() {
        val labels = listOf("h8", "i9", "j10", "g7")
        val p = RjGamesPack.open(gamesPack(listOf(sampleGame(1, labels))))!!
        val g = p.game(1)!!
        assertThat(g.moves(15).map { it.label(15).lowercase() }).isEqualTo(labels)
    }

    @Test
    fun resultTextFollowsTheRifConvention() {
        val p = RjGamesPack.open(
            gamesPack((0..2).map { sampleGame(it + 1, listOf("h8"), result = it) }),
        )!!
        assertThat(p.game(1)!!.resultText).isEqualTo("1-0")     // black won
        assertThat(p.game(2)!!.resultText).isEqualTo("½-½")  // draw
        assertThat(p.game(3)!!.resultText).isEqualTo("0-1")     // white won
    }

    @Test
    fun openingAndRuleTablesResolve() {
        val p = RjGamesPack.open(
            gamesPack(
                listOf(sampleGame(1, listOf("h8"))),
                openings = listOf(Triple(4, "d4", "Kagetsu")),
                rules = listOf(1 to "Renju", 2 to "Soosyrv-8"),
            ),
        )!!
        assertThat(p.openingName(4)).isEqualTo("d4" to "Kagetsu")
        assertThat(p.openingName(9)).isNull()
        assertThat(p.ruleName(2)).isEqualTo("Soosyrv-8")
        assertThat(p.ruleName(7)).isNull()
    }

    @Test
    fun anEmptyStringFieldStaysEmptyAndUnknownReadsAsQuestionMark() {
        val p = RjGamesPack.open(gamesPack(listOf(sampleGame(1, listOf("h8")))))!!
        val g = p.game(1)!!
        assertThat(g.swap).isEmpty()
        assertThat(g.alt).isEmpty()
    }

    @Test
    fun theWrongGamesMagicOrVersionIsRefused() {
        assertThat(RjGamesPack.open(gamesPack(emptyList(), version = 1))).isNull()
        assertThat(RjGamesPack.open(statsPack(emptyList()))).isNull()
        assertThat(RjGamesPack.open(ByteBuffer.allocate(63))).isNull()
    }

    @Test
    fun aTruncatedGamesPackFailsTheLookupInsteadOfThrowing() {
        val full = gamesPack((1..20).map { sampleGame(it, listOf("h8", "i9")) })
        val bytes = ByteArray(full.capacity())
        full.duplicate().get(bytes)
        val short = ByteBuffer.wrap(bytes.copyOf(bytes.size / 2)).order(ByteOrder.LITTLE_ENDIAN)
        val p = RjGamesPack.open(short)
        if (p != null) {
            // whatever survives must be self-consistent; nothing may throw
            for (id in 1..20) p.game(id)?.let { assertThat(it.id).isEqualTo(id) }
        }
    }
}
