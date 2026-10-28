package dev.gomoku.zenyth.feature.settings

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.gomoku.zenyth.BuildConfig
import dev.gomoku.zenyth.core.designsystem.component.LocalUiPrefs
import dev.gomoku.zenyth.core.designsystem.component.ZenythMark
import dev.gomoku.zenyth.core.designsystem.component.rememberCalmFling
import dev.gomoku.zenyth.core.i18n.AppLanguage
import dev.gomoku.zenyth.core.i18n.appLanguage
import dev.gomoku.zenyth.core.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One version's notes: a sentence of welcome, then the changes in titled groups.
 * Groups without a title are allowed (a file with only `- ` items is one group).
 */
data class ReleaseNotes(val intro: String? = null, val groups: List<Group> = emptyList()) {
    data class Group(val title: String?, val items: List<String>)

    val isEmpty: Boolean get() = intro == null && groups.all { it.items.isEmpty() }
}

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

    /** Every release's full notes, for the window's second button. */
    const val RELEASES_URL = "https://github.com/lowqen/Zenyth/releases"

    /**
     * Whether to show the notes now. Nothing seen yet means either a fresh
     * install or an update from a version older than this feature; Android's
     * install and update times tell the two apart.
     */
    fun shouldShow(seen: Int, current: Int, freshInstall: Boolean): Boolean =
        seen < current && !(seen == 0 && freshInstall)

    /**
     * `[ko]` / `[en]` / `[zh]` sections. Inside one: `> ` is the welcome
     * sentence, `## ` starts a group, `- ` is an item. `#` comments and
     * anything else are ignored.
     */
    fun parse(text: String): Map<AppLanguage, ReleaseNotes> {
        class Draft {
            var intro: String? = null
            val groups = mutableListOf<Pair<String?, MutableList<String>>>()
        }
        val drafts = LinkedHashMap<AppLanguage, Draft>()
        var section: Draft? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val language = when (line) {
                "[ko]" -> AppLanguage.KOREAN
                "[en]" -> AppLanguage.ENGLISH
                "[zh]" -> AppLanguage.CHINESE
                else -> null
            }
            if (language != null) {
                section = drafts.getOrPut(language) { Draft() }
                continue
            }
            val d = section ?: continue
            when {
                line.startsWith("> ") -> d.intro = listOfNotNull(d.intro, line.removePrefix("> ").trim()).joinToString(" ")
                line.startsWith("## ") -> d.groups += line.removePrefix("## ").trim() to mutableListOf()
                line.startsWith("- ") -> {
                    if (d.groups.isEmpty()) d.groups += null to mutableListOf()
                    d.groups.last().second += line.removePrefix("- ").trim()
                }
            }
        }
        return drafts.mapValues { (_, d) -> ReleaseNotes(d.intro, d.groups.map { ReleaseNotes.Group(it.first, it.second) }) }
    }

    /** This version's notes in the app's language (English if that one is missing), or empty ones. */
    fun load(context: Context, versionCode: Int = BuildConfig.VERSION_CODE): ReleaseNotes {
        val text = runCatching {
            context.assets.open("whatsnew/$versionCode.txt").use { it.readBytes().decodeToString() }
        }.getOrNull() ?: return ReleaseNotes()
        val notes = parse(text)
        return notes[appLanguage()] ?: notes[AppLanguage.ENGLISH] ?: ReleaseNotes()
    }

    private fun freshInstall(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    /** Decides off the main thread; null = nothing to show (and the version is marked seen). */
    internal suspend fun pending(context: Context, seen: Int): ReleaseNotes? = withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_CODE
        if (!shouldShow(seen, current, freshInstall(context))) return@withContext null
        load(context, current).takeUnless { it.isEmpty }
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
    var notes by remember { mutableStateOf<ReleaseNotes?>(null) }
    LaunchedEffect(seen) {
        if (seen.value >= BuildConfig.VERSION_CODE) return@LaunchedEffect
        notes = WhatsNew.pending(context, seen.value)
        if (notes == null) seen.set(BuildConfig.VERSION_CODE)
    }
    notes?.let {
        WhatsNewDialog(it) {
            seen.set(BuildConfig.VERSION_CODE)
            notes = null
        }
    }
}

/** [WhatsNewCard] in a dialog window. */
@Composable
fun WhatsNewDialog(notes: ReleaseNotes, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        WhatsNewCard(notes, onDismiss, Modifier.padding(horizontal = 20.dp, vertical = 32.dp))
    }
}

/**
 * The window itself, after Mornye's welcome (user request, 2026-10-29) and
 * made easier to read: the app's mark where Mornye has a red «!», a two-line
 * welcome with the version, ✕ to close; then a sentence of welcome and the
 * changes in short titled groups instead of one block of prose; a fade where
 * the text runs on below the fold, so it is plain there is more; and one
 * filled pill to start, with the full release history beside it.
 */
@Composable
fun WhatsNewCard(notes: ReleaseNotes, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val scroll = rememberScrollState()
    Surface(
        modifier = modifier.widthIn(max = 480.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ZenythMark(Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(start = 16.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        tr("새로워진 점", "What's new", "新功能"),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                    Text(
                        tr(
                            "Zenyth ${BuildConfig.VERSION_NAME}에\n오신 것을 환영합니다",
                            "Welcome to\nZenyth ${BuildConfig.VERSION_NAME}",
                            "欢迎使用\nZenyth ${BuildConfig.VERSION_NAME}",
                        ),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = tr("닫기", "Close", "关闭"))
                }
            }
            Box(Modifier.padding(top = 18.dp, end = 12.dp).weight(1f, fill = false)) {
                NotesBody(notes, scroll)
                // There is more below: the last lines fade into the card.
                if (scroll.canScrollForward) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, scheme.surfaceContainerHigh))),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { runCatching { uri.openUri(WhatsNew.RELEASES_URL) } }) {
                    Text(tr("전체 변경 기록", "All releases", "全部更新记录"))
                }
                Button(onClick = onDismiss, modifier = Modifier.height(48.dp), shape = CircleShape) {
                    Text(tr("시작하기", "Get started", "开始使用"), modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun NotesBody(notes: ReleaseNotes, scroll: ScrollState) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.heightIn(max = 440.dp).verticalScroll(scroll, flingBehavior = rememberCalmFling()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        notes.intro?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        notes.groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                group.title?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.primary,
                    )
                }
                group.items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // A small stone, sitting on the first line.
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurfaceVariant.copy(alpha = 0.6f)),
                        )
                        Text(item, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                    }
                }
            }
        }
        // Room for the fade to pass over before the last line.
        Box(Modifier.height(4.dp))
    }
}
