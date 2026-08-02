package dev.gomoku.yixindroid.domain.repository

import dev.gomoku.yixindroid.core.model.DbOpResult

/**
 * "Write the database out", and nothing else.
 *
 * A save is not an append: the engine replaces the whole file with what it holds
 * in memory, so it has to pass the same checks wherever it is asked for — the
 * database off, read-only, or a load that never finished. [DatabaseRepository.save]
 * is where those checks live, and this is the one-method view of it for the
 * callers that only need to ask (the prove run at the end of a proof).
 */
fun interface DatabaseSaver {
    suspend fun saveDatabase(): DbOpResult
}
