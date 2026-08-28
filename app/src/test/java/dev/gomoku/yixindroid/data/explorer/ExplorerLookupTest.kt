package dev.gomoku.yixindroid.data.explorer

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.ExplorerGames
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Opening26
import dev.gomoku.yixindroid.core.model.PosKey
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.RjGame
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Position → key → record → rows, over synthetic packs.
 *
 * The case that matters is the **symmetry round trip**: a stored next move
 * lives in the key's canonical frame, and the same shape reached through a
 * rotated or mirrored line must put that suggestion on the matching board
 * point. Nothing downstream can detect an error here — the numbers stay
 * plausible, they just land on the wrong intersections (계획서 §7 리스크).
 */
class ExplorerLookupTest {

    private val size = 15

    private fun move(label: String) = Move.fromLabel(label, size)!!
    private fun line(vararg labels: String) = Position(size, labels.map { move(it) })

    // ---- minimal pack writers (format spec: rifdb/rif_pack.py) --------------

    private class Blob {
        val out = ByteArrayOutputStream()
        val size: Int get() = out.size()
        fun u8(v: Int) = out.write(v and 0xff)
        fun u16(v: Int) { u8(v); u8(v shr 8) }
        fun u32(v: Int) { u16(v); u16(v ushr 16) }
        fun u64(v: Long) { u32(v.toInt()); u32((v ushr 32).toInt()) }
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun utf8z(s: String) { out.write(s.toByteArray(Charsets.UTF_8)); u8(0) }
        fun pad(to: Int) { while (size < to) u8(0) }
        fun buffer(): ByteBuffer =
            ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
    }

    private data class Next(val cell: Move, val n: Int, val bw: Int, val dw: Int, val ww: Int)
    private data class Row(
        val key: String,
        val n: Int, val bw: Int, val dw: Int, val ww: Int,
        val next: List<Next> = emptyList(),
        val gameIds: List<Int> = emptyList(),
    )

    private fun statsPack(rows: List<Row>): RjStatsPack {
        val rec = Blob()
        rec.pad(8)
        val offsets = rows.map { row ->
            val at = rec.size
            val kb = row.key.toByteArray(Charsets.US_ASCII)
            rec.u16(kb.size); rec.out.write(kb)
            rec.u32(row.n); rec.u32(row.bw); rec.u32(row.dw); rec.u32(row.ww)
            rec.u8(row.next.size)
            for (e in row.next) {
                rec.u8(e.cell.x); rec.u8(e.cell.y)
                rec.u32(e.n); rec.u32(e.bw); rec.u32(e.dw); rec.u32(e.ww)
            }
            rec.u32(row.gameIds.size)
            for (g in row.gameIds) rec.u32(g)
            at
        }
        var nslots = 1
        while (nslots < rows.size / 0.7) nslots *= 2
        val hashes = LongArray(nslots)
        val offs = IntArray(nslots)
        for ((i, row) in rows.withIndex()) {
            val h = RjStatsPack.fnv1a64(row.key)
            var s = (h and (nslots - 1).toLong()).toInt()
            while (offs[s] != 0) s = (s + 1) and (nslots - 1)
            hashes[s] = h; offs[s] = offsets[i]
        }
        val pack = Blob()
        pack.ascii("RJS1")
        pack.u32(2); pack.u32(rows.size); pack.u32(nslots)
        pack.u32(118860); pack.u32(20); pack.u32(2); pack.u32(20260731)
        pack.pad(64)
        for (s in 0 until nslots) { pack.u64(hashes[s]); pack.u32(offs[s]) }
        pack.out.write(rec.out.toByteArray())
        return RjStatsPack.open(pack.buffer())!!
    }

    private fun gamesPack(
        games: List<RjGame>,
        openings: List<Triple<Int, String, String>> = emptyList(),
    ): RjGamesPack {
        val pool = Blob()
        val map = LinkedHashMap<String, Int>()
        fun str(s: String) = map.getOrPut(s) { pool.size.also { pool.utf8z(s) } }
        str("?")
        val sec = Blob()
        val index = ArrayList<Pair<Int, Int>>()
        for (g in games.sortedBy { it.id }) {
            index.add(g.id to sec.size)
            sec.u32(g.id); sec.u16(g.year)
            sec.u8(g.result); sec.u8(g.rule); sec.u8(g.opening); sec.u8(if (g.rated) 1 else 0)
            listOf(
                g.black, g.white, g.tournament, g.round, g.swap, g.alt, g.info,
                g.blackCountry, g.whiteCountry, g.tourStart, g.tourEnd, g.tourCountry,
            ).forEach { sec.u32(str(it)) }
            sec.u8(g.cells.size)
            g.cells.forEach { sec.u8(it) }
        }
        val gameSec = 64
        val idxOff = gameSec + sec.size
        val openOff = idxOff + 8 * index.size
        val ruleOff = openOff + 4 + 9 * openings.size
        val poolOff = ruleOff + 4
        val openEntries = openings.map { Triple(it.first, str(it.second), str(it.third)) }

        val pack = Blob()
        pack.ascii("RJG1")
        pack.u32(2); pack.u32(games.size); pack.u32(gameSec)
        pack.u32(idxOff); pack.u32(openOff); pack.u32(ruleOff); pack.u32(poolOff)
        pack.pad(64)
        pack.out.write(sec.out.toByteArray())
        for ((id, off) in index) { pack.u32(id); pack.u32(gameSec + off) }
        pack.u32(openEntries.size)
        for ((id, a, n) in openEntries) { pack.u8(id); pack.u32(a); pack.u32(n) }
        pack.u32(0)
        pack.out.write(pool.out.toByteArray())
        return RjGamesPack.open(pack.buffer())!!
    }

