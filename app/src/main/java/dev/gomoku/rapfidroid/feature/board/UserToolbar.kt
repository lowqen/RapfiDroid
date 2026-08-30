package dev.gomoku.rapfidroid.feature.board

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.model.FunctionScripts
import dev.gomoku.rapfidroid.core.model.LngTable

/**
 * The desktop's user-defined toolbar (main.c:10064 `custom_function`), the same
 * buttons from the same `function/toolbar<n>.txt` files.
 *
 * A button is a label id, an icon name and a script. The script goes to the
 * console command language P10 already runs, so this file only has to turn a
 * GTK icon name into something drawable and a label id into text.
 *
 * `toolbarStyle` (settings.txt line 20) picks icon-only / icon+label, matching
 * the desktop's three modes as closely as a phone allows: the desktop's third
 * mode stacks label beside icon, which is what mode 1 already does here.
 */
@Composable
fun UserToolbar(
    items: List<FunctionScripts.ToolbarItem>,
    language: LngTable,
    style: Int,
    enabled: Boolean,
    onRun: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            ToolbarButton(
                item = item,
                label = language.label(item.lngId, fallbackLabel(item)),
                showLabel = style != 0,
                enabled = enabled,
                onClick = { onRun(item.script) },
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    item: FunctionScripts.ToolbarItem,
    label: String,
    showLabel: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(iconFor(item.icon), contentDescription = label, modifier = Modifier.size(18.dp))
            if (showLabel && label.isNotEmpty()) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * GTK stock icon name → the nearest Material icon. Unknown names get a generic
 * one rather than nothing: the button still works, and the label says what it
 * does. The names are kept verbatim in the model so a file written back out
 * still opens on the desktop.
 */
private fun iconFor(name: String): ImageVector = when (name) {
    "go-first" -> Icons.Filled.FirstPage
    "go-last" -> Icons.AutoMirrored.Filled.LastPage
    "go-previous" -> Icons.AutoMirrored.Filled.ArrowBack
    "go-next" -> Icons.AutoMirrored.Filled.ArrowForward
    "media-playback-start" -> Icons.Filled.PlayArrow
    "media-playback-stop" -> Icons.Filled.Stop
    "document-save", "document-save-as" -> Icons.Filled.Save
    "document-open" -> Icons.Filled.FolderOpen
    "edit-delete", "edit-clear" -> Icons.Filled.Delete
    "edit-find", "system-search" -> Icons.Filled.Search
    "preferences-system", "system-run" -> Icons.Filled.Settings
    else -> Icons.Filled.Bolt
}

/**
 * When no language file has been imported the numeric label id means nothing, so
 * fall back to the script itself — which is, after all, exactly what the button
 * does. The desktop has the same fallback in `TL(idx, "…")`, only with English.
 */
private fun fallbackLabel(item: FunctionScripts.ToolbarItem): String =
    item.script.lineSequence().firstOrNull().orEmpty()

/** Vertical variant for the wide layout, where the toolbar sits beside the board. */
@Composable
fun UserToolbarColumn(
    items: List<FunctionScripts.ToolbarItem>,
    language: LngTable,
    style: Int,
    enabled: Boolean,
    onRun: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        items.forEach { item ->
            ToolbarButton(
                item = item,
                label = language.label(item.lngId, fallbackLabel(item)),
                showLabel = style != 0,
                enabled = enabled,
                onClick = { onRun(item.script) },
            )
        }
    }
}
