package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Test

/**
 * The app axis of the 흑 5수 유불리 table.
 *
 * `rifdb/atlas_import.py` writes `openeval_golden.txt`: every representative
 * line of `opening_evals.txt` **plus every other legal move order that reaches
 * the same stones**, each with the grade the file gives it. That second half is
 * the point — the whole design rests on transpositions folding into one row, so
 * the golden asks for the fold directly instead of trusting it.
 *
 * The graded file itself is not in this repository (it is derived from a third
 * party's opening book and does not ship in the APK), so the tests rebuild a
 * valid one from the golden's representatives and parse *that*. What is under
 * test is this module's key and parser, and both see the real data shape.
 *
 * Regenerate with `python rifdb/atlas_import.py`.
 */
class OpeningEvalTest {

    private val size = 15

    private data class Row(val text: String, val moves: List<Move>, val grade: Int)

    private val golden: List<Row> by lazy {
        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("openeval_golden.txt")
        ) { "openeval_golden.txt is missing — run `python rifdb/atlas_import.py`" }
        stream.bufferedReader().useLines { lines ->
            lines.filterNot { it.startsWith("#") || it.isBlank() }.map { line ->
                val f = line.split('\t')
                check(f.size == 2) { "bad golden line: $line" }
                Row(
                    text = f[0],
                    moves = f[0].split(' ').map { Move.fromLabel(it, size)!! },
                    grade = f[1].removePrefix("+").toInt(),
                )
            }.toList()
        }
    }

    /** One line per distinct position, formatted exactly as the shipped file. */
    private fun rebuiltFile(): String {
        val seen = LinkedHashMap<String, Row>()
        for (r in golden) seen.getOrPut(PosKey.of(r.moves, size).key) { r }
        return buildString {
            append("# rebuilt from the golden by OpeningEvalTest\n")
            for (r in seen.values) append("${r.moves.size}\t${r.text}\t${r.grade}\t\n")
        }
    }

    @After
    fun clearTables() = OpeningTables.clear()

    @Test
    fun `the golden folds transpositions onto one position each`() {
        val byKey = golden.groupBy { PosKey.of(it.moves, size).key }
        assertThat(golden).hasSize(4013)
        assertThat(byKey).hasSize(1889)
        // every line of a group must carry the same grade, or the table would
        // depend on which move order the user happened to type
        for ((key, rows) in byKey) {
            assertWithMessage("grades of %s", key)
                .that(rows.map { it.grade }.distinct()).hasSize(1)
        }
        // and the fold must be real: most positions are reached more than once
        assertThat(byKey.count { it.value.size > 1 }).isGreaterThan(1000)
    }

    @Test
    fun `every golden line finds its grade through the parsed file`() {
        val load = OpeningTables.parseEvals(rebuiltFile(), size)
        assertThat(load.bad).isEqualTo(0)
        assertThat(load.rows).hasSize(1889)
        OpeningTables.evals = load.rows
        for (r in golden) {
            assertWithMessage(r.text)
                .that(OpeningName.gradeAt(r.moves, size)?.code).isEqualTo(r.grade)
        }
    }

    @Test
    fun `a line the file never wrote down still finds the row`() {
        // pick a golden line that is NOT one of the rebuilt representatives:
        // the file has never seen this move order, and the key must not care
        val load = OpeningTables.parseEvals(rebuiltFile(), size)
        OpeningTables.evals = load.rows
        val written = rebuiltFile().lineSequence()
            .filterNot { it.startsWith("#") }
            .mapNotNull { it.split('\t').getOrNull(1) }
            .toSet()
        val unwritten = golden.filter { it.text !in written }
        assertThat(unwritten).isNotEmpty()
        for (r in unwritten.take(200)) {
            assertWithMessage(r.text)
                .that(OpeningName.gradeAt(r.moves, size)?.code).isEqualTo(r.grade)
        }
    }

    @Test
    fun `the ladder is eleven grades in order with five shapes`() {
        assertThat(OpeningEval.ladder.map { it.code })
            .containsExactly(5, 4, 3, 2, 1, 0, -1, -2, -3, -4, -5).inOrder()
        assertThat(OpeningEval.ladder.map { it.ko }.toSet()).hasSize(11)
        assertThat(OpeningEval.ladder.map { it.en }.toSet()).hasSize(11)
        assertThat(OpeningEval.ladder.map { it.fill }.toSet()).hasSize(11)
        val shapes = OpeningEval.ladder.groupingBy { it.mark }.eachCount()
        assertThat(shapes[OpeningEval.Mark.CIRCLE]).isEqualTo(1)
        assertThat(shapes[OpeningEval.Mark.PENTAGON]).isEqualTo(3)
        assertThat(shapes[OpeningEval.Mark.SQUARE]).isEqualTo(3)
        assertThat(shapes[OpeningEval.Mark.TRIANGLE]).isEqualTo(3)
        assertThat(shapes[OpeningEval.Mark.CROSS]).isEqualTo(1)
        assertThat(OpeningEval.of(6)).isNull()
        assertThat(OpeningEval.of(-6)).isNull()
        assertThat(OpeningEval.of(null)).isNull()
        assertThat(OpeningEval.of(0)?.ko).isEqualTo("동등")
    }

    @Test
    fun `the parser refuses what a hand edit gets wrong`() {
        val load = OpeningTables.parseEvals(
            "﻿# grades\r\n" +
                "\r\n" +
                "5\th8 h9 h10 h11 g9\t+5\t책 12쪽\r\n" +
                "5\th8 h9 h10 h7 g8\t-5\t\n" +
                "4\th8 h9 h10 h11\t+0\t\n" +
                "5\th8 h9 h10 h11 g8\t+6\t\n" +      // out of range
                "5\th8 h9 h10 h11 f8\t-6\t\n" +      // out of range
                "5\th8 h9 h10 h11 e8\tzero\t\n" +    // not a number
                "5\th8 h9 h10 h11 d8\t\t\n" +        // empty grade
                "5\th8 h9 h10 h11\t+1\t\n" +         // ply disagrees with coords
                "5\th8 h9 h10 h11 zz\t+1\t\n" +      // unreadable coordinate
                "3\th8 h9 h10\t+1\t\n" +             // grades start at 4수
                "5\th8 h9 h10 h11 g9\t-3\t\n",       // duplicate: first wins
            size,
        )
        assertThat(load.rows).hasSize(3)
        assertThat(load.bad).isEqualTo(7)
        OpeningTables.evals = load.rows
        val moves = OpeningTables.parseMoves("h8 h9 h10 h11 g9", size)!!
        assertThat(OpeningName.gradeAt(moves, size)?.code).isEqualTo(5)
    }

    @Test
    fun `names and grades read the same file shape but different keys`() {
        // The very same four stones in the very same colours, reached two ways:
        // 한성 playing g9 fourth, and an indirect opening playing h9 fourth.
        // Two names — one grade. That split is the whole design.
        val direct = OpeningTables.parseMoves("h8 h9 h10 g9", size)!!
        val indirect = OpeningTables.parseMoves("h8 g9 h10 h9", size)!!
        assertThat(direct.filterIndexed { i, _ -> i % 2 == 0 }.toSet())
            .isEqualTo(indirect.filterIndexed { i, _ -> i % 2 == 0 }.toSet())
        assertThat(direct.filterIndexed { i, _ -> i % 2 == 1 }.toSet())
            .isEqualTo(indirect.filterIndexed { i, _ -> i % 2 == 1 }.toSet())
        assertThat(OpeningName.keyOf(direct, size))
            .isNotEqualTo(OpeningName.keyOf(indirect, size))
        assertThat(PosKey.of(direct, size).key).isEqualTo(PosKey.of(indirect, size).key)

        OpeningTables.names = OpeningTables.parseNames(
            "4\t${OpeningName.keyOf(direct, size)}\t한교\t\n" +
                "4\t${OpeningName.keyOf(indirect, size)}\t간접쪽\t\n",
            size,
        ).rows
        OpeningTables.evals =
            OpeningTables.parseEvals("4\th8 h9 h10 g9\t-2\t\n", size).rows

        assertThat(OpeningName.nameAt(4, direct, size)).isEqualTo("한교")
        assertThat(OpeningName.nameAt(4, indirect, size)).isEqualTo("간접쪽")
        assertThat(OpeningName.gradeAt(direct, size)?.code).isEqualTo(-2)
        assertThat(OpeningName.gradeAt(indirect, size)?.code).isEqualTo(-2)
    }

    @Test
    fun `an empty table names nothing and grades nothing`() {
        OpeningTables.clear()
        val moves = OpeningTables.parseMoves("h8 h9 h10 h11 g9", size)!!
        assertThat(OpeningName.nameAt(4, moves.take(4), size)).isNull()
        assertThat(OpeningName.gradeAt(moves, size)).isNull()
        // the computed plies keep working with no table at all
        assertThat(OpeningName.nameAt(1, moves.take(1), size)).isNotNull()
        assertThat(OpeningName.nameAt(3, moves.take(3), size)).isNotNull()
    }

    @Test
    fun `grades hang on four and five move positions only`() {
        OpeningTables.evals =
            OpeningTables.parseEvals("5\th8 h9 h10 h11 g9\t+5\t\n", size).rows
        val moves = OpeningTables.parseMoves("h8 h9 h10 h11 g9 f8", size)!!
        assertThat(OpeningName.gradeAt(moves.take(5), size)).isNotNull()
        assertThat(OpeningName.gradeAt(moves.take(3), size)).isNull()
        assertThat(OpeningName.gradeAt(moves, size)).isNull()
    }

    /**
     * 환원 — the third axis of the transposition enumerator, after
     * `openname.h on_transpositions` and `rifkey.transpositions`.
     *
     * The golden is the perfect oracle here: `atlas_import.py` builds it *by*
     * enumerating transpositions, so the lines sharing a [PosKey] are exactly
     * what this function must return for any one of them. If the Kotlin port
     * over- or under-counts, the two sets stop matching.
     */
    @Test
    fun `transpositions match the golden's own grouping`() {
        val byKey = golden.groupBy { PosKey.of(it.moves, size).key }
        var checked = 0
        for ((key, rows) in byKey) {
            val expect = rows.map { it.text }.toSortedSet()
            for (r in rows) {
                val got = OpeningName.transpositions(r.moves, size)
                    .map { line -> line.joinToString(" ") { m -> m.label(size).lowercase() } }
                assertWithMessage("transpositions of %s (shape %s)", r.text, key)
                    .that(got.toSortedSet()).isEqualTo(expect)
                checked++
            }
        }
        assertThat(checked).isEqualTo(4013)
    }

    @Test
    fun `a transposition list always contains the line itself`() {
        for (r in golden.take(500)) {
            val got = OpeningName.transpositions(r.moves, size)
                .map { line -> line.joinToString(" ") { m -> m.label(size).lowercase() } }
            assertWithMessage(r.text).that(got).contains(r.text)
        }
    }

    @Test
    fun `the same stones two ways get two names and one grade`() {
        val direct = OpeningTables.parseMoves("h8 h9 h10 g9", size)!!
        val alts = OpeningName.transpositions(direct, size)
            .map { line -> line.joinToString(" ") { m -> m.label(size).lowercase() } }
        assertThat(alts).containsExactly("h8 h9 h10 g9", "h8 i9 h10 h9")
        // the other order is a *different* opening — that is the whole point
        val other = OpeningTables.parseMoves("h8 i9 h10 h9", size)!!
        assertThat(Opening26.classify(direct.take(3)))
            .isNotEqualTo(Opening26.classify(other.take(3)))
        assertThat(PosKey.of(direct, size).key).isEqualTo(PosKey.of(other, size).key)
    }

    @Test
    fun `an unreachable order is never offered`() {
        // 4수 far from the centre: the first three still sit in the rule box,
        // so it cannot become move 3 of some other order
        val moves = OpeningTables.parseMoves("h8 i9 j10 i12", size)!!
        val got = OpeningName.transpositions(moves, size)
        assertThat(got).hasSize(1)
        assertThat(got.single().joinToString(" ") { it.label(size).lowercase() })
            .isEqualTo("h8 i9 j10 i12")
    }

    @Test
    fun `coordinates parse the way the desktop writes them`() {
        assertThat(OpeningTables.parseMoves("h8", size)).containsExactly(Move(7, 7))
        assertThat(OpeningTables.parseMoves("a1", size)).containsExactly(Move(0, 14))
        assertThat(OpeningTables.parseMoves("o15", size)).containsExactly(Move(14, 0))
        assertThat(OpeningTables.parseMoves("", size)).isEmpty()
        assertThat(OpeningTables.parseMoves("p8", size)).isNull()
        assertThat(OpeningTables.parseMoves("h16", size)).isNull()
        assertThat(OpeningTables.parseMoves("h0", size)).isNull()
        assertThat(OpeningTables.parseMoves("h", size)).isNull()
        assertThat(OpeningTables.parseMoves("h8x", size)).isNull()
    }
}
