package dev.gomoku.yixindroid.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gomoku.yixindroid.core.designsystem.component.QuietSwitch
import dev.gomoku.yixindroid.core.designsystem.component.YixinTopBar
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.feature.explorer.MoveOrderSection
import dev.gomoku.yixindroid.feature.explorer.OpeningExplorerSection
import dev.gomoku.yixindroid.feature.rankings.RankingsScreen
import dev.gomoku.yixindroid.feature.review.ReviewScreen

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
            tr("리뷰·증명", "Review"),
            tr("오프닝", "Openings"),
            tr("수순", "Move order"),
            tr("랭킹", "Rankings"),
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        YixinTopBar(
            title = tr("연구", "Research"),
            subtitle = titles.getOrNull(tab),
        )
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            titles.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
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
