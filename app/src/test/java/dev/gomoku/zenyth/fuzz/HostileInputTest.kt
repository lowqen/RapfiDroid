package dev.gomoku.zenyth.fuzz

import com.google.common.truth.Truth.assertThat
import dev.gomoku.zenyth.core.joseki.CurriculumJson
import dev.gomoku.zenyth.core.joseki.JosekiJson
import dev.gomoku.zenyth.core.joseki.LessonLink
import dev.gomoku.zenyth.core.joseki.Markdown
import dev.gomoku.zenyth.core.model.BoardTransform
import dev.gomoku.zenyth.core.model.FontSpec
import dev.gomoku.zenyth.core.model.FunctionScripts
import dev.gomoku.zenyth.core.model.GameFile
import dev.gomoku.zenyth.core.model.GameFileFormat
import dev.gomoku.zenyth.core.model.LngTable
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.PositionLink
import dev.gomoku.zenyth.core.model.SettingsFile
import dev.gomoku.zenyth.domain.rif.RifStreamParser
import dev.gomoku.zenyth.domain.settings.SettingsCodec
import org.junit.Test
import kotlin.random.Random

/**
 * Input the app does not choose: links any web page or app can send it, files
 * the user picks, the curriculum the network serves (docs/보안-강화-계획.md
 * Phase 2). Every reader here promises «null or an error message», so the bar
 * is simple — nothing throws.
 *
 * The first half pins the crashes that were found; the second feeds each reader
 * random and mutated input. The seed is fixed, so a failure reruns exactly; the
 * failing input is in the assertion message.
 */
class HostileInputTest {

    // ---- found crashes -------------------------------------------------------

    /** URLDecoder throws on a broken escape; `URI` happens to refuse these first, and must keep doing so. */
    @Test
    fun `a broken escape in a lesson link is no link`() {
        for (bad in listOf("%zz", "%", "%e", "%E3%8", "a%2", "%%")) {
            assertThat(LessonLink.parse("https://rapfiweb.pages.dev/joseki?lesson=$bad")).isNull()
            assertThat(LessonLink.parse("https://rapfiweb.pages.dev/joseki?lesson=ok&mode=$bad")).isNull()
        }
        // The good ones still read.
        assertThat(LessonLink.parse("https://rapfiweb.pages.dev/joseki?lesson=a%2Db")?.lessonId).isEqualTo("a-b")
    }

    /**
     * The crash that was real: MainActivity offers any `p=` link, BoardViewModel's
     * collector hands it to `fromText`, and URLDecoder's exception left
     * viewModelScope uncaught. A paste took the same path.
     */
    @Test
    fun `a broken escape in a position link is no position`() {
        for (bad in listOf("%zz", "%", "h8%", "h8%E3%8")) {
            assertThat(PositionLink.fromText("https://rapfiweb.pages.dev/board?p=$bad", 15)).isNull()
        }
        assertThat(PositionLink.fromText("https://rapfiweb.pages.dev/board?p=h8%20i9", 15)).hasSize(2)
    }

    /** Far longer than any board: refused before the shape regex walks it. */
    @Test
    fun `an endless position link is refused`() {
        val long = "h8".repeat(100_000)
        assertThat(PositionLink.parse(long, 15)).isNull()
        assertThat(PositionLink.fromText("https://rapfiweb.pages.dev/board?p=$long", 15)).isNull()
        // A full 22 x 22 board still fits.
        val full = (0 until 22).flatMap { x -> (0 until 22).map { y -> Move(x, y) } }
        assertThat(PositionLink.parse(PositionLink.encode(full, 22), 22)).hasSize(22 * 22)
    }

    /** `.sav` sized its list by the file's own count: «15 15 2000000000» was an OutOfMemoryError. */
    @Test
    fun `a sav file cannot size the parser's memory`() {
        val moves = GameFile.parse("15\n15\n2000000000\n7 7\n6 8\n".toByteArray(), GameFileFormat.SAV, 15)
        assertThat(moves).containsExactly(Move(x = 7, y = 7), Move(x = 8, y = 6)).inOrder()
    }

