package dev.gomoku.rapfidroid.core.joseki

import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.Opening26
import dev.gomoku.rapfidroid.core.model.OpeningEval
import dev.gomoku.rapfidroid.core.model.OpeningName
import java.util.UUID

/**
 * One joseki is one document (RapfiWeb W9): a tree of moves with comments,
 * evaluation symbols and roles. Nodes are addressed by **path** — child indices
 * from the root — and every edit returns a new root, sharing the untouched
 * branches, so a screen can hold it as a value.
 */

/** Move evaluation symbols, as game annotations use them. */
enum class JosekiMark(val symbol: String, val ko: String, val en: String) {
    BRILLIANT("!!", "최선", "Best"),
    GOOD("!", "좋은 수", "Good"),
    INTERESTING("!?", "흥미로운 수", "Interesting"),
    DUBIOUS("?!", "의문수", "Dubious"),
    BAD("?", "나쁜 수", "Bad"),
    BLUNDER("??", "큰 실수", "Blunder"),
    ;

    val label: String get() = tr(ko, en)
    val isGood: Boolean get() = this == BRILLIANT || this == GOOD || this == INTERESTING
    val isBad: Boolean get() = this == DUBIOUS || this == BAD || this == BLUNDER

    companion object {
        fun of(symbol: String?): JosekiMark? = entries.firstOrNull { it.symbol == symbol }
    }
}

/** What a branch is for, apart from its evaluation (the official curriculum's roles). */
enum class JosekiRole(val key: String, val ko: String, val en: String) {
    /** Plausible but walks into something, or a trap the opponent sets. */
    TRAP("trap", "함정수", "Trap"),

    /** The answer that punishes a bad move or a trap. */
    REFUTE("refute", "응징", "Refutation"),
    ;

    val label: String get() = tr(ko, en)

    companion object {
        fun of(key: String?): JosekiRole? = entries.firstOrNull { it.key == key }
    }
}

data class JosekiNode(
    /** Null only at the root. */
    val move: Move? = null,
    /** Markdown: bold, italics, lists, links, and coordinates that link to moves. */
    val comment: String = "",
    val mark: JosekiMark? = null,
    val role: JosekiRole? = null,
    /** `[0]` is the main line. */
    val children: List<JosekiNode> = emptyList(),
)

