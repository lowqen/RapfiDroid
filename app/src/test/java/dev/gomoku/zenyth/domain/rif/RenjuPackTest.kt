package dev.gomoku.zenyth.domain.rif

import com.google.common.truth.Truth.assertThat
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.PosKey
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/** The fast `.rif` path: byte parser, Zobrist keys, sorted pack, file round trip. */
class RenjuPackTest {

    private val rif = """
        <?xml version="1.0"?>
        <database version="1.0" date="2026-01-02">
        <!-- COPYRIGHT INFORMATION - RenjuNet &copy; 2001 -->
        <countries>
        <country id="1" name="Estonia" abbr="EST" />
        <country id="4" name="Japan" abbr="JPN" reversed="1" />
        </countries>
        <rules>
        <rule id="1" name="RIF" category="1">Renju International Federation</rule>
        <rule id="2" name="Yamaguchi" category="1" />
        </rules>
        <openings>
        <opening id="21" abbr="D1" name="Ura-getsu" />
        <opening id="24" abbr="I2" name="Hana-getsu" />
        </openings>
        <players>
        <player id="1" name="Ando" surname="Meritee" country="1" />
        <player id="2" name="Kazuto" surname="Hasegawa" country="4" />
        <player id="3" surname="Solo" country="1" />
        </players>
        <tournaments>
        <tournament id="1" name="World Championship" country="1" year="2005"
                    start="2005-08-01" end="2005-08-07" rule="1" rated="1" />
        <tournament id="2" name="Friendly &amp; casual" country="4" year="2019" rated="0" />
        </tournaments>
        <games>
        <game id="1" tournament="1" round="F2" rule="1" black="1" white="2"
              bresult="1" opening="21" alt="i8" swap="R">
        <move>h8 h9 h7</move>
        </game>
        <game id="2" tournament="1" round="F3" rule="1" black="2" white="1"
              bresult="0" opening="21" alt="" swap="">
        <move>h8 h7 h9</move>
        <info>a note</info>
        </game>
        <game id="3" tournament="2" round="" rule="2" black="3" white="1" bresult="0.5"
              opening="24">
        <move>h8 i9</move>
        </game>
        <game id="3" tournament="2" bresult="1"><move>h8</move></game>
        <game id="4" tournament="2" bresult="1"><move>h8 zz9</move></game>
        <game id="5" tournament="2" bresult="x"><move>h8 i9</move></game>
        <game id="6" tournament="2" bresult="1"><move>h8 h8</move></game>
        </games>
        </database>
    """.trimIndent()

    private fun tables(): RifTables {
        val p = RifStreamParser()
        // Fed in small chunks, so elements straddle the chunk boundaries.
        val bytes = rif.toByteArray(Charsets.UTF_8)
        var i = 0
        while (i < bytes.size) {
            val n = minOf(17, bytes.size - i)
            p.pushBytes(bytes.copyOfRange(i, i + n), n)
            i += n
        }
        return p.finish()
    }

    private fun m(label: String) = Move.fromLabel(label)!!

    @Test
    fun `parses the tables and keeps only replayable games`() {
        val t = tables()
        assertThat(t.games.id.toList()).containsExactly(1, 2, 3).inOrder()
        assertThat(t.date).isEqualTo("2026-01-02")
        assertThat(t.countries[4]).isEqualTo("Japan")
        assertThat(t.players[1]!!.display).isEqualTo("Ando Meritee")
        assertThat(t.tournaments[2]!!.name).isEqualTo("Friendly & casual")
        assertThat(t.openings[21]).isEqualTo("D1")
        assertThat(t.skipped["dup_or_bad_id"]).isEqualTo(1)
        assertThat(t.skipped["bad_moves"]).isEqualTo(2)
        assertThat(t.skipped["bad_bresult"]).isEqualTo(1)
        assertThat(t.games.cell(0, 0)).isEqualTo(7 * 15 + 7)
        assertThat(t.games.res[0].toInt()).isEqualTo(RifStreamParser.RES_BLACK)
        assertThat(t.games.res[2].toInt()).isEqualTo(RifStreamParser.RES_DRAW)
    }

