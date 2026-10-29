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

    /** `ZENYTH_JFR_DIR`: where [profiled] leaves its recordings; unset, it records nothing. */
    val jfrDir: File? = System.getenv("ZENYTH_JFR_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    /**
     * [block] under a Flight Recorder allocation profile, written to
     * `<jfrDir>/<name>.jfr` — the measured stretch alone, not the test's
     * start-up around it.
     */
    inline fun <T> profiled(name: String, block: () -> T): T {
        val dir = jfrDir ?: return block()
        val recording = Recorder.start()
        try {
            return block()
        } finally {
            dir.mkdirs()
            Recorder.stop(recording, File(dir, "$name.jfr"))
        }
    }

    /**
     * `jdk.jfr.Recording`, reached through the system class loader: inside a
     * Robolectric test the sandbox loader would instrument `jdk.jfr` itself,
     * which the module system refuses.
     */
    object Recorder {
        private val loader = ClassLoader.getSystemClassLoader()
        private val recording = loader.loadClass("jdk.jfr.Recording")
        private val settings = loader.loadClass("jdk.jfr.EventSettings")

        fun start(): Any {
            val r = recording.getConstructor().newInstance()
            val s = recording.getMethod("enable", String::class.java).invoke(r, "jdk.ObjectAllocationSample")
            settings.getMethod("with", String::class.java, String::class.java).invoke(s, "throttle", "1000000/s")
            settings.getMethod("withStackTrace").invoke(s)
            recording.getMethod("start").invoke(r)
            return r
        }

        fun stop(r: Any, file: File) {
            recording.getMethod("stop").invoke(r)
            recording.getMethod("dump", java.nio.file.Path::class.java).invoke(r, file.toPath())
            recording.getMethod("close").invoke(r)
        }
    }

    fun report(name: String, lines: List<String>) {
        val text = lines.joinToString("\n", postfix = "\n")
        println("---- $name ----\n$text")
        val dir = out ?: return
        dir.mkdirs()
        File(dir, "$name.txt").writeText(text)
    }
}
