package dev.gomoku.rapfidroid.feature.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.ConnectionState
import dev.gomoku.rapfidroid.core.model.DbDeleteFilter
import dev.gomoku.rapfidroid.core.model.DbDeleteScope
import dev.gomoku.rapfidroid.core.model.DbOpResult
import dev.gomoku.rapfidroid.core.model.DbState
import dev.gomoku.rapfidroid.core.model.Position
import dev.gomoku.rapfidroid.domain.repository.DatabaseRepository
import dev.gomoku.rapfidroid.domain.repository.DbPreferences
import dev.gomoku.rapfidroid.domain.repository.EngineRepository
import dev.gomoku.rapfidroid.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Database screen: every yixindb operation the desktop offers
 * (Database menu + the `db*` console commands), with the two guards this port
 * adds — read-only refusals surface as a message, and bulk delete / split need
 * the unlock switch (plan §7 decision 1).
 */
@HiltViewModel
class DatabaseViewModel @Inject constructor(
    private val repository: DatabaseRepository,
    private val engine: EngineRepository,
    private val settings: SettingsRepository,
    private val prefs: DbPreferences,
) : ViewModel() {

    private val notice = MutableStateFlow<String?>(null)
    private val pendingDelete = MutableStateFlow<DbDeleteScope?>(null)
    private val deleteDraft = MutableStateFlow(DbDeleteScope())
    private val pathDraft = MutableStateFlow("")

    init {
        viewModelScope.launch { pathDraft.value = prefs.lastPath.first() }
    }

    /** The screen's own transient state, folded into one flow so combine stays typed. */
    private data class Local(
        val notice: String?,
        val pendingDelete: DbDeleteScope?,
        val deleteDraft: DbDeleteScope,
        val path: String,
    )

    private val local = combine(notice, pendingDelete, deleteDraft, pathDraft) { n, p, d, path ->
        Local(n, p, d, path)
    }

    val uiState: StateFlow<DatabaseUiState> = combine(
        repository.state, repository.position, engine.state, settings.settings, local,
    ) { db: DbState, position: Position, connection: ConnectionState, config, l: Local ->
        DatabaseUiState(
            db = db,
            position = position,
            connection = connection,
            autoSave = config.dbAutoSave,
            autoSaveMinutes = config.dbAutoSaveMinutes,
            showBoardText = config.showBoardText,
            confirmDeletes = config.showDbDelConfirm,
            notice = l.notice,
            pendingDelete = l.pendingDelete,
            deleteDraft = l.deleteDraft,
            path = l.path,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatabaseUiState())

    // ---- toggles ----

    fun onUseDatabase(on: Boolean) = inScope { repository.setEnabled(on) }

    fun onReadOnly(on: Boolean) = inScope { repository.setReadOnly(on) }

    fun onUnlockDestructive(on: Boolean) = inScope { repository.setDestructiveUnlocked(on) }

    fun onAutoSave(on: Boolean) = inScope { settings.set("dbAutoSave", if (on) "1" else "0") }

    fun onAutoSaveMinutes(minutes: Int) = inScope {
        settings.set("dbAutoSaveMinutes", minutes.coerceIn(1, 600).toString())
    }

    fun onShowBoardText(on: Boolean) = inScope { settings.set("showBoardText", if (on) "1" else "0") }

    // ---- position scope ----

    fun onRefresh() = inScope { repository.refresh() }
    fun onQueryValue() = dispatch { repository.queryValue() }
    fun onQueryComment() = dispatch { repository.queryComment() }
    fun onSetBestMove() = dispatch { repository.setBestMove() }
    fun onClearBestMove() = dispatch { repository.clearBestMove() }
    fun onDeleteOne() = dispatch { repository.deleteOne() }
    fun onEditTag(tag: Char?) = dispatch { repository.editTag(tag) }
    fun onEditValue(value: Int) = dispatch { repository.editValue(value) }
    fun onEditDepth(depth: Int) = dispatch { repository.editDepth(depth) }

    // ---- bulk delete: draft -> confirm -> run ----

    fun onDeleteFilter(filter: DbDeleteFilter) {
        deleteDraft.value = deleteDraft.value.copy(filter = filter)
    }

    fun onDeleteRecursive(on: Boolean) {
        deleteDraft.value = deleteDraft.value.copy(recursive = on)
    }

    fun onDeleteStep(step: Int) {
        deleteDraft.value = deleteDraft.value.copy(step = step.coerceIn(1, 99))
    }

    /**
     * Ask before wiping branches — the desktop's `show_dbdelall_query`, which the
     * user can disable (settings.txt line 35). With confirmations off the delete
     * runs straight away, exactly as on the PC.
     */
    fun onRequestDeleteAll() {
        val scope = deleteDraft.value
        if (uiState.value.confirmDeletes) pendingDelete.value = scope else runDeleteAll(scope)
    }

    fun onCancelDeleteAll() {
        pendingDelete.value = null
    }

    fun onConfirmDeleteAll() {
        val scope = pendingDelete.value ?: return
        pendingDelete.value = null
        runDeleteAll(scope)
    }

    private fun runDeleteAll(scope: DbDeleteScope) = dispatch { repository.deleteAll(scope) }

    // ---- file operations ----

    fun onPathChange(path: String) {
        pathDraft.value = path
    }

    fun onSave() = dispatch { repository.save() }
    fun onOpenFile() = runWithPath { repository.openFile(it) }
    fun onMerge() = runWithPath { repository.merge(it) }
    fun onSplit() = runWithPath { repository.split(it) }
    fun onImportLib() = runWithPath { repository.importLib(it) }
    fun onExportLib() = runWithPath { repository.exportLib(it) }
    fun onExportText(all: Boolean) = runWithPath { repository.exportText(it, all) }
    fun onImportText() = runWithPath { repository.importText(it) }
    fun onExportPositions() = runWithPath { repository.exportPositions(it) }
    fun onCheck() = dispatch { repository.check() }
    fun onFix() = dispatch { repository.fix() }

    fun onClearLog() = repository.clearLog()

    fun onNoticeShown() {
        notice.value = null
    }

    private fun inScope(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    /** Runs one operation and turns a guard refusal into a visible notice. */
    private fun dispatch(block: suspend () -> DbOpResult) {
        viewModelScope.launch {
            when (val result = block()) {
                is DbOpResult.Refused -> notice.value = result.reason
                DbOpResult.Sent -> Unit
            }
        }
    }

    private fun runWithPath(block: suspend (String) -> DbOpResult) {
        val path = pathDraft.value.trim()
        if (path.isEmpty()) {
            notice.value = tr("엔진 쪽 파일 경로를 입력하세요", "Type a path on the engine's machine")
            return
        }
        viewModelScope.launch {
            prefs.setLastPath(path)
            when (val result = block(path)) {
                is DbOpResult.Refused -> notice.value = result.reason
                DbOpResult.Sent -> Unit
            }
        }
    }
}
