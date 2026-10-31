package dev.gomoku.zenyth.feature.data

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.zenyth.core.common.IoDispatcher
import dev.gomoku.zenyth.core.designsystem.component.Button
import dev.gomoku.zenyth.core.designsystem.component.WavyProgress
import dev.gomoku.zenyth.core.designsystem.component.ZenythSheet
import dev.gomoku.zenyth.core.designsystem.theme.MOTION_PANEL
import dev.gomoku.zenyth.core.designsystem.theme.MOTION_QUICK
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.PackBuildState
import dev.gomoku.zenyth.data.explorer.ExplorerPackStore
import dev.gomoku.zenyth.data.prefs.UiPrefs
import dev.gomoku.zenyth.data.rankings.FreqStore
import dev.gomoku.zenyth.data.rif.PackBuildManager
import dev.gomoku.zenyth.feature.onboarding.DoneCard
import dev.gomoku.zenyth.feature.onboarding.FeatureRow
import dev.gomoku.zenyth.feature.onboarding.HeroIcon
import dev.gomoku.zenyth.feature.onboarding.stepBodyStyle
import dev.gomoku.zenyth.feature.onboarding.stepTitleStyle
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Leading people to the RenjuNet game data from the research tabs that need it
 * (docs/보드-연구-개선-계획.md, R2). The openings' frequencies, scores and
 * games and the rankings' real-game tables are empty without it, and the old
 * card — one line and a button that opened a dialog — sat mid-list where it
 * was easy to scroll past.
 *
 * Three forms: a full card at the top of the tab ([GameDataCard]); a slim
 * banner it folds into on «나중에» ([GameDataBanner]); and, once per install,
 * a sheet the first time the openings or rankings are opened without data
 * ([GameDataSheet]). The move-order tab gets none: it never uses the data.
 */
object GameDataPrompt {
    enum class Form { NONE, CARD, BANNER }

    /**
     * Which form shows. [missing] is null until the disk has been looked at, so
     * nothing flashes at start-up. A build in progress, or one that has just
     * ended, keeps the card up — its progress and its result live there — and
     * folding it away only ever applies to the waiting card.
     */
    fun form(missing: Boolean?, build: PackBuildState, collapsed: Boolean): Form = when {
        build is PackBuildState.Running || build is PackBuildState.Done || build is PackBuildState.Failed -> Form.CARD
        missing != true -> Form.NONE
        collapsed -> Form.BANNER
        else -> Form.CARD
    }

    /** The sheet: once, and only when there is known to be nothing. */
    fun sheet(missing: Boolean?, build: PackBuildState, seen: Boolean): Boolean =
        missing == true && build is PackBuildState.Idle && !seen

    const val COLLAPSED_PREF = "data.prompt.collapsed"
    const val SHEET_PREF = "data.prompt.sheet"
}

/**
 * The prompt's state, shared by the research screen (the sheet) and its tabs
 * (the card): one view model on the research tab's back-stack entry.
 */
