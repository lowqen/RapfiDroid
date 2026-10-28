package dev.gomoku.zenyth

import java.io.File
import java.lang.management.ManagementFactory

/**
 * The before/after numbers of the performance work (docs/리팩토링-성능-위생-계획.md).
 *
 * The baseline tests measure only when `ZENYTH_PERF_OUT` names a folder — the
 * `perf-baseline.yml` run sets it — and are skipped otherwise, so the ordinary
 * suite stays quick and nothing in it depends on a stopwatch. They assert
 * nothing: they write what they counted to `<folder>/<name>.txt`.
 *
 * Counts (allocations, emissions, recompositions) are the numbers to compare;
 * they are nearly deterministic. Times are reported for colour only, and only a
 * CI run's are worth reading.
 */
internal object PerfBaseline {

    private val out: File? = System.getenv("ZENYTH_PERF_OUT")?.takeIf { it.isNotBlank() }?.let(::File)

    val enabled: Boolean get() = out != null

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    /** Bytes this thread has allocated so far. */
    fun allocatedBytes(): Long = threads.getThreadAllocatedBytes(Thread.currentThread().id)

    fun report(name: String, lines: List<String>) {
        val text = lines.joinToString("\n", postfix = "\n")
        println("---- $name ----\n$text")
        val dir = out ?: return
        dir.mkdirs()
        File(dir, "$name.txt").writeText(text)
    }
}
