package dev.gomoku.rapfidroid.core.joseki

import dev.gomoku.rapfidroid.core.model.Move

/**
 * The markdown a joseki comment may use — only what was asked for: **bold**,
 * *italics*, `code`, [links](https://…), `- ` and `1. ` lists, paragraphs and
 * line breaks, plus **coordinates that link to their move** (`h8`). A tree
 * of blocks, never HTML; links only for http, https and mailto (RapfiWeb
 * `core/joseki/markdown.ts`).
 */
object Markdown {

    sealed interface Inline {
        data class Text(val v: String) : Inline
        data class Bold(val c: List<Inline>) : Inline
        data class Italic(val c: List<Inline>) : Inline
        data class Code(val v: String) : Inline
        data class Link(val href: String, val c: List<Inline>) : Inline
        data class Coord(val label: String, val move: Move) : Inline
        data object Br : Inline
    }

    sealed interface Block {
        data class Para(val c: List<Inline>) : Block
        data class Bullets(val ordered: Boolean, val items: List<List<Inline>>) : Block
    }

    private val SAFE_HREF = Regex("^(https?://|mailto:)", RegexOption.IGNORE_CASE)
    private val COORD = Regex("(^|[^A-Za-z0-9])([A-Oa-o](?:1[0-5]|[1-9]))(?![A-Za-z0-9])")
    private val UL = Regex("^\\s*[-*+]\\s+(.*)$")
    private val OL = Regex("^\\s*\\d+[.)]\\s+(.*)$")

    private fun coords(text: String, size: Int): List<Inline> {
        val out = ArrayList<Inline>()
        var last = 0
        for (m in COORD.findAll(text)) {
            val start = m.range.first + m.groupValues[1].length
            val label = m.groupValues[2]
            val mv = Move.fromLabel(label.uppercase(), size) ?: continue
            if (start > last) out.add(Inline.Text(text.substring(last, start)))
            out.add(Inline.Coord(label, mv))
            last = start + label.length
        }
        if (last < text.length) out.add(Inline.Text(text.substring(last)))
        return out
    }

    fun parseInline(src: String, size: Int = Move.DEFAULT_SIZE): List<Inline> {
        val out = ArrayList<Inline>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) out.addAll(coords(buf.toString(), size))
            buf.clear()
        }
        var i = 0
        while (i < src.length) {
            val ch = src[i]
            if (ch == '\\' && i + 1 < src.length && src[i + 1] in "\\*_`[]()") {
                buf.append(src[i + 1])
                i += 2
                continue
            }
            if (ch == '\n') {
                flush()
                out.add(Inline.Br)
                i++
                continue
            }
            if (ch == '`') {
                val end = src.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    out.add(Inline.Code(src.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            }
            if (src.startsWith("**", i)) {
                val end = src.indexOf("**", i + 2)
                if (end > i + 2) {
                    flush()
                    out.add(Inline.Bold(parseInline(src.substring(i + 2, end), size)))
                    i = end + 2
                    continue
                }
            }
            if ((ch == '*' || ch == '_') && i + 1 < src.length && src[i + 1] != ch && src[i + 1] != ' ') {
                val end = src.indexOf(ch, i + 1)
                if (end > i + 1 && src[end - 1] != ' ') {
                    flush()
                    out.add(Inline.Italic(parseInline(src.substring(i + 1, end), size)))
                    i = end + 1
                    continue
                }
            }
            if (ch == '[') {
                val close = src.indexOf("](", i + 1)
                val end = if (close > 0) src.indexOf(')', close + 2) else -1
                if (close > i && end > close) {
                    val href = src.substring(close + 2, end).trim()
                    if (SAFE_HREF.containsMatchIn(href)) {
                        flush()
                        out.add(Inline.Link(href, parseInline(src.substring(i + 1, close), size)))
                        i = end + 1
                        continue
                    }
                }
            }
            buf.append(ch)
            i++
        }
        flush()
        return out
    }

    fun parse(src: String, size: Int = Move.DEFAULT_SIZE): List<Block> {
        val blocks = ArrayList<Block>()
        val para = ArrayList<String>()
        var listOrdered: Boolean? = null
        val items = ArrayList<String>()
        fun endPara() {
            if (para.isNotEmpty()) blocks.add(Block.Para(parseInline(para.joinToString("\n"), size)))
            para.clear()
        }
        fun endList() {
            val ordered = listOrdered
            if (ordered != null) blocks.add(Block.Bullets(ordered, items.map { parseInline(it, size) }))
            listOrdered = null
            items.clear()
        }
        for (line in src.replace(Regex("\r\n?"), "\n").split("\n")) {
            val ul = UL.matchEntire(line)
            val ol = if (ul == null) OL.matchEntire(line) else null
            if (ul != null || ol != null) {
                endPara()
                val ordered = ol != null
                if (listOrdered != ordered) {
                    endList()
                    listOrdered = ordered
                }
                items.add((ul ?: ol)!!.groupValues[1])
            } else if (line.isBlank()) {
                endPara()
                endList()
            } else if (listOrdered != null && Regex("^\\s{2,}").containsMatchIn(line)) {
                items[items.lastIndex] = items.last() + "\n" + line.trim()
            } else {
                endList()
                para.add(line)
            }
        }
        endPara()
        endList()
        return blocks
    }

    /** Text without the markup — for search and list previews. */
    fun plainText(src: String): String = src
        .replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_`]"), "")
        .replace(Regex("(?m)^\\s*([-*+]|\\d+[.)])\\s+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
