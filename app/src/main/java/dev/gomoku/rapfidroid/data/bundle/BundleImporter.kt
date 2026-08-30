package dev.gomoku.rapfidroid.data.bundle

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.rapfidroid.core.common.IoDispatcher
import dev.gomoku.rapfidroid.core.model.SettingsFile
import dev.gomoku.rapfidroid.data.appearance.SafTree
import dev.gomoku.rapfidroid.data.explorer.ExplorerPackStore
import dev.gomoku.rapfidroid.data.rankings.FreqStore
import dev.gomoku.rapfidroid.data.settings.SettingsFileIo
import dev.gomoku.rapfidroid.domain.repository.AppearanceRepository
import dev.gomoku.rapfidroid.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One folder pick, everything imported.
 *
 * The app needs six or seven separate files to reach full function — two packs,
 * the freq dataset, two settings files, `function/`, `language/` — and picking
 * them one at a time meant four trips through four different screens. They all
 * sit in one folder on the PC (next to `Yixin.exe`), so the folder is the unit
 * the user actually has: pick it once and each importer is handed the child it
 * knows how to read.
 *
 * Nothing here parses anything itself. Every file goes to the same importer a
 * single pick would have used, so there is no second reading of any format to
 * drift from the first — this only finds files and reports what happened.
 *
 * Missing files are not errors. A folder with only the packs in it is a
 * perfectly good folder; the report says what was found and what was not, and
 * only a folder with *nothing* recognisable fails.
 */
@Singleton
class BundleImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val packStore: ExplorerPackStore,
    private val freqStore: FreqStore,
    private val settings: SettingsRepository,
    private val appearance: AppearanceRepository,
    private val fileIo: SettingsFileIo,
) {

    /** What the last folder import found, for the card that offers it. */
    private val _report = MutableStateFlow<Report?>(null)
    val report: StateFlow<Report?> = _report.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /**
     * @param found one line per thing that loaded, in import order.
     * @param missing the names that were not in the folder.
     * @param failed name → why, for files that were there but unreadable.
     */
    data class Report(
        val folder: String,
        val found: List<String>,
        val missing: List<String>,
        val failed: List<String>,
    ) {
        val ok: Boolean get() = found.isNotEmpty()

        /** One line for a snackbar. */
        fun summary(): String = when {
            found.isEmpty() -> "$folder 안에서 읽을 자료를 찾지 못했습니다"
            else -> found.joinToString(" · ") + " 불러옴"
        }
    }

    /**
     * Import everything recognisable under [tree].
     *
     * Settings go first so that `language/` is read in the language the user's
     * own `settings.txt` asks for rather than the one the app happened to be
     * showing — importing both at once should land in one consistent state.
     */
    suspend fun importAll(tree: Uri): Result<Report> = runCatching {
        _running.value = true
        try {
            // The tree grant is what keeps the freq dataset readable on the next
            // launch; the packs are copied and the rest is parsed on the spot,
            // so nothing else depends on it surviving.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    tree, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            val saf = withContext(io) { SafTree(context.contentResolver, tree) }
            val found = ArrayList<String>()
            val missing = ArrayList<String>()
            val failed = ArrayList<String>()

            importSettings(saf, found, missing, failed)
            importAppearance(tree, found, missing)
            importPacks(saf, found, missing, failed)
            importFreq(saf, found, missing, failed)

            val label = withContext(io) { saf.displayName() } ?: "선택한 폴더"
            Report(label, found, missing, failed).also { _report.value = it }
        } finally {
            _running.value = false
        }
    }

    private suspend fun importSettings(
        saf: SafTree,
        found: MutableList<String>,
        missing: MutableList<String>,
        failed: MutableList<String>,
    ) {
        for (file in SettingsFile.entries) {
            val uri = withContext(io) { saf.fileUri(file.fileName) }
            if (uri == null) {
                missing += file.fileName
                continue
            }
            runCatching { settings.import(fileIo.read(uri), file) }
                .onSuccess { found += "${file.fileName} ${it}줄" }
                .onFailure { failed += "${file.fileName}: ${it.message}" }
        }
    }

    /**
     * `function/` and `language/` are read by [AppearanceRepository] straight
     * from the tree — it already takes the folder, which is why this is a
     * delegation and not a re-implementation. Its "neither folder is here"
     * failure is a miss, not an error, when the whole folder is being scanned.
     */
    private suspend fun importAppearance(
        tree: Uri,
        found: MutableList<String>,
        missing: MutableList<String>,
    ) {
        val index = settings.settings.value.language
        appearance.importFrom(tree, index)
            .onSuccess { found += it.removeSuffix(" 불러옴") }
            .onFailure { missing += "function/ · language/" }
    }

    private suspend fun importPacks(
        saf: SafTree,
        found: MutableList<String>,
        missing: MutableList<String>,
        failed: MutableList<String>,
    ) {
        val names = listOf(
            "renju_stats.pack", "renju_games.pack",
            ExplorerPackStore.NAMES, ExplorerPackStore.EVALS,
        )
        val hits = withContext(io) { names.associateWith { saf.fileUri(it) } }
        // Only the packs are worth naming as missing: the two tables ship in the
        // APK, so their absence from a transfer folder is the normal case.
        missing += names.filter { it.endsWith(".pack") && hits[it] == null }
        val uris = hits.values.filterNotNull()
        if (uris.isEmpty()) return
        packStore.import(uris)
            .onSuccess { found += it.substringBefore(" 불러옴").ifEmpty { "팩" } }
            .onFailure { failed += "팩: ${it.message}" }
    }

    /**
     * `freq_data.json` sits in `research/` on the PC and at the top of the
     * transfer folder; both layouts are the same data, so both are accepted
     * rather than making the user reshape a folder they copied.
     */
    private suspend fun importFreq(
        saf: SafTree,
        found: MutableList<String>,
        missing: MutableList<String>,
        failed: MutableList<String>,
    ) {
        val uri = withContext(io) {
            saf.fileUri(FREQ)
                ?: saf.folder("research")?.let { saf.fileUri(it, FREQ) }
        }
        if (uri == null) {
            missing += FREQ
            return
        }
        freqStore.import(uri, ownGrant = false)
            .onSuccess { found += "실전 데이터 %,d판".format(it.gameCount) }
            .onFailure { failed += "$FREQ: ${it.message}" }
    }

    private companion object {
        const val FREQ = "freq_data.json"
    }
}
