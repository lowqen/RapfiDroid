package dev.gomoku.rapfidroid.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow
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
 *
 * [fill] spreads the segments over the whole width in equal parts — a row of
 * views under a header (연구 · 정석), where the pill is the screen's own tabs and
 * a long label ellipsises rather than pushing the last view off the screen.
 * Without it the pill is as wide as its labels (the board's 분석 | 대국).
 */
@Composable
fun <T> RapfiSegmented(
    options: List<Pair<T, String>>,
    selection: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = (if (fill) modifier.fillMaxWidth() else modifier).height(40.dp),
    ) {
        Row((if (fill) Modifier.fillMaxWidth() else Modifier).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { (value, label) ->
                val on = value == selection
                Surface(
                    onClick = { onSelect(value) },
                    shape = CircleShape,
                    color = if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (on) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = (if (fill) Modifier.weight(1f) else Modifier)
                        .fillMaxHeight()
                        .semantics {
                            role = Role.Tab
                            selected = on
                        },
                ) {
                    Box(Modifier.padding(horizontal = if (fill) 8.dp else 16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
