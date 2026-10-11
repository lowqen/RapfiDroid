package dev.gomoku.rapfidroid.feature.joseki

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.designsystem.component.BoardMark
import dev.gomoku.rapfidroid.core.designsystem.theme.tabular
import dev.gomoku.rapfidroid.core.joseki.JosekiNode
import dev.gomoku.rapfidroid.core.joseki.JosekiRole
import dev.gomoku.rapfidroid.core.joseki.Markdown
import dev.gomoku.rapfidroid.core.joseki.Path
import dev.gomoku.rapfidroid.core.model.Move

/**
 * A joseki comment: the markdown subset, with coordinates that act — tapping
 * `h8` goes to that move ([onCoord]). Links open in the browser.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, onCoord: ((Move) -> Unit)? = null) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val blocks = Markdown.parse(text)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Markdown.Block.Para -> Text(
                    inline(block.c, linkColor, codeBg, onCoord),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.6f),
                )
                is Markdown.Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    block.items.forEachIndexed { i, item ->
                        Row {
                            Text(
                                if (block.ordered) "${i + 1}. " else "•  ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(inline(item, linkColor, codeBg, onCoord), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun inline(
    items: List<Markdown.Inline>,
    linkColor: Color,
    codeBg: Color,
    onCoord: ((Move) -> Unit)?,
): AnnotatedString = buildAnnotatedString {
    fun put(list: List<Markdown.Inline>) {
        list.forEach {
            when (it) {
                is Markdown.Inline.Text -> append(it.v)
                Markdown.Inline.Br -> append("\n")
                is Markdown.Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { put(it.c) }
                is Markdown.Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { put(it.c) }
                is Markdown.Inline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(it.v) }
                is Markdown.Inline.Link -> withLink(
                    LinkAnnotation.Url(it.href, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))),
                ) { put(it.c) }
                is Markdown.Inline.Coord -> if (onCoord != null) {
                    val move = it.move
                    withLink(
                        LinkAnnotation.Clickable(
                            "coord:${it.label}",
                            TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)),
                        ) { onCoord(move) },
                    ) { append(it.label) }
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(it.label) }
                }
            }
        }
    }
    put(items)
}

/** Colour of a branch by what it is: good green, bad or trap red, otherwise plain. */
@Composable
fun branchColor(node: JosekiNode, index: Int): Color = when {
    node.role == JosekiRole.TRAP || node.mark?.isBad == true -> MaterialTheme.colorScheme.error
    node.role == JosekiRole.REFUTE -> MaterialTheme.colorScheme.tertiary
    index == 0 || node.mark?.isGood == true -> Color(0xFF2E8B57)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The node's children as board marks: their symbol, else A, B, C. */
fun childMarks(node: JosekiNode): Map<Move, BoardMark> =
    node.children.mapIndexedNotNull { i, c ->
        val move = c.move ?: return@mapIndexedNotNull null
        val bad = c.role == JosekiRole.TRAP || c.mark?.isBad == true
        val good = i == 0 || c.mark?.isGood == true
        val color = when {
            bad -> Color(0xFFFF6B5E)
            good -> Color(0xFF6BE38A)
            else -> Color(0xFFF2E7D3)
        }
        move to BoardMark(c.mark?.symbol ?: ('A' + i).toString(), color)
    }.toMap()

/**
 * The move tree: each line runs across, a variation drops one step in under
 * the move it branches from. Tap a move to go there; the current one is
 * highlighted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreeView(root: JosekiNode, current: Path, size: Int, onGo: (Path) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (root.children.isEmpty()) return@Column
        Line(root, emptyList(), 0, current, size, onGo, depth = 0)
        for (k in 1 until root.children.size) Line(root, emptyList(), k, current, size, onGo, depth = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Line(parent: JosekiNode, parentPath: Path, startIndex: Int, current: Path, size: Int, onGo: (Path) -> Unit, depth: Int) {
    // The line: from parent.children[startIndex] down the main line.
    val chips = ArrayList<Triple<JosekiNode, Path, Int>>()
    var node = parent.children[startIndex]
    var path = parentPath + startIndex
    chips.add(Triple(node, path, startIndex))
    val forks = ArrayList<Triple<JosekiNode, Path, Int>>() // (parent, parentPath, child index >= 1)
    while (node.children.isNotEmpty()) {
        for (k in 1 until node.children.size) forks.add(Triple(node, path, k))
        val next = node.children[0]
        path = path + 0
        chips.add(Triple(next, path, 0))
        node = next
    }
    // Variations branching off the very first move of this line come from the parent.
    Column(Modifier.padding(start = (depth * 14).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chips.forEach { (n, p, i) ->
                val selected = p == current
                Text(
                    "${p.size}.${n.move?.label(size)?.lowercase().orEmpty()}${n.mark?.symbol.orEmpty()}" +
                        (n.role?.let { " " + it.label } ?: ""),
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { onGo(p) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else branchColor(n, i),
                )
            }
        }
        forks.forEach { (fp, fpath, k) ->
            Line(fp, fpath, k, current, size, onGo, depth + 1)
        }
    }
}
