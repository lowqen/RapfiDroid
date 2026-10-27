package dev.gomoku.rapfidroid.core.model

/**
 * How hot the phone is, as far as the on-device engine cares (T1): the platform's
 * thermal status (`PowerManager.THERMAL_STATUS_*`, API 29) and battery saver.
 *
 * The out-of-box profile takes every core ([LocalEngineProfile.forDevice]) because
 * that is the fastest setting on a short search. On a long analysis it is also the
 * hottest: on the Galaxy A16 even 4 threads reached LIGHT inside five minutes
 * (docs/엔진-고도화-마스터플랜.md D4). Past that point the phone throttles the cores
 * anyway, so the threads cost heat and battery without buying nodes — the cap below
 * gives them back before the system takes them.
 *
 * Only what is *sent* to the engine is capped. The stored profile stays the user's,
 * so the threads come back as soon as the phone cools down.
 */
data class ThermalState(
    val status: Int = STATUS_NONE,
    val powerSave: Boolean = false,
) {
    /**
     * The most threads the engine may use now for a profile of [threads], or null
     * when nothing needs to give. MODERATE halves the count (never below 2 — two big
     * cores carry most of the work), SEVERE and above leave one, and battery saver
     * holds it at 2 whatever the temperature.
     */
    fun threadCap(threads: Int): Int? {
        val heat = when {
            status >= STATUS_SEVERE -> 1
            status >= STATUS_MODERATE -> ((threads + 1) / 2).coerceAtLeast(MIN_MODERATE)
            else -> null
        }
        val saver = if (powerSave) POWER_SAVE_THREADS else null
        val cap = listOfNotNull(heat, saver).minOrNull() ?: return null
        return cap.takeIf { it < threads }
    }

    companion object {
        // PowerManager.THERMAL_STATUS_* — copied so this stays a plain JVM type the tests can use.
        const val STATUS_NONE = 0
        const val STATUS_LIGHT = 1
        const val STATUS_MODERATE = 2
        const val STATUS_SEVERE = 3
        const val STATUS_CRITICAL = 4

        private const val MIN_MODERATE = 2
        private const val POWER_SAVE_THREADS = 2
    }
}

/** This profile with at most [cap] threads; unchanged when [cap] is null. */
fun LocalEngineProfile.capThreads(cap: Int?): LocalEngineProfile =
    if (cap == null || cap >= threads) this else copy(threadNum = cap)
