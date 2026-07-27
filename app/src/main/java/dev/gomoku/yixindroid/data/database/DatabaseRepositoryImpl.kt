package dev.gomoku.yixindroid.data.database

import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.DbDeleteScope
import dev.gomoku.yixindroid.core.model.DbOpResult
import dev.gomoku.yixindroid.core.model.DbState
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.StoneColor
import dev.gomoku.yixindroid.domain.engine.DatabaseAggregator
import dev.gomoku.yixindroid.domain.engine.DbCheck
import dev.gomoku.yixindroid.domain.engine.DbClearBestMove
import dev.gomoku.yixindroid.domain.engine.DbDeleteAll
import dev.gomoku.yixindroid.domain.engine.DbDeleteOne
import dev.gomoku.yixindroid.domain.engine.DbEditComment
import dev.gomoku.yixindroid.domain.engine.DbEditLabel
import dev.gomoku.yixindroid.domain.engine.DbEditRecord
import dev.gomoku.yixindroid.domain.engine.DbFix
import dev.gomoku.yixindroid.domain.engine.DbLibExport
import dev.gomoku.yixindroid.domain.engine.DbLibImport
import dev.gomoku.yixindroid.domain.engine.DbMerge
import dev.gomoku.yixindroid.domain.engine.DbQueryAll
import dev.gomoku.yixindroid.domain.engine.DbQueryOne
import dev.gomoku.yixindroid.domain.engine.DbQueryText
import dev.gomoku.yixindroid.domain.engine.DbSave
import dev.gomoku.yixindroid.domain.engine.DbSetBestMove
import dev.gomoku.yixindroid.domain.engine.DbSetFile
import dev.gomoku.yixindroid.domain.engine.DbSplit
import dev.gomoku.yixindroid.domain.engine.DbTextExport
import dev.gomoku.yixindroid.domain.engine.DbTextExportAll
import dev.gomoku.yixindroid.domain.engine.DbTextImport
import dev.gomoku.yixindroid.domain.engine.DbToPos
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.repository.DatabaseRepository
import dev.gomoku.yixindroid.domain.repository.DbPreferences
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * P7 — the whole yixindb surface of the desktop, over the engine socket.
 *
 * Ported behaviours that are easy to get wrong:
 *  - **query pairing**: over the VPN a reply for an older position can land
 *    after the user moved on. The desktop counts queries and DONEs
 *    (`dbqueryseq`/`dbdoneseq`, main.c:1613 + 13550) and lets only the reply of
 *    the newest query drive the position value; the same clamp is here, because
 *    a stale reply flips the mate parity;
 *  - **auto-save while idle**: the timer fires only with the database on,
 *    writable and the engine not searching (main.c `db_autosave_tick`), so a
 *    save never interrupts a search;
 *  - **read-only**: every write path is refused client-side too, not just by
 *    the engine, so the UI can explain why nothing happened;
 *  - **destructive ops locked**: bulk deletes and split need an explicit opt-in
 *    (plan §7 decision 1). The engine has no undo and the file is shared with
 *    the PC.
 */
