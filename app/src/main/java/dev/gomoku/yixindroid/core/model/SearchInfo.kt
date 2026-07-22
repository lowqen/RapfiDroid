package dev.gomoku.yixindroid.core.model

/** One line of the debug console, tagged by direction. */
data class ConsoleLine(
    val outbound: Boolean,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
)
