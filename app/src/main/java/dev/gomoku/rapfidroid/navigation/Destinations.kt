package dev.gomoku.rapfidroid.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import dev.gomoku.rapfidroid.R

/**
 * Five bottom-bar entries, not one per screen.
 *
 * Seven was too many to read at a glance, and three of them (리뷰·익스플로러·랭킹)
 * are the same activity — studying a position away from the board. They now live
 * as tabs inside [Destination.Research], the way the desktop groups them under
 * one Analysis menu. Nothing was dropped; the sub-tabs keep their own state.
 */
enum class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Board("board", R.string.tab_board, Icons.Filled.GridOn),
    Research("research", R.string.tab_research, Icons.Filled.Science),
    Database("database", R.string.tab_database, Icons.Filled.Storage),
    Connect("connect", R.string.tab_connect, Icons.Filled.Cable),
    Settings("settings", R.string.tab_settings, Icons.Filled.Tune),
}
