package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gomoku.zenyth.core.designsystem.theme.RowNoteStyle
import dev.gomoku.zenyth.core.designsystem.theme.RowTitleStyle
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import kotlin.math.roundToInt

/*
 * The list parts of UI overhaul 2, M4 — Mornye's repository screen taken
 * apart: a chip, a row of chips that scrolls, a badge, an icon tile, the card
 * that holds them, and a progress bar that says how far.
 */

/**
 * The app's filter chip — Material's, with the same parameters, dressed as
 * Mornye's: the chosen one filled in the pale accent with no outline, the
 * rest a soft outline. A check leads the chosen one unless it has an icon of
 * its own.
 */
@Composable
fun RapfiFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon ?: if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            iconColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.secondaryContainer,
            selectedLabelColor = scheme.onSecondaryContainer,
            selectedLeadingIconColor = scheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = scheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
            selectedBorderWidth = 0.dp,
        ),
    )
}

/**
 * Chips in one line that scrolls sideways, as Mornye's filters run — a filter
 * row stays one row high however many it holds, instead of wrapping into a block.
 */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A small fact in a pill — a version, a count, a score. One step off the card
 * (`surfaceContainerHigh`); [accent] puts it in the pale accent for the one
 * that is good news (done, a best score).
 */
@Composable
fun PillBadge(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (accent) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        contentColor = if (accent) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp).tabular(),
            maxLines = 1,
        )
    }
}

/**
 * The rounded square at the head of an item card: the pale accent when the
 * thing is live, grey when it is not.
 */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, active: Boolean = true, size: Dp = 48.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(if (active) scheme.secondaryContainer else scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** The corner a picture at the head of an item card is cut to (a mini board, say). */
val TileShape = RoundedCornerShape(14.dp)

/**
 * One thing in a list, as Mornye's repository lists them: a tile at the head
 * ([leading] — an [IconTile] or a picture cut to [TileShape]), the name, a
 * badge on the right, two lines of what it is, and under it whatever the
 * thing needs — a progress bar, the one button ([footer]). With [onClick] the
 * whole card is the target.
 */
@Composable
fun ItemCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    description: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.large
    CardFace(
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(shape).clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                leading?.invoke()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            title,
                            modifier = Modifier.weight(1f),
                            style = RowTitleStyle.copy(fontWeight = FontWeight.Medium),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        badge?.invoke()
                    }
                    description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = RowNoteStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            footer?.let { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = it) }
        }
    }
}

/**
 * How far, as Mornye shows it: a rounded bar and, to its right, the amount in
 * the accent — the percentage unless [label] says it better ("3/8").
 */
@Composable
fun LabeledProgress(fraction: Float, modifier: Modifier = Modifier, label: String? = null) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmoothProgressBar(fraction, Modifier.weight(1f).height(6.dp))
        Text(
            label ?: "${(fraction.coerceIn(0f, 1f) * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold).tabular(),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
