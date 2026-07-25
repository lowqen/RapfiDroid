package dev.gomoku.yixindroid.data.settings

import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.DesktopSettings
import dev.gomoku.yixindroid.core.model.SettingsFile
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import dev.gomoku.yixindroid.domain.settings.SettingsCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val store: SettingsStore,
    @IoDispatcher io: CoroutineDispatcher,
) : SettingsRepository {

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val writeLock = Mutex()

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        // One read at startup; afterwards this object is the writer, so there is
        // no need to keep collecting (and no risk of clobbering a pending edit).
        scope.launch {
            _settings.value = store.settings.first()
            _loaded.value = true
        }
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        writeLock.withLock {
            val next = transform(_settings.value)
            if (next == _settings.value) return
            _settings.value = next
            store.save(next)
        }
    }

    override suspend fun set(id: String, raw: String) {
        val spec = DesktopSettings.spec(id) ?: return
        update { spec.write(it, raw) }
    }

    override suspend fun resetToDefaults() = update { AppSettings() }

    override fun export(file: SettingsFile): String =
        SettingsCodec.render(_settings.value, file)

    override suspend fun import(text: String, file: SettingsFile): Int {
        val applied = minOf(
            text.lineSequence().count { it.isNotBlank() },
            DesktopSettings.of(file).size,
        )
        update { SettingsCodec.parse(text, file, it) }
        return applied
    }
}
