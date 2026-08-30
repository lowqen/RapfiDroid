package dev.gomoku.rapfidroid.feature.database

import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.ConnectionState
import dev.gomoku.rapfidroid.core.model.DbDeleteScope
import dev.gomoku.rapfidroid.core.model.DbState
import dev.gomoku.rapfidroid.core.model.Position

data class DatabaseUiState(
    val db: DbState = DbState(),
    /** The path every position-scoped operation addresses (set by the board). */
    val position: Position = Position(),
    val connection: ConnectionState = ConnectionState.Disconnected,
    val autoSave: Boolean = true,
    val autoSaveMinutes: Int = 5,
    val showBoardText: Boolean = true,
    /** settings.txt line 35 — ask before deleting all branches. */
    val confirmDeletes: Boolean = true,
    val notice: String? = null,
    /** Non-null while a bulk delete waits for confirmation. */
    val pendingDelete: DbDeleteScope? = null,
    val deleteDraft: DbDeleteScope = DbDeleteScope(),
    /** Engine-side file path for the file operations. */
    val path: String = "",
) {
    val connected: Boolean get() = connection.isLive
    val canWrite: Boolean get() = db.canWrite(connected)
    val canDestroy: Boolean get() = canWrite && db.destructiveUnlocked

    /** Current path as board labels, e.g. "H8 I9 J10" (empty board = "빈 판"). */
    fun pathLabels(): String =
        if (position.moves.isEmpty()) tr("빈 판", "empty board")
        else position.moves.joinToString(" ") { it.label(position.size) }

    fun valueSummary(): String {
        val value = db.value ?: return tr("저장된 값 없음", "No stored value")
        return when {
            value.stmMate != null && value.stmMate > 0 -> tr("두는 쪽 승 · M${value.stmMate}", "Side to move wins · M${value.stmMate}")
            value.stmMate != null -> tr("두는 쪽 패 · M${-value.stmMate}", "Side to move loses · M${-value.stmMate}")
            else -> tr("흑 ${(value.blackWinRate * 100).toInt()}%", "Black ${(value.blackWinRate * 100).toInt()}%")
        }
    }
}
