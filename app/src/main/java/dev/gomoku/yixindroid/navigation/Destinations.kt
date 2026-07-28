package dev.gomoku.yixindroid.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import dev.gomoku.yixindroid.R

enum class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Board("board", R.string.tab_board, Icons.Filled.GridOn),
    Review("review", R.string.tab_review, Icons.Filled.Assessment),
    Explorer("explorer", R.string.tab_explorer, Icons.Filled.Explore),
    Rankings("rankings", R.string.tab_rankings, Icons.Filled.Leaderboard),
    Database("database", R.string.tab_database, Icons.Filled.Storage),
    Settings("settings", R.string.tab_settings, Icons.Filled.Tune),
    Connect("connect", R.string.tab_connect, Icons.Filled.Cable),
}
