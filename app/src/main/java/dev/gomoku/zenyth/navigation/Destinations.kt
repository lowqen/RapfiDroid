package dev.gomoku.zenyth.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.gomoku.zenyth.R

/**
 * Six bottom-bar entries, not one per screen — the five below plus 정석
 * (RapfiWeb's rail order: play and study first, the three tools after).
 *
 * Seven was too many to read at a glance, and three of them (리뷰·익스플로러·랭킹)
 * are the same activity — studying a position away from the board. They now live
 * as tabs inside [Destination.Research], the way the desktop groups them under
 * one Analysis menu. Nothing was dropped; the sub-tabs keep their own state.
 *
 * Each tab has two icons, Mornye's way (UI overhaul 2, M6): [icon] outlined while
 * it waits, [selectedIcon] filled once chosen. So every pair has to differ in
 * more than weight — 그리드 · 저장 · 번개 look the same either way in Material
 * Icons, hence the archive box, the bolt in a ring and the board's own [FilledGrid].
 */
enum class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Board("board", R.string.tab_board, Icons.Outlined.GridOn, FilledGrid),
    Research("research", R.string.tab_research, Icons.Outlined.Science, Icons.Filled.Science),
    Joseki("joseki", R.string.tab_joseki, Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook),
    Database("database", R.string.tab_database, Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    Connect("connect", R.string.tab_connect, Icons.Outlined.OfflineBolt, Icons.Filled.OfflineBolt),
    Settings("settings", R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}
