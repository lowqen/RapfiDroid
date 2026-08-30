package dev.gomoku.yixindroid.data.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.yixindroid.core.model.LocalEngineProfile
import dev.gomoku.yixindroid.domain.engine.MobileEngineConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The local engine cannot be started, with a reason fit to put on screen. */
class LocalEngineUnavailable(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Puts the on-device engine's working directory in order before it is started.
 *
 * Two locations, and they are not interchangeable:
 *
 *  - **The binary** lives in `nativeLibraryDir` and is never copied. Since
 *    Android 10 an app may not execute a file out of its own data directory;
 *    the one place left is the directory the package manager extracted native
 *    libraries into, and it only extracts entries named `lib*.so`. That is why
 *    the engine ships as `libengine.so` and why the module must set
 *    `jniLibs.useLegacyPackaging = true` — with the modern default the library
 *    is mapped straight out of the APK and no executable path exists at all.
 *  - **Everything the engine reads** (weights, model, `config.toml`) is copied
 *    to `filesDir/engine`, which also becomes its working directory: the build
 *    has no `--config` argument (that lives behind `COMMAND_MODULES`), so Rapfi
 *    finds its config and its weights by resolving them from the cwd.
 *
 * `rapfi.db` is written by the engine into that same directory, so extraction
 * overwrites rather than wipes — a database must survive an app update.
 */
@Singleton
class LocalEngineInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val engineDir: File get() = File(context.filesDir, ENGINE_DIR)

    val binary: File get() = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    /**
     * Extract on first run (and after every app update), then write the config
     * for [profile]. Returns the working directory to start the engine in.
     *
     * The config is rewritten every time because the profile can change without
     * the APK changing; the 40 MB of weights are only re-extracted when the
     * install itself changed, which is the only thing that can change them.
     */
    fun prepare(profile: LocalEngineProfile): File {
        val exe = binary
        if (!exe.exists()) {
            throw LocalEngineUnavailable(
                "온디바이스 엔진 파일이 없습니다 (${exe.path}). " +
                    "jniLibs/arm64-v8a/libengine.so 가 빠졌거나 이 기기가 arm64 가 아닙니다.",
            )
        }
        if (!exe.canExecute()) {
            throw LocalEngineUnavailable(
                "엔진 파일에 실행 권한이 없습니다 (${exe.path}). " +
                    "packaging { jniLibs { useLegacyPackaging = true } } 가 빠지면 이렇게 됩니다.",
            )
        }

        val dir = engineDir
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw LocalEngineUnavailable("엔진 작업 폴더를 만들 수 없습니다: ${dir.path}")
        }

        try {
            val stampFile = File(dir, STAMP_NAME)
            val stamp = installStamp()
            if (runCatching { stampFile.readText() }.getOrNull() != stamp) {
                extractAssets(dir)
                stampFile.writeText(stamp)
            }
            val template = context.assets.open("$ASSET_DIR/$CONFIG_NAME")
                .use { it.readBytes() }
                .decodeToString()
            File(dir, CONFIG_NAME).writeText(MobileEngineConfig.render(template, profile))
        } catch (e: LocalEngineUnavailable) {
            throw e
        } catch (e: Exception) {
            throw LocalEngineUnavailable(
                "엔진 파일 준비에 실패했습니다: ${e.message ?: e::class.simpleName}",
                e,
            )
        }
        return dir
    }

    private fun extractAssets(dir: File) {
        val names = context.assets.list(ASSET_DIR).orEmpty()
        if (names.isEmpty()) {
            throw LocalEngineUnavailable("assets/$ASSET_DIR 가 비어 있습니다 — 가중치가 APK 에 들어가지 않았습니다.")
        }
        for (name in names) {
            // The config is rendered, not copied: it carries the phone's own
            // hash and thread numbers.
            if (name == CONFIG_NAME) continue
            context.assets.open("$ASSET_DIR/$name").use { input ->
                File(dir, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    /**
     * Changes exactly when the installed APK changes, which is the only way the
     * bundled weights can change. Written last, so a copy interrupted halfway
     * (storage full, process killed) is retried on the next start instead of
     * leaving a truncated weight file behind a stamp that says "done".
     */
    private fun installStamp(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
    }.getOrElse { "0" }

    private companion object {
        const val ENGINE_DIR = "engine"
        const val ASSET_DIR = "engine"
        const val CONFIG_NAME = "config.toml"
        const val STAMP_NAME = ".installed"

        /** Must match the CMake `OUTPUT_NAME engine / PREFIX lib / SUFFIX .so`. */
        const val BINARY_NAME = "libengine.so"
    }
}
