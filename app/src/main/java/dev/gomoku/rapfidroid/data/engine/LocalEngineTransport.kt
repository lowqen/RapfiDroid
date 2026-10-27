package dev.gomoku.rapfidroid.data.engine

import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.LocalEngineProfile
import dev.gomoku.rapfidroid.core.model.MemoryTier
import dev.gomoku.rapfidroid.domain.engine.EngineVariant
import dev.gomoku.rapfidroid.domain.engine.EngineVariantSelector
import okio.buffer
import okio.sink
import okio.source
import java.util.concurrent.TimeUnit

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
 *
 * Which executable is started — dotprod or plain NEON — is the installer's
 * call ([LocalEngineInstaller.variant]); both search the same tree.
 */
class LocalEngineTransport(
    private val installer: LocalEngineInstaller,
    private val profile: LocalEngineProfile,
    /** The process died of SIGKILL without being asked to close (T3 — see [MemoryGuard]). */
    private val onKilled: () -> Unit = {},
) : EngineTransport {

    override val label: String get() = "on-device"

    override suspend fun open(): EngineChannel {
        val variant = installer.variant()
        val dir = installer.prepare(profile, variant)
        val process = try {
            ProcessBuilder(listOf(installer.binaryFor(variant).absolutePath))
                .directory(dir)
                // Rapfi writes its ERROR lines to stdout, but anything the
                // runtime says goes to stderr; folding them together keeps a
                // crash visible in the piskvork console instead of nowhere.
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            throw LocalEngineUnavailable(
                tr("엔진 프로세스를 시작하지 못했습니다: ${e.message ?: e::class.simpleName}", "Could not start the engine process: ${e.message ?: e::class.simpleName}", "无法启动引擎进程: ${e.message ?: e::class.simpleName}"),
                e,
            )
        }

        // The dotprod build's safety net. Its CPU check reads /proc/cpuinfo, and
        // a CPU (or emulator) that reports a feature it cannot execute kills the
        // engine with SIGILL — at the first atomic, or at the first NNUE
        // evaluation. To the connection that is only a dropped pipe and the
        // reconnect loop respawns the engine; recording the cause here, the
        // moment the process dies, makes that respawn start the NEON build.
        //
        // The same watcher reports a SIGKILL nobody asked for: the reaper below
        // uses one too, so a close marks itself first.
        val closing = java.util.concurrent.atomic.AtomicBoolean(false)
        Thread({
            val code = runCatching { process.waitFor() }.getOrNull() ?: return@Thread
            if (EngineVariantSelector.isIllegalInstruction(variant, code)) {
                installer.markDotprodFailed(code)
            } else if (!closing.get() && MemoryTier.isKill(code)) {
                onKilled()
            }
        }, "local-engine-exit-watch").apply { isDaemon = true }.start()

        val source = process.inputStream.source().buffer()
        val sink = process.outputStream.sink().buffer()

        return EngineChannel(source, sink) {
            closing.set(true)
            // Shutting down in the right order, because the database lives or
            // dies by it. yixindb is held in memory and written on command or on
            // close; a killed process runs neither, so everything this session
            // learned would be lost on hang-up.
            //
            // These are ordinary writes on the same pipe, so the engine executes
            // them before it sees EOF — no waiting for a reply is needed:
            //   YXSTOP         end the search, so waitForIdle() returns at once
            //   YXSAVEDATABASE flush the database to rapfi.db
            //   EOF            runProtocol() returns true and the engine exits
            runCatching {
                sink.writeUtf8("YXSTOP\nYXSAVEDATABASE\n").flush()
                sink.close()
            }
            // And a reaper, because "should exit" is not "did exit" — a search
            // that ignores YXSTOP would otherwise leave a process holding the
            // CPU. Off the caller's thread: hang-up comes from the UI.
            Thread({
                if (!process.waitFor(EXIT_GRACE_MS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                }
            }, "local-engine-reaper").apply { isDaemon = true }.start()
        }
    }

    private companion object {
        /** Long enough for a stopped search to unwind, short enough to be a hang-up. */
        const val EXIT_GRACE_MS = 3_000L
    }
}