@HiltViewModel
class GameDataViewModel @Inject constructor(
    private val packs: ExplorerPackStore,
    private val freq: FreqStore,
    private val manager: PackBuildManager,
    ui: UiPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    /**
     * Whether either half of the data is missing: null until known. Looked up
     * on disk rather than in the loaded stores — those stay empty until each
     * tab loads them, which would read as «no data» — and again whenever a
     * store or the build changes (a build adopted, the data cleared).
     */
    val missing: StateFlow<Boolean?> = combine(packs.packs, freq.bundle, manager.state) { _, _, _ -> }
        .map { withContext(io) { !(packs.onDisk && freq.onDisk) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val build: StateFlow<PackBuildState> = manager.state

    val collapsed: UiPrefs.BoolPref = ui.bool(GameDataPrompt.COLLAPSED_PREF, false)
    val sheetSeen: UiPrefs.BoolPref = ui.bool(GameDataPrompt.SHEET_PREF, false)

    fun onFilePicked(uri: Uri?) {
        if (uri != null) manager.start(uri)
    }

    fun onCancel() = manager.cancel()

    fun onAcknowledge() = manager.acknowledge()
}

/**
 * Which form the prompt takes now — for a list that should add the slot as an
 * item only when there is something in it (an empty item still takes a gap).
 */
@Composable
fun gameDataForm(viewModel: GameDataViewModel = hiltViewModel()): GameDataPrompt.Form {
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val build by viewModel.build.collectAsStateWithLifecycle()
    val collapsed by viewModel.collapsed.state.collectAsStateWithLifecycle()
    return GameDataPrompt.form(missing, build, collapsed)
}

/** The card or the banner, whichever [GameDataPrompt.form] says; nothing otherwise. */
@Composable
fun GameDataPromptSlot(modifier: Modifier = Modifier, viewModel: GameDataViewModel = hiltViewModel()) {
    val build by viewModel.build.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onFilePicked(it) }
    when (gameDataForm(viewModel)) {
        GameDataPrompt.Form.NONE -> Unit
        GameDataPrompt.Form.BANNER -> GameDataBanner(onExpand = { viewModel.collapsed.set(false) }, modifier = modifier)
        GameDataPrompt.Form.CARD -> GameDataCard(
            build = build,
            onPick = { picker.launch(arrayOf("*/*")) },
            onOpenSite = { openRenjuNet(context) },
            onLater = { viewModel.collapsed.set(true) },
            onCancel = viewModel::onCancel,
            onAcknowledge = viewModel::onAcknowledge,
            modifier = modifier,
        )
    }
}

/**
 * The full card, in the first-run setup's grammar: the icon in its circle, a
 * heavy title, a grey sentence, three things the data brings, then one thing
 * to do. The steps that happen elsewhere (the download is the user's, in the
 * browser — the licence keeps the app from fetching it) are one numbered line,
 * not a dialog. While the phone builds, the same place shows how far.
 */
@Composable
internal fun GameDataCard(
    build: PackBuildState,
    onPick: () -> Unit,
    onOpenSite: () -> Unit,
    onLater: () -> Unit,
    onCancel: () -> Unit,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainer) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroIcon(Icons.Outlined.QueryStats)
            Spacer(Modifier.height(20.dp))
            Text(
                tr("실전 대국 데이터를 추가하세요", "Add the real-game data", "添加实战对局数据"),
                style = stepTitleStyle(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                tr(
                    "RenjuNet 의 실제 대국 기록으로 폰 안에서 만듭니다. 기록은 이 기기 밖으로 나가지 않습니다",
                    "Built on this phone from RenjuNet's records of real games. They never leave it",
                    "用 RenjuNet 的实战对局记录在本机生成，记录不会离开本设备",
                ),
                style = stepBodyStyle(),
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FeatureRow(
                    Icons.Outlined.Explore,
                    tr("다음 수마다 실전 빈도와 승률", "Real-game frequency and score for every next move", "每一步后续着法的实战频率与胜率"),
                    title = tr("오프닝", "Openings", "开局"),
                )
                FeatureRow(
                    Icons.Outlined.Leaderboard,
                    tr("26주형과 5수 모양의 실전 승률 · 빈도", "Real-game scores of the 26 openings and the 5th-move shapes", "26 种开局与第 5 手形状的实战胜率 · 频率"),
                    title = tr("랭킹", "Rankings", "排行"),
                )
                FeatureRow(
                    Icons.Outlined.History,
                    tr("이 국면을 지난 실제 대국을 보드에 올리기", "Put the real games through this position on the board", "把经过此局面的实战对局放到棋盘上"),
                    title = tr("실제 기보", "Real games", "实战棋谱"),
                )
            }
            Spacer(Modifier.height(24.dp))
            AnimatedContent(
                targetState = build,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(MOTION_PANEL)) togetherWith fadeOut(tween(MOTION_QUICK)) },
                label = "gameDataState",
            ) { state ->
                when (state) {
                    is PackBuildState.Running -> Building(state, onCancel)
                    is PackBuildState.Done -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DoneCard(
                            tr(
                                "데이터가 준비됐습니다 · 대국 %,d판",
                                "The data is ready · %,d games",
                                "数据已就绪 · 对局 %,d 盘",
                            ).format(state.games),
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onAcknowledge) { Text(tr("확인", "OK", "确定")) }
                    }
                    is PackBuildState.Failed -> Waiting(
                        onPick, onOpenSite, onLater,
                        failure = tr("만들지 못했습니다: ", "The build did not finish: ", "未能生成：") + state.message,
                    )
                    PackBuildState.Idle -> Waiting(onPick, onOpenSite, onLater)
                }
            }
        }
    }
}