    /** A number past Int made `toInt` throw out of the caller's coroutine. */
    @Test
    fun `a sav file with a number past Int is unreadable`() {
        assertThat(GameFile.parse("15\n15\n99999999999\n7 7\n".toByteArray(), GameFileFormat.SAV, 15)).isNull()
        assertThat(GameFile.parse("15\n15\n1\n7 7\n".toByteArray(), GameFileFormat.SAV, 15)).hasSize(1)
    }

    @Test
    fun `deeply nested json neither overflows the stack nor throws`() {
        val arrays = "[".repeat(50_000) + "]".repeat(50_000)
        assertThat(CurriculumJson.parse(arrays)).isNull()
        assertThat(JosekiJson.import(arrays)).isInstanceOf(JosekiJson.Import.Error::class.java)

        // A joseki line ten thousand nodes deep: kept to the board's 225 points.
        val deep = StringBuilder()
        repeat(10_000) { i -> deep.append("{\"move\":{\"x\":${i % 15},\"y\":${i / 15 % 15}},\"children\":[") }
        deep.append("{}")
        repeat(10_000) { deep.append("]}") }
        val tree = "{\"format\":\"rapfiweb-joseki\",\"trees\":[{\"title\":\"t\",\"root\":{\"children\":[$deep]}}]}"
        JosekiJson.import(tree)
        CurriculumJson.parse("{\"format\":\"rapfiweb-curriculum\",\"schema\":1,\"courses\":[{\"id\":\"c\",\"lessons\":[{\"id\":\"l\",\"root\":{\"children\":[$deep]}}]}]}")
    }

    // ---- fuzzing -------------------------------------------------------------

    private val seeds = mapOf(
        "link" to listOf(
            "https://rapfiweb.pages.dev/board?p=h8i9j10",
            "https://rapfiweb.pages.dev/joseki?lesson=first-three-moves&mode=learn",
            "https://rapfiweb.pages.dev/joseki?course=renju-opening-basics",
            "h8 i9, J10",
        ),
        "psq" to listOf("Piskvorky 15x15, 0:0, 0\n8,8,0\n9,7,0\n7,9,0\n-1\n"),
        "sav" to listOf("15\n15\n3\n7 7\n6 8\n8 6\n"),
        "json" to listOf(
            "{\"format\":\"rapfiweb-joseki\",\"trees\":[{\"title\":\"a\",\"root\":{\"children\":[{\"move\":{\"x\":7,\"y\":7},\"comment\":\"**h8** [x](https://a.b)\",\"children\":[]}]}}]}",
            "{\"format\":\"rapfiweb-curriculum\",\"schema\":1,\"version\":\"1\",\"courses\":[{\"id\":\"c-1\",\"title\":{\"ko\":\"가\",\"en\":\"a\"},\"lessons\":[{\"id\":\"l-1\",\"root\":{\"children\":[{\"move\":{\"x\":7,\"y\":7},\"children\":[{\"move\":{\"x\":8,\"y\":6}}]}]}}]}]}",
        ),
        "text" to listOf(
            "1\t; board size\n15\n0;x\n",
            "12\nbench\ncommand on\n",
            "Consolas,Monospace 10",
            "1=Board\n2=Analyse\n",
            "# Title\n\n*a* **b** `c` [d](https://e.f) [g](javascript:x) h8\n- i\n1. j\n",
        ),
        "rif" to listOf(
            "<?xml version=\"1.0\"?><database><games><game id=\"1\" bresult=\"1\" rule=\"1\"><move>h8 h9 h7</move></game>" +
                "<game id=\"2\" bresult=\"0.5\"><move>h8 i9</move><info>a &amp; b</info></game></games></database>",
        ),
    )

