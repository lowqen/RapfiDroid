package dev.gomoku.yixindroid.domain.rif

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.PosKey
import dev.gomoku.yixindroid.data.explorer.RjGamesPack
import dev.gomoku.yixindroid.data.explorer.RjStatsPack
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * `.rif` -> statistics -> packs -> **the app's own readers**.
 *
 * Reading back with `RjStatsPack`/`RjGamesPack` rather than with assertions
 * about bytes is the point: those readers were written against packs the PC
 * pipeline built, so a pack this code writes that they can read is a pack the
 * desktop can read too. That is the whole claim of building on the device.
 */
class RifPipelineTest {

    // Four games, two of which are the same shape mirrored: black takes the
    // centre and white answers directly above / directly below. The board is
    // symmetric after one stone, so those two answers are one continuation.
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

    private fun parsed(): RifDatabase = RifParser().parse(rif.reader())

    @Test
    fun `parses the tables and keeps only replayable games`() {
        val db = parsed()

        assertThat(db.games.map { it.id }).containsExactly(1, 2, 3).inOrder()
        assertThat(db.countries[4]).isEqualTo("Japan")
        assertThat(db.players[3]!!.display).isEqualTo("Solo")
        assertThat(db.players[1]!!.display).isEqualTo("Ando Meritee")
        assertThat(db.tournaments[1]!!.rated).isEqualTo(1)
        assertThat(db.tournaments[2]!!.name).isEqualTo("Friendly & casual")
        assertThat(db.openings.map { it.abbr }).containsExactly("D1", "I2").inOrder()
        assertThat(db.rules.map { it.name }).containsExactly("RIF", "Yamaguchi").inOrder()

        // Every rejection is counted, never silent.
        assertThat(db.skipped["dup_or_bad_id"]).isEqualTo(1)   // second id=3
        assertThat(db.skipped["bad_moves"]).isEqualTo(2)       // zz9, and h8 twice
        assertThat(db.skipped["bad_bresult"]).isEqualTo(1)     // bresult="x"
    }

    @Test
    fun `move tokens become board cells`() {
        val game = parsed().games.first { it.id == 1 }
        // h8 is the centre: x = 'h'-'a' = 7, y = 15 - 8 = 7.
        assertThat(game.cells.first()).isEqualTo(7 * 15 + 7)
        assertThat(game.cells.toList()).containsExactly(112, 97, 127).inOrder()
        assertThat(game.resultIndex).isEqualTo(0)
        assertThat(parsed().games.first { it.id == 3 }.resultIndex).isEqualTo(1)
    }

    @Test
    fun `text content is decoded and kept`() {
        assertThat(parsed().games.first { it.id == 2 }.info).isEqualTo("a note")
        assertThat(parsed().games.first { it.id == 1 }.alt).isEqualTo("i8")
    }

    // ---- aggregation ----

    @Test
    fun `the empty board sees every game`() {
        val agg = PackAggregator().aggregate(parsed())
        val empty = agg.positions.first { it.key == PosKey.emptyKey() }

        assertThat(empty.games).isEqualTo(3)
        assertThat(agg.totalGames).isEqualTo(3)
        assertThat(empty.blackWins).isEqualTo(1)   // game 1
        assertThat(empty.draws).isEqualTo(1)       // game 3
        assertThat(empty.whiteWins).isEqualTo(1)   // game 2
        // Every game opens in the centre, so there is one continuation.
        assertThat(empty.next).hasSize(1)
        assertThat(empty.next.single().games).isEqualTo(3)
    }

    @Test
    fun `mirrored replies merge into one continuation`() {
        val agg = PackAggregator().aggregate(parsed())
        val centre = PosKey.keyOf(listOf(Move(7, 7)))
        val afterCentre = agg.positions.first { it.key == centre }

        assertThat(afterCentre.games).isEqualTo(3)
        // h9 (game 1), h7 (game 2) and i9 (game 3): the first two are reflections
        // of each other about a board that is symmetric after one stone, so they
        // are the same continuation; i9 is a different one.
        assertThat(afterCentre.next).hasSize(2)
        assertThat(afterCentre.next.first().games).isEqualTo(2)
        assertThat(afterCentre.next.last().games).isEqualTo(1)
        assertThat(afterCentre.next.sumOf { it.games }).isEqualTo(3)
    }

    @Test
    fun `positions below the threshold are dropped`() {
        val agg = PackAggregator(minGames = 3).aggregate(parsed())
        // Only positions seen by all three games survive: empty and the centre.
        assertThat(agg.positions.map { it.key })
            .containsExactly(PosKey.emptyKey(), PosKey.keyOf(listOf(Move(7, 7))))
    }

