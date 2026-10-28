package dev.gomoku.rapfidroid.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.BuildConfig
import dev.gomoku.rapfidroid.core.designsystem.component.LocalUiPrefs
import dev.gomoku.rapfidroid.core.designsystem.component.rememberCalmFling
import dev.gomoku.rapfidroid.core.designsystem.theme.tabular
import dev.gomoku.rapfidroid.core.i18n.AppLanguage
import dev.gomoku.rapfidroid.core.i18n.appLanguage
import dev.gomoku.rapfidroid.core.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What changed in this version, told once: the first time the app opens after
 * an update, in a window like Settings ▸ 정보 (user request, 2026-10-29). A
 * fresh install says nothing — there is no «before» to compare with — and the
 * same notes stay one tap away under 정보.
 *
 * The notes are `assets/whatsnew/<versionCode>.txt`, three languages in one
 * file (see the file's own header). A release that ships none simply shows
 * nothing; `WhatsNewTest` fails until the current versionCode has its file, so
 * that is a choice rather than an accident.
 */
object WhatsNew {
    /** The last versionCode whose notes were seen (or skipped); 0 = never. */
    const val SEEN_PREF = "whatsnew.seen"

    /**
     * Whether to show the notes now. Nothing seen yet means either a fresh
     * install or an update from a version older than this feature; Android's
     * install and update times tell the two apart.
     */
    fun shouldShow(seen: Int, current: Int, freshInstall: Boolean): Boolean =
        seen < current && !(seen == 0 && freshInstall)

    /** `[ko]` / `[en]` / `[zh]` sections of `- ` items; `#` lines and anything else are ignored. */
    fun parse(text: String): Map<AppLanguage, List<String>> {
        val out = LinkedHashMap<AppLanguage, MutableList<String>>()
        var section: AppLanguage? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line == "[ko]" -> section = AppLanguage.KOREAN
                line == "[en]" -> section = AppLanguage.ENGLISH
                line == "[zh]" -> section = AppLanguage.CHINESE
                line.startsWith("- ") && section != null ->
                    out.getOrPut(section) { mutableListOf() } += line.removePrefix("- ").trim()
            }
        }
        return out
    }

    /** This version's items in the app's language (English if that one is missing), or none. */
    fun load(context: Context, versionCode: Int = BuildConfig.VERSION_CODE): List<String> {
        val text = runCatching {
            context.assets.open("whatsnew/$versionCode.txt").use { it.readBytes().decodeToString() }
        }.getOrNull() ?: return emptyList()
        val notes = parse(text)
        return notes[appLanguage()] ?: notes[AppLanguage.ENGLISH].orEmpty()
    }

    private fun freshInstall(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    /** Decides off the main thread; null = nothing to show (and the version is marked seen). */
    internal suspend fun pending(context: Context, seen: Int): List<String>? = withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_CODE
        if (!shouldShow(seen, current, freshInstall(context))) return@withContext null
        load(context, current).ifEmpty { null }
    }
}

/**
 * Shows [WhatsNewDialog] once after an update. Sits beside the app's root, so
 * it opens over whichever screen the app starts on.
 */
@Composable
fun WhatsNewOnUpdate() {
    val prefs = LocalUiPrefs.current ?: return
    val context = LocalContext.current
    val seen = remember(prefs) { prefs.int(WhatsNew.SEEN_PREF, 0) }
    var items by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(seen) {
        if (seen.value >= BuildConfig.VERSION_CODE) return@LaunchedEffect
        items = WhatsNew.pending(context, seen.value)
        if (items == null) seen.set(BuildConfig.VERSION_CODE)
    }
    items?.let { notes ->
        WhatsNewDialog(notes) {
            seen.set(BuildConfig.VERSION_CODE)
            items = null
        }
    }
}

/** This version's notes, in the same frame as [AboutDialog]: name and version, then the items. */
@Composable
fun WhatsNewDialog(items: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("확인", "OK", "确定")) } },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("새로워진 점", "What's new", "新功能"), style = MaterialTheme.typography.titleLarge)
                Text(
                    "RapfiDroid ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}
