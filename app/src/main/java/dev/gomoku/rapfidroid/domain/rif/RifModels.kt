package dev.gomoku.rapfidroid.domain.rif

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
