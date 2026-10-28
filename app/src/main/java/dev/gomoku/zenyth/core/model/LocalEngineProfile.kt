package dev.gomoku.zenyth.core.model

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
 * The defaults are the device's, not constants ([forDevice], R3): **every core**
 * and a table sized by RAM. Measured on a Galaxy A16 (2 big + 6 little cores,
 * docs/엔진-고도화-마스터플랜.md D4 · peak · R3): the little cores do add nodes —
 * 4 → 6 → 8 threads gave ×1.00 → ×1.24 → ×1.58 peak nodes/s on short searches,
 * and 8 against 4 scored +40 Elo over 70 games (not yet significant). The heat on long
 * analyses is the user's to trade back from the settings screen; the
 * out-of-box answer is the fastest one. [DEFAULT_THREADS]/[DEFAULT_HASH_MB] are
 * only the fallback when the device cannot be read.
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
        /** Fallback only — when the device cannot be read; see [forDevice]. */
        const val DEFAULT_THREADS = 3
        /** Every core of an 8-core phone — the fastest setting measured (see the class doc). */
        const val MAX_THREADS = 8

        /**
         * The thread counts the settings screen offers. Always contains the
         * fallback default and the maximum (so the fastest setting is never
         * hidden behind the chip list); [threadChoices] adds the device's own.
         */
        val THREAD_CHOICES: List<Int> =
            (listOf(1, 2, DEFAULT_THREADS, 4, 6, MAX_THREADS).filter { it in 1..MAX_THREADS }).distinct().sorted()

        /** [THREAD_CHOICES] plus [default], so a 5- or 7-core phone still sees its own value selected. */
        fun threadChoices(default: Int): List<Int> =
            (THREAD_CHOICES + default.coerceIn(1, MAX_THREADS)).distinct().sorted()

        /** Fallback only — when the device cannot be read; see [forDevice]. */
        const val DEFAULT_HASH_MB = 128
        const val MIN_HASH_MB = 16
        const val MAX_HASH_MB = 512

        /**
         * The out-of-box profile for this phone (R3): all of its cores, capped at
         * [MAX_THREADS], and the largest table its RAM tier carries comfortably.
         * `null` (the device could not be read) falls back to the constants.
         */
        fun forDevice(device: DeviceSpec?): LocalEngineProfile {
            if (device == null) return LocalEngineProfile()
            return LocalEngineProfile(
                threadNum = device.cores.coerceIn(1, MAX_THREADS),
                hashSizeMb = hashForRam(device.totalRamMb),
            )
        }

        /**
         * Tiers by the RAM the system reports, which is below the box number: a
         * "4 GB" phone reports ~3.6 GB, "6 GB" ~5.5 GB (the A16), "8 GB" ~7.4 GB.
         * The engine with a 128 MB table measured ~180 MB resident on the A16, so
         * 256 MB there is ~0.3 GB of 5.5 — and the table is still the user's to shrink.
         */
        fun hashForRam(totalRamMb: Long): Int = when {
            totalRamMb <= 0 -> DEFAULT_HASH_MB
            totalRamMb < 3_000 -> 64
            totalRamMb < 4_500 -> 128
            totalRamMb < 7_000 -> 256
            else -> MAX_HASH_MB
        }

        /** The fixed defaults before R3, which installs from then on may have stored. */
        const val LEGACY_THREADS = 3
        const val LEGACY_HASH_MB = 128

        /**
         * The profile from what the store holds. Before R3 the settings screen
         * wrote *all three* numbers whenever any one changed, so a stored 3 or
         * 128 from that time is far more likely the old default riding along than
         * a choice — [legacy] (no version stamp yet) reads those two as unset, so
         * such installs get the device's numbers too. A missing value is the
         * device default; anything else is the user's.
         */
        fun fromStored(
            threads: Int?,
            hashMb: Int?,
            useDatabase: Boolean?,
            legacy: Boolean,
            defaults: LocalEngineProfile,
        ): LocalEngineProfile = LocalEngineProfile(
            threadNum = threads?.takeUnless { legacy && it == LEGACY_THREADS } ?: defaults.threadNum,
            hashSizeMb = hashMb?.takeUnless { legacy && it == LEGACY_HASH_MB } ?: defaults.hashSizeMb,
            useDatabase = useDatabase ?: defaults.useDatabase,
        )
    }
}

/** What [LocalEngineProfile.forDevice] needs to know about the phone. */
data class DeviceSpec(val cores: Int, val totalRamMb: Long) {
    companion object {
        /**
         * Count the CPUs in a kernel cpu list (`/sys/devices/system/cpu/possible`),
         * e.g. `0-7` or `0-3,6-7`. Read instead of `availableProcessors()`, which
         * counts only the cores *online* right now — phones park cores when idle,
         * so it can say 4 on an 8-core phone at the moment the app starts.
         */
        fun countCpuList(text: String): Int? {
            var n = 0
            for (part in text.trim().split(',')) {
                if (part.isBlank()) continue
                val bounds = part.trim().split('-')
                val lo = bounds[0].toIntOrNull() ?: return null
                val hi = (if (bounds.size > 1) bounds[1].toIntOrNull() else lo) ?: return null
                if (hi < lo) return null
                n += hi - lo + 1
            }
            return n.takeIf { it > 0 }
        }
    }
}
