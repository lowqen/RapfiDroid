package dev.gomoku.yixindroid.feature.review

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.yixindroid.core.model.GameFile
import dev.gomoku.yixindroid.core.model.GameFileContent
import dev.gomoku.yixindroid.core.model.GameFileFormat
import dev.gomoku.yixindroid.core.model.GameReport
import dev.gomoku.yixindroid.core.model.GradingPreset
import dev.gomoku.yixindroid.core.model.QueueEntry
import dev.gomoku.yixindroid.core.model.ReviewBudget
import dev.gomoku.yixindroid.data.game.GameFileIo
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.GameRepository
import dev.gomoku.yixindroid.domain.repository.ReviewRepository
import dev.gomoku.yixindroid.domain.repository.ReviewStart
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import dev.gomoku.yixindroid.domain.review.ReportFormats
import dev.gomoku.yixindroid.domain.review.ReportShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val review: ReviewRepository,
    private val game: GameRepository,
    private val engine: EngineRepository,
    private val settingsRepository: SettingsRepository,
    private val fileIo: GameFileIo,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val budget = MutableStateFlow(ReviewBudget())
    private val notice = MutableStateFlow<String?>(null)

    private data class Panel(
        val queue: List<QueueEntry>,
        val log: List<String>,
        val queueReports: List<GameReport>,
        val budget: ReviewBudget,
        val notice: String?,
    )

    private val panel = combine(
        review.queue, review.log, review.queueReports, budget, notice,
    ) { queue, log, reports, budget, notice ->
        Panel(queue, log, reports, budget, notice)
    }

    private val board = combine(game.position, game.future) { position, future ->
        position.moves.size + future.size
    }

    val uiState: StateFlow<ReviewUiState> = combine(
        review.progress, review.report, panel, settingsRepository.settings,
        combine(board, engine.state) { length, state -> length to state.isLive },
    ) { progress, report, panel, settings, boardState ->
        ReviewUiState(
            progress = progress,
            budget = panel.budget,
            report = report,
            queueReports = panel.queueReports,
            queue = panel.queue,
            log = panel.log,
            preset = GradingPreset.of(settings.mqPreset),
            skipOpening = settings.skipOpening,
            showBadges = settings.showMoveBadge,
            lineLength = boardState.first,
            connected = boardState.second,
            notice = panel.notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    init {
        // The review budget is session state in the desktop too (`reviewseconds`
        // lives in memory and in queue_state.txt), but the unit and the depth
        // are settings_dev lines 14/15, so they seed it.
        viewModelScope.launch {
            val settings = settingsRepository.settings.value
            budget.value = budget.value.copy(
                byDepth = settings.reviewByDepth,
                depth = settings.reviewDepth,
            )
        }
    }

    // ---- run ---------------------------------------------------------------

    fun onBudgetChange(next: ReviewBudget) {
        budget.value = next.sanitized()
        viewModelScope.launch {
            settingsRepository.set("reviewByDepth", if (next.byDepth) "1" else "0")
            settingsRepository.set("reviewDepth", next.depth.toString())
        }
    }

    fun onStartReview() = launchStart { review.start(budget.value) }

    fun onStartQueue() = launchStart { review.startQueue(budget.value) }

    fun onCancel() {
        viewModelScope.launch { review.cancel() }
    }

    private fun launchStart(block: suspend () -> ReviewStart) {
        viewModelScope.launch {
            when (val result = block()) {
                is ReviewStart.Refused -> notice.value = result.reason
                ReviewStart.Started -> Unit
            }
        }
    }

    fun onPreset(preset: GradingPreset) {
        review.setPreset(preset)
        viewModelScope.launch {
            settingsRepository.set("mqPreset", preset.ordinal.toString())
        }
    }

    fun onToggleSkipOpening() {
        val next = !settingsRepository.settings.value.skipOpening
        viewModelScope.launch { settingsRepository.set("skipOpening", if (next) "1" else "0") }
    }

    fun onToggleBadges() {
        val next = !settingsRepository.settings.value.showMoveBadge
        viewModelScope.launch { settingsRepository.set("showMoveBadge", if (next) "1" else "0") }
    }

    /** A row of the report jumps the board there (`game_report_row`). */
    fun onJumpTo(index: Int) {
        viewModelScope.launch { game.jumpTo(index) }
    }

    // ---- files -------------------------------------------------------------

    fun onLoadGame(uri: Uri) {
        viewModelScope.launch {
            val name = fileIo.displayName(uri)
            val format = GameFileFormat.of(name)
            if (format == null) {
                notice.value = "지원하지 않는 형식입니다 (.psq / .sav / .pos)"
                return@launch
            }
            val bytes = fileIo.read(uri.toString())
            val moves = bytes?.let {
                GameFile.parse(it, format, settingsRepository.settings.value.boardSize)
            }
            if (moves.isNullOrEmpty()) {
                notice.value = "기보를 읽을 수 없습니다"
                return@launch
            }
            review.loadGame(GameFileContent(moves, GameFile.baseName(name)))
            notice.value = "${moves.size}수를 불러왔습니다"
        }
    }

    fun onSaveGame(uri: Uri) {
        viewModelScope.launch {
            val name = fileIo.displayName(uri)
            val moves = game.position.value.moves + game.future.value
            val size = game.position.value.size
            val bytes = when (GameFileFormat.of(name)) {
                GameFileFormat.PSQ -> GameFile.writePsq(moves, size)
                else -> GameFile.writeSav(moves, size)   // the desktop's default
            }
            notice.value = runCatching { fileIo.write(uri, bytes) }.fold(
                onSuccess = { "${moves.size}수를 저장했습니다" },
                onFailure = { "저장 실패: ${it.message}" },
            )
        }
    }

    fun onEnqueue(uris: List<Uri>) {
        viewModelScope.launch {
            val entries = uris.map { uri ->
                QueueEntry(uri = uri.toString(), name = fileIo.displayName(uri))
            }.filter { GameFileFormat.of(it.name) != null }
            if (entries.isEmpty()) {
                notice.value = "큐에 넣을 수 있는 기보가 없습니다"
                return@launch
            }
            review.enqueue(entries)
            notice.value = "${entries.size}개를 큐에 넣었습니다"
        }
    }

    /** `queue_add_current`: park the board's line in the queue as a .psq. */
    fun onEnqueueCurrent() {
        viewModelScope.launch {
            val moves = game.position.value.moves + game.future.value
            if (moves.isEmpty()) {
                notice.value = "보드에 수가 없습니다"
                return@launch
            }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val name = "g_$stamp.psq"
            val path = fileIo.writeLocal(
                QUEUE_GAMES,
                name,
                String(GameFile.writePsq(moves, game.position.value.size)),
            )
            review.enqueue(listOf(QueueEntry(uri = Uri.fromFile(java.io.File(path)).toString(), name = name)))
            notice.value = "현재 대국을 큐에 넣었습니다 ($name)"
        }
    }

    fun onRemoveQueued(uri: String) = review.removeQueued(uri)

    fun onClearQueue() = review.clearQueue()

    // ---- exports -----------------------------------------------------------

    fun onExport(uri: Uri, format: ExportFormat) {
        val report = uiState.value.report ?: return
        viewModelScope.launch {
            val text = render(report, format)
            notice.value = runCatching { fileIo.write(uri, text.toByteArray()) }.fold(
                onSuccess = { "리포트를 저장했습니다" },
                onFailure = { "저장 실패: ${it.message}" },
            )
        }
    }

    /** `review_finish`'s automatic write into `reports/`, minus the file dialog. */
    fun onExportAll() {
        val report = uiState.value.report ?: return
        viewModelScope.launch {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(report.createdAt))
            val base = "${report.title}_${stamp}_${report.moveCount}moves"
            val paths = ExportFormat.entries.map { format ->
                fileIo.writeLocal(REPORTS, "$base.${format.extension}", render(report, format))
            }
            notice.value = "리포트 저장: ${paths.first().substringBeforeLast('/')}"
        }
    }

    private suspend fun render(report: GameReport, format: ExportFormat): String = when (format) {
        ExportFormat.CSV -> ReportFormats.csv(report)
        ExportFormat.MD -> ReportFormats.markdown(report)
        ExportFormat.HTML -> ReportFormats.html(
            report,
            shell(),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(report.createdAt)),
        )
    }

    /** The desktop's report page, split at the two injection points. */
    private suspend fun shell(): ReportShell {
        cachedShell?.let { return it }
        val read = { name: String ->
            context.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
        }
        val loaded = ReportShell(
            head = read("report_head.html"),
            body = read("report_body.html"),
            tail = read("report_tail.html"),
        )
        cachedShell = loaded
        return loaded
    }

    fun onNoticeShown() {
        notice.value = null
    }

    /** Snackbar for a section that shares this screen (the prove card). */
    fun onExternalNotice(text: String) {
        notice.value = text
    }

    private var cachedShell: ReportShell? = null

    private companion object {
        const val REPORTS = "reports"
        const val QUEUE_GAMES = "queue_games"
    }
}

enum class ExportFormat(val extension: String, val mime: String, val label: String) {
    HTML("html", "text/html", "HTML 리포트"),
    MD("md", "text/markdown", "마크다운"),
    CSV("csv", "text/csv", "CSV"),
}
