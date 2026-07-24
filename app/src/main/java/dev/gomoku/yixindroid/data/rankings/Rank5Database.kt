package dev.gomoku.yixindroid.data.rankings

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.yixindroid.core.model.ShapeRank
import java.io.File
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only access to the bundled `rank5.db` — the theoretical 5-move ranking.
 * This dataset is **pure computation (RenjuNet-free) and safe to bundle**.
 *
 * We ship it gzipped in `assets/rank5.db.gz` (~4 MB vs ~17 MB raw); on first use
 * it is decompressed into internal storage and opened read-only with plain
 * SQLite. Room is deliberately avoided here: the table is read-only with no
 * migrations, and Room's pre-packaged-DB identity-hash check is fragile to
 * satisfy without an on-device build. All calls block — invoke off the main
 * thread (the repository wraps them on the IO dispatcher).
 */
@Singleton
class Rank5Database @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var db: SQLiteDatabase? = null

    private fun database(): SQLiteDatabase = db ?: synchronized(this) {
        db ?: open().also { db = it }
    }

    private fun open(): SQLiteDatabase {
        val file = File(context.filesDir, DB_NAME)
        val marker = File(context.filesDir, "$DB_NAME.v")
        val current = marker.takeIf { it.exists() }?.readText()?.trim()
        if (!file.exists() || current != ASSET_VERSION) {
            context.assets.open(ASSET).use { gz ->
                GZIPInputStream(gz).use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }
            }
            marker.writeText(ASSET_VERSION)
        }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun query(sql: String, args: Array<String> = emptyArray()): List<ShapeRank> {
        database().rawQuery(sql, args).use { c ->
            val out = ArrayList<ShapeRank>(c.count)
            while (c.moveToNext()) out.add(c.toShapeRank())
            return out
        }
    }

    /** Theoretical ranking (best first). [m5Max] limits the shape's board extent
     *  (≤4 ≈ 9×9, ≤3 ≈ 7×7); null = full 15×15. */
    fun top(limit: Int, m5Max: Int? = null): List<ShapeRank> {
        val where = if (m5Max != null) "WHERE m5_dist <= $m5Max " else ""
        return query("SELECT $COLS FROM shape ${where}ORDER BY rank_raw LIMIT ?",
            arrayOf(limit.toString()))
    }

    /** Filter by representative-move substring and/or opening abbr, ranked. */
    fun search(repContains: String?, opening: String?, m5Max: Int?, limit: Int): List<ShapeRank> {
        val clauses = ArrayList<String>()
        val args = ArrayList<String>()
        if (!repContains.isNullOrBlank()) {
            clauses += "rep_moves LIKE ?"
            args += "%${repContains.trim().lowercase()}%"
        }
        if (!opening.isNullOrBlank()) {
            clauses += "opening = ?"
            args += opening
        }
        if (m5Max != null) clauses += "m5_dist <= $m5Max"
        val where = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")} "
        args += limit.toString()
        return query("SELECT $COLS FROM shape ${where}ORDER BY rank_raw LIMIT ?", args.toTypedArray())
    }

    /** Look up shapes by their (unique) `rank_raw`, for joining freq rows to the
     *  authoritative theoretical opening + canonical representative. */
    fun byRanks(ranks: Collection<Int>): Map<Int, ShapeRank> {
        if (ranks.isEmpty()) return emptyMap()
        val placeholders = ranks.joinToString(",") { "?" }
        return query(
            "SELECT $COLS FROM shape WHERE rank_raw IN ($placeholders)",
            ranks.map { it.toString() }.toTypedArray(),
        ).associateBy { it.rankRaw }
    }

    /** Distribution of shapes by placement-multiplicity group (32/16/8/4). */
    fun groupDistribution(): List<Pair<Int, Int>> {
        database().rawQuery(
            "SELECT count_raw, COUNT(*) FROM shape GROUP BY count_raw ORDER BY count_raw DESC",
            emptyArray(),
        ).use { c ->
            val out = ArrayList<Pair<Int, Int>>()
            while (c.moveToNext()) out.add(c.getInt(0) to c.getInt(1))
            return out
        }
    }

    /** Shape count per opening (abbr → count), for the 3-move theory overview. */
    fun openingCounts(): Map<String, Int> {
        database().rawQuery(
            "SELECT opening, COUNT(*) FROM shape GROUP BY opening", emptyArray(),
        ).use { c ->
            val out = LinkedHashMap<String, Int>()
            while (c.moveToNext()) out[c.getString(0)] = c.getInt(1)
            return out
        }
    }

    fun total(): Int = database().rawQuery(
        "SELECT COUNT(*) FROM shape", emptyArray(),
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun Cursor.toShapeRank() = ShapeRank(
        rankStd = getInt(0), rankRaw = getInt(1),
        countStd = getInt(2), countRaw = getInt(3),
        perPlacement = getInt(4), placements = getInt(5), stabilizer = getInt(6),
        opening = getString(7), repMoves = getString(8), m5Dist = getInt(9),
    )

    companion object {
        private const val ASSET = "rank5.db.gz"
        private const val DB_NAME = "rank5.db"
        // Bump when the bundled asset changes so old extractions are replaced.
        private const val ASSET_VERSION = "1"
        private const val COLS =
            "rank_std,rank_raw,count_std,count_raw,per_placement,placements,stabilizer," +
                "opening,rep_moves,m5_dist"
    }
}
