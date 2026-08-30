package dev.gomoku.rapfidroid.core.model

/** One completed principal variation (one `INFO PV <idx> … INFO PV DONE` block). */
data class PvSnapshot(
    val index: Int,
    val depth: Int,
    val winRate: Double?, // side-to-move perspective, 0..1
    val mate: Int?,       // +n = side-to-move mates in n, -n = gets mated; null = none
    val eval: Int?,       // score from INFO EVAL when it is not a mate
    val line: List<Move>, // BESTLINE, already in board coordinates
    /**
     * Which deepening round produced this PV, counted from the `INFO PV 0` that
     * opened it. Rounds do not have to be the same width: a deeper one may
     * return fewer PVs than the one before it, and without this the leftovers of
     * the wider round look like part of the narrower one — a stale line then
     * gets read as one of this round's answers.
     */
    val round: Int = 0,
) {
    val head: Move? get() = line.firstOrNull()
}

/** What a per-cell analysis label means (mirrors the desktop's `boardtag`). */
enum class TagKind { RATE, WIN, LOSE }

/**
 * A label drawn on one cell, as the desktop does on each `INFO PV DONE`:
 * `W<n>`/`L<n>` when the PV shows a mate, otherwise the win rate as `nn%`.
 *
 * [round] is what decides when a tag dies. main.c compares depths instead, and
 * a tag from an *earlier* round at the *same* depth therefore survives — so a
 * cell that has dropped out of the candidate set keeps showing its old `L40`
 * next to the current round's percentages. A round number cannot tie with
 * itself the way a depth can.
 */
data class CellTag(
    val label: String,
    val kind: TagKind,
    val depth: Int,
    val winRatePct: Int? = null,
    val round: Int = 0,
)

/** Realtime candidate state from `MESSAGE REALTIME POS` (live) / `DONE` (settled). */
enum class CandidateState { LIVE, DONE }

/** The nine status fields the desktop shows (lng strings 0..9). */
data class SearchStats(
    val depth: Int = 0,
    val selDepth: Int = 0,
    val evalCp: Int? = null,
    val mate: Int? = null,
    val winRatePct: Int? = null,
    val timeMs: Long? = null,
    val nodes: Long? = null,
    val speed: Long? = null,   // nodes / second
    val realtimeVal: Int? = null,
)

/** Rolling view of a search: PVs, per-cell tags and the realtime overlays. */
data class AnalysisSnapshot(
    val sideToMove: StoneColor = StoneColor.BLACK,
    val pvs: List<PvSnapshot> = emptyList(),
    val realtimeBest: Move? = null,
    val depth: Int = 0,
    val tags: Map<Move, CellTag> = emptyMap(),
    val candidates: Map<Move, CandidateState> = emptyMap(),
    val loseCells: Set<Move> = emptySet(),
    val realtimeLine: List<Move> = emptyList(),
    val stats: SearchStats = SearchStats(),
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
    /**
     * Search every defense instead of the k best moves — the desktop's
     * `searchdefend` (main.c:10883), which differs from an ordinary analysis
     * only in the command that starts it: `yxsearchdefend` in place of
     * `yxnbest`. The replies come back on the same channel, one PV per playable
     * defense, so everything downstream is unchanged.
     */
    val defend: Boolean = false,
)
