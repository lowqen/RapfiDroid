package dev.gomoku.rapfidroid.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * App-local database preferences (not part of the desktop settings files, whose
 * 67 lines are pinned by the codec). Behind an interface so the repository's
 * guard logic can be unit-tested without Android's DataStore.
 */
interface DbPreferences {
    /** Bulk delete / split opt-in, off by default (plan §7 decision 1). */
    val destructiveUnlocked: Flow<Boolean>

    /** Last engine-side path used by a file operation. */
    val lastPath: Flow<String>

    suspend fun setDestructiveUnlocked(on: Boolean)
    suspend fun setLastPath(path: String)
}
