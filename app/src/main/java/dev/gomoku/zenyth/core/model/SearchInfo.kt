package dev.gomoku.zenyth.core.model

/** One line of the debug console, tagged by direction. */
data class ConsoleLine(
    val outbound: Boolean,
    val text: String,
)
