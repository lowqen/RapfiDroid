package dev.gomoku.yixindroid.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.DesktopSettings
import dev.gomoku.yixindroid.core.model.LocalEngineProfile
import dev.gomoku.yixindroid.core.model.SettingCategory
import dev.gomoku.yixindroid.core.model.SettingEditor
import dev.gomoku.yixindroid.core.model.SettingsFile
import dev.gomoku.yixindroid.data.engine.DebugLogWriter
import dev.gomoku.yixindroid.data.engine.LocalEngineInstaller
import dev.gomoku.yixindroid.data.prefs.EndpointStore
import dev.gomoku.yixindroid.data.prefs.LocalEngineStore
import dev.gomoku.yixindroid.data.settings.SettingsFileIo
import dev.gomoku.yixindroid.domain.repository.AppearanceRepository
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val engine: EngineRepository,
    private val appearance: AppearanceRepository,
    private val fileIo: SettingsFileIo,
    private val debugLog: DebugLogWriter,
    private val localEngineStore: LocalEngineStore,
    private val endpointStore: EndpointStore,
    localEngine: LocalEngineInstaller,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Which desktop file the pending SAF pick refers to. */
    var pendingFile: SettingsFile = SettingsFile.MAIN
        private set

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s -> _state.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            engine.capabilities.collect { c -> _state.update { it.copy(capabilities = c) } }
        }
        viewModelScope.launch {
            engine.state.collect { s -> _state.update { it.copy(connected = s.isLive) } }
        }
        viewModelScope.launch {
            appearance.source.collect { src -> _state.update { it.copy(appearanceSource = src) } }
        }
        viewModelScope.launch {
            localEngineStore.profile.collect { p -> _state.update { it.copy(localProfile = p) } }
        }
        viewModelScope.launch {
            endpointStore.localMode.collect { on -> _state.update { it.copy(localMode = on) } }
        }
        viewModelScope.launch {
            endpointStore.serverEnabled.collect { on -> _state.update { it.copy(serverEnabled = on) } }
        }
        // Where the device's own database ends up. Worth showing: it is the one
        // file of this app's the user might want to pull off the phone, and it
        // is nowhere a file manager will stumble on it.
        _state.update {
            it.copy(localDbPath = File(localEngine.engineDir, LOCAL_DB_NAME).path)
        }
        viewModelScope.launch { refreshDebugLogSize() }
    }

    fun onLocalThreads(value: Int) = editLocal { it.copy(threadNum = value) }

    fun onLocalHash(value: Int) = editLocal { it.copy(hashSizeMb = value) }

    fun onLocalDatabase(on: Boolean) = editLocal { it.copy(useDatabase = on) }

    /**
     * Offer the server engine, or stop offering it. Turning it off also points
     * the app back at the on-device engine — the connection tab has no server
     * chip to come back through once it is hidden.
     */
    fun onServerEnabled(on: Boolean) {
        viewModelScope.launch { endpointStore.setServerEnabled(on) }
    }

    /**
     * Saved rather than held: the repository watches the store, so a change
     * reaches a *running* on-device engine the same way a desktop setting does.
     */
    private fun editLocal(edit: (LocalEngineProfile) -> LocalEngineProfile) {
        viewModelScope.launch { localEngineStore.save(edit(_state.value.localProfile)) }
    }

    private suspend fun refreshDebugLogSize() {
        val bytes = debugLog.size()
        _state.update { it.copy(debugLogBytes = bytes) }
    }

    fun onExportDebugLog(target: Uri) {
        viewModelScope.launch {
            val result = runCatching { debugLog.export(target) }
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { n -> tr("디버그 로그 ${n / 1024}KB 를 내보냈습니다", "Exported ${n / 1024}KB of debug log") },
                        onFailure = { e -> tr("내보내기 실패: ${e.message}", "Export failed: ${e.message}") },
                    ),
                )
            }
        }
    }

    fun onClearDebugLog() {
        viewModelScope.launch {
            debugLog.clear()
            refreshDebugLogSize()
            _state.update { it.copy(message = tr("디버그 로그를 지웠습니다", "Debug log cleared")) }
        }
    }

    fun onQuery(text: String) = _state.update { it.copy(query = text) }

    fun onCategory(category: SettingCategory?) = _state.update { it.copy(category = category) }

    fun onAdvanced(on: Boolean) = _state.update { it.copy(advanced = on) }

    /**
     * Commit one setting. Numbers are first clamped to the *engine-reported*
     * maximum where there is one (threads, hash), because the spec range only
     * knows the protocol limit — asking for more threads than the server has
     * would silently do nothing useful.
     */
    fun onValue(id: String, raw: String) {
        val spec = DesktopSettings.spec(id)
        val editor = spec?.let { _state.value.editorFor(it) }
        val value = if (editor is SettingEditor.Number) {
            raw.trim().toLongOrNull()?.coerceIn(editor.min, editor.max)?.toString() ?: raw
        } else {
            raw
        }
        viewModelScope.launch { settingsRepository.set(id, value) }
    }

    fun onReset() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            _state.update { it.copy(message = tr("설정을 PC 기본값으로 되돌렸습니다", "Settings are back to the PC defaults")) }
        }
    }

    fun prepare(file: SettingsFile) {
        pendingFile = file
    }

    fun onExport(uri: Uri?) {
        val target = uri ?: return
        val file = pendingFile
        viewModelScope.launch {
            val result = runCatching { fileIo.write(target, settingsRepository.export(file)) }
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { tr("${file.fileName} ${file.lineCount}줄을 내보냈습니다", "Exported ${file.lineCount} lines to ${file.fileName}") },
                        onFailure = { e -> tr("내보내기 실패: ${e.message}", "Export failed: ${e.message}") },
                    ),
                )
            }
        }
    }

    fun onImport(uri: Uri?) {
        val source = uri ?: return
        val file = pendingFile
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.import(fileIo.read(source), file)
            }
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { n -> tr("${file.fileName} ${n}줄을 불러왔습니다", "Read ${n} lines from ${file.fileName}") },
                        onFailure = { e -> tr("불러오기 실패: ${e.message}", "Import failed: ${e.message}") },
                    ),
                )
            }
        }
    }

    /**
     * Read `function/` and `language/` out of a picked Yixin folder. The language
     * index is the user's own `language` setting, so the labels arrive in the
     * language they already chose (main.c:14278 defaults it to Korean).
     */
    fun onImportAppearance(tree: Uri) {
        viewModelScope.launch {
            val index = settingsRepository.settings.value.language
            val result = appearance.importFrom(tree, index)
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { text -> text },
                        onFailure = { e -> tr("불러오기 실패: ${e.message}", "Import failed: ${e.message}") },
                    ),
                )
            }
        }
    }

    fun onResetAppearance() {
        viewModelScope.launch {
            appearance.reset()
            _state.update { it.copy(message = tr("툴바·핫키·언어를 기본값으로 되돌렸습니다", "Toolbar, hotkeys and language are back to their defaults")) }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private companion object {
        /** `[database] url` in the generated config, resolved against the engine's cwd. */
        const val LOCAL_DB_NAME = "rapfi.db"
    }
}
