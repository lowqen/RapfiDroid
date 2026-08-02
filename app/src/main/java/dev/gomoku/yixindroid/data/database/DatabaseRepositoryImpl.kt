package dev.gomoku.yixindroid.data.database

import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.DbDeleteScope
import dev.gomoku.yixindroid.core.model.DbOpResult
import dev.gomoku.yixindroid.core.model.DbState
import dev.gomoku.yixindroid.core.model.EngineBusy
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
import dev.gomoku.yixindroid.domain.engine.DbQueryPairing
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
    private val busy: EngineBusy,
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
    private val pairing = DbQueryPairing()

    /**
     * Whether the engine finished reading the database file.
     *
     * NONE is not "we cannot tell" — the server's config has
     * `enable_by_default = false`, so an engine that has not reported a load
     * holds no database at all and reads the file only once
     * `INFO usedatabase 1` arrives. Saving from either NONE or LOADING would
     * write far less than the file already contains.
     */
    private enum class LoadState { NONE, LOADING, LOADED }

    @Volatile
    private var loadState = LoadState.NONE

    /**
     * A save the engine has begun and not finished. It rewrites the whole file,
     * so two overlapping saves interleave into one truncated result — which is
     * what a 5-minute timer did to a database that takes longer than that to
     * write. Null when nothing is in flight; otherwise when it started.
     */
    @Volatile
    private var savingSince: Long? = null

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
        //
        // The same edge covers the end of every search: the engine goes back to
        // Ready when it reports the move it settled on, and the desktop's own
        // handler re-reads the database at that exact point (main.c:13961).
        repoScope.launch {
            engine.state
                .map { it == ConnectionState.Ready }
                .distinctUntilChanged()
                .collect { ready -> if (ready) refresh() }
        }
        // A new engine process holds nothing this one told us about: the proxy
        // spawns one Rapfi per connection, so a `LOADED` remembered across a
        // drop would let a save go out against a database that was never read.
        repoScope.launch {
            engine.state.collect { state ->
                if (state is ConnectionState.Disconnected || state is ConnectionState.Error) {
                    loadState = LoadState.NONE
                    savingSince = null
                }
            }
        }
        repoScope.launch { autoSaveLoop() }
    }

    // ---- incoming ----------------------------------------------------------

    private fun onResponse(response: EngineResponse) {
        val snapshot = aggregator.consume(response)
        if (response is EngineResponse.DbDone) pairing.onDone()
        if (snapshot != null) {
            val paired = pairing.paired
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
                // The engine's own account of what it is doing to the file, which
                // is the only thing that makes a save safe to send: it says when
                // the database became complete in memory, and when a write it
                // began has finished.
                if (response.saving) {
                    if (response.started) {
                        savingSince = System.currentTimeMillis()
                    } else {
                        savingSince?.let {
                            log(
                                tr(
                                    "저장 완료 (${(System.currentTimeMillis() - it) / 1000}초)",
                                    "Saved in ${(System.currentTimeMillis() - it) / 1000}s",
                                ),
                            )
                        }
                        savingSince = null
                        _state.update { it.copy(lastSaveAt = System.currentTimeMillis()) }
                    }
                } else {
                    loadState = if (response.started) LoadState.LOADING else LoadState.LOADED
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

    /**
     * `show_database()` — ask for every cell value and text of this position.
     *
     * The desktop only ever calls it with the engine idle: each caller stops a
     * running search first (`stop_search_for_board_edit`), and the reply handler
     * calls it once the search has ended. A query sent into a running search is
     * not lost, but Rapfi answers it only when the search finishes, so the values
     * would arrive minutes late and out of order with the board they belong to.
     * Deferring here keeps that rule without every caller having to know it: the
     * Ready edge above re-runs the query the moment the engine is listening.
     */
    override suspend fun refresh() {
        if (!canQuery()) return
        if (engine.state.value == ConnectionState.Thinking) return
        pairing.onQuery(System.currentTimeMillis())
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

    /**
     * `yxsavedatabase`. Not an append — the engine writes its whole in-memory
     * database over the file, so every condition that makes that unsafe is
     * checked here rather than at the call sites. A caller that forgets one
     * destroys data, which is how the auto-save timer came to be the only path
     * that knew searching mattered.
     *
     * A save goes out only when: the database is in use, the user has not asked
     * for read-only (both in [guarded]), the engine finished reading the file,
     * it is not searching, and no earlier save is still running.
     */
    override suspend fun save(): DbOpResult = save(attachIfMissing = true)

    /**
     * @param attachIfMissing whether a missing database may be attached from
     *   here. True when a person asked for the save; **false for the timer** —
     *   an attach reloads the whole file into the engine, and a timer that did
     *   that every interval would be the RAM disaster of session 48 on a loop.
     */
    private suspend fun save(attachIfMissing: Boolean): DbOpResult {
        // Connected, database on, not read-only — asked first because they are
        // the answers the user can act on, and because attaching a database the
        // user turned off would be answering a question nobody asked.
        guarded(write = true) { }.let { if (it is DbOpResult.Refused) return it }
        when (loadState) {
            LoadState.NONE -> {
                // Nothing worth writing yet. If a person asked, attach the
                // database and let the next save go out once the engine reports
                // it read; `0` first because the attach guard passes that
                // through and re-arms the `1` (P11).
                if (!attachIfMissing) return DbOpResult.Refused("")
                send(EngineCommand.Info("usedatabase", "0"))
                send(EngineCommand.Info("usedatabase", "1"))
                return DbOpResult.Refused(
                    tr(
                        "아직 데이터베이스를 불러오지 않았습니다 — 지금 불러오는 중이니 잠시 뒤 다시 저장하세요",
                        "No database is loaded yet — loading it now, save again in a moment",
                    ),
                )
            }
            LoadState.LOADING -> return DbOpResult.Refused(
                tr(
                    "불러오기가 끝나지 않았습니다 — 지금 저장하면 파일이 읽다 만 사본으로 덮입니다",
                    "The load never finished — saving now would replace the file with a partial copy",
                ),
            )
            LoadState.LOADED -> Unit
        }
        if (engineBusy()) {
            return DbOpResult.Refused(
                tr(
                    "엔진이 탐색 중입니다 — 끝난 뒤에 저장합니다",
                    "The engine is still searching — the save waits for it",
                ),
            )
        }
        savingSince?.let { since ->
            val elapsed = System.currentTimeMillis() - since
            if (elapsed < SAVE_MAX_MS) {
                return DbOpResult.Refused(
                    tr(
                        "이전 저장이 ${elapsed / 1000}초째 진행 중입니다 — 겹쳐 쓰면 파일이 잘립니다",
                        "The previous save has run for ${elapsed / 1000}s — overlapping writes truncate the file",
                    ),
                )
            }
            // Past any plausible duration the DONE is not coming (a dropped
            // socket, an engine that died mid-write). Let this one through
            // rather than never saving again.
            savingSince = null
        }
        return guarded(write = true) {
            send(DbSave)
            log(tr("저장 요청", "Save requested"))
        }
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
     * Is a search running? `Thinking` covers an analysis or a balance search;
     * [EngineBusy] covers a review or a proof, which drive the engine directly
     * and would otherwise look idle for their whole run.
     */
    private fun engineBusy(): Boolean =
        engine.state.value == ConnectionState.Thinking || busy.isBusy

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
     * Periodic `yxsavedatabase`, the port of `db_autosave_tick`. Every
     * precondition lives in [save] now, so the timer cannot be the one path that
     * knows a rule the buttons do not — which is exactly how it came to be the
     * only caller that checked whether the engine was searching.
     */
    private suspend fun autoSaveLoop() {
        // Ticks once a minute and counts, so changing the interval takes effect
        // right away (the desktop re-arms its GTK timer for the same reason).
        var elapsed = 0
        while (true) {
            delay(60_000L)
            elapsed++
            val interval = settings.settings.value.dbAutoSaveMinutes.coerceAtLeast(1)
            if (elapsed < interval) continue
            elapsed = 0
            if (!settings.settings.value.dbAutoSave) continue
            val result = runCatching { save(attachIfMissing = false) }.getOrNull()
            if (result is DbOpResult.Sent) {
                log(tr("자동 저장 (${interval}분 주기)", "Auto-saved (every $interval min)"))
            }
        }
    }

    private companion object {
        const val LOG_LIMIT = 200

        /** Longer than any plausible save; past it a missing DONE means the save
         *  is gone, not slow. */
        const val SAVE_MAX_MS = 10L * 60 * 1000
        val DB_MESSAGE_KEYWORDS = listOf(
            "database", "db ", "record", "delete", "deleted", "merge", "split",
            "lib", "saved", "loading", "loaded", "txt",
        )
    }
}
