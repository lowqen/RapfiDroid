package dev.gomoku.yixindroid.domain.rif

/**
 * The parts of a RenjuNet RIF database the opening explorer needs.
 *
 * Field names and semantics follow `rifdb/rif_import.py`, which is the schema
 * the PC pipeline built and therefore what the packs were made of. Anything the
 * packs do not carry (cities, months, native names, per-game clocks) is dropped
 * at parse time rather than modelled and ignored.
 *
 * ⚠ These objects hold RenjuNet content. The database allows **offline
 * non-commercial use only** and forbids putting its contents or modifications
 * on any website or online system — so nothing built from them is ever
 * uploaded, exported or shared by this app.
 */
data class RifDatabase(
    val games: List<RifGame>,
    val tournaments: Map<Int, RifTournament>,
    val players: Map<Int, RifPlayer>,
    val openings: List<RifOpening>,
    val rules: List<RifRule>,
    /** Country id -> name. The packs store the name, not the abbreviation. */
    val countries: Map<Int, String>,
    /** Why games were dropped, by reason — surfaced so a bad file is visible. */
    val skipped: Map<String, Int>,
) {
    val gameCount: Int get() = games.size

    /** Country name for an id that may be absent; "" is what the packs store then. */
    fun countryOf(id: Int?): String = id?.let { countries[it] } ?: ""
}

data class RifGame(
    val id: Int,
    val tournament: Int,
    val round: String,
    val rule: Int,
    val black: Int,
    val white: Int,
    /** 1.0 black win, 0.5 draw, 0.0 white win — the only values kept. */
    val blackResult: Double,
    val opening: Int,
    val alt: String,
    val swap: String,
    /** Board cells in play order, `y * 15 + x` (`rifkey.parse_move`). */
    val cells: IntArray,
    val info: String,
) {
    /** 0 black win, 1 draw, 2 white win — the packs' encoding. */
    val resultIndex: Int get() = if (blackResult == 1.0) 0 else if (blackResult == 0.5) 1 else 2

    // Generated equals/hashCode would compare `cells` by identity; these are
    // value objects in tests, so compare it by content.
    override fun equals(other: Any?): Boolean =
        this === other || (other is RifGame && id == other.id && cells.contentEquals(other.cells) &&
            tournament == other.tournament && round == other.round && rule == other.rule &&
            black == other.black && white == other.white && blackResult == other.blackResult &&
            opening == other.opening && alt == other.alt && swap == other.swap && info == other.info)

    override fun hashCode(): Int = id * 31 + cells.contentHashCode()
}

data class RifTournament(
    val id: Int,
    val name: String,
    val country: Int,
    val year: Int,
    val start: String,
    val end: String,
    val rated: Int,
)

data class RifPlayer(
    val id: Int,
    val name: String,
    val surname: String,
    val country: Int,
) {
    /** How the packs spell a player: given name then surname, "?" when neither. */
    val display: String
        get() = listOf(name, surname).filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { "?" }
}

data class RifOpening(val id: Int, val abbr: String, val name: String)

data class RifRule(val id: Int, val name: String)
