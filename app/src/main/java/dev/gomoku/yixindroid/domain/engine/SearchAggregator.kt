package dev.gomoku.yixindroid.domain.engine

import dev.gomoku.yixindroid.core.model.AnalysisSnapshot
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.PvSnapshot
import dev.gomoku.yixindroid.core.model.StoneColor

/**
 * Stateful assembler that turns the per-line `INFO …` stream into
 * [AnalysisSnapshot]s, mirroring the desktop's cur* accumulation committed on
 * `INFO PV DONE`. Not thread-safe: drive it from a single collector.
 */
class SearchAggregator(private val sideToMove: StoneColor) {

    private var curIndex = 0
    private var curDepth = 0
    private var curMate: Int? = null
    private var curWinRate: Double? = null
    private var curLine: List<Move> = emptyList()

    private var depth = 0
    private var numPv = 1
    private var realtimeBest: Move? = null
    private val pvs = sortedMapOf<Int, PvSnapshot>()

    /** Feed one parsed response; returns a new snapshot when it changed, else null. */
    fun consume(response: EngineResponse): AnalysisSnapshot? {
        when (response) {
            is EngineResponse.InfoPvStart -> {
                curIndex = response.index
                curMate = null
                curWinRate = null
                curLine = emptyList()
            }
            is EngineResponse.InfoNumPv -> {
                numPv = response.count.coerceAtLeast(1)
                val stale = pvs.keys.filter { it >= numPv }
                stale.forEach { pvs.remove(it) }
                if (stale.isNotEmpty()) return snapshot()
            }
            is EngineResponse.InfoDepth -> {
                curDepth = response.depth
                depth = response.depth
            }
            is EngineResponse.InfoEval -> curMate = response.mate
            is EngineResponse.InfoWinRate -> curWinRate = response.winRate
            is EngineResponse.InfoBestline -> curLine = response.line
            is EngineResponse.InfoPvDone -> {
                pvs[curIndex] = PvSnapshot(curIndex, curDepth, curWinRate, curMate, curLine)
                return snapshot()
            }
            is EngineResponse.RealtimeBest -> {
                realtimeBest = response.move
                return snapshot()
            }
            is EngineResponse.RealtimeRefresh -> Unit
            else -> return null
        }
        return null
    }

    fun reset() {
        pvs.clear()
        realtimeBest = null
        depth = 0
        curIndex = 0
    }

    private fun snapshot() =
        AnalysisSnapshot(
            sideToMove = sideToMove,
            pvs = pvs.values.toList(),
            realtimeBest = realtimeBest,
            depth = depth,
        )
}
