package dev.gomoku.zenyth.domain.engine

import com.google.common.truth.Truth.assertWithMessage
import dev.gomoku.zenyth.core.model.StoneColor
import dev.gomoku.zenyth.domain.engine.reference.ReferenceResponseParser
import dev.gomoku.zenyth.domain.engine.reference.ReferenceSearchAggregator
import org.junit.Test

/**
 * The engine's read path, held to its frozen self (docs/리팩토링-성능-위생-계획.md,
 * Phase 0 → 2). Phase 2 rewrites [ResponseParser] and [SearchAggregator] for
 * fewer allocations; whatever it does, every line must parse to the same
 * response — or fail the same way — and every response must leave the same
 * snapshot as the copies in `reference/` that were taken before it started.
 */
class EngineIoEquivalenceTest {

    private val mappers = listOf(CoordMapper(), CoordMapper(15, flipY = true), CoordMapper(20))

    private val streams: Map<String, List<String>> = mapOf(
        "corpus" to EngineStreams.corpus,
        "search" to EngineStreams.search(depths = 18, multiPv = 3),
        "search-1pv" to EngineStreams.search(depths = 12, multiPv = 1, seed = 3),
        "fuzz" to EngineStreams.fuzz(30_000),
        "mutated" to EngineStreams.mutated(30_000),
    )

    /** A result, or the kind of failure; a parser that throws must keep throwing. */
    private fun outcome(block: () -> EngineResponse): Any =
        runCatching(block).getOrElse { "threw ${it::class.qualifiedName}" }

    @Test
    fun `every line parses as it did before`() {
        val misses = mutableListOf<String>()
        for ((name, lines) in streams) {
            for (coord in mappers) {
                for (line in lines) {
                    val before = outcome { ReferenceResponseParser.parse(line, coord) }
                    val after = outcome { ResponseParser.parse(line, coord) }
                    if (before != after && misses.size < 20) {
                        misses += "[$name size=${coord.size} flipY=${coord.flipY}] «$line»\n  before $before\n  after  $after"
                    }
                }
            }
        }
        assertWithMessage(misses.joinToString("\n")).that(misses).isEmpty()
    }

    @Test
    fun `every response leaves the snapshot it did before`() {
        val misses = mutableListOf<String>()
        for ((name, lines) in streams) {
            val responses = lines.mapNotNull { runCatching { ResponseParser.parse(it, CoordMapper()) }.getOrNull() }
            for (side in StoneColor.entries) {
                val before = ReferenceSearchAggregator(side)
                val after = SearchAggregator(side)
                responses.forEachIndexed { i, r ->
                    // Now and then the search starts over, as a restart does.
                    if (i % 997 == 996) {
                        before.reset()
                        after.reset()
                    }
                    val b = before.consume(r)
                    val a = after.consume(r)
                    if (b != a && misses.size < 10) misses += "[$name $side #$i] ${r.raw}\n  before $b\n  after  $a"
                }
                if (before.snapshot() != after.snapshot() && misses.size < 10) misses += "[$name $side] final snapshot"
                if (before.finalPvs() != after.finalPvs() && misses.size < 10) misses += "[$name $side] finalPvs"
            }
        }
        assertWithMessage(misses.joinToString("\n")).that(misses).isEmpty()
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
