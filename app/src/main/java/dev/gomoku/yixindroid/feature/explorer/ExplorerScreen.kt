package dev.gomoku.yixindroid.feature.explorer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * The desktop's two research windows — 오프닝 익스플로러 and 수순 탐색기 — as one
 * tab. They are a pair by design: the explorer merges transpositions into one
 * position, the move-order browser unfolds them again (개발_핸드북.md §8).
 *
 * Both follow the board rather than owning it, so switching between them (or
 * away to the board) never interrupts anything.
 */
@Composable
fun ExplorerScreen(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = remember { listOf("오프닝", "수순") }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, style = MaterialTheme.typography.titleSmall) },
                )
            }
        }
        when (tab) {
            0 -> OpeningExplorerSection()
            else -> MoveOrderSection()
        }
    }
}
