package dev.gomoku.yixindroid.domain.rif

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

/**
 * Reads a RenjuNet `.rif` (43 MB of XML at the time of writing, ~158k games)
 * into memory, streaming.
 *
 * **Why a hand-written scanner.** The platform's `XmlPullParser` is not
 * available to JVM unit tests and StAX is not available on Android, so a shared
 * implementation would have to be one or the other plus a shim. The RIF is
 * machine-generated and regular — attributes always double-quoted, no CDATA, no
 * namespaces — so a purpose-built scanner is both smaller than the shim and
 * testable in exactly the form that runs on the device.
 *
 * Streaming matters: the file is 43 MB of UTF-8, which is ~86 MB once it is a
 * `String`. Nothing here holds more than one element at a time.
 *
 * Games are dropped exactly where `rifdb/rif_import.py` drops them — unplayable
 * or ambiguous records only — and the reasons are counted rather than hidden,
 * because "12 fewer games than the PC built" is otherwise invisible.
 */
class RifParser(private val boardSize: Int = 15) {

    fun parse(input: InputStream, onProgress: (games: Int) -> Unit = {}): RifDatabase {
        val reader = InputStreamReader(input, Charsets.UTF_8).buffered(1 shl 16)
        return parse(reader, onProgress)
    }

    fun parse(reader: Reader, onProgress: (games: Int) -> Unit = {}): RifDatabase {
        val scanner = XmlLite(reader)
        val games = ArrayList<RifGame>(200_000)
        val tournaments = HashMap<Int, RifTournament>()
        val players = HashMap<Int, RifPlayer>()
        val openings = ArrayList<RifOpening>()
        val rules = ArrayList<RifRule>()
        val countries = HashMap<Int, String>()
        val skipped = HashMap<String, Int>()
        val seenIds = HashSet<Int>()

        // Game children arrive as sibling elements, so the open <game> is held
        // until </game> tells us it is complete.
        var pending: PendingGame? = null

        while (true) {
            val tag = scanner.nextTag() ?: break
            // Captured as a val each turn: a smart cast on a var the loop
            // reassigns is the kind of thing that compiles today and stops
            // compiling after an unrelated edit.
            val open = pending
            when {
                tag.closing && tag.name == "game" -> {
                    open?.let { finish(it, games, seenIds, skipped) }
                    pending = null
                    if (games.size % PROGRESS_EVERY == 0 && games.isNotEmpty()) onProgress(games.size)
                }
                tag.closing -> Unit
                tag.name == "game" -> {
                    // A <game> that never closed: count it rather than lose it silently.
                    if (open != null) bump(skipped, "unclosed_game")
                    pending = PendingGame(
                        id = tag.int("id", -1),
                        tournament = tag.int("tournament"),
                        round = tag.str("round"),
                        rule = tag.int("rule"),
                        black = tag.int("black"),
                        white = tag.int("white"),
                        bresult = tag.str("bresult"),
                        opening = tag.int("opening"),
                        alt = tag.str("alt"),
                        swap = tag.str("swap"),
                    )
                    if (tag.selfClosing) {
                        bump(skipped, "empty_moves")
                        pending = null
                    }
                }
                tag.name == "move" && open != null -> open.moves = scanner.textUntilClose()
                tag.name == "info" && open != null -> open.info = scanner.textUntilClose()
                tag.name == "tournament" -> {
                    val id = tag.int("id")
                    tournaments[id] = RifTournament(
                        id = id,
                        name = tag.str("name"),
                        country = tag.int("country"),
                        year = tag.int("year"),
                        start = tag.str("start"),
                        end = tag.str("end"),
                        rated = tag.int("rated"),
                    )
                }
                tag.name == "player" -> {
                    val id = tag.int("id")
                    players[id] = RifPlayer(id, tag.str("name"), tag.str("surname"), tag.int("country"))
                }
                tag.name == "opening" -> openings += RifOpening(tag.int("id"), tag.str("abbr"), tag.str("name"))
                tag.name == "rule" -> rules += RifRule(tag.int("id"), tag.str("name"))
                tag.name == "country" -> countries[tag.int("id")] = tag.str("name")
            }
        }
        pending?.let { finish(it, games, seenIds, skipped) }
        onProgress(games.size)

        return RifDatabase(
            games = games,
            tournaments = tournaments,
            players = players,
            openings = openings.sortedBy { it.id },
            rules = rules.sortedBy { it.id },
            countries = countries,
            skipped = skipped,
        )
    }

    private class PendingGame(
        val id: Int,
        val tournament: Int,
        val round: String,
        val rule: Int,
        val black: Int,
        val white: Int,
        val bresult: String,
        val opening: Int,
        val alt: String,
        val swap: String,
        var moves: String = "",
        var info: String = "",
    )

    private fun finish(
        g: PendingGame,
        out: MutableList<RifGame>,
        seen: MutableSet<Int>,
        skipped: MutableMap<String, Int>,
    ) {
        // The order of these three, and the fact that only an accepted game
        // claims its id, follow `rif_import.run` — a rejected record must not
        // make the next one with the same id look like a duplicate.
        if (g.id < 0 || g.id in seen) return bump(skipped, "dup_or_bad_id")
        val cells = parseMoves(g.moves)
            ?: return bump(skipped, if (g.moves.isBlank()) "empty_moves" else "bad_moves")
        val result = g.bresult.toDoubleOrNull() ?: return bump(skipped, "bad_bresult")
        if (result !in ACCEPTED_RESULTS) return bump(skipped, "bad_bresult")
        seen.add(g.id)
        out += RifGame(
            id = g.id,
            tournament = g.tournament,
            round = g.round,
            rule = g.rule,
            black = g.black,
            white = g.white,
            blackResult = result,
            opening = g.opening,
            alt = g.alt,
            swap = g.swap,
            cells = cells,
            info = g.info,
        )
    }

    /**
     * `"h8 h9 ..."` -> board cells. Null when a token is malformed or a square
     * repeats, which is `rif_import.validate_moves`' rule: a game that cannot be
     * replayed is not a game.
     */
    fun parseMoves(text: String): IntArray? {
        val tokens = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val cells = IntArray(tokens.size)
        val seen = HashSet<Int>(tokens.size * 2)
        for ((i, token) in tokens.withIndex()) {
            val cell = parseMove(token) ?: return null
            if (!seen.add(cell)) return null
            cells[i] = cell
        }
        return cells
    }

    /** `"h8"` -> `y * size + x`, with `x = col - 'a'` and `y = size - row`. */
    fun parseMove(token: String): Int? {
        if (token.length < 2) return null
        val x = token[0] - 'a'
        if (x < 0 || x >= boardSize) return null
        val row = token.substring(1).toIntOrNull() ?: return null
        if (row < 1 || row > boardSize) return null
        return (boardSize - row) * boardSize + x
    }

    private fun bump(map: MutableMap<String, Int>, reason: String) {
        map[reason] = (map[reason] ?: 0) + 1
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val PROGRESS_EVERY = 5_000

        /** Black win, draw, white win. Anything else is a record we cannot score. */
        val ACCEPTED_RESULTS = setOf(0.0, 0.5, 1.0)
    }
}
