package dev.gomoku.yixindroid.data.rankings

import dev.gomoku.yixindroid.core.model.PlayerRef
import dev.gomoku.yixindroid.core.model.ShapeTheory
import dev.gomoku.yixindroid.domain.rankings.FreqBundle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * On-disk shape of `freq_data.json` (emitted by tools/freq35.py). Only the
 * fields the app needs are declared; unknown keys (opabbr/opko/opromaji — we
 * bundle [dev.gomoku.yixindroid.core.model.Opening26] instead) are ignored.
 *
 * `shapes` rows are heterogeneous JSON arrays `[rep, rankRaw, countRaw,
 * countStd]`, decoded by [ShapeTheorySerializer]. `games` rows are
 * `[black, white, rule, o3, k5, res]` int arrays.
 */
@Serializable
data class FreqDataDto(
    val generated: String = "",
    val players: List<List<String>> = emptyList(),
    val rules: List<String> = emptyList(),
    val shapes: List<@Serializable(with = ShapeTheorySerializer::class) ShapeTheory> = emptyList(),
    val games: List<IntArray> = emptyList(),
) {
    fun toBundle(): FreqBundle = FreqBundle(
        generated = generated,
        players = players.mapIndexed { i, p ->
            PlayerRef(index = i, name = p.getOrElse(0) { "" }, country = p.getOrElse(1) { "" })
        },
        rules = rules,
        shapes = shapes,
        games = games,
    )
}

/** Reads a `[String, Int, Int, Int]` JSON array into a [ShapeTheory] (read-only). */
object ShapeTheorySerializer : KSerializer<ShapeTheory> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ShapeTheory")

    override fun deserialize(decoder: Decoder): ShapeTheory {
        val jd = decoder as? JsonDecoder
            ?: error("ShapeTheorySerializer requires a JSON decoder")
        val arr = jd.decodeJsonElement().jsonArray
        return ShapeTheory(
            repMoves = arr[0].jsonPrimitive.content,
            theoryRankRaw = arr.getOrNull(1)?.jsonPrimitive?.int ?: 0,
            theoryCountRaw = arr.getOrNull(2)?.jsonPrimitive?.int ?: 0,
            theoryCountStd = arr.getOrNull(3)?.jsonPrimitive?.int ?: 0,
        )
    }

    override fun serialize(encoder: Encoder, value: ShapeTheory): Unit =
        error("ShapeTheory is read-only")
}
