package dev.gomoku.rapfidroid.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.designsystem.component.QuietSwitch
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiSegmented
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiTopBar
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.feature.explorer.MoveOrderSection
import dev.gomoku.rapfidroid.feature.explorer.OpeningExplorerSection
import dev.gomoku.rapfidroid.feature.rankings.RankingsScreen
import dev.gomoku.rapfidroid.feature.review.ReviewScreen

/**
 * 연구 — everything that studies a position instead of playing one: the review
 * and prove pipeline, the two openings explorers, and the rankings dashboard.
 *
 * They were four bottom-bar entries; the desktop keeps all of them under one
 * Analysis menu, and grouping them here gets the bar down to five readable
 * items. Each tab keeps its own view model and state, so switching costs
 * nothing and a running review is unaffected.
 */
@Composable
fun ResearchScreen(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = remember {
        listOf(
            tr("리뷰·증명", "Review", "复盘·证明"),
            tr("오프닝", "Openings", "开局"),
            tr("수순", "Move order", "手顺"),
            tr("랭킹", "Rankings", "排行"),
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        RapfiTopBar(title = tr("연구", "Research", "研究"))
        RapfiSegmented(
            options = titles.mapIndexed { i, title -> i to title },
            selection = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fill = true,
        )
        // A crossfade, not a slide: four panes that swap in place, on a screen
        // whose content is mostly boards and numbers. Sliding one board off
        // while another arrives is motion sickness, not polish.
        QuietSwitch(tab, Modifier.fillMaxSize()) { index ->
            when (index) {
                0 -> ReviewScreen()
                1 -> OpeningExplorerSection()
                2 -> MoveOrderSection()
                else -> RankingsScreen()
            }
        }
    }
}
