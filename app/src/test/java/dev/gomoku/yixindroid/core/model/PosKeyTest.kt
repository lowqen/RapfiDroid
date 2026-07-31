package dev.gomoku.yixindroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The **fourth axis** of the position-key cross-validation.
 *
 * `rifdb/rif_crosscheck.py` runs `rifkey.py` against
 * `Yixin-Board/tests/test_webkey.exe` (= `main.c web_poskey`) and writes the
 * cases they agree on to `poskey_golden.txt` in this module's test resources.
 * These tests read that file, so a divergence between the Kotlin port and the
 * other three implementations fails the build rather than producing quietly
 * wrong statistics (CLAUDE.md: 한쪽 수정 시 rif_crosscheck.py 재검증 필수).
 *
 * Regenerate with `python rif_crosscheck.py --emit` after touching any of them.
 */
class PosKeyTest {

    private val size = 15

    private data class Golden(
        val moves: List<Move>,
        val text: String,
        val key: String,
        val transform: Int,
        val probe: String,
    )

    /** The probe cell `rif_crosscheck.py` uses: its 8 images are all distinct,
     *  so the image pins down the transform exactly. */
    private val probeCell = Move.fromLabel("b3", size)!!

    private val golden: List<Golden> by lazy {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("poskey_golden.txt")) {
            "poskey_golden.txt is missing — run `python rifdb/rif_crosscheck.py --emit`"
        }
        stream.bufferedReader().useLines { lines ->
            lines.filterNot { it.startsWith("#") || it.isBlank() }.map { line ->
                val f = line.split('\t')
                check(f.size == 4) { "bad golden line: $line" }
                Golden(
                    moves = f[0].split(' ').map { Move.fromLabel(it, size)!! },
                    text = f[0],
                    key = f[1],
                    transform = f[2].toInt(),
                    probe = f[3],
                )
            }.toList()
        }
    }

    @Test
    fun goldenFileIsPresentAndSubstantial() {
        assertThat(golden.size).isAtLeast(200)
    }

    @Test
    fun everyGoldenKeyMatches() {
        val bad = golden.filter { PosKey.keyOf(it.moves, size) != it.key }
        assertThat(bad.map { it.text }).isEmpty()
    }

    @Test
    fun everyGoldenTransformMatches() {
        val bad = golden.filter { PosKey.of(it.moves, size).transform != it.transform }
        assertThat(bad.map { it.text }).isEmpty()
    }

    /**
     * The transform is what puts a stored next move back on the board, so the
     * golden pins the *image of a probe cell*, not just the id — mapping through
     * a wrong-but-equal-key transform would draw suggestions in the wrong place.
     */
    @Test
    fun everyGoldenProbeImageMatches() {
        val bad = golden.filter {
            val t = PosKey.of(it.moves, size).transform
            PosKey.tf(t, size, probeCell).label(size).lowercase() != it.probe
        }
        assertThat(bad.map { it.text }).isEmpty()
    }

    // ---- properties the golden cannot express -------------------------------

    @Test
    fun allEightSymmetriesOfALineShareOneKey() {
        // test_webkey.c's own self-test position
        val line = listOf("h8", "i9", "j8", "g10", "k11").map { Move.fromLabel(it, size)!! }
        val keys = (0 until 8).map { t ->
            PosKey.keyOf(line.map { PosKey.tf(t, size, it) }, size)
        }
        assertThat(keys.toSet()).hasSize(1)
    }

    @Test
    fun differentPositionsGetDifferentKeys() {
        val a = listOf("h8", "i9").map { Move.fromLabel(it, size)!! }
        val b = listOf("h8", "i8").map { Move.fromLabel(it, size)!! }
        assertThat(PosKey.keyOf(a, size)).isNotEqualTo(PosKey.keyOf(b, size))
    }

    @Test
    fun colourComesFromMoveOrderNotFromCells() {
        val a = listOf("h8", "i9").map { Move.fromLabel(it, size)!! }
        val b = listOf("i9", "h8").map { Move.fromLabel(it, size)!! }
        assertThat(PosKey.keyOf(a, size)).isNotEqualTo(PosKey.keyOf(b, size))
    }

    @Test
    fun emptyLineIsJustTheBoardSize() {
        assertThat(PosKey.keyOf(emptyList(), size)).isEqualTo("15")
        assertThat(PosKey.of(emptyList(), size).transform).isEqualTo(0)
    }

    @Test
    fun everyTransformIsUndoneByItsInverse() {
        for (t in 0 until 8) {
            for (cell in listOf("a1", "b3", "h8", "o15", "c9")) {
                val m = Move.fromLabel(cell, size)!!
                val there = PosKey.tf(t, size, m)
                assertThat(PosKey.tf(PosKey.inverse(t), size, there)).isEqualTo(m)
            }
        }
    }

    /**
     * Candidates are compared as strings, not by board index. On a 15 board the
     * row numbers "10".."15" sort before "2".."9", which is exactly why the
     * desktop compares serialisations — an index-order comparison would pick a
     * different symmetry and a different key.
     */
    @Test
    fun candidatesAreComparedAsStrings() {
        assertThat("15a10b" < "15a2b").isTrue()
        // h8 alone is symmetric under all 8 transforms; adding i9 breaks it, and
        // rifkey/main.c both settle on t=1 with key 15g9wh8b.
        val line = listOf("h8", "i9").map { Move.fromLabel(it, size)!! }
        val r = PosKey.of(line, size)
        assertThat(r.key).isEqualTo("15g9wh8b")
        assertThat(r.transform).isEqualTo(1)
    }

    /** A tie keeps the smallest transform id (`t == 0 || strcmp < 0`). */
    @Test
    fun tiesKeepTheSmallestTransform() {
        // tengen alone maps to itself under every symmetry: all 8 serialise the
        // same, so the first one wins.
        val r = PosKey.of(listOf(Move.fromLabel("h8", size)!!), size)
        assertThat(r.key).isEqualTo("15h8b")
        assertThat(r.transform).isEqualTo(0)
    }

    @Test
    fun toBoardRoundTripsAStoredSuggestion() {
        val line = listOf("h8", "i9", "j10").map { Move.fromLabel(it, size)!! }
        val r = PosKey.of(line, size)
        val onBoard = Move.fromLabel("k11", size)!!
        val canonical = PosKey.tf(r.transform, size, onBoard)
        assertThat(r.toBoard(canonical, size)).isEqualTo(onBoard)
    }
}
