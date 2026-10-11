package dev.gomoku.rapfidroid.core.joseki

import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.PosKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Joseki files — the JSON RapfiWeb writes (`format: rapfiweb-joseki`), so a
 * collection moves between the browser and the phone unchanged.
 *
 * Reading **validates**: an off-board, repeated or duplicate move drops its
 * branch, an unknown symbol is ignored, text is capped. Somebody else's file
 * must not break the screen or bloat the document.
 */
object JosekiJson {
    const val FORMAT = "rapfiweb-joseki"
    private const val MAX_COMMENT = 20_000
    private const val MAX_TITLE = 200

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    // ---- write -----------------------------------------------------------------

    fun nodeJson(n: JosekiNode): JsonObject = buildJsonObject {
        put("move", n.move?.let { m -> buildJsonObject { put("x", m.x); put("y", m.y) } } ?: JsonNull)
        put("comment", n.comment)
        n.mark?.let { put("mark", it.symbol) }
        n.role?.let { put("role", it.key) }
        put("children", buildJsonArray { n.children.forEach { add(nodeJson(it)) } })
    }

    fun treeJson(t: JosekiTree): JsonObject = buildJsonObject {
        put("id", t.id)
        put("title", t.title)
        put("category", t.category)
        put("tags", buildJsonArray { t.tags.forEach { add(JsonPrimitive(it)) } })
        put("boardSize", t.boardSize)
        put("rule", "renju")
        put("root", nodeJson(t.root))
        put("createdAt", t.createdAt)
        put("updatedAt", t.updatedAt)
        put("schema", 1)
    }

    fun export(trees: List<JosekiTree>, now: Long = System.currentTimeMillis()): String =
        json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("format", FORMAT)
                put("schema", 1)
                put("exportedAt", now)
                put("trees", buildJsonArray { trees.forEach { add(treeJson(it)) } })
            },
        )

    // ---- read ------------------------------------------------------------------

    sealed interface Import {
        data class Ok(val trees: List<JosekiTree>, val dropped: Int) : Import
        data class Error(val message: String) : Import
    }

    internal fun JsonElement?.str(max: Int): String =
        ((this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: "").take(max)

    internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    internal fun moveOf(e: JsonElement?): Move? {
        val o = e.obj() ?: return null
        val x = (o["x"] as? JsonPrimitive)?.intOrNull ?: return null
        val y = (o["y"] as? JsonPrimitive)?.intOrNull ?: return null
        return Move(x, y)
    }

    /** Validates one node and everything below it; dropped branches are counted. */
    internal fun cleanNode(
        raw: JsonElement?,
        line: MutableSet<Move>,
        size: Int,
        drop: IntArray,
        depth: Int,
        comment: (JsonElement?) -> String = { it.str(MAX_COMMENT) },
    ): JosekiNode {
        val r = raw.obj() ?: JsonObject(emptyMap())
        val kids = (r["children"] as? JsonArray).orEmpty()
        val children = ArrayList<JosekiNode>()
        if (depth < size * size) {
            for (k in kids) {
                val m = moveOf(k.obj()?.get("move"))
                if (m == null || !m.isInside(size) || m in line || children.any { it.move == m }) {
                    drop[0]++
                    continue
                }
                line.add(m)
                val child = cleanNode(k, line, size, drop, depth + 1, comment)
                line.remove(m)
                children.add(child.copy(move = m))
            }
        } else {
            drop[0] += kids.size
        }
        return JosekiNode(
            move = null,
            comment = comment(r["comment"]),
            mark = JosekiMark.of(r["mark"].str(4)),
            role = JosekiRole.of(r["role"].str(16)),
            children = children,
        )
    }

    fun cleanTree(raw: JsonElement?, now: Long = System.currentTimeMillis()): Pair<JosekiTree, Int>? {
        val r = raw.obj() ?: return null
        val root = r["root"].obj() ?: return null
        val size = (r["boardSize"] as? JsonPrimitive)?.intOrNull ?: Move.DEFAULT_SIZE
        if (size != Move.DEFAULT_SIZE) return null
        val drop = IntArray(1)
        val node = cleanNode(root, HashSet(), size, drop, 0)
        val id = r["id"].str(64)
        return JosekiTree(
            id = id.ifEmpty { java.util.UUID.randomUUID().toString() },
            title = r["title"].str(MAX_TITLE).ifEmpty { tr("가져온 정석", "Imported joseki", "导入的定式") },
            category = r["category"].str(MAX_TITLE),
            tags = (r["tags"] as? JsonArray).orEmpty().map { it.str(40) }.filter { it.isNotEmpty() }.take(32),
            boardSize = size,
            root = node,
            createdAt = (r["createdAt"] as? JsonPrimitive)?.longOrNull ?: now,
            updatedAt = (r["updatedAt"] as? JsonPrimitive)?.longOrNull ?: now,
        ) to drop[0]
    }

    /** Our export (`{format, trees}`), one tree, or an array of trees. */
    fun import(text: String, now: Long = System.currentTimeMillis()): Import {
        val data = runCatching { json.parseToJsonElement(text) }.getOrNull()
            ?: return Import.Error(tr("JSON 이 아닙니다", "Not JSON", "不是 JSON"))
        val list: List<JsonElement> = when {
            data is JsonArray -> data
            data is JsonObject && data["trees"] is JsonArray -> data["trees"] as JsonArray
            else -> listOf(data)
        }
        val trees = ArrayList<JosekiTree>()
        var dropped = 0
        for (raw in list) {
            val (tree, d) = cleanTree(raw, now) ?: continue
            trees.add(tree)
            dropped += d
        }
        if (trees.isEmpty()) return Import.Error(tr("정석이 들어 있지 않습니다", "No joseki in it", "其中没有定式"))
        return Import.Ok(trees, dropped)
    }

    /** One canonical key per node with a move (symmetries match), first one per key. */
    data class Key(val key: String, val path: Path, val ply: Int)

    fun indexKeys(t: JosekiTree): List<Key> {
        val out = ArrayList<Key>()
        val seen = HashSet<String>()
        JosekiOps.walk(t.root) { _, path, moves ->
            if (moves.isEmpty()) return@walk
            val key = PosKey.keyOf(moves, t.boardSize)
            if (seen.add(key)) out.add(Key(key, path, moves.size))
        }
        return out
    }
}
