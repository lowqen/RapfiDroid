package dev.gomoku.rapfidroid.data.tools

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.rapfidroid.core.common.IoDispatcher
import dev.gomoku.rapfidroid.core.model.CallbackConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.toolsDataStore by preferencesDataStore(name = "tools")

/**
 * Persists the callback scripts. The desktop keeps each one in its own file
 * under `function/` (`callback_mate.txt`, …) and the three numbers in
 * `settings_dev.txt`; on the phone they are one JSON blob, since there is no
 * file layout for the user to edit by hand.
 */
@Singleton
class ToolsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val key = stringPreferencesKey("callbacks")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): CallbackConfig = withContext(io) {
        val raw = context.toolsDataStore.data.first()[key] ?: return@withContext CallbackConfig()
        runCatching { json.decodeFromString(Dto.serializer(), raw).toModel() }
            .getOrElse { CallbackConfig() }
    }

    suspend fun save(config: CallbackConfig) = withContext(io) {
        val text = json.encodeToString(Dto.serializer(), Dto.of(config))
        context.toolsDataStore.edit { it[key] = text }
        Unit
    }

    @Serializable
    private data class Dto(
        val enabled: Boolean = false,
        val drawCount: Int = 10,
        val minPly: Int = 1,
        val maxPly: Int = CallbackConfig.MAX_PLY,
        val onMate: String = "",
        val onMated: String = "",
        val onDraw: String = "",
        val onMove: String = "",
        val onMoveMinPly: String = "",
        val onMoveMaxPly: String = "",
    ) {
        fun toModel() = CallbackConfig(
            enabled, drawCount, minPly, maxPly,
            onMate, onMated, onDraw, onMove, onMoveMinPly, onMoveMaxPly,
        )

        companion object {
            fun of(c: CallbackConfig) = Dto(
                c.enabled, c.drawCount, c.minPly, c.maxPly,
                c.onMate, c.onMated, c.onDraw, c.onMove, c.onMoveMinPly, c.onMoveMaxPly,
            )
        }
    }
}
