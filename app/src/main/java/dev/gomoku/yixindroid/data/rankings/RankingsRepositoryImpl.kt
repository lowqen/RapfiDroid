package dev.gomoku.yixindroid.data.rankings

import android.net.Uri
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.OpeningRanking
import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.RankingFilter
import dev.gomoku.yixindroid.core.model.ShapeFreqRow
import dev.gomoku.yixindroid.core.model.ShapeRank
import dev.gomoku.yixindroid.domain.rankings.FreqAnalyzer
import dev.gomoku.yixindroid.domain.rankings.FreqBundle
import dev.gomoku.yixindroid.domain.repository.RankingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RankingsRepositoryImpl @Inject constructor(
    private val rank5: Rank5Database,
    private val freqStore: FreqStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : RankingsRepository {

    override val freq: StateFlow<FreqBundle?> = freqStore.bundle

    override suspend fun restoreFreq() = freqStore.restore()

    override suspend fun importFreq(uri: Uri): Result<Unit> =
        freqStore.import(uri).map { }

    override suspend fun clearFreq() = freqStore.clear()

    override suspend fun theoreticalTop(limit: Int, m5Max: Int?): List<ShapeRank> =
        withContext(io) { rank5.top(limit, m5Max) }

    override suspend fun searchShapes(
        repContains: String?, opening: String?, m5Max: Int?, limit: Int,
    ): List<ShapeRank> =
        withContext(io) { rank5.search(repContains, opening, m5Max, limit) }

    override suspend fun shapesByRank(ranks: Set<Int>): Map<Int, ShapeRank> =
        withContext(io) { rank5.byRanks(ranks) }

    override suspend fun groupDistribution(): List<Pair<Int, Int>> =
        withContext(io) { rank5.groupDistribution() }

    override suspend fun openingShapeCounts(): Map<String, Int> =
        withContext(io) { rank5.openingCounts() }

    override suspend fun shapeTotal(): Int = withContext(io) { rank5.total() }

    override suspend fun matchPlayers(query: String): List<PlayerRef> {
        val bundle = freqStore.bundle.value ?: return emptyList()
        return withContext(io) { FreqAnalyzer.matchPlayers(bundle, query) }
    }

    override suspend fun matchRules(query: String): List<Int> {
        val bundle = freqStore.bundle.value ?: return emptyList()
        return withContext(io) { FreqAnalyzer.matchRules(bundle, query) }
    }

    override fun ruleName(index: Int): String? =
        freqStore.bundle.value?.rules?.getOrNull(index)

    override suspend fun openingRanking(filter: RankingFilter): OpeningRanking? {
        val bundle = freqStore.bundle.value ?: return null
        return withContext(io) { FreqAnalyzer.openingRanking(bundle, filter) }
    }

    override suspend fun fiveMoveRanking(filter: RankingFilter, top: Int): List<ShapeFreqRow> {
        val bundle = freqStore.bundle.value ?: return emptyList()
        return withContext(io) { FreqAnalyzer.fiveMoveRanking(bundle, filter, top) }
    }

    override suspend fun countsByTheoryRank(filter: RankingFilter): Map<Int, Int> {
        val bundle = freqStore.bundle.value ?: return emptyMap()
        return withContext(io) { FreqAnalyzer.countsByTheoryRank(bundle, filter) }
    }
}
