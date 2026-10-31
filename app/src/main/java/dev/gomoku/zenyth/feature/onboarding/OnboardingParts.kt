package dev.gomoku.zenyth.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gomoku.zenyth.core.designsystem.component.IconTile
import dev.gomoku.zenyth.core.designsystem.theme.LocalReducedMotion
import dev.gomoku.zenyth.core.designsystem.theme.RowNoteStyle
import dev.gomoku.zenyth.core.designsystem.theme.RowTitleStyle
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.AppLocales
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.feature.settings.CheckMark
import kotlin.math.min

/*
 * The first-run setup's parts, after Mornye's (docs/온보딩-계획.md §0): the
 * icon in a pale circle, the big heavy title, the ring that fills step by step,
 * the floating «다음» in the corner, and the card a done step turns into.
 * Colours are this app's roles in the places Mornye uses its own.
 */

/**
 * Korean broken between words, not syllables (the first shots split «상태/가»
 * and «엔진/이»). Phrase breaking is Android 13's; older phones keep their
 * usual breaks, which is no worse than before.
 */
internal val WordBreaks = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)

/** A step's title: the page title's weight, a size smaller (Mornye ~24 – 26sp). */
@Composable
internal fun stepTitleStyle(): TextStyle =
    MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 34.sp, lineBreak = LineBreak.Heading)

/** The welcome's title, between the two. */
@Composable
internal fun welcomeTitleStyle(): TextStyle =
    MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp, lineBreak = LineBreak.Heading)

/** The tour's titles: a step larger than the setup's (Mornye ~30 – 34sp there). */
@Composable
internal fun tourTitleStyle(): TextStyle =
    MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp, lineBreak = LineBreak.Heading)

/**
 * The grey sentence under a title. Centred, so its lines are balanced too:
 * no last line holding a single word.
 */
@Composable
internal fun stepBodyStyle(): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading)

/** A step's icon: 96dp pale circle, the strong accent inside. */
@Composable
internal fun HeroIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(96.dp).clip(CircleShape).background(scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(44.dp))
    }
}

/**
 * `n/N` in a ring that fills clockwise from twelve o'clock — Material 3
 * Expressive's circular progress: the filled arc and the track part with a
 * small gap at each end, and the arc grows on a spring as a step is passed.
 */
