package dev.gomoku.yixindroid.data.rankings

import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.ShapeRef
import dev.gomoku.yixindroid.domain.rankings.FreqBundle
import kotlinx.serialization.Serializable

/**
 * On-disk shape of `freq_data.json` (emitted by tools/freq35.py). Only the
 * fields the app needs are declared; unknown keys (opabbr/opko/opromaji — we
 * bundle [dev.gomoku.yixindroid.core.model.Opening26] instead) are ignored.
 *
 * `shapes` rows are the representative move order of each distinct 5-move
 * shape. `games` rows are `[black, white, rule, o3, k5, res]` int arrays.
 */
@Serializable
data class FreqDataDto(
    val generated: String = "",
    val players: List<List<String>> = emptyList(),
    val rules: List<String> = emptyList(),
    val shapes: List<String> = emptyList(),
    val games: List<IntArray> = emptyList(),
) {
    fun toBundle(): FreqBundle = FreqBundle(
        generated = generated,
        players = players.mapIndexed { i, p ->
            PlayerRef(index = i, name = p.getOrElse(0) { "" }, country = p.getOrElse(1) { "" })
        },
        rules = rules,
        shapes = shapes.map { ShapeRef(it) },
        games = games,
    )
}