    @Test
    fun `equal hashes exactly when equal keys`() {
        val rnd = Random(7)
        val lines = (0 until 400).map {
            val n = rnd.nextInt(0, 9)
            val seen = HashSet<Move>()
            val out = ArrayList<Move>()
            while (out.size < n) {
                val mv = Move(rnd.nextInt(5, 10), rnd.nextInt(5, 10))
                if (seen.add(mv)) out.add(mv)
            }
            out
        }
        // Every symmetry of a line must land on the same hash.
        for (line in lines.take(60)) {
            val h = ZobristScanner.of(line).hash
            for (t in 0 until 8) {
                val image = line.map { PosKey.tf(t, 15, it) }
                assertThat(ZobristScanner.of(image).hash).isEqualTo(h)
            }
        }
        val byKey = HashMap<String, Long>()
        val byHash = HashMap<Long, String>()
        for (line in lines) {
            val key = PosKey.of(line).key
            val h = ZobristScanner.of(line).hash
            byKey[key]?.let { assertThat(it).isEqualTo(h) }
            byHash[h]?.let { assertThat(it).isEqualTo(key) }
            byKey[key] = h
            byHash[h] = key
        }
    }

    @Test
    fun `counts positions and folds symmetric continuations`() {
        val pack = RenjuPack.build(tables())
        val empty = pack.lookup(emptyList())!!
        assertThat(empty.n).isEqualTo(3)
        assertThat(listOf(empty.b, empty.d, empty.w)).containsExactly(1, 1, 1).inOrder()

        val h8 = pack.lookup(listOf(m("H8")))!!
        assertThat(h8.n).isEqualTo(3)
        // h9 and h7 are one continuation after a lone centre stone.
        assertThat(h8.next.map { it.n }).containsExactly(2, 1).inOrder()
        assertThat(h8.next.first().move).isAnyOf(m("H9"), m("H7"), m("G8"), m("I8"))

        val line = pack.lookup(listOf(m("H8"), m("H7")))!!
        assertThat(line.n).isEqualTo(2)
        assertThat(line.b).isEqualTo(1)
        assertThat(line.w).isEqualTo(1)

        // Seen in one game only: under the two-game floor.
        assertThat(pack.lookup(listOf(m("H8"), m("I9")))).isNull()
    }

    @Test
    fun `the games pane counts transpositions and symmetries`() {
        val pack = RenjuPack.build(tables())
        val (games, matched) = pack.gamesThrough(listOf(m("H8"), m("H9")), limit = 10)
        assertThat(matched).isEqualTo(2)
        assertThat(games.map { pack.gameId[it] }).containsExactly(1, 2).inOrder()
        assertThat(pack.players[pack.gameBlack[0]]).isEqualTo("Ando Meritee")
        assertThat(pack.openings[pack.gameOpening[0]]).isEqualTo("D1")
        assertThat(pack.gameMoves(0).map { it.label() }).containsExactly("H8", "H9", "H7").inOrder()
    }

    @Test
    fun `a pack survives the file`() {
        val pack = RenjuPack.build(tables())
        val out = ByteArrayOutputStream()
        pack.write(out)
        val back = RenjuPack.read(ByteArrayInputStream(out.toByteArray()))!!
        assertThat(back.posHash.toList()).isEqualTo(pack.posHash.toList())
        assertThat(back.edgeN.toList()).isEqualTo(pack.edgeN.toList())
        assertThat(back.players.toList()).isEqualTo(pack.players.toList())
        assertThat(back.moves.toList()).isEqualTo(pack.moves.toList())
        assertThat(back.date).isEqualTo("2026-01-02")
        assertThat(back.lookup(listOf(m("H8")))!!.n).isEqualTo(3)
    }

    @Test
    fun `ply ranges cover every ply once`() {
        for (p in 1..5) {
            val r = RenjuPack.plyRanges(20, p)
            assertThat(r.first().first).isEqualTo(0)
            assertThat(r.last().second).isEqualTo(20)
            r.zipWithNext().forEach { (a, b) -> assertThat(b.first).isEqualTo(a.second + 1) }
        }
    }

    @Test
    fun `radix order is signed long order`() {
        val rnd = Random(3)
        val n = 500
        val h = LongArray(n) { rnd.nextLong() }
        val p = IntArray(n) { (rnd.nextInt(0, 256) shl 2) or rnd.nextInt(0, 3) }
        val (sh, sp) = RenjuPack.radixSort(h.copyOf(), p.copyOf(), LongArray(n), IntArray(n), n)
        for (i in 1 until n) {
            assertThat(sh[i] > sh[i - 1] || (sh[i] == sh[i - 1] && (sp[i] ushr 2) >= (sp[i - 1] ushr 2))).isTrue()
        }
    }
}
