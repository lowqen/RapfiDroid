package dev.gomoku.yixindroid.data.rif

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.PackBuildState
import dev.gomoku.yixindroid.data.explorer.ExplorerPackStore
import dev.gomoku.yixindroid.domain.rif.PackAggregator
import dev.gomoku.yixindroid.domain.rif.PackWriter
import dev.gomoku.yixindroid.domain.rif.RifParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the opening-explorer packs on this device from the user's own `.rif`.
 *
 * **Why the app does this at all.** The RenjuNet database allows offline
 * non-commercial use and forbids putting its contents *or modifications* on any
 * website or online system — so the packs cannot be shipped in the APK and
 * cannot be offered as a download. The only path that serves a user who has no
 * PC is for them to fetch the official file from renju.net and for the phone to
 * do the rest. Nothing here uploads anything.
 *
 * A foreground service holds the process up while it runs, because this takes
 * minutes and the screen will go off.
 */
@Singleton
class PackBuildManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packs: ExplorerPackStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val _state = MutableStateFlow<PackBuildState>(PackBuildState.Idle)
    val state: StateFlow<PackBuildState> = _state.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = _state.value is PackBuildState.Running

    fun start(source: Uri) {
        if (isRunning) return
        _state.value = PackBuildState.Running(PackBuildState.Phase.Reading, 0, 0)
        PackBuildService.start(context)
        job = scope.launch {
            try {
                build(source)
            } catch (e: CancellationException) {
                _state.value = PackBuildState.Idle
                throw e
            } catch (e: OutOfMemoryError) {
                // Worth its own message: the fix is not "try again" but "close
                // other apps", and on a small device possibly "this will not fit".
                _state.value = PackBuildState.Failed(
                    tr(
                        "기기 메모리가 부족합니다. 다른 앱을 닫고 다시 시도해 보세요.",
                        "This device ran out of memory. Close other apps and try again.",
                    ),
                )
            } catch (e: Throwable) {
                _state.value = PackBuildState.Failed(
                    e.message ?: e::class.simpleName ?: tr("알 수 없는 오류", "Unknown error"),
                )
            } finally {
                PackBuildService.stop(context)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = PackBuildState.Idle
        PackBuildService.stop(context)
    }

    /** Clear a finished result so the screen goes back to offering a build. */
    fun acknowledge() {
        if (_state.value !is PackBuildState.Running) _state.value = PackBuildState.Idle
    }

    private suspend fun build(source: Uri) {
        val db = context.contentResolver.openInputStream(source)?.use { input ->
            RifParser().parse(input) { games ->
                _state.value = PackBuildState.Running(PackBuildState.Phase.Reading, games, 0)
            }
        } ?: error(tr("파일을 열 수 없습니다", "Could not open the file"))

        if (db.games.isEmpty()) {
            error(
                tr(
                    "기보가 하나도 없습니다 — renju.net 의 XML 형식 파일이 맞는지 확인해 주세요.",
                    "No games in that file — check it is the XML download from renju.net.",
                ),
            )
        }

        val aggregate = PackAggregator().aggregate(db) { pass, done, total ->
            val phase = if (pass == 1) PackBuildState.Phase.Counting else PackBuildState.Phase.Aggregating
            _state.value = PackBuildState.Running(phase, done, total)
        }

        _state.value = PackBuildState.Running(PackBuildState.Phase.Writing, 0, 0)
        val work = packs.workDir
        val stats = File(work, "stats.part")
        val games = File(work, "games.part")
        try {
            stats.outputStream().buffered().use { PackWriter().writeStats(it, aggregate, today()) }
            games.outputStream().buffered().use { PackWriter().writeGames(it, db) }
            packs.adopt(stats, games).getOrThrow()
        } finally {
            stats.delete()
            games.delete()
        }

        _state.value = PackBuildState.Done(
            games = db.gameCount,
            positions = aggregate.positions.size,
            skipped = db.skipped.values.sum(),
        )
    }

    /** `YYYYMMDD`, the stamp the explorer shows so data age is visible. */
    private fun today(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 10_000 +
            (c.get(Calendar.MONTH) + 1) * 100 +
            c.get(Calendar.DAY_OF_MONTH)
    }
}