/** The way in: how to get the file, the one filled pill, and the two quiet ways out. */
@Composable
private fun Waiting(onPick: () -> Unit, onOpenSite: () -> Unit, onLater: () -> Unit, failure: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        failure?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
        Text(
            tr(
                "① renju.net 에서 «XML» 기보 파일(약 43MB)을 받고 ② 아래에서 고르면, 폰이 몇 초 만에 만듭니다",
                "① Download the «XML» game file from renju.net (about 43 MB), ② pick it below, and the phone builds the data in seconds",
                "① 从 renju.net 下载 «XML» 棋谱文件(约 43MB)，② 在下方选择，手机几秒内即可生成",
            ),
            style = MaterialTheme.typography.bodyMedium.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(tr("받은 파일 고르기", "Pick the file", "选择下载的文件"), style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpenSite) { Text(tr("renju.net 열기", "Open renju.net", "打开 renju.net")) }
            TextButton(onClick = onLater) {
                Text(tr("나중에", "Later", "以后再说"), color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** A build under way: its phase, the wave, and that it goes on without this screen. */
@Composable
private fun Building(state: PackBuildState.Running, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val fraction = state.fraction
        Text(
            state.phase.label + (fraction?.let { " · ${(it * 100).toInt()}%" } ?: ""),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(10.dp))
        WavyProgress(fraction = fraction ?: 0f, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            tr(
                "앱을 나가도 계속되고, 알림에서도 볼 수 있습니다",
                "It goes on if you leave the app, and shows in a notification",
                "离开应用也会继续，也可以在通知中查看",
            ),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onCancel) { Text(tr("중지", "Stop", "停止")) }
    }
}

/** The card folded away: one line that says what is off, and the way back to it. */
@Composable
internal fun GameDataBanner(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onExpand,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.QueryStats, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("실전 대국 데이터가 없습니다", "No real-game data", "没有实战对局数据"), style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("빈도 · 승률 · 실제 기보가 비어 있습니다", "Frequencies, scores and games are empty", "频率 · 胜率 · 实战棋谱为空"),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onExpand) { Text(tr("추가", "Add", "添加")) }
        }
    }
}

/**
 * Once per install, the first time the openings or the rankings open with no
 * data: the same offer, short, in a sheet. Any way out of it counts as seen.
 */
@Composable
fun GameDataSheetOnce(viewModel: GameDataViewModel = hiltViewModel()) {
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val build by viewModel.build.collectAsStateWithLifecycle()
    val seen by viewModel.sheetSeen.state.collectAsStateWithLifecycle()
    if (!GameDataPrompt.sheet(missing, build, seen)) return
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onFilePicked(it) }
    GameDataSheet(
        onPick = {
            viewModel.sheetSeen.set(true)
            picker.launch(arrayOf("*/*"))
        },
        onOpenSite = { openRenjuNet(context) },
        onDismiss = { viewModel.sheetSeen.set(true) },
    )
}

@Composable
private fun GameDataSheet(onPick: () -> Unit, onOpenSite: () -> Unit, onDismiss: () -> Unit) {
    ZenythSheet(
        title = tr("실전 대국 데이터를 추가할까요?", "Add the real-game data?", "要添加实战对局数据吗？"),
        onDismiss = onDismiss,
        dismiss = { TextButton(onClick = onDismiss) { Text(tr("나중에", "Later", "以后再说")) } },
        confirm = { Button(onClick = onPick) { Text(tr("받은 파일 고르기", "Pick the file", "选择下载的文件")) } },
    ) {
        Text(
            tr(
                "오프닝의 실전 빈도 · 승률 · 실제 기보와 랭킹의 실전 표는 RenjuNet 대국 데이터로 채워집니다. renju.net 에서 «XML» 파일을 받아 고르면 폰이 몇 초 만에 만듭니다.",
                "The openings' real-game frequencies, scores and games and the rankings' tables come from RenjuNet's game data. Download the «XML» file from renju.net and pick it, and the phone builds it in seconds.",
                "开局的实战频率、胜率、实战棋谱以及排行的实战表格都来自 RenjuNet 对局数据。从 renju.net 下载 «XML» 文件并选择它，手机几秒内即可生成。",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenSite) { Text(tr("renju.net 열기", "Open renju.net", "打开 renju.net")) }
    }
}

private fun openRenjuNet(context: android.content.Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RENJU_NET_GAMES))) }
}
