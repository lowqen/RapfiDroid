package dev.gomoku.rapfidroid.feature.connection

import dev.gomoku.rapfidroid.core.model.ConsoleLine

/** Immutable, capped view of recent console lines (newest kept). */
data class ConsoleBuffer(val lines: List<ConsoleLine> = emptyList()) {
    fun plus(line: ConsoleLine): ConsoleBuffer {
        val next = if (lines.size >= MAX) lines.drop(lines.size - MAX + 1) else lines
        return ConsoleBuffer(next + line)
    }

    private companion object {
        const val MAX = 500
    }
}
