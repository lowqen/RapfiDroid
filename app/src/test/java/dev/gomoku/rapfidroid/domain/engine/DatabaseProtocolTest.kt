package dev.gomoku.rapfidroid.domain.engine

import dev.gomoku.rapfidroid.core.model.DbBound
import dev.gomoku.rapfidroid.core.model.DbCell
import dev.gomoku.rapfidroid.core.model.DbCellKind
import dev.gomoku.rapfidroid.core.model.DbDeleteFilter
import dev.gomoku.rapfidroid.core.model.DbDeleteScope
import dev.gomoku.rapfidroid.core.model.DbSnapshot
import dev.gomoku.rapfidroid.core.model.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P7 protocol tests. The oracle is the desktop (`main.c`): every expected wire
 * string below is the text `execute_command` / `show_database` sends for the same
 * action, and the parsed values are what `iochannelout_watch` extracts.
 */
class DatabaseProtocolTest {

    private val coord = CoordMapper()

    /** h8 i9 j10 — 15x15, so h8 = (x=7, y=7) and the wire form is "row,col". */
    private val path = listOf(Move(7, 7), Move(8, 6), Move(9, 5))
    private val pathWire = "7,7\n6,8\n5,9"

    // ---- commands -----------------------------------------------------------

    @Test
    fun `query all sends head, path and done`() {
        assertEquals(
            "yxquerydatabaseallt\n$pathWire\ndone",
            DbQueryAll(path).serialize(coord),
        )
    }

    @Test
    fun `query one and text use their own heads`() {
        assertEquals("yxquerydatabaseone\n$pathWire\ndone", DbQueryOne(path).serialize(coord))
        assertEquals("yxquerydatabasetext\n$pathWire\ndone", DbQueryText(path).serialize(coord))
    }

    @Test
    fun `empty path still sends head and done`() {
        assertEquals("yxquerydatabaseallt\ndone", DbQueryAll(emptyList()).serialize(coord))
    }

    @Test
    fun `comment is quoted and escapes quotes and backslashes`() {
        val command = DbEditComment("""say "hi" \ now""", path)
        assertEquals(
            """yxedittextdatabase "say \"hi\" \\ now"""" + "\n$pathWire\ndone",
            command.serialize(coord),
        )
    }

    @Test
    fun `cell label targets the cell and keeps the path`() {
        val command = DbEditLabel(target = Move(8, 6), label = "A", moves = path)
        assertEquals("yxeditlabeldatabase 6,8 A\n$pathWire\ndone", command.serialize(coord))
    }

    @Test
    fun `empty cell label keeps the trailing space that deletes it`() {
        // main.c:2618 sends "yxeditlabeldatabase %d,%d \n" for an empty text.
        val command = DbEditLabel(target = Move(7, 7), label = "", moves = emptyList())
        assertEquals("yxeditlabeldatabase 7,7 \ndone", command.serialize(coord))
    }

    @Test
    fun `cell label is capped at six characters like the desktop entry`() {
        val command = DbEditLabel(Move(7, 7), "ABCDEFGH", emptyList())
        assertTrue(command.serialize(coord).startsWith("yxeditlabeldatabase 7,7 ABCDEF\n"))
    }

    @Test
    fun `record edits use the desktop's masks`() {
        assertEquals(
            "yxedittvddatabase 1 87 0 0\n$pathWire\ndone",
            DbEditRecord.tag('W', path).serialize(coord),
        )
        assertEquals(
            "yxedittvddatabase 1 -1 0 0\n$pathWire\ndone",
            DbEditRecord.tag(null, path).serialize(coord),
        )
        assertEquals(
            "yxedittvddatabase 2 -1 250 0\n$pathWire\ndone",
            DbEditRecord.value(250, path).serialize(coord),
        )
        assertEquals(
            "yxedittvddatabase 4 -1 0 18\n$pathWire\ndone",
            DbEditRecord.depth(18, path).serialize(coord),
        )
    }

