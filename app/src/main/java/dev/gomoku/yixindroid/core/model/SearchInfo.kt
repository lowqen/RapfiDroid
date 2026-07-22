package dev.gomoku.yixindroid.core.model

/**
 * Realtime search snapshot. Populated in P2 once the exact Rapfi INFO/PV line
 * grammar is captured via the P1 raw console; carried here now so the response
 * types are stable. Win rate is normalized to Black; mate is ±M (positive =
 * side-to-move wins).
 */
data class SearchInfo(
    val depth: Int? = null,
    val selDepth: Int? = null,
    val evalMillis: Int? = null,
    val nodes: Long? = null,
    val speedNps: Long? = null,
    val winRate: Double? = null,
    val mate: Int? = null,
    val pv: List<Move> = emptyList(),
)

/** One line of the debug console, tagged by direction. */
data class ConsoleLine(
    val outbound: Boolean,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
)
