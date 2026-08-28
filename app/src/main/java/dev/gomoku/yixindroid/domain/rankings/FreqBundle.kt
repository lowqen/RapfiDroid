package dev.gomoku.yixindroid.domain.rankings

import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.ShapeRef

/**
 * A parsed, framework-free view of an imported `freq_data.json`. The data layer
 * builds this from the on-disk DTO; the analyzer consumes only this, so all
 * ranking logic stays pure and JVM-unit-testable.
 *
 * Each game row is a dense `IntArray` of `[blackIdx, whiteIdx, ruleIdx, o3, k5,
 * res]` — exactly the tuple freq35 emits. `o3` is the 3-move opening (0..25 or
 * 26), `k5` is the shape index (or -1), `res` is 2/1/0/3 (B/D/W-win/unknown).
 *
 * RenjuNet-derived: never bundled, never redistributed — imported by the user.
 */
data class FreqBundle(
    val generated: String,
    val players: List<PlayerRef>,
    val rules: List<String>,
    val shapes: List<ShapeRef>,
    val games: List<IntArray>,
) {
    val gameCount: Int get() = games.size

    companion object {
        val EMPTY = FreqBundle("", emptyList(), emptyList(), emptyList(), emptyList())
    }
}
