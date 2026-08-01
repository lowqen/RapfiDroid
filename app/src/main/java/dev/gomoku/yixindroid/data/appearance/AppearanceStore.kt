package dev.gomoku.yixindroid.data.appearance

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.FunctionScripts
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

/**
 * Keeps an imported toolbar/hotkey/language set across restarts.
 *
 * The files themselves are not kept — a SAF grant can be revoked and the user's
 * folder can move — so the parsed definitions are stored instead. That also
 * means an import is a snapshot: editing `toolbar1.txt` on the PC needs another
 * import, which is the same contract the settings file already has.
 */
@Singleton
class AppearanceStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val key = stringPreferencesKey("functions")
    private val json = Json { ignoreUnknownKeys = true }

    class Saved(
        val toolbar: List<FunctionScripts.ToolbarItem>,
        val hotkeys: List<FunctionScripts.HotkeyItem>,
        val labels: Map<Int, String>,
        val source: String?,
    )

    suspend fun load(): Saved? = withContext(io) {
        val raw = context.appearanceDataStore.data.first()[key] ?: return@withContext null
        runCatching {
            val dto = json.decodeFromString(Dto.serializer(), raw)
            Saved(
                toolbar = dto.toolbar.map {
                    FunctionScripts.ToolbarItem(it.lngId, it.icon, it.script)
                },
                hotkeys = dto.hotkeys.map { FunctionScripts.HotkeyItem(it.keyIndex, it.script) },
                labels = dto.labels.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap(),
                source = dto.source,
            )
        }.getOrNull()
    }

    suspend fun save(saved: Saved) = withContext(io) {
        val dto = Dto(
            toolbar = saved.toolbar.map { ToolbarDto(it.lngId, it.icon, it.script) },
            hotkeys = saved.hotkeys.map { HotkeyDto(it.keyIndex, it.script) },
            labels = saved.labels.entries.associate { (k, v) -> k.toString() to v },
            source = saved.source,
        )
        val text = json.encodeToString(Dto.serializer(), dto)
        context.appearanceDataStore.edit { it[key] = text }
        Unit
    }

    suspend fun clear() = withContext(io) {
        context.appearanceDataStore.edit { it.remove(key) }
        Unit
    }

    @Serializable
    private data class ToolbarDto(val lngId: Int = 0, val icon: String = "", val script: String = "")

    @Serializable
    private data class HotkeyDto(val keyIndex: Int = 0, val script: String = "")

    @Serializable
    private data class Dto(
        val toolbar: List<ToolbarDto> = emptyList(),
        val hotkeys: List<HotkeyDto> = emptyList(),
        // JSON object keys are strings; the ids are numbers on both sides of it.
        val labels: Map<String, String> = emptyMap(),
        val source: String? = null,
    )
}