    @Test
    fun `best move commands`() {
        assertEquals("yxsetbestmovedatabase\n$pathWire\ndone", DbSetBestMove(path).serialize(coord))
        assertEquals(
            "yxclearbestmovedatabase\n$pathWire\ndone",
            DbClearBestMove(path).serialize(coord),
        )
    }

    @Test
    fun `delete one and delete all variants match the console commands`() {
        assertEquals("yxdeletedatabaseone\n$pathWire\ndone", DbDeleteOne(path).serialize(coord))

        fun head(scope: DbDeleteScope) =
            DbDeleteAll(scope, emptyList()).serialize(coord).lineSequence().first()

        assertEquals("yxdeletedatabaseall", head(DbDeleteScope(DbDeleteFilter.ALL)))
        assertEquals("yxdeletedatabaseall nonwl", head(DbDeleteScope(DbDeleteFilter.NON_WL)))
        assertEquals(
            "yxdeletedatabaseall nonwlrecursive",
            head(DbDeleteScope(DbDeleteFilter.NON_WL, recursive = true)),
        )
        assertEquals("yxdeletedatabaseall wl", head(DbDeleteScope(DbDeleteFilter.WL)))
        assertEquals(
            "yxdeletedatabaseall wlrecursive",
            head(DbDeleteScope(DbDeleteFilter.WL, recursive = true)),
        )
        assertEquals("yxdeletedatabaseall w", head(DbDeleteScope(DbDeleteFilter.WIN)))
        assertEquals(
            "yxdeletedatabaseall wrecursive",
            head(DbDeleteScope(DbDeleteFilter.WIN, recursive = true)),
        )
        assertEquals("yxdeletedatabaseall l", head(DbDeleteScope(DbDeleteFilter.LOSE)))
        assertEquals(
            "yxdeletedatabaseall lrecursive",
            head(DbDeleteScope(DbDeleteFilter.LOSE, recursive = true)),
        )
        assertEquals("yxdeletedatabaseall wlnostep", head(DbDeleteScope(DbDeleteFilter.WL_NO_STEP)))
        assertEquals(
            "yxdeletedatabaseall wlnosteprecursive",
            head(DbDeleteScope(DbDeleteFilter.WL_NO_STEP, recursive = true)),
        )
        assertEquals(
            "yxdeletedatabaseall wlinstep 5",
            head(DbDeleteScope(DbDeleteFilter.WL_IN_STEP, step = 5)),
        )
        assertEquals(
            "yxdeletedatabaseall wlinsteprecursive 12",
            head(DbDeleteScope(DbDeleteFilter.WL_IN_STEP, recursive = true, step = 12)),
        )
    }

    @Test
    fun `file commands put the engine-side path on the second line`() {
        assertEquals("yxsetdatabase\nrapfi.db", DbSetFile("rapfi.db").serialize(coord))
        assertEquals("yxdbmerge\nother.db", DbMerge(" other.db ").serialize(coord))
        assertEquals("yxdbsplit\npart.db", DbSplit("part.db").serialize(coord))
        assertEquals("yxlibtodb\nbook.lib", DbLibImport("book.lib").serialize(coord))
        assertEquals("yxdbtolib\nbook.lib", DbLibExport("book.lib").serialize(coord))
        assertEquals("yxdbtotxt\npart.csv", DbTextExport("part.csv").serialize(coord))
        assertEquals("yxdbtotxtall\nall.csv", DbTextExportAll("all.csv").serialize(coord))
        assertEquals("yxtxttodb\nall.csv", DbTextImport("all.csv").serialize(coord))
        assertEquals("yxdbtopos\nout.pos", DbToPos("out.pos").serialize(coord))
    }

    @Test
    fun `bare commands`() {
        assertEquals("yxsavedatabase", DbSave.serialize(coord))
        assertEquals("yxdbcheck", DbCheck.serialize(coord))
        assertEquals("yxdbfix", DbFix.serialize(coord))
        assertEquals("info usedatabase 1", DbUse(true).serialize(coord))
        assertEquals("info usedatabase 0", DbUse(false).serialize(coord))
    }

