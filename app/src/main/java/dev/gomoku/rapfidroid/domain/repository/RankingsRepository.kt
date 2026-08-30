package dev.gomoku.rapfidroid.domain.repository

import android.net.Uri
import dev.gomoku.rapfidroid.core.model.OpeningRanking
import dev.gomoku.rapfidroid.core.model.PlayerRef
import dev.gomoku.rapfidroid.core.model.RankingFilter
import dev.gomoku.rapfidroid.core.model.ShapeFreqRow
import dev.gomoku.rapfidroid.domain.rankings.FreqBundle
import kotlinx.coroutines.flow.StateFlow

/**
 * Backs the Rankings dashboard.
 *
 * One data source: **freq**, the empirical play frequency built from the user's
 * own RenjuNet download. It is user-imported, so every query here returns
 * null/empty until [importFreq] succeeds — the 26 openings themselves are
 * static and come from [dev.gomoku.rapfidroid.core.model.Opening26].
 */
interface RankingsRepository {

    /** Imported empirical dataset, or null when nothing is loaded. */
    val freq: StateFlow<FreqBundle?>

    suspend fun restoreFreq()
    suspend fun importFreq(uri: Uri): Result<Unit>
    suspend fun clearFreq()

    suspend fun matchPlayers(query: String): List<PlayerRef>
    suspend fun matchRules(query: String): List<Int>
    fun ruleName(index: Int): String?
    suspend fun openingRanking(filter: RankingFilter): OpeningRanking?
    suspend fun fiveMoveRanking(filter: RankingFilter, top: Int = 40): List<ShapeFreqRow>
}
