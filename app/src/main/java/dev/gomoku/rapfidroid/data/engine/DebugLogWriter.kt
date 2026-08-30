package dev.gomoku.rapfidroid.data.engine

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.rapfidroid.core.common.IoDispatcher
import dev.gomoku.rapfidroid.core.model.ConsoleLine
import dev.gomoku.rapfidroid.domain.repository.EngineRepository
import dev.gomoku.rapfidroid.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * settings.txt line 36, "record debug log" — the desktop's `debuglog`
 * (main.c:181, written around every engine line).
 *
 * The point of this setting is being able to hand someone the transcript of a
 * session that went wrong, which on a phone means a file that can be shared.
 * So: append every console line while the setting is on, cap the file so a long
 * session cannot fill the device, and offer it for export.
 *
 * It records the same lines the console shows — both directions, engine traffic
 * only. Nothing else about the device goes in.
 */
@Singleton
class DebugLogWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val engine: EngineRepository,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** Call once at startup; the setting is read per line, so it can be toggled live. */
    fun start() {
        scope.launch {
            engine.console.collect { line ->
                if (!settings.settings.value.recordDebugLog) return@collect
                runCatching { append(line) }
            }
        }
    }

    private fun append(line: ConsoleLine) {
        val f = file
        // Roll rather than truncate: the beginning of a session is usually the
        // interesting part (the handshake), so keeping the *old* half would be
        // wrong too. Starting over at least leaves a coherent transcript.
        if (f.length() > MAX_BYTES) f.writeText("--- 로그가 ${MAX_BYTES / 1024}KB 를 넘어 새로 시작합니다 ---\n")
        val direction = if (line.outbound) ">>" else "<<"
        f.appendText("${stamp.format(Date())} $direction ${line.text}\n")
    }

    /** Size in bytes, for the settings screen to show something honest. */
    suspend fun size(): Long = withContext(io) { file.length() }

    suspend fun export(target: Uri): Long = withContext(io) {
        val bytes = if (file.isFile) file.readBytes() else ByteArray(0)
        context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
            ?: error("파일을 만들 수 없습니다")
        bytes.size.toLong()
    }

    suspend fun clear() = withContext(io) {
        file.delete()
        Unit
    }

    companion object {
        const val FILE_NAME = "rapfidroid-debug.log"
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}
