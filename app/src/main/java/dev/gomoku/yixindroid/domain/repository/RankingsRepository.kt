package dev.gomoku.yixindroid.domain.repository

import android.net.Uri
import dev.gomoku.yixindroid.core.model.OpeningRanking
import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.RankingFilter
import dev.gomoku.yixindroid.core.model.ShapeFreqRow
import dev.gomoku.yixindroid.core.model.ShapeRank
import dev.gomoku.yixindroid.domain.rankings.FreqBundle
import kotlinx.coroutines.flow.StateFlow

/**
 * Backs the Rankings dashboard. Two data sources with different licences:
 *  - **rank5** (theoretical 5-move ranking) is bundled and always available;
 *  - **freq** (empirical play frequency) is user-imported (RenjuNet), so every
 *    freq-derived query returns null/empty until [importFreq] succeeds.
 */
interface RankingsRepository {

    /** Imported empirical dataset, or null when nothing is loaded. */
    val freq: StateFlow<FreqBundle?>

    suspend fun restoreFreq()
    suspend fun importFreq(uri: Uri): Result<Unit>
    suspend fun clearFreq()

    // ---- rank5 (bundled, theoretical) ----
    suspend fun theoreticalTop(limit: Int, m5Max: Int? = null): List<ShapeRank>
    suspend fun searchShapes(
        repContains: String?, opening: String?, m5Max: Int?, limit: Int,
    ): List<ShapeRank>
    suspend fun shapesByRank(ranks: Set<Int>): Map<Int, ShapeRank>
    suspend fun groupDistribution(): List<Pair<Int, Int>>
    suspend fun openingShapeCounts(): Map<String, Int>
    suspend fun shapeTotal(): Int

    /** Message for the bundled-dataset load failure, or null if it loaded. Valid
     *  only after a rank5 query has been attempted. */
    fun rank5Error(): String?

    // ---- freq (empirical, imported) ----
    suspend fun matchPlayers(query: String): List<PlayerRef>
    suspend fun matchRules(query: String): List<Int>
    fun ruleName(index: Int): String?
    suspend fun openingRanking(filter: RankingFilter): OpeningRanking?
    suspend fun fiveMoveRanking(filter: RankingFilter, top: Int = 40): List<ShapeFreqRow>
    suspend fun countsByTheoryRank(filter: RankingFilter): Map<Int, Int>
}
