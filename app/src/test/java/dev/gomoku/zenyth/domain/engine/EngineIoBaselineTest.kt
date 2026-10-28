package dev.gomoku.zenyth.domain.engine

import dev.gomoku.zenyth.PerfBaseline
import dev.gomoku.zenyth.core.model.StoneColor
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * What the engine's read path costs per line: allocation and time for
 * [ResponseParser], then for [SearchAggregator] on top. Phase 2's before and
 * after (docs/리팩토링-성능-위생-계획.md). See [PerfBaseline].
 */
class EngineIoBaselineTest {

    private val lines = EngineStreams.search(depths = 24, multiPv = 3)
    private val coord = CoordMapper()

    private class Pass(val bytes: Long, val nanos: Long)

    private inline fun measure(passes: Int, block: () -> Unit): List<Pass> = List(passes) {
        val bytes = PerfBaseline.allocatedBytes()
        val start = System.nanoTime()
        block()
        Pass(PerfBaseline.allocatedBytes() - bytes, System.nanoTime() - start)
    }

    private fun summary(label: String, passes: List<Pass>, perPass: Int): String {
        val bytes = passes.map { it.bytes.toDouble() / perPass }.sorted()
        val nanos = passes.map { it.nanos.toDouble() / perPass }.sorted()
        return "%-28s %8.1f B/line  %8.1f ns/line  (median of %d passes, %d lines each)"
            .format(label, bytes[bytes.size / 2], nanos[nanos.size / 2], passes.size, perPass)
    }

    @Test
    fun `engine read path per line`() {
        assumeTrue(PerfBaseline.enabled)
        var sink = 0
        repeat(WARMUP) { lines.forEach { sink += ResponseParser.parse(it, coord).raw.length } }
        val parse = measure(PASSES) { lines.forEach { sink += ResponseParser.parse(it, coord).raw.length } }

        val responses = lines.map { ResponseParser.parse(it, coord) }
        var snapshots = 0
        repeat(WARMUP) {
            val agg = SearchAggregator(StoneColor.BLACK)
            responses.forEach { if (agg.consume(it) != null) sink++ }
        }
        val aggregate = measure(PASSES) {
            val agg = SearchAggregator(StoneColor.BLACK)
            snapshots = 0
            responses.forEach { if (agg.consume(it) != null) snapshots++ }
        }

        PerfBaseline.report(
            "engine_io",
            listOf(
                "lines per pass: ${lines.size}; snapshots per pass: $snapshots (${"%.2f".format(snapshots.toDouble() / lines.size)} per line)",
                summary("ResponseParser.parse", parse, lines.size),
                summary("SearchAggregator.consume", aggregate, lines.size),
                "(sink $sink)",
            ),
        )
    }

    private companion object {
        const val WARMUP = 20
        const val PASSES = 15
    }
}
