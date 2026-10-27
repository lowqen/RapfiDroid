package dev.gomoku.rapfidroid.core.model

/**
 * The on-device engine's memory, as far as the phone's RAM is concerned (T3).
 *
 * Measured on the Galaxy A16 (docs/엔진-고도화-마스터플랜.md D5): the engine holds
 * **its hash table plus ~95 MB** (both renju nets, eight threads), and holds it from
 * the start — the table is zero-filled when it is allocated, so a long analysis
 * does not grow it. The table is therefore the one knob, and the tiers below are
 * the steps [LocalEngineProfile.hashForRam] already uses.
 */
object MemoryTier {

    /** Table sizes to step down through after the system killed the engine for memory. */
    val HASH_STEPS: List<Int> = listOf(512, 256, 128, 64)

    /**
     * The next table below [currentMb], or null when there is nothing lower to give
     * — then the kill was not the table's doing and lowering further would not help.
     */
    fun stepDown(currentMb: Int): Int? = HASH_STEPS.firstOrNull { it < currentMb }

    /**
     * The database size worth a warning. yixindb is read whole into the engine's
     * memory, so it adds its own size on top of the table: 5 % of the phone's RAM
     * (~275 MB on a 5.5 GB phone), never below 64 MB.
     */
    fun databaseWarnBytes(totalRamMb: Long): Long =
        maxOf(64L shl 20, (totalRamMb shl 20) / 20)

    /** `ActivityManager`'s exit code for a process killed by signal 9 (128 + 9), or the bare signal. */
    fun isKill(exitCode: Int): Boolean = exitCode == 137 || exitCode == 9
}

/** This profile with its table at most [capMb]; unchanged when [capMb] is null. */
fun LocalEngineProfile.capHash(capMb: Int?): LocalEngineProfile =
    if (capMb == null || capMb >= hashMb) this else copy(hashSizeMb = capMb)
