package dev.gomoku.yixindroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The three saved-game formats, against the bytes the desktop reads and writes
 * (`load_game_file` main.c:3536, `show_dialog_save` 3665, `queue_add_current`
 * 7470).
 */
class GameFileTest {

    private val size = 15
    private val line = listOf(Move(x = 7, y = 7), Move(x = 8, y = 6), Move(x = 6, y = 8))

    @Test
    fun extensionsAreRecognisedCaseInsensitively() {
        assertThat(GameFileFormat.of("game.psq")).isEqualTo(GameFileFormat.PSQ)
        assertThat(GameFileFormat.of("GAME.PSQ")).isEqualTo(GameFileFormat.PSQ)
        assertThat(GameFileFormat.of("a.b.SAV")).isEqualTo(GameFileFormat.SAV)
        assertThat(GameFileFormat.of("x.pos")).isEqualTo(GameFileFormat.POS)
        assertThat(GameFileFormat.of("notes.txt")).isNull()
        assertThat(GameFileFormat.of("noextension")).isNull()
    }

    // ---- psq ---------------------------------------------------------------

    /** `x,y,time` is 1-based with x = column, and the line ends at `-1`. */
    @Test
    fun psqIsReadTheWayTheDesktopParsesIt() {
        val text = """
            Piskvorky 15x15, 11:11, 0
            8,8,0
            9,7,3
            7,9,1
            -1
            some trailing junk
        """.trimIndent()
        val moves = GameFile.parse(text.toByteArray(), GameFileFormat.PSQ, size)
        assertThat(moves).isEqualTo(line)
    }

    @Test
    fun psqStopsAtTheFirstNonNumericLine() {
        val text = "header\n8,8,0\nPlayer1 wins\n9,7,0\n"
        assertThat(GameFile.parse(text.toByteArray(), GameFileFormat.PSQ, size))
            .containsExactly(Move(x = 7, y = 7))
    }

    @Test
    fun psqRoundTripsThroughTheWriter() {
        val bytes = GameFile.writePsq(line, size)
        assertThat(String(bytes)).startsWith("Piskvorky 15x15, 0:0, 0\n8,8,0\n")
        assertThat(String(bytes)).endsWith("-1\n")
        assertThat(GameFile.parse(bytes, GameFileFormat.PSQ, size)).isEqualTo(line)
    }

    // ---- sav ---------------------------------------------------------------

    /** Two sizes to skip, a count, then `row col` — the desktop's own layout. */
    @Test
    fun savRoundTrips() {
        val bytes = GameFile.writeSav(line, size)
        assertThat(String(bytes)).isEqualTo("15\n15\n3\n7 7\n6 8\n8 6\n")
        assertThat(GameFile.parse(bytes, GameFileFormat.SAV, size)).isEqualTo(line)
    }

    @Test
    fun savStopsAtTheStoredCount() {
        val text = "15\n15\n2\n7 7\n6 8\n8 6\n"
        assertThat(GameFile.parse(text.toByteArray(), GameFileFormat.SAV, size))
            .isEqualTo(line.take(2))
    }

    @Test
    fun savNeedsAHeader() {
        assertThat(GameFile.parse("15\n".toByteArray(), GameFileFormat.SAV, size)).isNull()
    }

    // ---- pos ---------------------------------------------------------------

    /** One byte per move, `col * 15 + row` (main.c:3546 reads `xy % 15` as the row). */
    @Test
    fun posUnpacksOneBytePerMove() {
        val bytes = byteArrayOf(2, (7 * 15 + 7).toByte(), (8 * 15 + 6).toByte())
        assertThat(GameFile.parse(bytes, GameFileFormat.POS, size)).isEqualTo(line.take(2))
    }

    @Test
    fun posTruncatedFileKeepsWhatItHas() {
        val bytes = byteArrayOf(5, (7 * 15 + 7).toByte())
        assertThat(GameFile.parse(bytes, GameFileFormat.POS, size))
            .containsExactly(Move(x = 7, y = 7))
    }

    @Test
    fun anEmptyFileIsNotAGame() {
        assertThat(GameFile.parse(ByteArray(0), GameFileFormat.POS, size)).isNull()
    }

    // ---- names -------------------------------------------------------------

    /** `queue_basename`: no directory, no extension, spaces to underscores. */
    @Test
    fun baseNameFollowsTheDesktop() {
        assertThat(GameFile.baseName("C:\\dir\\my game.psq")).isEqualTo("my_game")
        assertThat(GameFile.baseName("/sdcard/games/final.round.sav")).isEqualTo("final")
        assertThat(GameFile.baseName("")).isEqualTo("game")
        assertThat(GameFile.baseName(".psq")).isEqualTo("game")
    }

    @Test
    fun movesOutsideTheBoardEndTheLine() {
        val text = "header\n8,8,0\n99,99,0\n"
        assertThat(GameFile.parse(text.toByteArray(), GameFileFormat.PSQ, size))
            .containsExactly(Move(x = 7, y = 7))
    }

    @Test
    fun aRepeatedPointEndsTheLine() {
        val text = "header\n8,8,0\n8,8,0\n"
        assertThat(GameFile.parse(text.toByteArray(), GameFileFormat.PSQ, size))
            .containsExactly(Move(x = 7, y = 7))
    }
}
