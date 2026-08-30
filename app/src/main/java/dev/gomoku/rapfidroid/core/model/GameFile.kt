package dev.gomoku.rapfidroid.core.model

import dev.gomoku.rapfidroid.core.i18n.tr

/**
 * The three saved-game formats the desktop reads (`load_game_file`,
 * main.c:3536) and the two it writes (`show_dialog_save` / `queue_add_current`).
 *
 * The desktop picks the format from the extension alone and replays the moves
 * onto the **current** board — it reads the stored board size but never uses it
 * (`fscanf("%*d")`). This does the same, and simply refuses moves that would
 * fall outside the board in use.
 */
enum class GameFileFormat(val extension: String, val label: String) {
    /** Piskvork: header line, then `col,row,time` 1-based, terminated by `-1`. */
    PSQ("psq", tr("Piskvork 기보", "Piskvork game")),

    /** Yixin: size, size, count, then `row col` per line. */
    SAV("sav", tr("Yixin 저장 국면", "Yixin saved position")),

    /** Yixin binary: a count byte, then one byte per move (`col * 15 + row`). */
    POS("pos", tr("POS 국면", "POS position")),
    ;

    companion object {
        fun of(name: String): GameFileFormat? {
            val dot = name.lastIndexOf('.')
            if (dot < 0) return null
            val ext = name.substring(dot + 1).lowercase()
            return entries.firstOrNull { it.extension == ext }
        }
    }
}

/** A loaded game: the line itself plus the name reports are titled with. */
data class GameFileContent(val moves: List<Move>, val name: String)

object GameFile {

    /** `.pos` packs a 15x15 board into one byte per move, whatever the setting. */
    private const val POS_SIZE = 15

    /**
     * Parse [bytes] as [format]. Returns null when the file is unreadable;
     * moves outside [size] end the line, exactly as the desktop's `make_move`
     * bounds check would (it silently ignores them).
     */
    fun parse(bytes: ByteArray, format: GameFileFormat, size: Int): List<Move>? = when (format) {
        GameFileFormat.POS -> parsePos(bytes, size)
        GameFileFormat.SAV -> parseSav(text(bytes), size)
        GameFileFormat.PSQ -> parsePsq(text(bytes), size)
    }

    private fun text(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1)

    /** main.c:3546 — `x = xy % 15`, `y = xy / 15`, then `make_move(x, y)`. */
    private fun parsePos(bytes: ByteArray, size: Int): List<Move>? {
        if (bytes.isEmpty()) return null
        val count = bytes[0].toInt() and 0xFF
        val out = ArrayList<Move>(count)
        for (i in 1..count) {
            if (i >= bytes.size) break
            val xy = bytes[i].toInt() and 0xFF
            val move = Move(x = xy / POS_SIZE, y = xy % POS_SIZE)
            if (!move.isInside(size) || move in out) break
            out += move
        }
        return out
    }

    /** main.c:3568 — two sizes to skip, a count, then `row col` pairs. */
    private fun parseSav(text: String, size: Int): List<Move>? {
        val numbers = NUMBER.findAll(text).map { it.value.toInt() }.toList()
        if (numbers.size < 3) return null
        val count = numbers[2]
        val out = ArrayList<Move>(count.coerceAtLeast(0))
        var i = 3
        while (out.size < count && i + 1 < numbers.size) {
            val move = Move(x = numbers[i + 1], y = numbers[i])
            i += 2
            if (!move.isInside(size) || move in out) break
            out += move
        }
        return out
    }

    /**
     * main.c:3592 — skip the header line, then read `col,row,time` (1-based)
     * until a line that does not start with a digit, or the `-1` terminator.
     */
    private fun parsePsq(text: String, size: Int): List<Move>? {
        val lines = text.lineSequence().toList()
        if (lines.isEmpty()) return null
        val out = ArrayList<Move>()
        for (raw in lines.drop(1)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.firstOrNull()?.isDigit() != true) break
            val parts = line.split(',')
            if (parts.size < 2) break
            val col = parts[0].trim().toIntOrNull() ?: break
            val row = parts[1].trim().toIntOrNull() ?: break
            if (col < 0) break               // the `-1` terminator
            val move = Move(x = col - 1, y = row - 1)
            if (!move.isInside(size) || move in out) break
            out += move
        }
        return out
    }

    /** `show_dialog_save` (main.c:3700): size, size, count, then `row col`. */
    fun writeSav(moves: List<Move>, size: Int): ByteArray = buildString {
        append(size).append('\n')
        append(size).append('\n')
        append(moves.size).append('\n')
        moves.forEach { append(it.y).append(' ').append(it.x).append('\n') }
    }.toByteArray(Charsets.US_ASCII)

    /** `queue_add_current` (main.c:7470): the layout the .psq reader parses. */
    fun writePsq(moves: List<Move>, size: Int): ByteArray = buildString {
        append("Piskvorky ").append(size).append('x').append(size).append(", 0:0, 0\n")
        moves.forEach { append(it.x + 1).append(',').append(it.y + 1).append(",0\n") }
        append("-1\n")
    }.toByteArray(Charsets.US_ASCII)

    /**
     * `queue_basename` (main.c:7263): the file name without directories or
     * extension, spaces folded to underscores — it names the exported reports.
     */
    fun baseName(path: String): String {
        val tail = path.substringAfterLast('/').substringAfterLast('\\')
        val stem = tail.substringBefore('.')
        val cleaned = stem.map { if (it == ' ' || it == '\t') '_' else it }.joinToString("")
        return cleaned.ifEmpty { "game" }
    }

    private val NUMBER = Regex("-?\\d+")
}
