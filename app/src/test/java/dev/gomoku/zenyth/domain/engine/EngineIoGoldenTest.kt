package dev.gomoku.zenyth.domain.engine

import com.google.common.truth.Truth.assertWithMessage
import dev.gomoku.zenyth.PerfBaseline
import dev.gomoku.zenyth.core.model.StoneColor
import org.junit.Test
import java.security.MessageDigest

/**
 * The engine's read path, held to what it has always given
 * (docs/리팩토링-성능-위생-계획.md, Phase 7-6). For every stream of
 * [EngineStreams] — the grammar's corpus, two Rapfi-shaped searches, 30 000
 * seeded fuzz lines and 30 000 lines bent with Unicode — and every board
 * mapping: what [ResponseParser] makes of each line (or the failure it
 * raises), and every snapshot [SearchAggregator] builds from the responses,
 * hashed. `engine_io_golden.txt` holds the hashes, recorded from the code that
 * frozen pre-Phase-2 copies had proved equal to their own, line for line; the
 * copies are gone, the hashes keep the promise.
 */
class EngineIoGoldenTest {

    private val mappers = listOf(CoordMapper(), CoordMapper(15, flipY = true), CoordMapper(20))

    private val streams: Map<String, List<String>> = mapOf(
        "corpus" to EngineStreams.corpus,
        "search" to EngineStreams.search(depths = 18, multiPv = 3),
        "search-1pv" to EngineStreams.search(depths = 12, multiPv = 1, seed = 3),
        "fuzz" to EngineStreams.fuzz(30_000),
        "mutated" to EngineStreams.mutated(30_000),
    )

    private fun outcome(block: () -> EngineResponse): String =
        runCatching(block).fold({ it.toString() }, { "threw ${it::class.qualifiedName}" })

    private class Digest {
        private val md = MessageDigest.getInstance("SHA-256")
        var count = 0
            private set

        fun add(text: String) {
            md.update(text.toByteArray(Charsets.UTF_8))
            md.update(0)
            count++
        }

        fun hex(): String = md.digest().joinToString("") { "%02x".format(it) }
    }

    /** One line per stream and mapping, and per stream and side: kind, name, hash, count. */
    private fun current(): List<String> = buildList {
        for ((name, lines) in streams) {
            for (coord in mappers) {
                val d = Digest()
                lines.forEach { d.add(outcome { ResponseParser.parse(it, coord) }) }
                add("parse $name size=${coord.size} flipY=${coord.flipY} ${d.hex()} ${d.count}")
            }
        }
        for ((name, lines) in streams) {
            val responses = lines.mapNotNull { runCatching { ResponseParser.parse(it, CoordMapper()) }.getOrNull() }
            for (side in StoneColor.entries) {
                val agg = SearchAggregator(side)
                val d = Digest()
                responses.forEachIndexed { i, r ->
                    // Now and then the search starts over, as a restart does.
                    if (i % 997 == 996) agg.reset()
                    d.add(agg.consume(r).toString())
                }
                d.add(agg.snapshot().toString())
                d.add(agg.finalPvs().toString())
                add("aggregate $name $side ${d.hex()} ${d.count}")
            }
        }
    }

    @Test
    fun `the engine read path gives what it always gave`() {
        val now = current()
        val golden = javaClass.getResourceAsStream("/engine_io_golden.txt")?.bufferedReader()?.readLines()
            ?.filter { it.isNotBlank() && !it.startsWith("#") }
        if (golden == null) {
            // Missing is a failure, never a skip. A perf-baseline run writes the
            // current hashes out (perf/engine_io_golden.txt) to be looked at.
            PerfBaseline.report("engine_io_golden", now)
            throw AssertionError("engine_io_golden.txt is missing from the test resources")
        }
        val changed = now.filterNot { it in golden }
        assertWithMessage("streams whose output changed:\n" + changed.joinToString("\n"))
            .that(now).containsExactlyElementsIn(golden).inOrder()
    }

    @Test
    fun `the streams reach every kind of response`() {
        val kinds = streams.values.flatten()
            .mapNotNull { runCatching { ResponseParser.parse(it, CoordMapper()) }.getOrNull() }
            .map { it::class.java.simpleName }
            .toSet()
        assertWithMessage("response kinds the streams never produce")
            .that(allKinds - kinds)
            .isEmpty()
    }

    /** Every [EngineResponse] — spelt out, since the test classpath has no kotlin-reflect. */
    private val allKinds = setOf(
        "BestMove", "InfoPvStart", "InfoPvDone", "InfoNumPv", "InfoDepth", "InfoEval", "InfoWinRate",
        "InfoBestline", "InfoStat", "Thinking", "RealtimeBest", "RealtimePos", "RealtimeLose", "RealtimeDone",
        "RealtimePv", "RealtimeVal", "RealtimeRefresh", "Forbid", "DbCellValue", "DbRefresh", "DbDone", "DbOne",
        "DbTextLine", "DbFileEvent", "OpeningMove", "OpeningSwap", "SoosorvFifth", "Capability", "Ok", "Message",
        "Debug", "Error", "About", "Unknown",
    )
}