    private val alphabet = "aAhHpPzZ0123456789%&=?#/.,:;-_ \t\n\r{}[]\"\\<>*`!'~&#;é한"

    private fun mutate(seed: String, r: Random): String {
        val s = StringBuilder(seed)
        repeat(1 + r.nextInt(8)) {
            val at = if (s.isEmpty()) 0 else r.nextInt(s.length + 1)
            when (r.nextInt(5)) {
                0 -> s.insert(at, alphabet[r.nextInt(alphabet.length)])
                1 -> if (at < s.length) s.deleteCharAt(at)
                2 -> if (at < s.length) s.setCharAt(at, alphabet[r.nextInt(alphabet.length)])
                3 -> if (s.isNotEmpty()) { // repeat a slice
                    val from = r.nextInt(s.length)
                    val to = (from + r.nextInt(40)).coerceAtMost(s.length)
                    s.insert(at, s.substring(from, to).repeat(1 + r.nextInt(20)))
                }
                else -> s.insert(at, listOf("%", "%zz", "\u0000", "-1", "99999999999", "&#x;", "\"", "]}", "{\"").random(r))
            }
        }
        return s.toString()
    }

    private fun random(r: Random): String =
        String(CharArray(r.nextInt(300)) { alphabet[r.nextInt(alphabet.length)] })

    private fun inputs(kind: String, n: Int = 2_000): Sequence<String> {
        val r = Random(kind.hashCode())
        val pool = seeds.getValue(kind)
        return generateSequence { if (r.nextInt(4) == 0) random(r) else mutate(pool.random(r), r) }.take(n)
    }

    private fun survives(input: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            throw AssertionError("threw ${t::class.simpleName} on input ${input.take(500)}", t)
        }
    }

    @Test
    fun `links never throw`() {
        for (s in inputs("link")) survives(s) {
            LessonLink.parse(s)
            PositionLink.fromText(s, 15)
            PositionLink.parse(s, 22)
            BoardTransform.fromPositionString(s, 15)
        }
    }

    @Test
    fun `game files never throw`() {
        val r = Random(7)
        for (s in inputs("psq") + inputs("sav")) survives(s) {
            for (format in GameFileFormat.entries) GameFile.parse(s.toByteArray(Charsets.ISO_8859_1), format, 15)
        }
        repeat(2_000) {
            val bytes = r.nextBytes(r.nextInt(600))
            survives(bytes.joinToString(",")) { for (format in GameFileFormat.entries) GameFile.parse(bytes, format, 22) }
        }
    }

    @Test
    fun `joseki and curriculum json never throw`() {
        for (s in inputs("json")) survives(s) {
            JosekiJson.import(s)
            CurriculumJson.parse(s)
        }
    }

    @Test
    fun `settings scripts fonts tables and markdown never throw`() {
        for (s in inputs("text")) survives(s) {
            for (file in SettingsFile.entries) SettingsCodec.parse(s, file)
            FunctionScripts.parseToolbar(s)
            FunctionScripts.parseHotkey(s)
            FontSpec.parse(s)
            LngTable.parse(s)
            Markdown.parse(s)
        }
    }

    @Test
    fun `the rif reader never throws`() {
        for (s in inputs("rif", 1_000)) survives(s) {
            val p = RifStreamParser()
            val bytes = s.toByteArray(Charsets.UTF_8)
            // Small chunks, so every element straddles a boundary somewhere.
            var i = 0
            while (i < bytes.size) {
                val n = minOf(7, bytes.size - i)
                p.pushBytes(bytes.copyOfRange(i, i + n), n)
                i += n
            }
            p.finish()
        }
        val r = Random(11)
        repeat(300) {
            val bytes = r.nextBytes(r.nextInt(4_000))
            survives("${bytes.size} random bytes") { RifStreamParser().apply { pushBytes(bytes) }.finish() }
        }
    }
}