    private fun game(
        id: Int,
        black: String = "Ivan Ivanov",
        white: String = "Wang Wei",
        opening: Int = 1,
        moves: List<String> = listOf("h8", "i9", "j10"),
    ) = RjGame(
        id = id, year = 2019, result = 0, rule = 1, opening = opening, rated = true,
        black = black, white = white, tournament = "WC", round = "1", swap = "",
        alt = "", info = "", blackCountry = "RUS", whiteCountry = "CHN",
        tourStart = "", tourEnd = "", tourCountry = "",
        cells = moves.map { move(it).let { m -> m.y * 15 + m.x } },
    )

    // ---- the symmetry round trip -------------------------------------------

    /**
     * `h8 i9` canonicalises through transform 1, so the record's next moves are
     * stored in that frame. The row must come back on the board point the user
     * would actually play.
     */
    @Test
    fun storedNextMovesComeBackOnTheRightBoardPoints() {
        val pos = line("h8", "i9")
        val id = PosKey.of(pos.moves, size)
        assertThat(id.transform).isNotEqualTo(0)   // the interesting case
        val onBoard = move("j10")
        val stored = PosKey.tf(id.transform, size, onBoard)

        val stats = statsPack(
            listOf(Row(id.key, 100, 60, 5, 35, next = listOf(Next(stored, 40, 25, 2, 13)))),
        )
        val found = ExplorerLookup.lookup(stats, gamesPack(emptyList()), pos)!!
        assertThat(found.position.next.single().move).isEqualTo(onBoard)
        assertThat(found.position.next.single().games).isEqualTo(40)
    }

    /**
     * The whole point of the key: every rotation and mirror of a line reads the
     * same record, and each gets its suggestion on its own board.
     */
    @Test
    fun everySymmetryOfALineFindsTheSameRecordWithItsOwnBoardPoints() {
        val base = line("h8", "i9", "j10")
        val id = PosKey.of(base.moves, size)
        val stored = PosKey.tf(id.transform, size, move("k11"))
        val stats = statsPack(
            listOf(Row(id.key, 90, 50, 4, 36, next = listOf(Next(stored, 30, 20, 1, 9)))),
        )
        val games = gamesPack(emptyList())

        for (t in 0 until 8) {
            val turned = Position(size, base.moves.map { PosKey.tf(t, size, it) })
            val found = ExplorerLookup.lookup(stats, games, turned)!!
            assertThat(found.position.games).isEqualTo(90)
            // the suggestion follows the board it was asked about
            assertThat(found.position.next.single().move)
                .isEqualTo(PosKey.tf(t, size, move("k11")))
        }
    }

    @Test
    fun anUnknownPositionHasNoRecord() {
        val stats = statsPack(listOf(Row("15", 10, 5, 0, 5)))
        assertThat(ExplorerLookup.lookup(stats, gamesPack(emptyList()), line("h8"))).isNull()
    }

    @Test
    fun theEmptyBoardReadsTheTotalsRecord() {
        val stats = statsPack(listOf(Row("15", 118860, 60000, 8860, 50000)))
        val found = ExplorerLookup.lookup(stats, gamesPack(emptyList()), Position(size))!!
        assertThat(found.position.games).isEqualTo(118860)
        assertThat(found.position.line).isEqualTo("시작 국면")
    }

    @Test
    fun nonStandardBoardSizesAreOutOfScope() {
        val stats = statsPack(listOf(Row("15", 10, 5, 0, 5)))
        assertThat(ExplorerLookup.lookup(stats, gamesPack(emptyList()), Position(20))).isNull()
    }

    @Test
    fun theLineIsShownInPlayedOrder() {
        assertThat(ExplorerLookup.lineText(line("h8", "i9", "f6")))
            .isEqualTo("H8, I9, F6")
    }

    // ---- games pane ---------------------------------------------------------

    @Test
    fun theGameListIsCappedButTheCountStaysExact() {
        val ids = (1..1500).toList()
        val stats = statsPack(listOf(Row("15", 1500, 800, 100, 600, gameIds = ids)))
        val games = gamesPack(ids.map { game(it) })
        val stat = ExplorerLookup.lookup(stats, games, Position(size))!!.stat
        val list = ExplorerLookup.gameList(stat, games, "")
        assertThat(list.matched).isEqualTo(1500)
        assertThat(list.shown).isEqualTo(ExplorerGames.MAX_ROWS)
    }

