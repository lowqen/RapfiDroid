package dev.gomoku.rapfidroid.core.model

/**
 * Limits the engine reports about itself. Rapfi prints them once, right after
 * `START`, as `MESSAGE INFO MAX_THREAD_NUM <n>` / `MESSAGE INFO MAX_HASH_SIZE <n>`
 * (main.c:13742). Null means "not reported yet" — the settings UI then leaves the
 * user's value alone instead of clamping it to a placeholder.
 */
data class EngineCapabilities(
    val maxThreadNum: Int? = null,
    val maxHashSizeMb: Int? = null,
) {
    /** Fold one `MESSAGE INFO <key> <value>` line in; unknown keys change nothing. */
    fun with(key: String, value: String): EngineCapabilities {
        val n = value.trim().substringBefore(' ').toIntOrNull() ?: return this
        return when (key.uppercase()) {
            "MAX_THREAD_NUM" -> copy(maxThreadNum = n.coerceAtLeast(1))
            // The engine reports log2(bytes); the desktop converts to MB the same way.
            "MAX_HASH_SIZE" -> copy(maxHashSizeMb = if (n <= 10) 1 else 1 shl (n - 10))
            else -> this
        }
    }
}