    // ---- parsing ------------------------------------------------------------

    @Test
    fun `cell line parses coordinates, packed tag, fields and text`() {
        val parsed = ResponseParser.parse("MESSAGE DATABASE 7 8 7960377 -12 20 3 0 note", coord)
        parsed as EngineResponse.DbCellValue
        assertEquals(Move(8, 7), parsed.move)          // row 7, col 8
        assertEquals(7960377, parsed.packedTag)        // "w39"
        assertEquals(listOf(-12, 20, 3, 0), parsed.fields)
        assertEquals("note", parsed.text)
    }

    @Test
    fun `cell line without a text is fine`() {
        val parsed = ResponseParser.parse("MESSAGE DATABASE 0 0 0 0 0 0 0", coord)
        parsed as EngineResponse.DbCellValue
        assertEquals("", parsed.text)
    }

    @Test
    fun `refresh, done, one and file events`() {
        assertTrue(ResponseParser.parse("MESSAGE DATABASE REFRESH", coord) is EngineResponse.DbRefresh)
        assertTrue(ResponseParser.parse("MESSAGE DATABASE DONE", coord) is EngineResponse.DbDone)

        val one = ResponseParser.parse("MESSAGE DATABASE ONE 87 250 18 3 h8", coord)
        one as EngineResponse.DbOne
        assertEquals('W'.code, one.tag)
        assertEquals(250, one.value)
        assertEquals(18, one.depth)
        assertEquals(3, one.bound)
        assertEquals("h8", one.label)

        val save = ResponseParser.parse("MESSAGE DATABASE SAVE START rapfi.db", coord)
        save as EngineResponse.DbFileEvent
        assertTrue(save.saving && save.started)
        assertEquals("rapfi.db", save.file)

        val load = ResponseParser.parse("MESSAGE DATABASE LOAD DONE", coord)
        load as EngineResponse.DbFileEvent
        assertTrue(!load.saving && !load.started)
    }

    @Test
    fun `plain messages are not swallowed by the database branch`() {
        val parsed = ResponseParser.parse("MESSAGE Saved database file using 0 ms", coord)
        assertTrue(parsed is EngineResponse.Message)
    }

    // ---- packed tag decoding ------------------------------------------------

    @Test
    fun `packed tags decode big-endian like main c`() {
        assertEquals("", DatabaseAggregator.decodeTag(0))
        assertEquals("W", DatabaseAggregator.decodeTag('w'.code))          // single char, no star
        assertEquals("D", DatabaseAggregator.decodeTag('d'.code))
        assertEquals("W39", DatabaseAggregator.decodeTag(pack("w39")))
        assertEquals("L5", DatabaseAggregator.decodeTag(pack("l5")))
        assertEquals("39%", DatabaseAggregator.decodeTag(pack("39%")))
        assertEquals("100%", DatabaseAggregator.decodeTag(pack("100%")))
        // a recorded step of zero is drawn as "W*" (main.c:2009-2011)
        assertEquals("W*", DatabaseAggregator.decodeTag(pack("w0")))
    }

    private fun pack(text: String): Int =
        text.fold(0) { acc, c -> (acc shl 8) or c.code }

    // ---- aggregation --------------------------------------------------------

