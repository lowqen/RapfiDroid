package dev.gomoku.zenyth.data.game

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.BoardSessions
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the two boards survive a restart (RapfiWeb's autosave). A game used to
 * vanish when the process did; now the game record and the analysis board come
 * back as they were, down to a resignation.
 */
interface BoardSessionStore {
    /** The tab a first launch opens on. */
    val defaultMode: BoardMode

    fun load(): BoardSessions?

    fun save(sessions: BoardSessions)

    companion object {
        /** Nothing kept, game tab — what the tests and a bare repository use. */
        val NONE: BoardSessionStore = object : BoardSessionStore {
            override val defaultMode = BoardMode.GAME
            override fun load(): BoardSessions? = null
            override fun save(sessions: BoardSessions) = Unit
        }
    }
}

@Singleton
class PrefsBoardSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) : BoardSessionStore {
    private val prefs = context.getSharedPreferences("boards", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** Analysis first, like RapfiWeb: someone opening the app wants to look at a position. */
    override val defaultMode = BoardMode.ANALYSIS

    override fun load(): BoardSessions? =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(BoardSessions.serializer(), it) }.getOrNull() }

    override fun save(sessions: BoardSessions) {
        prefs.edit().putString(KEY, json.encodeToString(BoardSessions.serializer(), sessions)).apply()
    }

    private companion object {
        const val KEY = "sessions.v1"
    }
}
