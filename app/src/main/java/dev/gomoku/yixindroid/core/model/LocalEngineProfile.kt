package dev.gomoku.yixindroid.core.model

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
 *  3. yixindb is loaded whole into RAM (the server's config says so in as many
 *     words), so the database stays off on device.
 *
 * Threads default to 3, not to every core: the little cores add heat and
 * scheduling noise without adding nodes.
 */
data class LocalEngineProfile(
    val threadNum: Int = DEFAULT_THREADS,
    val hashSizeMb: Int = DEFAULT_HASH_MB,
    /** yixindb on device stays off until there is a device-sized database. */
    val useDatabase: Boolean = false,
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
        const val MAX_THREADS = 8
        const val DEFAULT_HASH_MB = 128
        const val MIN_HASH_MB = 16
        const val MAX_HASH_MB = 512
    }
}