    @Test
    fun `aggregator collects cells, clears on refresh and closes on done`() {
        val agg = DatabaseAggregator()
        agg.consume(ResponseParser.parse("MESSAGE DATABASE REFRESH", coord))
        agg.consume(ResponseParser.parse("MESSAGE DATABASE 7 7 ${pack("w39")} 0 0 0 0", coord))
        agg.consume(ResponseParser.parse("MESSAGE DATABASE 6 8 ${pack("63%")} 0 0 0 0 x", coord))
        val snapshot = agg.consume(ResponseParser.parse("MESSAGE DATABASE DONE", coord))!!
        assertEquals(2, snapshot.cells.size)
        assertEquals("W39", snapshot.cells[Move(7, 7)]?.tagLabel)
        assertEquals("x", snapshot.cells[Move(8, 6)]?.text)
        assertTrue(agg.batchComplete)

        agg.consume(ResponseParser.parse("MESSAGE DATABASE REFRESH", coord))
        assertEquals(0, agg.snapshot().cells.size)
    }

    @Test
    fun `single line comment is unescaped`() {
        val agg = DatabaseAggregator()
        val snapshot = agg.consume(
            ResponseParser.parse("""MESSAGE DATABASE TEXT "say \"hi\" \\ ok"""", coord),
        )!!
        assertEquals("""say "hi" \ ok""", snapshot.comment)
    }

    @Test
    fun `multi line comment keeps reading until the closing quote`() {
        val agg = DatabaseAggregator()
        assertNull(agg.consume(ResponseParser.parse("""MESSAGE DATABASE TEXT "line one""", coord)))
        assertNull(agg.consume(ResponseParser.parse("line two", coord)))
        val snapshot = agg.consume(ResponseParser.parse("""line three"""", coord))!!
        assertEquals("line one\nline two\nline three", snapshot.comment)
    }

    @Test
    fun `a position with no comment clears it`() {
        val agg = DatabaseAggregator()
        agg.consume(ResponseParser.parse("""MESSAGE DATABASE TEXT "old"""", coord))
        val snapshot = agg.consume(ResponseParser.parse("MESSAGE DATABASE TEXT", coord))!!
        assertEquals("", snapshot.comment)
    }

    @Test
    fun `db one becomes a record entry`() {
        val agg = DatabaseAggregator()
        val snapshot = agg.consume(
            ResponseParser.parse("MESSAGE DATABASE ONE 76 -300 12 1 h8", coord),
        )!!
        val entry = snapshot.entry!!
        assertEquals('L', entry.tag)
        assertEquals(-300, entry.value)
        assertEquals(12, entry.depth)
        assertEquals(DbBound.UPPER, entry.bound)
    }

    // ---- position value derivation (evalbar_update_from_db) -----------------

    private fun snapshotOf(vararg labels: String): DbSnapshot {
        val cells = labels.mapIndexed { i, label ->
            val move = Move(i, 0)
            move to DbCell(move = move, tagLabel = label)
        }.toMap()
        return DbSnapshot(cells = cells)
    }

    /**
     * Read the value of a position in which *every* playable point happens to
     * carry a record, which is the case the older assertions were written for.
     * The interesting case — a partly explored position — passes its own count.
     */
    private fun DbSnapshot.valueOfComplete(blackToMove: Boolean) =
        positionValue(blackToMove, playablePoints = cells.size)

    @Test
    fun `best stored rate becomes the side-to-move value`() {
        val value = snapshotOf("40%", "63%", "12%").valueOfComplete(blackToMove = true)!!
        assertEquals(0.63, value.stmWinRate, 1e-9)
        assertEquals(0.63, value.blackWinRate, 1e-9)
        assertNull(value.blackMate)
    }

    @Test
    fun `white to move mirrors the rate for the bar`() {
        val value = snapshotOf("63%").valueOfComplete(blackToMove = false)!!
        assertEquals(0.63, value.stmWinRate, 1e-9)
        assertEquals(0.37, value.blackWinRate, 1e-9)
    }

    @Test
    fun `a win beats any rate and takes the shortest mate`() {
        val value = snapshotOf("90%", "W7", "W3", "L9").valueOfComplete(blackToMove = true)!!
        assertEquals(1.0, value.stmWinRate, 1e-9)
        assertEquals(3, value.stmMate)
        assertEquals(3, value.blackMate)
        assertEquals(1.0, value.blackWinRate, 1e-9)
    }

    @Test
    fun `mate signs flip for white to move`() {
        val value = snapshotOf("W5").valueOfComplete(blackToMove = false)!!
        assertEquals(-5, value.blackMate)
        assertEquals(0.0, value.blackWinRate, 1e-9)
    }

    @Test
    fun `only losses means the longest defence is reported`() {
        val value = snapshotOf("L4", "L11").valueOfComplete(blackToMove = true)!!
        assertEquals(0.0, value.stmWinRate, 1e-9)
        assertEquals(-11, value.stmMate)
        assertEquals(-11, value.blackMate)
    }

    /**
     * The false mate, in its smallest form. The database stores proven results
     * only, so the moves that are still open have no record at all — counting
     * just the records and concluding "every move loses" turns an ordinary,
     * half-explored position into a forced loss, and reports the *longest* of
     * the losses because among losses the longest is the best one.
     */
    @Test
    fun `losses that do not cover every move are not a loss`() {
        assertNull(snapshotOf("L4", "L11").positionValue(blackToMove = true, playablePoints = 30))
        // ... and with one point left over it is still not a loss.
        assertNull(snapshotOf("L4", "L11").positionValue(blackToMove = true, playablePoints = 3))
        // Not knowing how many moves there are is not evidence either.
        assertNull(snapshotOf("L4", "L11").positionValue(blackToMove = true, playablePoints = 0))
    }

    /**
     * The asymmetry that makes the rule above correct: one winning move is a
     * win no matter how much of the position is unexplored, because the side to
     * move only has to find one. Same records, opposite direction.
     */
    @Test
    fun `a single win needs no completeness`() {
        val value = snapshotOf("W6", "L11").positionValue(blackToMove = true, playablePoints = 30)!!
        assertEquals(1.0, value.stmWinRate, 1e-9)
        assertEquals(6, value.stmMate)
    }

    /** A live percentage outranks any number of refuted moves, as it always did. */
    @Test
    fun `a stored rate outranks recorded losses`() {
        val value = snapshotOf("L4", "L40", "10%").positionValue(blackToMove = true, playablePoints = 30)!!
        assertEquals(0.10, value.stmWinRate, 1e-9)
        assertNull(value.stmMate)
    }

    @Test
    fun `a draw counts as fifty percent when nothing better is stored`() {
        val value = snapshotOf("D", "20%").valueOfComplete(blackToMove = true)!!
        assertEquals(0.5, value.stmWinRate, 1e-9)
    }

    @Test
    fun `notes alone are not a value`() {
        assertNull(snapshotOf("abc", "!").valueOfComplete(blackToMove = true))
        assertNull(DbSnapshot().valueOfComplete(blackToMove = true))
    }

    @Test
    fun `free text is used when the tag is empty, and cell kinds are recognised`() {
        val move = Move(3, 3)
        val snapshot = DbSnapshot(cells = mapOf(move to DbCell(move, tagLabel = "", text = "77%")))
        assertEquals(0.77, snapshot.valueOfComplete(blackToMove = true)!!.stmWinRate, 1e-9)

        val cell = DbCell(move, tagLabel = "W12")
        assertEquals(DbCellKind.WIN, cell.kindOf())
        assertEquals(12, cell.mateStep())
        assertEquals(DbCellKind.RATE, DbCell(move, tagLabel = "63%").kindOf())
        assertEquals(63, DbCell(move, tagLabel = "63%").winRatePct())
        assertEquals(DbCellKind.NOTE, DbCell(move, tagLabel = "?!").kindOf())
    }

    @Test
    fun `board text wins over the tag only while the toggle is on`() {
        val cell = DbCell(Move(1, 1), tagLabel = "W3", text = "A")
        assertEquals("A", cell.display(showBoardText = true))
        assertEquals("W3", cell.display(showBoardText = false))
        // the value logic always prefers the tag, like main.c does
        assertEquals("W3", cell.valueLabel())
    }
}
