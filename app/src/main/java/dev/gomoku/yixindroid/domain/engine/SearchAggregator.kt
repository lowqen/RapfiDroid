package dev.gomoku.yixindroid.domain.engine

import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.CandidateState
import dev.gomoku.yixindroid.core.model.CellTag
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.PvSnapshot
import dev.gomoku.yixindroid.core.model.SearchStats
import dev.gomoku.yixindroid.core.model.StoneColor
import dev.gomoku.yixindroid.core.model.TagKind
import kotlin.math.roundToInt

/**
 * Stateful assembler that turns the per-line engine stream into
 * [AnalysisSnapshot]s. Ported from the desktop dispatcher (main.c
 * `iochannelout_watch`), including its per-cell tag bookkeeping:
 *
 *  - each `INFO PV <i>` … `INFO PV DONE` block commits one [PvSnapshot];
 *  - on DONE the head of that PV's BESTLINE gets a tag (`W<n>`/`L<n>` for a mate,
 *    else `nn%`) recorded together with the iteration depth;
 *  - on the last PV of a round (`curpvidx + 1 == curnumpv`) every tag shallower
 *    than the current depth is cleared, exactly as the desktop does;
 *  - `MESSAGE REALTIME POS/DONE/LOSE/REFRESH/BEST/VAL/PV` drive the live overlays.
 *
 * Unlike the desktop we also emit **partial** snapshots (depth/eval/winrate as
 * they arrive) so the board reacts immediately instead of only on DONE.
 * Not thread-safe: drive it from a single collector.
 */
class SearchAggregator(private val sideToMove: StoneColor) {

    private var curIndex = 0
    private var curDepth = 0
    private var curMate: Int? = null
    private var curEval: Int? = null
    private var curWinRate: Double? = null
    private var curLine: List<Move> = emptyList()

    private var depth = 0
    private var numPv = 1

    /**
     * True once a real `INFO PV` block has been seen. Both output formats can
     * arrive in detailed mode, and the plain-text line carries only the first
     * PV — so after this flips, [EngineResponse.Thinking] contributes counters
     * only and never overwrites the authoritative PV set.
     */
    private var sawInfoPv = false
    private var realtimeBest: Move? = null
    private var realtimeLine: List<Move> = emptyList()
    private var stats = SearchStats()

    private val pvs = sortedMapOf<Int, PvSnapshot>()
    private val tags = LinkedHashMap<Move, CellTag>()
    private val candidates = LinkedHashMap<Move, CandidateState>()
    private val loseCells = LinkedHashSet<Move>()

