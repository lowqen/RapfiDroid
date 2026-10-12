package dev.gomoku.rapfidroid.domain.engine

/**
 * The two builds of the on-device engine. Both search the **same tree** — the
 * `BENCH` hash is `e240ab16` for either — so picking one changes how fast the
 * phone analyses, never what it concludes.
 *
 *  - [DOTPROD] is built for ARMv8.2 (`-march=armv8.2-a+dotprod`): the NNUE's
 *    int8 dot product becomes one `sdot` instead of four instructions, which
 *    measured **+30%** nodes/s on a Galaxy A16 (docs/엔진-고도화-마스터플랜.md, D0).
 *  - [NEON] is plain ARMv8.0 and runs on every arm64 phone.
 *
 * Binary names must stay `lib*.so`: the package manager extracts nothing else,
 * and the extracted directory is the only place an app may execute from.
 */
enum class EngineVariant(val binaryName: String, val label: String) {
    DOTPROD("libengine_dotprod.so", "dotprod"),
    NEON("libengine.so", "neon"),
}

/**
 * Chooses the build this CPU can run. Pure, so the decision can be tested
 * without a phone; the installer feeds it `/proc/cpuinfo` and what it knows.
 */
object EngineVariantSelector {

    /**
     * What `-march=armv8.2-a+dotprod` lets the compiler emit, as Linux names it:
     * the dot product itself, and the two ARMv8.1 features that come with the
     * 8.2 baseline — LSE atomics (`std::atomic` turns into `ldadd`/`cas`) and
     * RDM. A CPU that reports `asimddp` has the others in practice, but a
     * missing one would SIGILL at the first atomic, so all three are required.
     */
    val REQUIRED_FEATURES: Set<String> = setOf("asimddp", "atomics", "asimdrdm")

    /**
     * How a process killed by SIGILL (signal 4) exits, as `java.lang.Process`
     * reports it: `128 + 4` from the OpenJDK-based libcore (Android 7+, which is
     * all of minSdk 26), the bare signal number from the older ProcessManager.
     * Rapfi itself only ever exits 0 or 1, so reading a plain 4 as SIGILL can
     * only err towards NEON — slower, never broken.
     */
    val SIGILL_EXIT_CODES: Set<Int> = setOf(128 + 4, 4)

    /**
     * Features **every** core reports. big.LITTLE parts list each core, and the
     * scheduler may move the engine onto any of them, so a feature only one
     * cluster has does not count. Empty when no `Features` line is present
     * (an unknown format is not evidence of dotprod).
     */
    fun commonFeatures(cpuinfo: String): Set<String> =
        cpuinfo.lineSequence()
            .filter { it.substringBefore(':').trim().equals("Features", ignoreCase = true) }
            .map { it.substringAfter(':').trim().split(Regex("\\s+")).filter(String::isNotEmpty).toSet() }
            .reduceOrNull { acc, set -> acc intersect set }
            .orEmpty()

    /**
     * @param cpuinfo the text of `/proc/cpuinfo`, or null when it could not be read
     * @param dotprodShipped whether `libengine_dotprod.so` is actually in the APK
     * @param dotprodFailed whether the dotprod build already died of SIGILL here
     */
    fun choose(cpuinfo: String?, dotprodShipped: Boolean, dotprodFailed: Boolean): EngineVariant {
        if (!dotprodShipped || dotprodFailed || cpuinfo == null) return EngineVariant.NEON
        return if (commonFeatures(cpuinfo).containsAll(REQUIRED_FEATURES)) {
            EngineVariant.DOTPROD
        } else {
            EngineVariant.NEON
        }
    }

    /** True when [variant] ended in a way that means "this CPU cannot run it". */
    fun isIllegalInstruction(variant: EngineVariant, exitCode: Int): Boolean =
        variant == EngineVariant.DOTPROD && exitCode in SIGILL_EXIT_CODES
}