@Composable
internal fun StepProgressRing(step: Int, of: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val fraction by animateFloatAsState(
        targetValue = step.toFloat() / of,
        animationSpec = if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "setupRing",
    )
    val label = tr("단계 $step/$of", "Step $step of $of", "第 $step/$of 步")
    val track = scheme.surfaceContainerHighest
    val arc = scheme.primary
    Box(
        modifier.size(48.dp).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val style = Stroke(stroke, cap = StrokeCap.Round)
            val sweep = 360f * fraction
            // Each gap also has to clear the round caps (~4° at this size).
            val gap = 12f
            val trackSweep = 360f - sweep - 2 * gap
            if (trackSweep > 0f) {
                drawArc(track, -90f + sweep + gap, trackSweep, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            }
            if (sweep > 0f) {
                drawArc(arc, -90f, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            }
        }
        // The ring is a fixed 48dp: past 1.3 the numbers would leave it (2.0 shot).
        // The label stays readable — TalkBack hears the step above anyway.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, min(density.fontScale, 1.3f))) {
            Text(
                "$step/$of",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold).tabular(),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * The floating button in the corner (Mornye's): pale with → «다음» on the way,
 * the strong accent with ✓ «시작하기» on the last step, grey while the step
 * still waits for an answer. The corners square a little under the finger, as
 * every pill in the app does.
 */
@Composable
internal fun OnboardingFab(last: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val corner by animateDpAsState(
        targetValue = if (pressed && !reduced) 14.dp else 20.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "fabCorner",
    )
    val container by animateColorAsState(
        when {
            !enabled -> scheme.surfaceContainerHighest
            last -> scheme.primary
            else -> scheme.secondaryContainer
        },
        label = "fabFill",
    )
    val content by animateColorAsState(
        when {
            !enabled -> scheme.onSurface.copy(alpha = 0.38f)
            last -> scheme.onPrimary
            else -> scheme.onSecondaryContainer
        },
        label = "fabInk",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        shadowElevation = if (enabled) 4.dp else 0.dp,
        interactionSource = interaction,
    ) {
        Row(Modifier.padding(start = 20.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (last) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (last) tr("시작하기", "Get started", "开始使用") else tr("다음", "Next", "下一步"),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** What a step did, said once it is done: a full-width pale card with the check. */
@Composable
internal fun DoneCard(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.secondaryContainer)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(true)
        Spacer(Modifier.width(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = scheme.onSecondaryContainer,
        )
    }
}

/**
 * The page's last lines fade into it while there is more below — a page cut
 * off at a large font gave no sign it scrolled (2.0 shot). The same fade as
 * the «새로워진 점» window.
 */
@Composable
internal fun BoxScope.MoreBelowFade(scroll: ScrollState) {
    if (!scroll.canScrollForward) return
    val surface = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(40.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, surface))),
    )
}

/**
 * One thing the app does: the icon tile, then a sentence at row size — or,
 * with a [title], the name in bold over a quieter line (the tour, where each
 * row is a feature with a name).
 */
@Composable
internal fun FeatureRow(icon: ImageVector, text: String, modifier: Modifier = Modifier, title: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon)
        Spacer(Modifier.width(20.dp))
        if (title == null) {
            Text(text, style = RowTitleStyle.copy(lineBreak = WordBreaks), color = scheme.onSurface)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = RowTitleStyle.copy(fontWeight = FontWeight.SemiBold, lineBreak = WordBreaks), color = scheme.onSurface)
                Text(text, style = RowNoteStyle.copy(lineBreak = WordBreaks), color = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Which page of the tour: a dot each, the current one stretched into a pill
 * in the strong accent, on a spring. A dot takes a tap to its page; TalkBack
 * hears «4쪽 중 2쪽» once for the row.
 */
@Composable
internal fun PageDots(count: Int, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val said = tr("${count}쪽 중 ${current + 1}쪽", "Page ${current + 1} of $count", "第 ${current + 1} 页，共 $count 页")
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = said },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == current
            val width by animateDpAsState(
                targetValue = if (on) 32.dp else 8.dp,
                animationSpec = if (reduced) snap() else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                label = "dotWidth",
            )
            val color by animateColorAsState(if (on) scheme.primary else scheme.outlineVariant, label = "dotColor")
            // The dot is 8dp; the place to tap is 24dp around it.
            Box(
                Modifier
                    .height(24.dp)
                    .clickable(interactionSource = null, indication = null) { onSelect(i) }
                    .padding(horizontal = 4.dp)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width, 8.dp).clip(CircleShape).background(color))
            }
        }
    }
}

/**
 * The language, asked without a step of its own: a small pill in the welcome's
 * top corner naming the language on screen. Most people keep the phone's, so
 * it waits there rather than standing in the way.
 */
@Composable
internal fun LanguagePill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val said = tr("언어: $label", "Language: $label", "语言：$label")
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp).semantics { contentDescription = said },
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 10.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Mornye's language list: the chosen row a pale pill with its name in bold and
 * the check at the end, the rest bare — the phone for «기기 설정», a globe for
 * a language.
 */
@Composable
internal fun LanguageRow(choice: AppLocales.Choice, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = CircleShape,
        color = if (selected) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (choice.tag.isEmpty()) Icons.Filled.PhoneAndroid else Icons.Filled.Language,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(20.dp))
            Text(
                choice.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
                modifier = Modifier.weight(1f),
            )
            CheckMark(selected)
        }
    }
}
