package dev.gomoku.rapfidroid.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.designsystem.theme.MOTION_QUICK
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.Duration
import dev.gomoku.rapfidroid.data.prefs.UiPrefs

/**
 * The calm parts — RapfiWeb's `.calm-design/DESIGN.md` brought back to the app.
 *
 *  - **Card**: one white face on the paper with a hairline border, no shadow and
 *    no grey tile inside it (no surface on a surface).
 *  - **Disclosure**: an expert group folded behind one line — title on the left,
 *    the current value on the right, a chevron. It remembers being opened.
 *  - **Hint**: one line of grey text, never a paragraph.
 */

/** Where the folds remember being open; provided once by the activity. */
val LocalUiPrefs = staticCompositionLocalOf<UiPrefs?> { null }

/** The card face: lightest container on paper, one step up in the dark. */
@Composable
fun cardColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) scheme.surfaceContainer else scheme.surfaceContainerLowest
}

@Composable
fun CalmCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacing: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cardColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** A card's head line: the title, then whatever belongs on its right. */
@Composable
fun CardTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

/** One line of help. If it needs two, it belongs in a fold. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/**
 * An expert group folded behind one line. [summary] is the current value — the
 * part most people read and never open the fold for.
 */
@Composable
fun Disclosure(
    id: String,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    defaultOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val prefs = LocalUiPrefs.current
    var open by rememberSaveable(id) { mutableStateOf(prefs?.isOpen(id, defaultOpen) ?: defaultOpen) }
    val turn by animateFloatAsState(if (open) 90f else 0f, tween(MOTION_QUICK), label = "chevron")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button) {
                    open = !open
                    prefs?.setOpen(id, open)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(
                summary.orEmpty(),
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = if (open) tr("접기", "Collapse") else tr("펼치기", "Expand"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).rotate(turn),
            )
        }
        AnimatedVisibility(
            open,
            enter = fadeIn(tween(MOTION_QUICK)) + expandVertically(tween(MOTION_QUICK)),
            exit = fadeOut(tween(MOTION_QUICK)) + shrinkVertically(tween(MOTION_QUICK)),
        ) {
            Column(
                Modifier.padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** A card that is nothing but a fold. */
@Composable
fun DisclosureCard(
    id: String,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    defaultOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    CalmCard(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), spacing = 0.dp) {
        Disclosure(id, title, summary = summary, defaultOpen = defaultOpen, content = content)
    }
}

/**
 * A row of a settings group inside a card: the name on top, the control under
 * it. The why goes in [note] — one line, quiet — or nowhere.
 */
@Composable
fun FieldRow(
    label: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            note?.let {
                Text(
                    it,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content()
    }
}

/** Pick one of a few values. Chips wrap, so a long list never runs off a phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            FilterChip(
                selected = on,
                onClick = { onSelect(value) },
                enabled = enabled,
                label = { Text(label) },
                leadingIcon = if (on) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

/**
 * The «직접» field beside a row of time chips: type `2.5`, `1500ms`, `2분` or
 * `무제한` ([Duration]). Out-of-range values are clamped and unreadable ones go
 * back to the default; either way the reason is handed to [onNote].
 */
@Composable
fun DurationField(
    valueMs: Long,
    spec: Duration.Spec,
    presetsMs: List<Long>,
    onCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onNote: (String) -> Unit = {},
) {
    val custom = valueMs !in presetsMs
    var text by remember(valueMs) { mutableStateOf(if (custom) Duration.text(valueMs, spec.unit) else "") }
    fun commit() {
        if (text.isBlank()) return
        val r = Duration.parse(text, spec)
        when (r) {
            is Duration.Result.Ok -> Unit
            is Duration.Result.Clamped -> onNote(r.note)
            is Duration.Result.Fallback -> onNote(r.note)
        }
        onCommit(r.ms)
        text = if (r.ms in presetsMs) "" else Duration.text(r.ms, spec.unit)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier.width(132.dp),
        enabled = enabled,
        singleLine = true,
        label = { Text(tr("직접", "Custom")) },
        placeholder = { Text(if (spec.unit == Duration.Unit.S) tr("초", "sec") else "ms") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

/** Shorthand: the chip row and the custom field, side by side when they fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimePicker(
    valueMs: Long,
    presetsMs: List<Long>,
    spec: Duration.Spec,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onNote: (String) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChoiceChips(
            options = presetsMs.map { it to Duration.format(it) },
            selected = valueMs,
            onSelect = onChange,
            enabled = enabled,
        )
        DurationField(valueMs, spec, presetsMs, onChange, enabled = enabled, onNote = onNote)
    }
}

/** Padding every scrolling screen uses around its cards. */
val ScreenPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)

/**
 * A drop-in for Material's `Card` with the calm face (white, hairline, no
 * elevation). Screens import it as `Card` so their layouts stay untouched.
 */
@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = cardColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}
