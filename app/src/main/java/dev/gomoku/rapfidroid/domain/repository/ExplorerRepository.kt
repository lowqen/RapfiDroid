package dev.gomoku.rapfidroid.domain.repository

import android.net.Uri
import dev.gomoku.rapfidroid.core.model.ExplorerGames
import dev.gomoku.rapfidroid.core.model.ExplorerPosition
import dev.gomoku.rapfidroid.core.model.ExplorerStatus
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.PackInfo
import dev.gomoku.rapfidroid.core.model.RjGame
import kotlinx.coroutines.flow.StateFlow

/**
 * The opening explorer: RenjuNet statistics for the position on the board.
 *
 * Follows the board the way the desktop window does (`web_refresh` →
 * `rjexp_schedule`) — there is nothing to start or stop, the numbers just track
 * the current line.
 *
 * ⚠ The packs are **user-imported and never redistributed** (RenjuNet licence);
 * everything below returns empty until a device build has produced them.
 */
interface ExplorerRepository {

    /** Header numbers of the loaded packs, or null when none are loaded. */
    val packs: StateFlow<PackInfo?>

    /** Statistics for the current board position, or null when there are none. */
    val position: StateFlow<ExplorerPosition?>

    val status: StateFlow<ExplorerStatus>

    /** Re-map previously imported packs (call once at startup). */
    suspend fun restore()

    suspend fun clearPacks()

    /** Games through the current position, narrowed by a player-name filter
     *  (case-insensitive substring, like the desktop — RIF names are latin). */
    suspend fun games(filter: String): ExplorerGames

    fun game(id: Int): RjGame?

    fun ruleName(rule: Int): String?

    /** RIF opening filed for a game: the 주형 label, or null. */
    fun openingLabel(opening: Int): String?

    /** Play a listed next move onto the board. Returns a refusal reason, or
     *  null when it was played. */
    suspend fun playNext(move: Move): String?

    /** Replace the board with a game from the pack. Returns a refusal reason. */
    suspend fun loadGame(id: Int): String?
}
