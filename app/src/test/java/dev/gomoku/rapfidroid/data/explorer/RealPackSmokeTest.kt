package dev.gomoku.rapfidroid.data.explorer

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.PosKey
import dev.gomoku.rapfidroid.core.model.Position
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/**
 * Reads the **deployed** packs next to `Yixin.exe`, when this machine has them.
 *
 * The synthetic fixtures in [RjPacksTest] prove the reader matches the format
 * spec; this proves it matches the files the desktop actually ships, which is
 * the only thing that settles a "the pack will not load" report. It is skipped
 * wherever the packs are absent, and it reads them in place — no RenjuNet data
 * enters the repository (rifdb/README.md).
 */
class RealPackSmokeTest {

    // Gradle의 JVM 테스트 작업 디렉터리는 기본적으로 모듈 폴더(RapfiDroid/app)이므로,
    // 프로젝트 루트의 test-yixin/ 까지는 두 단계 위로 올라간다. 저장소를 어디로
    // 옮기거나 복제해도 그대로 성립하는 상대 경로다.
    private val deployDir = File("../../test-yixin")
    private val statsFile = File(deployDir, "renju_stats.pack")
    private val gamesFile = File(deployDir, "renju_games.pack")

    private fun map(file: File) =
        RandomAccessFile(file, "r").use {
            it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length())
        }

    private fun stats(): RjStatsPack {
        assumeTrue("deployed renju_stats.pack not present", statsFile.isFile)
        return checkNotNull(RjStatsPack.open(map(statsFile))) { "stats pack refused" }
    }

    private fun games(): RjGamesPack {
        assumeTrue("deployed renju_games.pack not present", gamesFile.isFile)
        return checkNotNull(RjGamesPack.open(map(gamesFile))) { "games pack refused" }
    }

    @Test
    fun theDeployedStatsPackOpens() {
        val p = stats()
        assertThat(p.positions).isGreaterThan(100_000)
        assertThat(p.totalGames).isGreaterThan(100_000)
        assertThat(p.maxPlies).isEqualTo(20)
        assertThat(p.minGames).isEqualTo(2)
    }

    @Test
    fun theEmptyBoardRecordHoldsEveryGame() {
        val p = stats()
        val s = checkNotNull(p.lookup("15")) { "no record for the empty board" }
        assertThat(s.games).isEqualTo(p.totalGames)
        assertThat(s.gameCount).isEqualTo(p.totalGames)
        assertThat(s.nextCount).isGreaterThan(0)
    }

    @Test
    fun theOpeningMovesResolve() {
        val p = stats()
        val h8 = checkNotNull(p.lookup(PosKey.keyOf(listOf(Move.fromLabel("h8", 15)!!), 15)))
        assertThat(h8.games).isGreaterThan(1000)
        assertThat(h8.games).isAtMost(p.totalGames)
        // a real second move, through the key's own canonicalisation
        val line = listOf("h8", "i9").map { Move.fromLabel(it, 15)!! }
        val two = checkNotNull(p.lookup(PosKey.keyOf(line, 15)))
        assertThat(two.games).isGreaterThan(0)
        assertThat(two.blackWins + two.draws + two.whiteWins).isEqualTo(two.games)
    }

    @Test
    fun theDeployedGamesPackResolvesRecordsAndTables() {
        val s = stats()
        val g = games()
        val root = checkNotNull(s.lookup("15"))
        val first = checkNotNull(g.game(root.gameIdAt(0))) { "first listed game missing" }
        assertThat(first.black).isNotEmpty()
        assertThat(first.white).isNotEmpty()
        assertThat(first.cells).isNotEmpty()
        assertThat(first.year).isAtLeast(1900)
        assertThat(g.openingName(first.opening)).isNotNull()
    }

    /** The whole lookup path the screen uses, on real data. */
    @Test
    fun theLookupPathWorksEndToEnd() {
        val s = stats()
        val g = games()
        val pos = Position(15, listOf("h8", "i9").map { Move.fromLabel(it, 15)!! })
        val found = checkNotNull(ExplorerLookup.lookup(s, g, pos)) { "no stats for h8 i9" }
        assertThat(found.position.games).isGreaterThan(0)
        assertThat(found.position.next).isNotEmpty()
        // every suggestion must land on an empty point of *this* board
        for (n in found.position.next) {
            assertThat(n.move.isInside(15)).isTrue()
            assertThat(pos.moves).doesNotContain(n.move)
        }
        val list = ExplorerLookup.gameList(found.stat, g, "")
        assertThat(list.matched).isEqualTo(found.position.gameCount)
        assertThat(list.rows).isNotEmpty()
    }
}
