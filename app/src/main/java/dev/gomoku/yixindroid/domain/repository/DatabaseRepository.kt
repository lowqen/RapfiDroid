package dev.gomoku.yixindroid.domain.repository

import dev.gomoku.yixindroid.core.model.DbDeleteScope
import dev.gomoku.yixindroid.core.model.DbOpResult
import dev.gomoku.yixindroid.core.model.DbState
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import kotlinx.coroutines.flow.StateFlow

/**
 * yixindb access (P7). The database itself lives **next to the remote engine**,
 * so every operation is a command over the same socket and every path is a
 * server-side path — the phone never holds the file.
 *
 * The position of record is set by the board through [setPosition]; queries and
 * edits address that path exactly like the desktop's `movepath` does.
 */
interface DatabaseRepository {

    val state: StateFlow<DbState>

    /** The path all position-scoped operations address. */
    val position: StateFlow<Position>

    /**
     * Point the database at [position] and re-query it (the desktop calls
     * `show_database()` after every board change). No-op when the database is
     * off or the engine is not connected.
     */
    suspend fun setPosition(position: Position)

    /** Re-run the query for the current position. */
    suspend fun refresh()

    /** `dbval`: fetch the single stored record for the current position. */
    suspend fun queryValue(): DbOpResult

    /** `dbtext`: fetch the position comment. */
    suspend fun queryComment(): DbOpResult

    // ---- edits (all refused while read-only) ----

    suspend fun editComment(comment: String): DbOpResult

    /** Board text on one cell; an empty [label] removes it. */
    suspend fun editCellLabel(cell: Move, label: String): DbOpResult

    /** Record tag ('W'/'L'/'D'/…); null clears it. */
    suspend fun editTag(tag: Char?): DbOpResult

    suspend fun editValue(value: Int): DbOpResult

    suspend fun editDepth(depth: Int): DbOpResult

    /** Mark / unmark the last played move as this position's best move. */
    suspend fun setBestMove(): DbOpResult
    suspend fun clearBestMove(): DbOpResult

    // ---- deletes ----

    /** Delete just this position's record. */
    suspend fun deleteOne(): DbOpResult

    /** Bulk delete below this position — needs [DbState.destructiveUnlocked]. */
    suspend fun deleteAll(scope: DbDeleteScope): DbOpResult

    // ---- file operations (server-side paths) ----

    suspend fun save(): DbOpResult
    suspend fun openFile(path: String): DbOpResult
    suspend fun merge(path: String): DbOpResult

    /** Split the subtree below the current position — needs the unlock. */
    suspend fun split(path: String): DbOpResult
    suspend fun importLib(path: String): DbOpResult
    suspend fun exportLib(path: String): DbOpResult
    suspend fun exportText(path: String, all: Boolean): DbOpResult
    suspend fun importText(path: String): DbOpResult
    suspend fun exportPositions(path: String): DbOpResult
    suspend fun check(): DbOpResult
    suspend fun fix(): DbOpResult

    // ---- toggles ----

    /** Mirrors settings.txt line 32 and pushes `info usedatabase`. */
    suspend fun setEnabled(on: Boolean)

    /** Mirrors line 33 and pushes `info database_readonly`. */
    suspend fun setReadOnly(on: Boolean)

    /** Opt in to bulk deletes / split (app-local preference, off by default). */
    suspend fun setDestructiveUnlocked(on: Boolean)

    fun clearLog()
}
