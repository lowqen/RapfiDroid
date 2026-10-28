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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
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

/**
 * Folds the bottom bar away while a list is being read, and brings it back the
 * moment the reader turns round (Mornye's `MornyeChromeController`, P2 ④).
 *
 * Attached once, above every tab; it only watches — it never consumes a pixel,
 * so the lists scroll exactly as before. The rules:
 *
 *  - only a **finger** counts: a fling's tail, a list scrolling itself to its
 *    newest line (the engine console) and a horizontal swipe do nothing;
 *  - only what a list **actually scrolled** counts. A drag on a screen that
 *    does not scroll, or on a list too short to move, moves nothing and hides
 *    nothing — it used to count the finger's travel and fold the bar away
 *    under a screen that had not moved;
 *  - [threshold] pixels of deliberate travel one way, not a twitch;
 *  - down the list hides, up the list shows, and reaching the top shows —
 *    Mornye waits for the top, but here six tabs are the way around the app
 *    and nobody should have to scroll home to change screens;
 *  - the board's tab never hides it ([enabled] false there): the board does not
 *    scroll as a list, and its move bar sits right above the tabs.
 */
class BarHider(private val threshold: Float) : NestedScrollConnection {
    var hidden by mutableStateOf(false)
        private set

    /** Off on the board's tab. Turning it off shows the bar. */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) show()
        }

    private var travel = 0f

    fun show() {
        hidden = false
        travel = 0f
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
        // Pulling down with nothing left above: the list is at its top.
        if (available.y > 0f) {
            show()
            return Offset.Zero
        }
        val moved = consumed.y
        if (moved == 0f) return Offset.Zero
        if (travel != 0f && (travel > 0f) != (moved > 0f)) travel = 0f
        travel += moved
        when {
            travel <= -threshold -> { hidden = true; travel = 0f }
            travel >= threshold -> { hidden = false; travel = 0f }
        }
        return Offset.Zero
    }
}
