package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The **third axis** of the opening-name frame-key cross-validation.
 *
 * `rifdb/rif_crosscheck.py` runs `rifkey.framekey` against
 * `Yixin-Board/tests/test_openname.exe` (= the `openname.h` main.c includes)
 * over every four-move line in the 5×5 box, and writes the cases they agree on
 * to `openname_golden.txt` in this module's test resources. These tests read
 * that file, so a divergence between the Kotlin port and the other two fails
 * the build instead of quietly naming a shape after its mirror image.
 *
 * Regenerate with `python rifdb/rif_crosscheck.py --emit` after touching any
 * of the three.
 */
class OpeningNameTest {

    private val size = 15

    private data class Golden(
        val text: String,
        val moves: List<Move>,
        val key: String,
        val transform: Int,
        val kind: Int,
    )

    private val golden: List<Golden> by lazy {
        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("openname_golden.txt")
        ) { "openname_golden.txt is missing — run `python rifdb/rif_crosscheck.py --emit`" }
        stream.bufferedReader().useLines { lines ->
            lines.filterNot { it.startsWith("#") || it.isBlank() }.map { line ->
                val f = line.split('\t')
                check(f.size == 4) { "bad golden line: $line" }
                Golden(
                    text = f[0],
                    moves = f[0].split(' ').map { Move.fromLabel(it, size)!! },
                    key = f[1],
                    transform = f[2].toInt(),
                    kind = f[3].toInt(),
                )
            }.toList()
        }
    }

    private fun kindOf(m: OpeningName.Move2Kind) = when (m) {
        OpeningName.Move2Kind.NONE -> -1
        OpeningName.Move2Kind.DIRECT -> 0
        OpeningName.Move2Kind.INDIRECT -> 1
    }

    @Test
    fun theGoldenFileCoversTheWholeWorksheet() {
        // 1 tengen + 8 second moves + 26 openings + 1070 fourth-move shapes
        // (7×7 — the 5×5 box is the *third* move's rule, not the fourth's)
        // + 1077 fifth-move shapes (what the 유불리 evaluation table points at)
        // + 5 lines that must have no frame at all.
        assertThat(golden).hasSize(2187)
        assertThat(golden.count { it.transform < 0 }).isEqualTo(5)
        assertThat(golden.count { it.moves.size == 4 }).isEqualTo(1070)
        assertThat(golden.count { it.moves.size == 5 }).isEqualTo(1077)
    }

    @Test
    fun everyGoldenLineAgreesWithTheDesktop() {
        val wrong = golden.filter { g ->
            val frame = OpeningName.frameOf(g.moves, size)
            if (g.transform < 0) frame != null
            else frame == null || frame.key != g.key || frame.transform != g.transform
        }
        assertThat(wrong.map { it.text }).isEmpty()
    }

    @Test
    fun everyGoldenLineAgreesOnHowTheSecondMoveBlocks() {
        val wrong = golden.filter { kindOf(OpeningName.move2Kind(it.moves, size)) != it.kind }
        assertThat(wrong.map { it.text }).isEmpty()
    }

    /**
     * The point of the frame: all 8 placements of a line are the same shape.
     * The golden lines already arrive pre-rotated, so this re-rotates them
     * and demands the key never moves.
     */
    @Test
    fun allEightPlacementsOfALineShareOneKey() {
        for (g in golden) {
            if (g.transform < 0) continue
            for (t in 0 until 8) {
                val moved = g.moves.map { OpeningName.tf(t, size, it) }
                assertThat(OpeningName.keyOf(moved, size)).isEqualTo(g.key)
            }
        }
    }

    /**
     * A mirror-symmetric opening folds its 22 fourth-move placements into 12
     * shapes; an asymmetric one keeps all 22. This is what stops the worksheet
     * asking the user to name the same shape twice.
     */
    @Test
    fun aSymmetricOpeningFoldsItsFourthMovesInHalf() {
        fun shapes(line: String, r: Int): Int {
            val base = line.split(' ').map { Move.fromLabel(it, size)!! }
            val keys = mutableSetOf<String>()
            for (dy in -r..r) for (dx in -r..r) {
                val m = Move(7 + dx, 7 + dy)
                if (m in base) continue
                OpeningName.keyOf(base + m, size)?.let { keys += it }
            }
            return keys.size
        }
        assertThat(shapes("h8 h9 h10", 2)).isEqualTo(12)  // 한성 — 3rd stone on the axis
        assertThat(shapes("h8 h9 g9", 2)).isEqualTo(22)   // 화월 — no residual mirror
        // the worksheet asks over 7×7, because the 5×5 box is the *third*
        // move's rule and does not bind the fourth
        assertThat(shapes("h8 h9 h10", 3)).isEqualTo(25)
        assertThat(shapes("h8 h9 g9", 3)).isEqualTo(46)
    }

    /**
     * The frame key's 3-move prefix must never disagree with [Opening26]: they
     * are two classifiers over the same normalisation, and a name chain that
     * showed one opening's 주형 next to another's 4수 name would be worse than
     * showing nothing.
     */
    @Test
    fun theFrameAgreesWithThe26OpeningClassifier() {
        val byKey = mutableMapOf<String, Int>()
        for (g in golden) {
            if (g.moves.size < 3) continue
            val id = Opening26.classify(g.moves.take(3))
            if (id == Opening26.NONSTD) continue
            val key = OpeningName.keyOf(g.moves.take(3), size)!!
            val seen = byKey.put(key, id)
            assertThat(seen ?: id).isEqualTo(id)
        }
        // all 26 openings appear, each under exactly one frame key
        assertThat(byKey.values.toSet()).hasSize(26)
        assertThat(byKey.keys).hasSize(26)
    }

    @Test
    fun aLineWithoutAFrameHasNoName() {
        assertThat(OpeningName.keyOf(listOf(Move.fromLabel("a1", size)!!), size)).isNull()
        assertThat(OpeningName.keyOf(emptyList(), size)).isNull()
        // The frame stops at 5 — 6수 and beyond have neither a name nor a row
        // in any table, so they get no key.
        val six = "h8 h9 g9 g8 f7 j10".split(' ').map { Move.fromLabel(it, size)!! }
        assertThat(OpeningName.keyOf(six, size)).isNull()
        assertThat(OpeningName.keyOf(six.take(OpeningName.MAX_PLY), size))
            .isEqualTo("h8 h9 g9 g8")
        // …but a *name* still stops at 4: 렌주 has no per-shape 5수 name.
        assertThat(OpeningName.nameAt(5, six, size)).isNull()
    }

    /**
     * Names live at ply 4, the 흑 5수 유불리 evaluations at ply 5, and the two
     * tables get shown together. That only works if the 5-move key's 4-move
     * prefix is the 4-move key — otherwise one line would appear under two
     * identities. Minimising a 5-long sequence only reaches index 4 when the
     * first four tie, so the property holds; this pins it down.
     */
    @Test
    fun aFifthMoveKeyExtendsItsFourthMoveKey() {
        for (g in golden) {
            if (g.moves.size != 5 || g.transform < 0) continue
            val four = OpeningName.keyOf(g.moves.take(4), size)
            assertThat(g.key).startsWith("$four ")
        }
        val line = "h8 h9 h10 h11 g9".split(' ').map { Move.fromLabel(it, size)!! }
        assertThat(OpeningName.keyOf(line, size)).isEqualTo("h8 h9 h10 h11 g9")
    }

    /**
     * 2026-08-14, from the user: in 한성 the 4th move at h11 and at h7 are
     * different openings with different names — "위아래의 대칭은 칸수 차이로
     * 미세하게 다르므로 구분한다". The frame pins move 1 to tengen, so the
     * up-down flip that would swap h8 and h10 is never a candidate, and the two
     * already differ. (The bug that hid this was the worksheet's *range*, which
     * applied the third move's 5×5 rule to the fourth move.)
     */
    @Test
    fun fourthMovesAboveAndBelowTheAxisStayDistinct() {
        fun key(line: String) =
            OpeningName.keyOf(line.split(' ').map { Move.fromLabel(it, size)!! }, size)
        assertThat(key("h8 h9 h10 h11")).isNotEqualTo(key("h8 h9 h10 h7"))
        assertThat(key("h8 h9 h10 h12")).isNotEqualTo(key("h8 h9 h10 h6"))
        assertThat(key("h8 h9 h10 h11")).isEqualTo("h8 h9 h10 h11")
        // and the left/right mirror really is one shape — that fold is correct
        assertThat(key("h8 h9 h10 g9")).isEqualTo(key("h8 h9 h10 i9"))
    }
}
