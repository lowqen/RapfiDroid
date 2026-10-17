package dev.gomoku.rapfidroid.core.model

/**
 * What the on-device engine is allowed to take. **This is a safety limit before
 * it is a preference.**
 *
 * The desktop settings this app mirrors ask for 4 threads, an 8192 MB hash and
 * the database attached — sane on the EPYC server, fatal on a phone:
 *
 *  1. `INFO hash_size` is handed straight to the searcher's memory limit
 *     (`gomocup.cpp`, the `HASH_SIZE` branch). 8 GiB does not fail cleanly: the
 *     transposition table halves until an allocation succeeds, and a table that
 *     "succeeds" at 2 GB under overcommit gets the app killed the moment it is
 *     filled.
 *  2. The engine's own reply, `INFO MAX_HASH_SIZE 30`, is converted by
 *     [EngineCapabilities] with the desktop's formula and lands far above
 *     anything a phone has — so the existing clamp does not catch this.
 * Threads default to 3 and go up to [MAX_THREADS]. Measured on a Galaxy A16
 * (2 big + 6 little cores, docs/엔진-고도화-마스터플랜.md D4 and the peak test):
 * the little cores **do** add nodes — 4 → 6 → 8 threads gave ×1.00 → ×1.24 →
 * ×1.58 peak nodes/s on short searches — but 4 threads already raised the
 * thermal status within five minutes, and 8 leave nothing free for the UI. So
 * the default stays the cheap middle and the maximum is the user's to pick.
 *
 * The database is a different matter and is **on**. yixindb is held whole in
 * RAM, which is why the server's config refuses to attach *its* database at
 * boot — but the device's own starts empty and grows only from what this phone
 * analysed, so it costs what it holds. The size worth warning about arrives by
 * import, not by use.
 */
data class LocalEngineProfile(
    val threadNum: Int = DEFAULT_THREADS,
    val hashSizeMb: Int = DEFAULT_HASH_MB,
    /** `rapfi.db` next to the engine, in this app's own storage. */
    val useDatabase: Boolean = true,
) {
    val threads: Int get() = threadNum.coerceIn(1, MAX_THREADS)
    val hashMb: Int get() = hashSizeMb.coerceIn(MIN_HASH_MB, MAX_HASH_MB)

    /** `default_tt_size_kb` for the generated `config.toml` (KiB, as Rapfi reads it). */
    val ttSizeKb: Long get() = hashMb.toLong() shl 10

    /**
     * The parameters the desktop settings produced, brought down to what this
     * phone can actually hold. Everything else — rule, level, caution factor,
     * multi-PV — is left exactly as the user set it, because those are what make
     * the two ends agree.
     */
    fun clamp(params: EngineParams): EngineParams = params.copy(
        threadNum = threads,
        hashSizeMb = hashMb,
        useDatabase = useDatabase,
    )

    companion object {
        const val DEFAULT_THREADS = 3
        /** Every core of an 8-core phone — the fastest setting measured (see the class doc). */
        const val MAX_THREADS = 8

        /**
         * The thread counts the settings screen offers. Always contains the
         * default (so a fresh install shows its own value as selected) and the
         * maximum (so the fastest setting is never hidden behind the chip list).
         */
        val THREAD_CHOICES: List<Int> =
            (listOf(1, 2, DEFAULT_THREADS, 4, 6, MAX_THREADS).filter { it in 1..MAX_THREADS }).distinct().sorted()
        const val DEFAULT_HASH_MB = 128
        const val MIN_HASH_MB = 16
        const val MAX_HASH_MB = 512
    }
}
