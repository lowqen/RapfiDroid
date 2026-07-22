package dev.gomoku.yixindroid.core.model

/** One completed principal variation (one `INFO PV <idx> … INFO PV DONE` block). */
data class PvSnapshot(
    val index: Int,
    val depth: Int,
    val winRate: Double?, // side-to-move perspective, 0..1
    val mate: Int?,       // +n = side-to-move mates in n, -n = gets mated; null = none
    val line: List<Move>, // BESTLINE, already in board coordinates
) {
    val head: Move? get() = line.firstOrNull()
}

/** Rolling view of a search: the current PVs plus the realtime best marker. */
data class AnalysisSnapshot(
    val sideToMove: StoneColor = StoneColor.BLACK,
    val pvs: List<PvSnapshot> = emptyList(),
    val realtimeBest: Move? = null,
    val depth: Int = 0,
) {
    val best: PvSnapshot? get() = pvs.minByOrNull { it.index }

    /** Win rate from Black's perspective (for the eval bar), or null. */
    fun blackWinRate(): Double? {
        val w = best?.winRate ?: return null
        return if (sideToMove == StoneColor.BLACK) w else 1.0 - w
    }

    fun blackMate(): Int? {
        val m = best?.mate ?: return null
        return if (sideToMove == StoneColor.BLACK) m else -m
    }
}

/** Analysis request knobs (grows in later phases: time, depth, rule). */
data class AnalyzeParams(
    val multiPv: Int = 1,
)
