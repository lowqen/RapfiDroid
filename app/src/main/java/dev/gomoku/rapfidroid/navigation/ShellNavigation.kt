package dev.gomoku.rapfidroid.navigation

import dev.gomoku.rapfidroid.core.designsystem.component.rememberCalmFling
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.designsystem.component.engineWord
import dev.gomoku.rapfidroid.core.model.ConnectionState

/**
 * The six tabs along the bottom — a phone held upright.
 *
 * Stateless: the shell owns the back stack and hands in what is selected, so
 * the bar can be drawn (and photographed) without one.
 */
@Composable
fun RapfiBottomBar(
    selected: Destination?,
    connection: ConnectionState,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<Destination> = Destination.entries,
) {
    NavigationBar(modifier) {
        destinations.forEach { dest ->
            NavigationBarItem(
                selected = dest == selected,
                onClick = { onSelect(dest) },
                icon = { DestinationIcon(dest, connection) },
                label = { DestinationLabel(dest) },
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
                    icon = { DestinationIcon(dest, connection) },
                    label = { DestinationLabel(dest) },
                    alwaysShowLabel = showLabels,
                )
            }
        }
    }
}

/** The dot rides the tab that leads to the answer: the engine's state on 엔진. */
@Composable
private fun DestinationIcon(dest: Destination, connection: ConnectionState) {
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
            Icon(dest.icon, contentDescription = null)
        }
    } else {
        Icon(dest.icon, contentDescription = null)
    }
}

@Composable
private fun DestinationLabel(dest: Destination) {
    Text(stringResource(dest.labelRes), maxLines = 1, overflow = TextOverflow.Clip)
}
