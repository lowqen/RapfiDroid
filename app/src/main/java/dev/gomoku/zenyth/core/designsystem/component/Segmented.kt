package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.theme.ChromeMotion
import dev.gomoku.zenyth.core.designsystem.theme.MOTION_QUICK

/**
 * Pick one of two to four views — the app's one segmented control.
 *
 * Four of them had grown up separately: the board's own pill, a `TabRow` on the
 * joseki and tools screens, a `ScrollableTabRow` on research, a
 * `SegmentedButton` on rankings. This is the board's pill, moved here so the
 * others could come to it (P0, P2, P4 of docs/UI-개편-마스터플랜.md). Material 3
 * 1.4 has no connected button group; when 1.5 brings one, this is the one place
 * that changes.
 *
 * [fill] spreads the segments over the whole width in equal parts — a row of
 * views under a header (연구 · 정석), where the pill is the screen's own tabs and
 * a long label ellipsises rather than pushing the last view off the screen.
 * Without it the pill is as wide as its labels (the board's 분석 | 대국).
 *
 * The selection is one pill that **slides** to the chosen segment on a spatial
 * spring, and the labels change colour on an effects spring (P5, Mornye's
 * selection pill). Until the segments have been measured — the first frame —
 * the chosen segment paints its own background, so nothing flashes empty.
 * Reduced motion keeps the old 150ms change.
 */
@Composable
fun <T> RapfiSegmented(
    options: List<Pair<T, String>>,
    selection: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    val density = LocalDensity.current
    // Each segment's left edge and width inside the row, once laid out.
    val starts = remember(options.size) { mutableStateListOf<Dp?>().apply { repeat(options.size) { add(null) } } }
    val widths = remember(options.size) { mutableStateListOf<Dp?>().apply { repeat(options.size) { add(null) } } }
    val chosen = options.indexOfFirst { it.first == selection }
    val measured = chosen >= 0 && starts.getOrNull(chosen) != null && widths.getOrNull(chosen) != null
    // The first placement snaps; only a change of selection slides.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(measured) { if (measured) placed = true }
    val slide: FiniteAnimationSpec<Dp> = if (placed) ChromeMotion.spatial(tween(MOTION_QUICK)) else snap()
    val pillStart by animateDpAsState(if (measured) starts[chosen]!! else 0.dp, slide, label = "segmentStart")
    val pillWidth by animateDpAsState(if (measured) widths[chosen]!! else 0.dp, slide, label = "segmentWidth")
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = (if (fill) modifier.fillMaxWidth() else modifier).height(40.dp),
    ) {
        Box(Modifier.padding(3.dp)) {
            if (measured) {
                Box(Modifier.matchParentSize()) {
                    Box(
                        Modifier
                            .offset(x = pillStart)
                            .width(pillWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
            }
            Row(
                if (fill) Modifier.fillMaxWidth() else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEachIndexed { i, (value, label) ->
                    val on = i == chosen
                    val content by animateColorAsState(
                        if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        ChromeMotion.effects(tween(MOTION_QUICK)),
                        label = "segmentLabel",
                    )
                    Surface(
                        onClick = { onSelect(value) },
                        shape = CircleShape,
                        // The sliding pill paints the selection once measured.
                        color = if (on && !measured) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = content,
                        modifier = (if (fill) Modifier.weight(1f) else Modifier)
                            .fillMaxHeight()
                            .onPlaced { coords ->
                                with(density) {
                                    starts[i] = coords.positionInParent().x.toDp()
                                    widths[i] = coords.size.width.toDp()
                                }
                            }
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
}
