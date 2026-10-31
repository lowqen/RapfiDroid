package dev.gomoku.zenyth.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import dev.gomoku.zenyth.core.designsystem.theme.LocalReducedMotion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.component.ConnectedGroup
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.designsystem.theme.RowNoteStyle
import dev.gomoku.zenyth.core.designsystem.theme.RowTitleStyle
import dev.gomoku.zenyth.core.i18n.AppLocales
import dev.gomoku.zenyth.core.i18n.tr

/**
 * The page's hero, as Mornye's design page opens: the theme as it is now, in
 * small — a corner of the board and the move bar on the page's own colour,
 * with soft rounds of the accent behind them and a badge saying which theme it is.
 */
@Composable
internal fun ThemePreview() {
    val scheme = MaterialTheme.colorScheme
    val skin = ZenythTheme.board
    val dark = scheme.surface.luminance() < 0.5f
    Box(
        Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHigh),
    ) {
        Canvas(Modifier.matchParentSize()) {
            fun blob(color: Color, x: Float, y: Float, r: Float) {
                val center = Offset(size.width * x, size.height * y)
                val radius = size.minDimension * r
                drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
            }
            blob(scheme.primaryContainer, 0.12f, 0.18f, 0.75f)
            blob(scheme.secondaryContainer, 0.95f, 0.92f, 0.85f)
            // One hue only, as Mornye's: the warm tertiary went brown in the dark.
            // A lavender step between the container and the accent — the accent
            // at 16 % over the pale card read as grey in light (M7).
            blob(lerp(scheme.primaryContainer, scheme.primary, 0.28f), 0.78f, 0.05f, 0.45f)
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Canvas(Modifier.size(104.dp)) {
                val edge = 7
                val pad = size.width / (edge + 1)
                drawRoundRect(skin.wood, cornerRadius = CornerRadius(10.dp.toPx()))
                for (i in 1..edge) {
                    val at = pad * i
                    drawLine(skin.line, Offset(pad, at), Offset(size.width - pad, at), strokeWidth = 1.dp.toPx())
                    drawLine(skin.line, Offset(at, pad), Offset(at, size.height - pad), strokeWidth = 1.dp.toPx())
                }
                val r = pad * 0.44f
                listOf(4 to 4, 3 to 3, 5 to 3).forEach { (x, y) ->
                    val c = Offset(pad * x, pad * y)
                    drawCircle(Brush.radialGradient(listOf(skin.blackHigh, skin.blackLow), c - Offset(r * 0.3f, r * 0.3f), r * 1.4f), r, c)
                }
                listOf(4 to 3, 3 to 5).forEach { (x, y) ->
                    val c = Offset(pad * x, pad * y)
                    drawCircle(Brush.radialGradient(listOf(skin.whiteHigh, skin.whiteLow), c - Offset(r * 0.3f, r * 0.3f), r * 1.4f), r, c)
                    drawCircle(skin.whiteRim, r, c, style = Stroke(0.8.dp.toPx()))
                }
            }
            Surface(shape = CircleShape, color = scheme.surfaceContainer) {
                Row(
                    Modifier.padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniKey(Icons.AutoMirrored.Filled.KeyboardArrowLeft, scheme.surfaceContainerHighest, scheme.onSurfaceVariant, 30.dp)
                    MiniKey(Icons.Filled.PlayArrow, scheme.primary, scheme.onPrimary, 44.dp)
                    MiniKey(Icons.AutoMirrored.Filled.KeyboardArrowRight, scheme.surfaceContainerHighest, scheme.onSurfaceVariant, 30.dp)
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            shape = CircleShape,
            color = scheme.surface,
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(if (dark) tr("어두운", "Dark", "深色") else tr("밝은", "Light", "浅色"), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
@Composable
private fun MiniKey(icon: ImageVector, container: Color, content: Color, width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(width = width, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
    }
}
/**
 * System · light · dark as three buttons in a row, as Mornye's design page
 * picks its theme: the chosen one filled in the strong accent and rounded all
 * the way, the others a quiet face with softer corners (the shape moves
 * between the two, M3).
 */
@Composable
internal fun ThemeButtons(selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val options = listOf(
        Triple(0, Icons.Outlined.BrightnessAuto, tr("시스템", "System", "跟随系统")),
        Triple(1, Icons.Outlined.LightMode, tr("밝은", "Light", "浅色")),
        Triple(2, Icons.Outlined.DarkMode, tr("어두운", "Dark", "深色")),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, icon, label) ->
            val on = value == selected
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val reduced = LocalReducedMotion.current
            // Chosen = round, the rest soft squares, and a press squares a little
            // further and springs back (M7) — the toolbar keys' motion.
            val corner by animateIntAsState(
                when {
                    pressed && !reduced -> 16
                    on -> 50
                    else -> 26
                },
                if (reduced) snap() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                label = "themeCorner",
            )
            val container by animateColorAsState(if (on) scheme.primary else scheme.surfaceContainerHighest, label = "themeFill")
            val content by animateColorAsState(if (on) scheme.onPrimary else scheme.onSurfaceVariant, label = "themeInk")
            Surface(
                selected = on,
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f).height(68.dp).semantics { role = Role.RadioButton },
                interactionSource = interaction,
                shape = RoundedCornerShape(corner),
                color = container,
                contentColor = content,
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, contentDescription = null)
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
/**
 * The language as a list with a check on the chosen row (Mornye's single
 * choice), not a row of chips: four names read down better than across.
 * Android 13+ only — older phones change it in their own settings.
 */
@Composable
internal fun LanguageGroup() {
    val context = LocalContext.current
    if (!AppLocales.supported(context)) {
        Hint(tr("언어는 기기 설정에서 바꿉니다", "Change the language in the phone's settings", "请在系统设置中更改语言"))
        return
    }
    val current = AppLocales.current(context)
    ConnectedGroup(title = tr("언어", "Language", "语言")) {
        AppLocales.choices().forEach { (tag, label) ->
            row(
                onClick = { AppLocales.set(context, tag) },
                selected = tag == current,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = RowTitleStyle, modifier = Modifier.weight(1f))
                    CheckMark(tag == current)
                }
            }
        }
    }
}
/**
 * The chosen row's mark: a filled accent circle with a check; nothing on the
 * rest. Choosing a row grows it in from a soft square to the circle on a spring
 * (M7, Material 3 Expressive's shape change); reduced motion just shows it.
 * Silent to TalkBack — the row itself says it is selected. The first-run
 * language step and its «done» cards use it too.
 */
@Composable
internal fun CheckMark(on: Boolean) {
    val reduced = LocalReducedMotion.current
    val bouncy = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
    val grow by animateFloatAsState(if (on) 1f else 0f, if (reduced) snap() else bouncy, label = "checkGrow")
    val round by animateIntAsState(if (on) 50 else 22, if (reduced) snap() else spring(dampingRatio = 0.6f), label = "checkRound")
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (grow > 0.01f) {
            Box(
                Modifier
                    .size(24.dp)
                    .graphicsLayer { scaleX = grow; scaleY = grow }
                    .clip(RoundedCornerShape(round))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
/**
 * A row that names a page or a thing to do: the outlined icon, the name, what
 * is inside, and at the end a chevron — or [trailing] in its place (a switch).
 */
@Composable
internal fun LinkRow(
    icon: ImageVector,
    title: String,
    note: String?,
    trailing: @Composable () -> Unit = {
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    },
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = RowTitleStyle)
            note?.let { Text(it, style = RowNoteStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing()
    }
}
/**
 * The search, as a filled pill with no outline (Mornye's: 56dp, one step off
 * the page). It looks through the app's own settings and the desktop's.
 */
@Composable
internal fun SearchPill(query: String, onQuery: (String) -> Unit) {
    val face = MaterialTheme.colorScheme.surfaceContainerHigh
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(tr("설정 검색", "Search settings", "搜索设置")) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Close, contentDescription = tr("지우기", "Clear", "清除"))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = face,
            unfocusedContainerColor = face,
            disabledContainerColor = face,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}