@Singleton
class DatabaseRepositoryImpl @Inject constructor(
    private val engine: EngineRepository,
    private val settings: SettingsRepository,
    private val prefs: DbPreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
) : DatabaseRepository {

    /** Named to keep `scope` free for the delete-scope parameter below. */
    private val repoScope = CoroutineScope(SupervisorJob() + io)
    private val aggregator = DatabaseAggregator()

    private val _state = MutableStateFlow(DbState())
    override val state: StateFlow<DbState> = _state.asStateFlow()

    private val _position = MutableStateFlow(Position())
    override val position: StateFlow<Position> = _position.asStateFlow()

    /** Desktop `dbqueryseq` / `dbdoneseq`. Guarded by the single collector. */
    private var querySeq = 0
    private var doneSeq = 0

    init {
        repoScope.launch {
            engine.responses.collect { response -> onResponse(response) }
        }
        // The two database flags live in the desktop settings file; the engine
        // side of them (`info usedatabase` / `database_readonly`) is already
        // pushed by EngineParams, so here we only mirror them into the state.
        repoScope.launch {
            settings.settings
                .map { it.useDatabase to it.databaseReadonly }
                .distinctUntilChanged()
                .collect { (enabled, readOnly) ->
                    val wasEnabled = _state.value.enabled
                    _state.update { it.copy(enabled = enabled, readOnly = readOnly) }
                    if (enabled && !wasEnabled) refresh()
                    if (!enabled) {
                        aggregator.clearCells()
                        _state.update { it.copy(snapshot = aggregator.snapshot(), value = null) }
                    }
                }
        }
        repoScope.launch {
            prefs.destructiveUnlocked.collect { unlocked ->
                _state.update { it.copy(destructiveUnlocked = unlocked) }
            }
        }
        // The desktop queries the database as soon as the engine is up
        // (load_setting -> use_database -> show_database). Without this the first
        // board the user sees after connecting would have no values on it.
        repoScope.launch {
            engine.state
                .map { it == ConnectionState.Ready }
                .distinctUntilChanged()
                .collect { ready -> if (ready) refresh() }
        }
        repoScope.launch { autoSaveLoop() }
    }

    // ---- incoming ----------------------------------------------------------

    private fun onResponse(response: EngineResponse) {
        val snapshot = aggregator.consume(response)
        if (response is EngineResponse.DbDone) {
            // Clamp exactly like main.c: a DONE from a non-refresh query (dbval,
            // dbtext, an edit ack) must not push the counter past the queries.
            doneSeq = min(doneSeq + 1, querySeq)
        }
        if (snapshot != null) {
            val paired = doneSeq == querySeq
            _state.update { current ->
                current.copy(
                    snapshot = snapshot,
                    // Only the newest query's result may set the position value.
                    value = if (paired) {
                        snapshot.positionValue(_position.value.sideToMove == StoneColor.BLACK)
                    } else {
                        current.value
                    },
                    progress = aggregator.fileProgress,
                )
            }
        }
        when (response) {
            is EngineResponse.DbOne -> log(
                aggregator.snapshot().entry?.summary() ?: "DB 레코드 없음",
            )
            is EngineResponse.DbFileEvent -> log(
                when {
                    response.started && response.saving -> "저장 중: ${response.file}"
                    response.started -> "불러오는 중: ${response.file}"
                    response.saving -> "저장 완료"
                    else -> "불러오기 완료"
                },
            ).also {
                if (!response.started && response.saving) {
                    _state.update { it.copy(lastSaveAt = System.currentTimeMillis()) }
                }
            }
            is EngineResponse.Message -> if (looksLikeDbMessage(response.text)) log(response.text)
            is EngineResponse.Error -> if (looksLikeDbMessage(response.text)) log("오류: ${response.text}")
            else -> Unit
        }
    }

    /**
     * The engine reports database work as plain `MESSAGE` text ("Saved database
     * file using 0 ms", "Deleted 12 records", …). There is no marker to key on,
     * so the log filters on the vocabulary those messages use.
     */
    private fun looksLikeDbMessage(text: String): Boolean {
        val lower = text.lowercase()
        return DB_MESSAGE_KEYWORDS.any { it in lower }
    }

    private fun log(line: String) {
        _state.update { current ->
            val out = current.log + line
            current.copy(log = if (out.size > LOG_LIMIT) out.takeLast(LOG_LIMIT) else out)
        }
    }

    override fun clearLog() = _state.update { it.copy(log = emptyList()) }

    // ---- queries -----------------------------------------------------------

    override suspend fun setPosition(position: Position) {
        val changed = _position.value != position
        _position.value = position
        if (changed) {
            // A new position invalidates the previous cell set immediately; the
            // engine also sends REFRESH, but only once its reply arrives.
            aggregator.clearCells()
            _state.update { it.copy(snapshot = aggregator.snapshot(), value = null) }
            refresh()
        }
    }

    override suspend fun refresh() {
        if (!canQuery()) return
        querySeq++
        send(DbQueryAll(_position.value.moves))
    }

    override suspend fun queryValue(): DbOpResult = guarded(write = false) {
        send(DbQueryOne(_position.value.moves))
    }

    override suspend fun queryComment(): DbOpResult = guarded(write = false) {
        send(DbQueryText(_position.value.moves))
    }

    // ---- edits -------------------------------------------------------------

    override suspend fun editComment(comment: String): DbOpResult = guarded(write = true) {
        send(DbEditComment(comment, _position.value.moves))
        // The desktop re-reads the comment right after writing it (main.c:10939).
        send(DbQueryText(_position.value.moves))
        log("주석 저장")
    }

    override suspend fun editCellLabel(cell: Move, label: String): DbOpResult =
        guarded(write = true) {
            send(DbEditLabel(cell, label, _position.value.moves))
            log(
                if (label.isBlank()) "${cell.label(_position.value.size)} 라벨 삭제"
                else "${cell.label(_position.value.size)} 라벨 = $label",
            )
            refresh()
        }

    override suspend fun editTag(tag: Char?): DbOpResult = guarded(write = true) {
        send(DbEditRecord.tag(tag, _position.value.moves))
        log(if (tag == null) "태그 삭제" else "태그 = $tag")
        refresh()
    }

    override suspend fun editValue(value: Int): DbOpResult = guarded(write = true) {
        send(DbEditRecord.value(value, _position.value.moves))
        log("값 = $value")
        refresh()
    }

    override suspend fun editDepth(depth: Int): DbOpResult = guarded(write = true) {
        send(DbEditRecord.depth(depth, _position.value.moves))
        log("깊이 = $depth")
        refresh()
    }

    override suspend fun setBestMove(): DbOpResult = guarded(write = true) {
        send(DbSetBestMove(_position.value.moves))
        log("최선수로 표시")
        refresh()
    }

    override suspend fun clearBestMove(): DbOpResult = guarded(write = true) {
        send(DbClearBestMove(_position.value.moves))
        log("최선수 표시 해제")
        refresh()
    }

    // ---- deletes -----------------------------------------------------------

    override suspend fun deleteOne(): DbOpResult = guarded(write = true) {
        send(DbDeleteOne(_position.value.moves))
        log("이 국면 기록 삭제")
        refresh()
    }

    override suspend fun deleteAll(scope: DbDeleteScope): DbOpResult =
        guarded(write = true, destructive = true) {
            send(DbDeleteAll(scope, _position.value.moves))
            log("일괄 삭제: ${scope.title()}")
            refresh()
        }

    // ---- files -------------------------------------------------------------

    override suspend fun save(): DbOpResult = guarded(write = true) {
        send(DbSave)
        log("저장 요청")
    }

    override suspend fun openFile(path: String): DbOpResult = guarded(write = true) {
        send(DbSetFile(path))
        log("DB 파일 지정: $path")
        refresh()
    }

    override suspend fun merge(path: String): DbOpResult = guarded(write = true) {
        send(DbMerge(path))
        log("병합: $path")
        refresh()
    }

    override suspend fun split(path: String): DbOpResult =
        guarded(write = true, destructive = true) {
            // main.c sends the board first so the engine splits at this position.
            send(EngineCommand.YxBoard(_position.value.placements()))
            send(DbSplit(path))
            log("분할: $path")
        }

    override suspend fun importLib(path: String): DbOpResult = guarded(write = true) {
        send(DbLibImport(path))
        log("Lib 가져오기: $path")
        refresh()
    }

    override suspend fun exportLib(path: String): DbOpResult = guarded(write = false) {
        send(DbLibExport(path))
        log("Lib 내보내기: $path")
    }

    override suspend fun exportText(path: String, all: Boolean): DbOpResult =
        guarded(write = false) {
            if (all) send(DbTextExportAll(path)) else send(DbTextExport(path))
            log(if (all) "전체 CSV 내보내기: $path" else "이 분기 CSV 내보내기: $path")
        }

    override suspend fun importText(path: String): DbOpResult = guarded(write = true) {
        send(DbTextImport(path))
        log("CSV 가져오기: $path")
        refresh()
    }

    override suspend fun exportPositions(path: String): DbOpResult = guarded(write = false) {
        send(DbToPos(path))
        log("국면 목록 내보내기: $path")
    }

    override suspend fun check(): DbOpResult = guarded(write = false) {
        send(DbCheck)
        log("무결성 검사 요청")
    }

    override suspend fun fix(): DbOpResult = guarded(write = true) {
        send(DbFix)
        log("복구 요청")
    }

    // ---- toggles -----------------------------------------------------------

    override suspend fun setEnabled(on: Boolean) {
        // settings.txt line 32 is the source of truth; EngineParams turns the
        // change into `info usedatabase <n>` so there is exactly one sender.
        settings.set("useDatabase", if (on) "1" else "0")
    }

    override suspend fun setReadOnly(on: Boolean) {
        settings.set("databaseReadonly", if (on) "1" else "0")
    }

    override suspend fun setDestructiveUnlocked(on: Boolean) = prefs.setDestructiveUnlocked(on)

    // ---- plumbing ----------------------------------------------------------

    /**
     * Stops the internal collectors and the auto-save loop. The app keeps one
     * singleton for its whole lifetime, so this is only used by tests (and by any
     * future teardown) — without it a virtual-time test scheduler would never go
     * idle, since the loop delays forever.
     */
    fun shutdown() {
        repoScope.cancel()
    }

    private suspend fun send(command: EngineCommand) = engine.send(command)

    private fun canQuery(): Boolean = _state.value.enabled && engine.state.value.isLive

    /**
     * Run [block] if the guards allow it. Mirrors the desktop's preconditions:
     * `usedatabase` on (`show_database` no-ops otherwise), not read-only for
     * writes, plus this port's extra lock on destructive operations.
     */
    private suspend inline fun guarded(
        write: Boolean,
        destructive: Boolean = false,
        block: () -> Unit,
    ): DbOpResult {
        val current = _state.value
        if (!engine.state.value.isLive) return DbOpResult.Refused("엔진에 연결되어 있지 않습니다")
        if (!current.enabled) return DbOpResult.Refused("데이터베이스 사용이 꺼져 있습니다")
        if (write && current.readOnly) return DbOpResult.Refused("읽기 전용 상태입니다")
        if (destructive && !current.destructiveUnlocked) {
            return DbOpResult.Refused("파괴적 연산이 잠겨 있습니다 (설정에서 해제)")
        }
        block()
        return DbOpResult.Sent
    }

    /**
     * Periodic `yxsavedatabase`, the port of `db_autosave_tick`: only while the
     * database is on, writable and the engine is **idle**, so records that live
     * in engine memory get flushed without disturbing a search. The engine skips
     * the disk write when nothing is dirty.
     */
    private suspend fun autoSaveLoop() {
        // Ticks once a minute and counts, so changing the interval takes effect
        // right away (the desktop re-arms its GTK timer for the same reason).
        var elapsed = 0
        while (true) {
            delay(60_000L)
            elapsed++
            val config = settings.settings.value
            val interval = config.dbAutoSaveMinutes.coerceAtLeast(1)
            if (elapsed < interval) continue
            elapsed = 0
            val idle = engine.state.value == ConnectionState.Ready
            if (config.dbAutoSave && config.useDatabase && !config.databaseReadonly && idle) {
                runCatching {
                    send(DbSave)
                    log("자동 저장 (${interval}분 주기)")
                }
            }
        }
    }

    private companion object {
        const val LOG_LIMIT = 200
        val DB_MESSAGE_KEYWORDS = listOf(
            "database", "db ", "record", "delete", "deleted", "merge", "split",
            "lib", "saved", "loading", "loaded", "txt",
        )
    }
}
