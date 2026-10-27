package dev.gomoku.rapfidroid.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Pick one of two to four views — the app's one segmented control.
 *
 * Four of them had grown up separately: the board's own pill, a `TabRow` on the
 * joseki and tools screens, a `ScrollableTabRow` on research, a
 * `SegmentedButton` on rankings. This is the board's pill, moved here unchanged
 * so the others can come to it (P2 and P4 of docs/UI-개편-마스터플랜.md).
 * Material 3 1.4 has no connected button group; when 1.5 brings one, this is the
 * one place that changes.
 */
@Composable
fun <T> RapfiSegmented(
    options: List<Pair<T, String>>,
    selection: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(40.dp),
    ) {
        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { (value, label) ->
                val on = value == selection
                Surface(
                    onClick = { onSelect(value) },
                    shape = CircleShape,
                    color = if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (on) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxHeight()
                        .semantics {
                            role = Role.Tab
                            selected = on
                        },
                ) {
                    Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}
