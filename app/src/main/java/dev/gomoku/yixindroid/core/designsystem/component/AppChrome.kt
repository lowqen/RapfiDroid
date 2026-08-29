package dev.gomoku.yixindroid.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gomoku.yixindroid.core.designsystem.theme.MOTION_QUICK
import dev.gomoku.yixindroid.core.designsystem.theme.Spacing

/**
 * The one snackbar in the app.
 *
 * There used to be three ways of saying something went wrong: a hand-built
 * `Snackbar` composable with its own `delay(3000)` on the board, a real
 * `SnackbarHost` in the explorer, and inline banners everywhere else — which
 * meant a notice could appear in three different places depending on which
 * screen produced it, and the board's version scrolled away with the page.
 *
 * The host lives on the app's scaffold and is reached from anywhere:
 *
 *     val snackbar = LocalSnackbarHostState.current
 *     LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it) } }
 */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/**
 * Every screen's header.
 *
 * Not one screen had an app bar, so each drew its own title at whatever size
 * suited it — `titleLarge` on one, `titleMedium` on the next, nothing at all on
 * the board and the database — and a screen-level action had nowhere to go but
 * the middle of the content. This is that place.
 *
 * Insets are zero on purpose: the app scaffold has already inset its content
 * below the status bar, and a bar that inset itself again would leave a band of
 * empty colour above every screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YixinTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

/**
 * A crossfade between two things that occupy the same place — the app's only
 * screen transition.
 *
 * Never a slide: three of the four things that swap in this app are boards, and
 * sliding one board out while another arrives is motion sickness rather than
 * polish. 150 ms, so it is felt and not watched.
 */
@Composable
fun <T> QuietSwitch(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    Crossfade(
        targetState = target,
        animationSpec = tween(MOTION_QUICK),
        label = "quietSwitch",
        modifier = modifier,
        content = content,
    )
}

/**
 * How wide one column of reading is allowed to get.
 *
 * Two of the research screens are a *sequence*, not a set of tiles: the position,
 * then its continuations, then the games that reached it. Splitting that into
 * columns would break the order it is read in, so on a wide screen they stay one
 * column and centre themselves instead of stretching a "h8 · 12 games · ▮▮▮" row
 * across a thousand points of tablet.
 *
 *     Modifier.fillMaxHeight().wrapContentWidth().widthIn(max = ReadingWidth)
 */
val ReadingWidth = 640.dp

/**
 * Wide enough for two columns. 640dp is a landscape phone; below it a split
 * would squeeze each half to something unreadable.
 *
 * The board screen has always branched on this. The connection screen now does
 * too — it stacks a console under four rows of controls, which in landscape
 * left the console measured at nothing — so the number lives here rather than
 * being copied, and the two screens turn into two columns at the same moment.
 */
val WideLayoutMin = 640.dp

/**
 * What a screen shows when it has nothing to show.
 *
 * Four screens had four answers to that — a paragraph, a shorter paragraph, a
 * line of grey text, and nothing — none of which told the reader what to do
 * about it. One shape, and the thing to do about it goes in [action].
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(40.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
