package dev.gomoku.rapfidroid.core.joseki

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.Move
import org.junit.Test
import java.io.File

class JosekiTest {

    private fun m(label: String) = Move.fromLabel(label)!!

    private fun line(vararg labels: String) = labels.map { m(it) }

    @Test
    fun `adding a move that exists follows it`() {
        val a = JosekiOps.addLine(JosekiNode(), line("H8", "H9", "G9"))
        assertThat(a.added).isEqualTo(3)
        val b = JosekiOps.addLine(a.root, line("H8", "H9", "I9"))
        assertThat(b.added).isEqualTo(1)
        assertThat(b.path).containsExactly(0, 0, 1).inOrder()
        assertThat(JosekiOps.countNodes(b.root)).isEqualTo(4)
        // Illegal: a point already on the line.
        assertThat(JosekiOps.addMove(b.root, b.path, m("H8"))).isNull()
    }

    @Test
    fun `promote, reorder and remove`() {
        var r = JosekiOps.addLine(JosekiNode(), line("H8", "H9")).root
        r = JosekiOps.addLine(r, line("H8", "I9")).root
        val (promoted, path) = JosekiOps.promoteLine(r, listOf(0, 1))
        assertThat(path).containsExactly(0, 0).inOrder()
        assertThat(JosekiOps.mainLine(promoted)).containsExactly(m("H8"), m("I9")).inOrder()
        val (swapped, p2) = JosekiOps.moveSibling(promoted, listOf(0, 0), 1)
        assertThat(p2).containsExactly(0, 1).inOrder()
        assertThat(JosekiOps.mainLine(swapped)).containsExactly(m("H8"), m("H9")).inOrder()
        val (removed, parent) = JosekiOps.removeAt(swapped, listOf(0, 0))!!
        assertThat(parent).containsExactly(0)
        assertThat(JosekiOps.nodeAt(removed, listOf(0))!!.children.map { it.move }).containsExactly(m("I9"))
    }

    @Test
    fun `typed lines`() {
        val ok = JosekiOps.parseMoveText("1. h8 2. i9, J10") as JosekiOps.Parsed.Ok
        assertThat(ok.moves).containsExactly(m("H8"), m("I9"), m("J10")).inOrder()
        val glued = JosekiOps.parseMoveText("h8i9j10") as JosekiOps.Parsed.Ok
        assertThat(glued.moves).hasSize(3)
        assertThat(JosekiOps.parseMoveText("h8 h8")).isInstanceOf(JosekiOps.Parsed.Error::class.java)
        assertThat(JosekiOps.parseMoveText("h8 zz")).isInstanceOf(JosekiOps.Parsed.Error::class.java)
        assertThat(JosekiOps.movesText(ok.moves)).isEqualTo("h8 i9 j10")
    }

    @Test
    fun `json round trip and validation`() {
        var root = JosekiOps.addLine(JosekiNode(), line("H8", "H9", "G9")).root
        root = JosekiOps.setComment(root, listOf(0), "**centre** h8")
        root = JosekiOps.setMark(root, listOf(0, 0), JosekiMark.GOOD)
        root = JosekiOps.setRole(root, listOf(0, 0, 0), JosekiRole.TRAP)
        val tree = JosekiTree(id = "t1", title = "Test", root = root)
        val back = JosekiJson.import(JosekiJson.export(listOf(tree))) as JosekiJson.Import.Ok
        assertThat(back.trees.single().root).isEqualTo(root)
        assertThat(back.dropped).isEqualTo(0)

        val bad = """{"title":"x","root":{"children":[{"move":{"x":7,"y":7},"children":[{"move":{"x":7,"y":7},"children":[]},{"move":{"x":99,"y":1},"children":[]}]}]}}"""
        val r = JosekiJson.import(bad) as JosekiJson.Import.Ok
        assertThat(r.dropped).isEqualTo(2)
        assertThat(JosekiOps.countNodes(r.trees.single().root)).isEqualTo(1)
        assertThat(JosekiJson.import("nope")).isInstanceOf(JosekiJson.Import.Error::class.java)
    }

