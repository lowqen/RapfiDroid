package dev.gomoku.rapfidroid.domain.engine

import dev.gomoku.rapfidroid.core.model.DbBound
import dev.gomoku.rapfidroid.core.model.DbCell
import dev.gomoku.rapfidroid.core.model.DbEntry
import dev.gomoku.rapfidroid.core.model.DbFileProgress
import dev.gomoku.rapfidroid.core.model.DbSnapshot
import dev.gomoku.rapfidroid.core.model.Move

/**
 * Assembles the `MESSAGE DATABASE …` stream into a [DbSnapshot], mirroring the
 * desktop's bookkeeping (main.c `iochannelout_watch`, branch `MESSAGE DATABASE`):
 *
 *  - `REFRESH` clears every cell tag before a fresh set arrives;
 *  - each cell line updates one point (packed tag + free text);
 *  - `DONE` ends the batch — the caller decides whether this batch is still the
 *    one it asked for (the desktop's `dbqueryseq`/`dbdoneseq` pairing, which
 *    lives in the repository here);
 *  - `TEXT` carries the position comment, possibly across several physical
 *    lines, with `\"` and `\\` escapes;
 *  - `ONE` is the single-record reply of `dbval`.
 *
 * Pure and single-threaded, like [SearchAggregator].
 */
class DatabaseAggregator {

    private val cells = LinkedHashMap<Move, DbCell>()
    private var comment: String = ""
    private var entry: DbEntry? = null

    /** Set while a `TEXT "…` block is still missing its closing quote. */
    private val pendingText = StringBuilder()
    private var textOpen = false

    /** Latest file operation in flight, or null. */
    var fileProgress: DbFileProgress? = null
        private set

    /** True once a DONE closed a batch — the repository pairs it with its query. */
    var batchComplete: Boolean = false
        private set

    fun snapshot(): DbSnapshot = DbSnapshot(
        cells = LinkedHashMap(cells),
        comment = comment,
        entry = entry,
    )

    fun reset() {
        cells.clear()
        comment = ""
        entry = null
        pendingText.setLength(0)
        textOpen = false
        batchComplete = false
        fileProgress = null
    }

    /** Clears only the per-cell values, as `REFRESH` does. */
    fun clearCells() = cells.clear()

    /**
     * Feed one parsed response. Returns a new snapshot when the database view
     * changed, else null. While a multi-line comment is open every line is
     * swallowed into it — exactly what the desktop does by reading ahead.
     */
    fun consume(response: EngineResponse): DbSnapshot? {
        if (textOpen && response !is EngineResponse.DbTextLine) {
            return appendCommentLine(response.raw.trimEnd('\r', '\n'))
        }
        return when (response) {
            is EngineResponse.DbRefresh -> {
                cells.clear()
                batchComplete = false
                snapshot()
            }

            is EngineResponse.DbCellValue -> {
                val label = decodeTag(response.packedTag)
                if (label.isEmpty() && response.text.isEmpty()) {
                    cells.remove(response.move)
                } else {
                    cells[response.move] = DbCell(
                        move = response.move,
                        tagLabel = label,
                        text = response.text,
                        fields = response.fields,
                    )
                }
                snapshot()
            }

            is EngineResponse.DbDone -> {
                batchComplete = true
                snapshot()
            }

            is EngineResponse.DbOne -> {
                entry = DbEntry(
                    tag = if (response.tag > 0) response.tag.toChar() else null,
                    value = response.value,
                    depth = response.depth,
                    bound = DbBound.of(response.bound),
                    label = response.label,
                )
                snapshot()
            }

            is EngineResponse.DbTextLine -> startComment(response.body)

            is EngineResponse.DbFileEvent -> {
                fileProgress = if (response.started) {
                    DbFileProgress(saving = response.saving, file = response.file)
                } else {
                    null
                }
                null
            }

            else -> null
        }
    }

    /** `TEXT "…"` — unescape in place, keep reading if the quote never closes. */
    private fun startComment(body: String): DbSnapshot? {
        pendingText.setLength(0)
        if (!body.startsWith('"')) {
            // No quoted payload at all: the position simply has no comment.
            comment = ""
            textOpen = false
            return snapshot()
        }
        textOpen = true
        return appendCommentLine(body.drop(1), firstLine = true)
    }

    private fun appendCommentLine(line: String, firstLine: Boolean = false): DbSnapshot? {
        if (!firstLine) pendingText.append('\n')
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {                     // closing quote: comment complete
                    comment = pendingText.toString()
                    pendingText.setLength(0)
                    textOpen = false
                    return snapshot()
                }
                c == '\\' && i + 1 < line.length -> {
                    val next = line[i + 1]
                    if (next == '"' || next == '\\') {
                        pendingText.append(next)
                        i += 2
                        continue
                    }
                    pendingText.append(c)
                }
                else -> pendingText.append(c)
            }
            i++
        }
        return null // still open, wait for the next line
    }

    companion object {
        /**
         * Decode `boardtag`: 1-4 characters packed big-endian into one int
         * (main.c:1700-1719). `'w'<<16 | '3'<<8 | '9'` → "w39". The desktop also
         * upper-cases a leading w/l/d when drawing and renders step 0 as `W*`
         * (main.c:1967-2011) — done here so the label is display-ready.
         */
        fun decodeTag(tag: Int): String {
            if (tag <= 0) return ""
            val bytes = when {
                tag < 128 -> byteArrayOf(tag.toByte())
                tag ushr 16 == 0 -> byteArrayOf((tag shr 8).toByte(), tag.toByte())
                tag ushr 24 == 0 ->
                    byteArrayOf((tag shr 16).toByte(), (tag shr 8).toByte(), tag.toByte())
                else -> byteArrayOf(
                    (tag shr 24).toByte(), (tag shr 16).toByte(),
                    (tag shr 8).toByte(), tag.toByte(),
                )
            }
            val text = String(bytes, Charsets.ISO_8859_1)
            val head = when (text.first()) {
                'w' -> 'W'
                'l' -> 'L'
                'd' -> 'D'
                else -> text.first()
            }
            val rest = text.drop(1)
            // A single-character tag is drawn as-is; only the multi-character
            // form gets the "no recorded distance" star (main.c:2009-2011,
            // where atoi() of a non-numeric or zero step yields "W*").
            val step = rest.trimStart().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            return when {
                rest.isNotEmpty() && (head == 'W' || head == 'L') && step == 0 -> "$head*"
                else -> head + rest
            }
        }
    }
}
