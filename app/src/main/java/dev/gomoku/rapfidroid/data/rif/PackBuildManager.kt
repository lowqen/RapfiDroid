package dev.gomoku.rapfidroid.data.rif

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.rapfidroid.core.common.IoDispatcher
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.PackBuildState
import dev.gomoku.rapfidroid.data.explorer.ExplorerPackStore
import dev.gomoku.rapfidroid.data.rankings.FreqDataDto
import dev.gomoku.rapfidroid.data.rankings.FreqStore
import dev.gomoku.rapfidroid.domain.rif.FreqBuilder
import dev.gomoku.rapfidroid.domain.rif.RenjuPack
import dev.gomoku.rapfidroid.domain.rif.RifStreamParser
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
    private val freqStore: FreqStore,
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
                        "设备内存不足。请关闭其他应用后重试。",
                    ),
                )
            } catch (e: Throwable) {
                _state.value = PackBuildState.Failed(
                    e.message ?: e::class.simpleName ?: tr("알 수 없는 오류", "Unknown error", "未知错误"),
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

    /**
     * The fast path (RapfiWeb W7): the file is read as bytes into game columns,
     * positions are counted by 64-bit Zobrist keys and a radix sort, and the
     * result is one pack file. Seconds where the old two-pass string build took
     * minutes.
     */
    private suspend fun build(source: Uri) {
        val started = System.currentTimeMillis()
        val total = fileSize(source)
        val tables = context.contentResolver.openInputStream(source)?.use { input ->
            RifStreamParser(expectedBytes = total).parse(input) { read ->
                _state.value = PackBuildState.Running(
                    PackBuildState.Phase.Reading,
                    (read shr 10).toInt(),
                    (total shr 10).toInt(),
                )
            }
        } ?: error(tr("파일을 열 수 없습니다", "Could not open the file", "无法打开文件"))

        if (tables.games.count == 0) {
            error(
                tr(
                    "기보가 하나도 없습니다. renju.net 의 XML 형식 파일이 맞는지 확인해 주세요.",
                    "No games in that file. Check it is the XML download from renju.net.",
                    "文件中没有棋谱。请确认是 renju.net 的 XML 下载文件。",
                ),
            )
        }

        val pack = RenjuPack.build(tables) { phase, fraction ->
            val p = if (phase == 0) PackBuildState.Phase.Counting else PackBuildState.Phase.Aggregating
            _state.value = PackBuildState.Running(p, (fraction * 1000).toInt(), 1000)
        }

        _state.value = PackBuildState.Running(PackBuildState.Phase.Writing, 0, 0)
        val part = File(packs.workDir, "renju.part")
        try {
            part.outputStream().use { pack.write(it) }
            packs.adopt(part).getOrThrow()
        } finally {
            part.delete()
        }

        // The rankings are the same games counted differently.
        val freq = FreqBuilder.fromPack(pack)
        freqStore.adopt(
            FreqDataDto(
                generated = today().toString(),
                players = freq.players,
                rules = freq.rules,
                shapes = freq.shapes,
                games = freq.games,
            ),
        ).getOrThrow()

        _state.value = PackBuildState.Done(
            games = pack.gameCount,
            positions = pack.positionCount,
            skipped = tables.skipped.values.sum(),
            elapsedMs = System.currentTimeMillis() - started,
        )
    }

    private fun fileSize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    /** `YYYYMMDD`, the stamp the explorer shows so data age is visible. */
    private fun today(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 10_000 +
            (c.get(Calendar.MONTH) + 1) * 100 +
            c.get(Calendar.DAY_OF_MONTH)
    }
}
