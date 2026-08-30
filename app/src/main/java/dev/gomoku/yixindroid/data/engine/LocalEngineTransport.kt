package dev.gomoku.yixindroid.data.engine

import dev.gomoku.yixindroid.core.model.LocalEngineProfile
import okio.buffer
import okio.sink
import okio.source

/**
 * Rapfi on this phone, as a **child process** talking over stdin/stdout — the
 * same shape the desktop GUI has always used, and deliberately not a JNI call
 * into the app's own process:
 *
 *  1. The engine calls `std::exit` when the transposition table cannot be
 *     allocated at all (`hashtable.cpp`). In-process that takes the whole app
 *     down with no message; out of process it is a dead pipe the UI can report.
 *  2. The build resolves `config.toml` and its weights from the working
 *     directory (it has no `--config` argument), and `chdir` in-process would
 *     move the *app's* cwd.
 *  3. Cancelling is then just killing a process, which is what the desktop does.
 */
class LocalEngineTransport(
    private val installer: LocalEngineInstaller,
    private val profile: LocalEngineProfile,
) : EngineTransport {

    override val label: String get() = "on-device"

    override suspend fun open(): EngineChannel {
        val dir = installer.prepare(profile)
        val process = try {
            ProcessBuilder(listOf(installer.binary.absolutePath))
                .directory(dir)
                // Rapfi writes its ERROR lines to stdout, but anything the
                // runtime says goes to stderr; folding them together keeps a
                // crash visible in the piskvork console instead of nowhere.
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            throw LocalEngineUnavailable(
                "엔진 프로세스를 시작하지 못했습니다: ${e.message ?: e::class.simpleName}",
                e,
            )
        }

        return EngineChannel(
            source = process.inputStream.source().buffer(),
            sink = process.outputStream.sink().buffer(),
        ) {
            // Closing stdin is the polite exit — `runProtocol` returns on EOF.
            // It is not enough on its own: `gomocupLoop` then waits for the
            // search threads to finish, so an engine that was thinking would
            // outlive the disconnect the user just asked for.
            runCatching { process.outputStream.close() }
            process.destroyForcibly()
        }
    }
}