    @Test
    fun `game lists are rated first, then newest`() {
        val agg = PackAggregator().aggregate(parsed())
        val empty = agg.positions.first { it.key == PosKey.emptyKey() }
        // Games 1 and 2 are in the rated 2005 tournament, game 3 in an unrated
        // 2019 one — rated wins over recency, and ids fall within a tie.
        assertThat(empty.gameIds.toList()).containsExactly(2, 1, 3).inOrder()
    }

    // ---- the packs, read back by the app's own readers ----

    @Test
    fun `stats pack round trips through the reader`() {
        val db = parsed()
        val agg = PackAggregator().aggregate(db)
        val bytes = ByteArrayOutputStream().also { PackWriter().writeStats(it, agg, 20_260_102) }
        val pack = RjStatsPack.open(ByteBuffer.wrap(bytes.toByteArray()))

        assertThat(pack).isNotNull()
        assertThat(pack!!.positions).isEqualTo(agg.positions.size)
        assertThat(pack.totalGames).isEqualTo(3)
        assertThat(pack.maxPlies).isEqualTo(PackAggregator.DEFAULT_MAX_PLIES)
        assertThat(pack.minGames).isEqualTo(PackAggregator.DEFAULT_MIN_GAMES)
        assertThat(pack.date).isEqualTo(20_260_102)

        val empty = pack.lookup(PosKey.emptyKey())
        assertThat(empty).isNotNull()
        assertThat(empty!!.games).isEqualTo(3)
        assertThat(empty.blackWins).isEqualTo(1)
        assertThat(empty.whiteWins).isEqualTo(1)
        assertThat(empty.nextMoves().single().games).isEqualTo(3)
        assertThat((0 until empty.gameCount).map { empty.gameIdAt(it) })
            .containsExactly(2, 1, 3).inOrder()

        // Every position the aggregate produced is findable — a hash index that
        // loses one entry still passes a single lookup.
        for (pos in agg.positions) assertThat(pack.lookup(pos.key)).isNotNull()
        assertThat(pack.lookup("no such key")).isNull()
    }

    @Test
    fun `games pack round trips through the reader`() {
        val db = parsed()
        val bytes = ByteArrayOutputStream().also { PackWriter().writeGames(it, db) }
        val pack = RjGamesPack.open(ByteBuffer.wrap(bytes.toByteArray()))

        assertThat(pack).isNotNull()
        assertThat(pack!!.gameCount).isEqualTo(3)

        val one = pack.game(1)
        assertThat(one).isNotNull()
        assertThat(one!!.black).isEqualTo("Ando Meritee")
        assertThat(one.white).isEqualTo("Kazuto Hasegawa")
        assertThat(one.tournament).isEqualTo("World Championship")
        assertThat(one.round).isEqualTo("F2")
        assertThat(one.year).isEqualTo(2005)
        assertThat(one.rated).isTrue()
        assertThat(one.result).isEqualTo(0)
        assertThat(one.blackCountry).isEqualTo("Estonia")
        assertThat(one.whiteCountry).isEqualTo("Japan")
        assertThat(one.tourStart).isEqualTo("2005-08-01")
        assertThat(one.cells).containsExactly(112, 97, 127).inOrder()
        assertThat(one.alt).isEqualTo("i8")

        val three = pack.game(3)
        assertThat(three!!.rated).isFalse()
        assertThat(three.black).isEqualTo("Solo")
        assertThat(three.result).isEqualTo(1)
        assertThat(three.tourCountry).isEqualTo("Japan")

        assertThat(pack.openingName(21)).isEqualTo("D1" to "Ura-getsu")
        assertThat(pack.ruleName(2)).isEqualTo("Yamaguchi")
        assertThat(pack.game(999)).isNull()
    }

    @Test
    fun `an empty database still produces readable packs`() {
        val empty = RifDatabase(emptyList(), emptyMap(), emptyMap(), emptyList(), emptyList(), emptyMap(), emptyMap())
        val agg = PackAggregator().aggregate(empty)
        val stats = ByteArrayOutputStream().also { PackWriter().writeStats(it, agg, 0) }
        val games = ByteArrayOutputStream().also { PackWriter().writeGames(it, empty) }

        assertThat(RjStatsPack.open(ByteBuffer.wrap(stats.toByteArray()))).isNotNull()
        assertThat(RjGamesPack.open(ByteBuffer.wrap(games.toByteArray()))!!.gameCount).isEqualTo(0)
    }
}