    @Test
    fun `index keys match symmetric positions`() {
        val tree = JosekiTree(title = "t", root = JosekiOps.addLine(JosekiNode(), line("H8", "H9")).root)
        val keys = JosekiJson.indexKeys(tree)
        assertThat(keys).hasSize(2)
        val mirrored = dev.gomoku.rapfidroid.core.model.PosKey.keyOf(line("H8", "H7"))
        assertThat(keys.map { it.key }).contains(mirrored)
    }

    @Test
    fun `markdown blocks and coordinates`() {
        val blocks = Markdown.parse("**Bold** and h8\n\n- one\n- two [x](https://a.b)\n\n1. first")
        assertThat(blocks).hasSize(3)
        val para = blocks[0] as Markdown.Block.Para
        assertThat(para.c.first()).isInstanceOf(Markdown.Inline.Bold::class.java)
        assertThat(para.c.filterIsInstance<Markdown.Inline.Coord>().single().move).isEqualTo(m("H8"))
        val list = blocks[1] as Markdown.Block.Bullets
        assertThat(list.ordered).isFalse()
        assertThat(list.items).hasSize(2)
        assertThat(list.items[1].filterIsInstance<Markdown.Inline.Link>()).hasSize(1)
        assertThat((blocks[2] as Markdown.Block.Bullets).ordered).isTrue()
        // javascript: is not a link
        val js = Markdown.parseInline("[x](javascript:alert(1))")
        assertThat(js.filterIsInstance<Markdown.Inline.Link>()).isEmpty()
        assertThat(Markdown.plainText("**a** [b](https://c) _d_")).isEqualTo("a b d")
    }

    @Test
    fun `quiz accepts good moves, refuses bad ones and scores first tries`() {
        // Black learns. h8 (black), then white h9 auto, black g9 good / i9 bad.
        var root = JosekiOps.addLine(JosekiNode(), line("H8", "H9", "G9", "G10")).root
        root = JosekiOps.addLine(root, line("H8", "H9", "I9", "I10")).root
        root = JosekiOps.setMark(root, listOf(0, 0, 1), JosekiMark.BAD)
        var s = Quiz.start(root, blackLearner = true)
        assertThat(s.total).isEqualTo(2)
        s = Quiz.answer(root, s, m("H8"))
        assertThat(s.feedback).isInstanceOf(QuizFeedback.Correct::class.java)
        assertThat(s.path).containsExactly(0, 0).inOrder() // white's reply played
        s = Quiz.answer(root, s, m("I9"))
        val miss = s.feedback as QuizFeedback.Mistake
        assertThat(miss.refute).isEqualTo(m("I10"))
        s = Quiz.answer(root, s, m("A1"))
        assertThat(s.feedback).isInstanceOf(QuizFeedback.Outside::class.java)
        assertThat(s.hint).isTrue()
        assertThat(Quiz.expected(root, s)).isEqualTo(m("G9"))
        s = Quiz.answer(root, s, m("G9"))
        assertThat(s.done).isTrue()
        assertThat(s.answered).isEqualTo(2)
        assertThat(s.firstTry).isEqualTo(1)
        assertThat(s.score).isEqualTo(50)
    }

    @Test
    fun `the shipped curriculum is valid`() {
        val file = File("src/main/assets/joseki/curriculum.json")
        val c = CurriculumJson.parse(file.readText(), korean = true)!!
        assertThat(c.courses).isNotEmpty()
        assertThat(c.problems).isEmpty()
        c.courses.forEach { course ->
            assertThat(course.lessons).isNotEmpty()
            course.lessons.forEach { assertThat(CurriculumJson.learnerMoves(it.root, it.blackLearner)).isGreaterThan(0) }
        }
        val en = CurriculumJson.parse(file.readText(), korean = false)!!
        assertThat(en.courses.first().title).isNotEqualTo(c.courses.first().title)
        assertThat(c.findLesson(c.courses.first().lessons.first().id)).isNotNull()
    }
}