data class JosekiTree(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    /** Opening name; empty means it is derived from the line ([JosekiOps.classify]). */
    val category: String = "",
    val tags: List<String> = emptyList(),
    val boardSize: Int = Move.DEFAULT_SIZE,
    val root: JosekiNode = JosekiNode(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

typealias Path = List<Int>

object JosekiOps {

    /** A document this large is worth a warning before saving gets heavy. */
    const val NODE_WARN = 2000

    fun nodeAt(root: JosekiNode, path: Path): JosekiNode? {
        var n = root
        for (i in path) n = n.children.getOrNull(i) ?: return null
        return n
    }

    fun movesTo(root: JosekiNode, path: Path): List<Move> {
        val out = ArrayList<Move>()
        var n = root
        for (i in path) {
            val c = n.children.getOrNull(i) ?: break
            out.add(c.move!!)
            n = c
        }
        return out
    }

    /** [path] followed down the main line to its end. */
    fun mainLineFrom(root: JosekiNode, path: Path = emptyList()): Path {
        val out = path.toMutableList()
        var n = nodeAt(root, path)
        while (n != null && n.children.isNotEmpty()) {
            out.add(0)
            n = n.children[0]
        }
        return out
    }

    fun mainLine(root: JosekiNode): List<Move> = movesTo(root, mainLineFrom(root))

    fun countNodes(root: JosekiNode): Int {
        var n = 0
        walk(root) { _, _, _ -> n++ }
        return n - 1
    }

    /** Every node, depth first, root included, with its path and line. */
    fun walk(root: JosekiNode, fn: (JosekiNode, Path, List<Move>) -> Unit) {
        val stack = ArrayDeque<Triple<JosekiNode, Path, List<Move>>>()
        stack.addLast(Triple(root, emptyList(), emptyList()))
        while (stack.isNotEmpty()) {
            val (node, path, moves) = stack.removeLast()
            fn(node, path, moves)
            for (i in node.children.indices.reversed()) {
                val c = node.children[i]
                stack.addLast(Triple(c, path + i, moves + c.move!!))
            }
        }
    }

    /** Why [m] cannot go at the end of [path], or null when it can. */
    fun illegal(root: JosekiNode, path: Path, m: Move, size: Int = Move.DEFAULT_SIZE): String? {
        if (!m.isInside(size)) return tr("판 밖입니다", "Off the board")
        if (m in movesTo(root, path)) return tr("이미 돌이 있는 자리입니다", "That point is taken")
        return null
    }

    private fun replaceAt(root: JosekiNode, path: Path, f: (JosekiNode) -> JosekiNode): JosekiNode {
        if (path.isEmpty()) return f(root)
        val i = path[0]
        val child = root.children.getOrNull(i) ?: return root
        val children = root.children.toMutableList()
        children[i] = replaceAt(child, path.drop(1), f)
        return root.copy(children = children)
    }

    data class Step(val root: JosekiNode, val path: Path, val added: Boolean)

    /**
     * One move at the end of [path]. A move that is already there is followed,
     * not added again — the standard game-editor behaviour. Null when illegal.
     */
    fun addMove(root: JosekiNode, path: Path, m: Move, size: Int = Move.DEFAULT_SIZE): Step? {
        val at = nodeAt(root, path) ?: return null
        val existing = at.children.indexOfFirst { it.move == m }
        if (existing >= 0) return Step(root, path + existing, false)
        if (illegal(root, path, m, size) != null) return null
        val next = replaceAt(root, path) { it.copy(children = it.children + JosekiNode(move = m)) }
        return Step(next, path + at.children.size, true)
    }

    data class LineResult(val root: JosekiNode, val path: Path, val added: Int, val stoppedAt: Int?)

    /** A whole line from [from], following what exists and adding the rest. */
    fun addLine(root: JosekiNode, moves: List<Move>, from: Path = emptyList(), size: Int = Move.DEFAULT_SIZE): LineResult {
        var r = root
        var p = from
        var added = 0
        moves.forEachIndexed { i, m ->
            val step = addMove(r, p, m, size) ?: return LineResult(r, p, added, i)
            r = step.root
            p = step.path
            if (step.added) added++
        }
        return LineResult(r, p, added, null)
    }

    /** Removes the node and everything under it; returns the parent's path. */
    fun removeAt(root: JosekiNode, path: Path): Pair<JosekiNode, Path>? {
        if (path.isEmpty() || nodeAt(root, path) == null) return null
        val parent = path.dropLast(1)
        val i = path.last()
        return replaceAt(root, parent) { n -> n.copy(children = n.children.filterIndexed { j, _ -> j != i }) } to parent
    }

    /** Makes this line the main line at every step; the new path is all zeros. */
    fun promoteLine(root: JosekiNode, path: Path): Pair<JosekiNode, Path> {
        var r = root
        for (d in path.indices) {
            val i = path[d]
            if (i == 0) continue
            r = replaceAt(r, path.take(d)) { n ->
                val children = n.children.toMutableList()
                val c = children.removeAt(i)
                n.copy(children = listOf(c) + children)
            }
        }
        return r to path.map { 0 }
    }

    /** One place earlier (-1) or later (+1) among siblings. */
    fun moveSibling(root: JosekiNode, path: Path, dir: Int): Pair<JosekiNode, Path> {
        if (path.isEmpty()) return root to path
        val parent = path.dropLast(1)
        val i = path.last()
        val sibs = nodeAt(root, parent)?.children?.size ?: 0
        val j = i + dir
        if (j < 0 || j >= sibs) return root to path
        val next = replaceAt(root, parent) { n ->
            val children = n.children.toMutableList()
            val t = children[i]
            children[i] = children[j]
            children[j] = t
            n.copy(children = children)
        }
        return next to (parent + j)
    }

    fun setComment(root: JosekiNode, path: Path, comment: String) = replaceAt(root, path) { it.copy(comment = comment) }
    fun setMark(root: JosekiNode, path: Path, mark: JosekiMark?) = replaceAt(root, path) { it.copy(mark = mark) }
    fun setRole(root: JosekiNode, path: Path, role: JosekiRole?) = replaceAt(root, path) { it.copy(role = role) }

    // ---- typed lines -----------------------------------------------------------

    sealed interface Parsed {
        data class Ok(val moves: List<Move>) : Parsed
        data class Error(val message: String) : Parsed
    }

    private val COORD = Regex("([a-zA-Z])(\\d{1,2})")

    /**
     * A hand-typed line: `h8 i9 j10`, `h8,i9`, `H8I9J10`, `1. h8 2. i9`. Every
     * token must be coordinates only; one unreadable token fails the whole
     * line — half a line taken silently would file the joseki under the wrong one.
     */
    fun parseMoveText(text: String, size: Int = Move.DEFAULT_SIZE): Parsed {
        val moves = ArrayList<Move>()
        for (raw in text.split(Regex("[\\s,;]+"))) {
            val tok = raw.replace(Regex("^\\d+[.)]"), "").replace(Regex("^[.)]+"), "")
            if (tok.isEmpty()) continue
            var at = 0
            while (at < tok.length) {
                val m = COORD.find(tok, at)?.takeIf { it.range.first == at }
                    ?: return Parsed.Error(tr("«$raw» 를 좌표로 읽지 못했습니다", "«$raw» is not a coordinate"))
                var digits = m.groupValues[2]
                var mv = Move.fromLabel(m.groupValues[1] + digits, size)
                if (mv == null && digits.length == 2) {
                    digits = digits.take(1)
                    mv = Move.fromLabel(m.groupValues[1] + digits, size)
                }
                if (mv == null) return Parsed.Error(tr("«${m.value}» 는 판 밖입니다", "«${m.value}» is off the board"))
                if (mv in moves) {
                    return Parsed.Error(tr("${moves.size + 1}수 ${mv.label(size)} 는 이미 둔 자리입니다", "Move ${moves.size + 1}, ${mv.label(size)}, is already taken"))
                }
                moves.add(mv)
                at += 1 + digits.length
            }
        }
        return Parsed.Ok(moves)
    }

    fun movesText(moves: List<Move>, size: Int = Move.DEFAULT_SIZE): String =
        moves.joinToString(" ") { it.label(size).lowercase() }

    // ---- classification ---------------------------------------------------------

    data class Classification(
        /** What the list shows: the user's category, else the derived name. */
        val category: String,
        val chain: List<String>,
        /** The 26-opening group of the first three moves, or null. */
        val group: String?,
        val grade: OpeningEval.Grade?,
    )

    /** Derived from the main line, not typed: name chain, 26 group, 5th-move grade. */
    fun classify(tree: JosekiTree): Classification = classify(mainLine(tree.root), tree.category, tree.boardSize)

    fun classify(line: List<Move>, userCategory: String = "", size: Int = Move.DEFAULT_SIZE): Classification {
        val names = (1..minOf(OpeningName.MAX_PLY, line.size)).mapNotNull { OpeningName.nameAt(it, line, size) }
        val group = if (line.size >= 3) {
            Opening26.classify(line.take(3)).takeIf { it != Opening26.NONSTD }?.let { Opening26.label(it) }
        } else {
            null
        }
        val grade = if (line.size >= 5) OpeningName.gradeAt(line.take(5), size) else null
        val user = userCategory.trim()
        return Classification(
            category = user.ifEmpty { names.lastOrNull() ?: tr("분류 없음", "Unclassified") },
            chain = names,
            group = group,
            grade = grade,
        )
    }
}
