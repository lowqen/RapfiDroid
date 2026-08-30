package dev.gomoku.rapfidroid.core.model

/**
 * Makes `INFO usedatabase 1` idempotent, which the engine itself is not.
 *
 * Rapfi's handler is
 *
 * ```cpp
 * else if (token == "USEDATABASE") {
 *     std::cin >> val;
 *     if (val == 1)
 *         Search::Engine.setupDatabase(Database::createDBStorage(DatabaseCfg));
 *     else
 *         Search::Engine.setupDatabase(nullptr);
 * }
 * ```
 *
 * and C++ evaluates the argument before the call, so `createDBStorage` builds a
 * **complete second copy of the database** and only then does `setupDatabase`
 * release the first. Measured against the live server on 2026-08-01: a bare
 * connection loads the 600 MB yixindb (47,684,775 records, ~4.9 GB
 * decompressed) once in 13.9 s, and a single extra `INFO usedatabase 1` loads
 * the whole thing again in 18.5 s. With the 8 GiB transposition table that the
 * handshake allocates first, that second copy is what runs the server out of
 * memory.
 *
 * The engine has no way to tell us it already has the database, so the client
 * remembers instead: one attach per engine process, and a repeat is dropped.
 * Detaching is real work — it frees the copy — so `0` always goes through and
 * re-arms the next attach. That is also the safe way to force a reload: off,
 * then on, because the `0` releases the old copy before the `1` builds a new
 * one.
 *
 * Retire this once `USEDATABASE` is guarded with `if (!Search::Engine.dbStorage())`
 * and the server is rebuilt; until then it is the only thing standing between a
 * rule change and an out-of-memory engine.
 */
class DatabaseAttachGuard {

    private var attached = false

    /** Whether the engine is believed to hold the database right now. */
    val isAttached: Boolean get() = attached

    /**
     * True when an `INFO <key> <value>` may go to the engine. Anything that is
     * not `usedatabase` passes through untouched.
     */
    fun allow(key: String, value: String): Boolean {
        if (!key.equals(KEY, ignoreCase = true)) return true
        val attach = value.trim() == "1"
        if (attach && attached) return false
        attached = attach
        return true
    }

    /**
     * Forget the attach state. The proxy spawns a fresh Rapfi per connection, so
     * every new socket starts with no database and must attach again.
     */
    fun reset() {
        attached = false
    }

    companion object {
        const val KEY = "usedatabase"
    }
}
