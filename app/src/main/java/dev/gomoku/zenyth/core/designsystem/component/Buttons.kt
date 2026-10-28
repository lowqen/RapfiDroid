package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.theme.LocalReducedMotion
import kotlin.math.roundToInt

/*
 * The app's three text buttons (UI overhaul 2, M7). Same names and the same
 * first parameters as Material's, so a screen switches by its import alone:
 *
 * - **48dp tall**, Mornye's pill, not Material's 40 — a bigger target and a
 *   calmer, fuller shape;
 * - **the press changes the shape**, Material 3 Expressive's way: the pill
 *   squares its corners while held and springs back on release, the same
 *   motion the board's toolbar keys make (M5). Reduced motion keeps the pill.
 */

/** A filled pill: the one action a card or a window leads with. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = PillHeight),
        enabled = enabled,
        shape = pressShape(interaction),
        contentPadding = PillPadding,
        interactionSource = interaction,
        content = content,
    )
}

/** An outlined pill: the second action beside a [Button], or a quiet one alone. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = PillHeight),
        enabled = enabled,
        shape = pressShape(interaction),
        contentPadding = PillPadding,
        interactionSource = interaction,
        content = content,
    )
}

/** A pale filled pill: between the two. */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = PillHeight),
        enabled = enabled,
        shape = pressShape(interaction),
        contentPadding = PillPadding,
        interactionSource = interaction,
        content = content,
    )
}

/**
 * A pill (50 % corners) that squares to [PressedCorner] while [interaction]
 * is pressed, on a soft spring so the release overshoots a little — the
 * Expressive press. Shared by every pill so they all move alike.
 */
@Composable
internal fun pressShape(interaction: MutableInteractionSource): Shape {
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val percent by animateFloatAsState(
        targetValue = if (pressed && !reduced) PressedCorner else 50f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "pressShape",
    )
    // Whole percents: half a dp a step on a 48dp pill, finer than the eye follows.
    return RoundedCornerShape(percent.roundToInt().coerceIn(0, 50))
}

/** Mornye's button height. */
val PillHeight = 48.dp

/** Corner (percent of the height) a pressed pill squares to. */
private const val PressedCorner = 26f

private val PillPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