    /** Feed one parsed response; returns a new snapshot when it changed, else null. */
    fun consume(response: EngineResponse): AnalysisSnapshot? {
        when (response) {
            is EngineResponse.InfoPvStart -> {
                sawInfoPv = true
                curIndex = response.index
                curMate = null
                curEval = null
                curWinRate = null
                curLine = emptyList()
                return null
            }

            is EngineResponse.InfoNumPv -> {
                numPv = response.count.coerceAtLeast(1)
                val stale = pvs.keys.filter { it >= numPv }
                stale.forEach { pvs.remove(it) }
                if (stale.isEmpty()) return null
            }

            is EngineResponse.InfoDepth -> {
                curDepth = response.depth
                depth = response.depth
                stats = stats.copy(depth = response.depth)
            }

            is EngineResponse.InfoEval -> {
                curMate = response.mate
                curEval = response.cp
                stats = stats.copy(mate = response.mate, evalCp = response.cp)
            }

            is EngineResponse.InfoWinRate -> {
                curWinRate = response.winRate
                stats = stats.copy(winRatePct = pct(response.winRate))
            }

            is EngineResponse.InfoBestline -> curLine = response.line

            is EngineResponse.InfoPvDone -> {
                pvs[curIndex] = PvSnapshot(
                    index = curIndex, depth = curDepth, winRate = curWinRate,
                    mate = curMate, eval = curEval, line = curLine,
                )
                curLine.firstOrNull()?.let { head -> tags[head] = tagFor(head) }
                // Last PV of this iteration: drop tags left over from shallower ones.
                if (curIndex + 1 >= numPv) {
                    tags.entries.retainAll { it.value.depth >= curDepth }
                }
            }

            is EngineResponse.InfoStat -> {
                stats = when (response.key) {
                    "NODE", "NODES" -> stats.copy(nodes = response.value)
                    "SPEED" -> stats.copy(speed = response.value)
                    "TIME" -> stats.copy(timeMs = response.value)
                    else -> return null
                }
            }

            // Fallback path: the engine's plain-text thinking line.
            is EngineResponse.Thinking -> {
                stats = stats.copy(
                    depth = response.depth ?: stats.depth,
                    selDepth = response.selDepth ?: stats.selDepth,
                    evalCp = response.evalCp ?: stats.evalCp,
                    mate = response.mate ?: stats.mate,
                    timeMs = response.timeMs ?: stats.timeMs,
                    nodes = response.nodes ?: stats.nodes,
                    speed = response.speed ?: stats.speed,
                )
                if (!sawInfoPv) {
                    response.depth?.let { curDepth = it; depth = it }
                    if (response.line.isNotEmpty()) {
                        curLine = response.line
                        curMate = response.mate
                        curEval = response.evalCp
                        pvs[0] = PvSnapshot(
                            index = 0, depth = response.depth ?: curDepth, winRate = curWinRate,
                            mate = response.mate, eval = response.evalCp, line = response.line,
                        )
                    }
                }
            }

            is EngineResponse.RealtimeBest -> realtimeBest = response.move
            is EngineResponse.RealtimePos -> candidates[response.move] = CandidateState.LIVE
            is EngineResponse.RealtimeDone -> {
                if (candidates[response.move] == CandidateState.LIVE) {
                    candidates[response.move] = CandidateState.DONE
                } else return null
            }
            is EngineResponse.RealtimeLose -> loseCells.add(response.move)
            is EngineResponse.RealtimePv -> realtimeLine = response.line
            is EngineResponse.RealtimeVal -> stats = stats.copy(realtimeVal = response.value)
            is EngineResponse.RealtimeRefresh -> candidates.clear()

            else -> return null
        }
        return snapshot()
    }

    fun reset() {
        pvs.clear()
        tags.clear()
        candidates.clear()
        loseCells.clear()
        realtimeBest = null
        realtimeLine = emptyList()
        stats = SearchStats()
        depth = 0
        curIndex = 0
        numPv = 1
        sawInfoPv = false
    }

    /**
     * The desktop's tag: a mate shows as `W<n>`/`L<n>`, otherwise the win rate as
     * `nn%` (clamped to 99, as main.c does).
     */
    private fun tagFor(head: Move): CellTag {
        val mate = curMate
        val ratePct = curWinRate?.let { pct(it) }
        return when {
            mate != null && mate > 0 -> CellTag("W$mate", TagKind.WIN, curDepth, ratePct)
            mate != null && mate < 0 -> CellTag("L${-mate}", TagKind.LOSE, curDepth, ratePct)
            ratePct != null -> CellTag("$ratePct%", TagKind.RATE, curDepth, ratePct)
            else -> CellTag("", TagKind.RATE, curDepth, null)
        }
    }

    private fun pct(winRate: Double): Int =
        (winRate * 100).roundToInt().coerceIn(0, 99)

    private fun snapshot() =
        AnalysisSnapshot(
            sideToMove = sideToMove,
            pvs = pvs.values.toList(),
            realtimeBest = realtimeBest,
            depth = depth,
            tags = LinkedHashMap(tags),
            candidates = LinkedHashMap(candidates),
            loseCells = LinkedHashSet(loseCells),
            realtimeLine = realtimeLine,
            stats = stats,
        )
}