    @Test
    fun theNameFilterMatchesEitherColourCaseInsensitively() {
        val stats = statsPack(listOf(Row("15", 3, 3, 0, 0, gameIds = listOf(1, 2, 3))))
        val games = gamesPack(
            listOf(
                game(1, black = "Ando Meritee", white = "Wang Wei"),
                game(2, black = "Kazuto Hasegawa", white = "Ando Meritee"),
                game(3, black = "Sushkov Vladimir", white = "Nakamura Shigeru"),
            ),
        )
        val stat = ExplorerLookup.lookup(stats, games, Position(size))!!.stat
        assertThat(ExplorerLookup.gameList(stat, games, "meritee").matched).isEqualTo(2)
        assertThat(ExplorerLookup.gameList(stat, games, "NAKAMURA").matched).isEqualTo(1)
        assertThat(ExplorerLookup.gameList(stat, games, "zzz").matched).isEqualTo(0)
        assertThat(ExplorerLookup.gameList(stat, games, "  ").matched).isEqualTo(3)
    }

    @Test
    fun gameRowsCarryWhatTheListShows() {
        val stats = statsPack(listOf(Row("15", 1, 1, 0, 0, gameIds = listOf(7))))
        val games = gamesPack(listOf(game(7)))
        val stat = ExplorerLookup.lookup(stats, games, Position(size))!!.stat
        val row = ExplorerLookup.gameList(stat, games, "").rows.single()
        assertThat(row.id).isEqualTo(7)
        assertThat(row.year).isEqualTo(2019)
        assertThat(row.black).isEqualTo("Ivan Ivanov")
        assertThat(row.result).isEqualTo("1-0")
        assertThat(row.tournament).isEqualTo("WC")
    }

    // ---- opening labels -----------------------------------------------------

    @Test
    fun rifOpeningAbbreviationsMapOntoThe26Openings() {
        assertThat(ExplorerLookup.openingIndex("d1")).isEqualTo(0)
        assertThat(ExplorerLookup.openingIndex("D13")).isEqualTo(12)
        assertThat(ExplorerLookup.openingIndex("i1")).isEqualTo(13)
        assertThat(ExplorerLookup.openingIndex("i13")).isEqualTo(25)
        assertThat(ExplorerLookup.openingIndex("d14")).isNull()
        assertThat(ExplorerLookup.openingIndex("x3")).isNull()
        assertThat(ExplorerLookup.openingIndex("")).isNull()
    }

    /**
     * Each next-move row carries the name that move would *make*, so browsing a
     * position is browsing the names of the moves out of it, ordered by how
     * often they are played. Names are looked up in the board frame, which is
     * the step that could silently pair a name with its mirror image.
     */
    @Test
    fun nextMovesCarryTheNameTheyWouldMake() {
        val games = gamesPack(listOf(game(1)), openings = emptyList())

        fun namesOf(pos: Position, vararg candidates: String): Map<String, String?> {
            val id = PosKey.of(pos.moves, size)
            val stats = statsPack(listOf(Row(
                id.key, 9, 5, 0, 4,
                next = candidates.map {
                    Next(PosKey.tf(id.transform, size, move(it)), 3, 2, 0, 1)
                },
                gameIds = listOf(1),
            )))
            return ExplorerLookup.lookup(stats, games, pos)!!
                .position.next.associate { it.move.label(size) to it.name }
        }

        // after 천원, the candidates are the two ways to block it
        val blocks = namesOf(line("h8"), "h9", "i9")
        assertThat(blocks["H9"]).isEqualTo(tr("직접막기", "Direct block"))
        assertThat(blocks["I9"]).isEqualTo(tr("간접막기", "Indirect block"))

        // after a 2nd move, the candidates are the 26 openings
        val openings = namesOf(line("h8", "h9"), "h10", "g9")
        assertThat(openings["H10"]).isEqualTo(Opening26.name(0))   // 한성
        assertThat(openings["G9"]).isEqualTo(Opening26.name(3))    // 화월

        // names stop at the 4th move — 렌주 has no per-shape 5수 name
        val fifth = namesOf(line("h8", "h9", "g9", "g8"), "f7")
        assertThat(fifth["F7"]).isNull()
    }

    @Test
    fun anUnmappableAbbreviationFallsBackToTheRifName() {
        val games = gamesPack(
            listOf(game(1, opening = 9)),
            openings = listOf(Triple(9, "x9", "Something Else")),
        )
        assertThat(ExplorerLookup.gameOpeningLabel(games, 9)).isEqualTo("x9 (Something Else)")
        assertThat(ExplorerLookup.gameOpeningLabel(games, 3)).isNull()
    }
}
