package dev.gomoku.zenyth.navigation

import dev.gomoku.zenyth.core.designsystem.component.rememberCalmFling
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.component.engineWord
import dev.gomoku.zenyth.core.model.ConnectionState

/**
 * The six tabs along the bottom — a phone held upright.
 *
 * Stateless: the shell owns the back stack and hands in what is selected, so
 * the bar can be drawn (and photographed) without one.
 *
 * Mornye's bar (UI overhaul 2, M6): one step off the page (`surfaceContainer`),
 * the chosen tab filled and bold on its pale pill, the rest outlined.
 */
@Composable
fun RapfiBottomBar(
    selected: Destination?,
    connection: ConnectionState,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<Destination> = Destination.entries,
) {
    NavigationBar(modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        destinations.forEach { dest ->
            NavigationBarItem(
                selected = dest == selected,
                onClick = { onSelect(dest) },
                icon = { DestinationIcon(dest, connection, dest == selected) },
                label = { DestinationLabel(dest, dest == selected) },
            )
        }
    }
}

/**
 * The same six tabs down the side — anything at least [RailLayoutMin] wide,
 * which includes every phone on its side. There the height is the scarce side,
 * and the board is sized by it.
 *
 * Six items with labels need about 380dp; a phone on its side has less than
 * that under the status bar, so the column scrolls rather than clip the last
 * tab, and [showLabels] false (short windows) keeps only the selected label.
 * Its tabs follow the bar's rule — filled and bold when chosen, outlined when not.
 */
@Composable
fun RapfiRail(
    selected: Destination?,
    connection: ConnectionState,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    destinations: List<Destination> = Destination.entries,
) {
    NavigationRail(modifier) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling())
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            destinations.forEach { dest ->
                NavigationRailItem(
                    selected = dest == selected,
                    onClick = { onSelect(dest) },
                    icon = { DestinationIcon(dest, connection, dest == selected) },
                    label = { DestinationLabel(dest, dest == selected) },
                    alwaysShowLabel = showLabels,
                )
            }
        }
    }
}

/** The dot rides the tab that leads to the answer: the engine's state on 엔진. */
@Composable
private fun DestinationIcon(dest: Destination, connection: ConnectionState, selected: Boolean) {
    val icon = if (selected) dest.selectedIcon else dest.icon
    if (dest == Destination.Connect) {
        val (word, color) = engineWord(connection)
        BadgedBox(
            badge = {
                Badge(
                    containerColor = color,
                    modifier = Modifier.semantics { contentDescription = word },
                )
            },
        ) {
            Icon(icon, contentDescription = null)
        }
    } else {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun DestinationLabel(dest: Destination, selected: Boolean) {
    Text(
        stringResource(dest.labelRes),
        fontWeight = if (selected) FontWeight.Bold else null,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}
