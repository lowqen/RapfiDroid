package dev.gomoku.zenyth.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.zenyth.core.designsystem.component.FilledTonalButton
import dev.gomoku.zenyth.core.designsystem.component.RoundBackButton
import dev.gomoku.zenyth.core.designsystem.component.ZenythMark
import dev.gomoku.zenyth.core.designsystem.component.ZenythSheet
import dev.gomoku.zenyth.core.designsystem.component.rememberCalmFling
import dev.gomoku.zenyth.core.designsystem.component.rememberZenythHaptics
import dev.gomoku.zenyth.core.designsystem.theme.LocalReducedMotion
import dev.gomoku.zenyth.core.designsystem.theme.MOTION_PANEL
import dev.gomoku.zenyth.core.designsystem.theme.MOTION_QUICK
import dev.gomoku.zenyth.core.i18n.AppLanguage
import dev.gomoku.zenyth.core.i18n.AppLocales
import dev.gomoku.zenyth.core.i18n.appLanguage
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.data.prefs.UiPrefs

/**
 * What the setup asks, in order. Two screens at most (decided 2026-10-31: the
 * eleven of the first plan were too many before the board): who the app is —
 * with the language in a corner of it — and, where Android asks for it, the
 * notification permission, the one thing that cannot wait for the user to
 * meet it. Theme, data and the tour stay where they are already found.
 */
enum class SetupStep { Welcome, Notifications }

/** Android 12 and below need no notification permission: the welcome alone. */
fun setupSteps(sdk: Int = Build.VERSION.SDK_INT): List<SetupStep> = buildList {
    add(SetupStep.Welcome)
    if (sdk >= Build.VERSION_CODES.TIRAMISU) add(SetupStep.Notifications)
}

/**
 * The setup, holding the state: the step (in [stepPref], so a language change
 * or a closed app resumes there), the permission and the language sheet.
 */
@Composable
fun SetupScreen(stepPref: UiPrefs.IntPref, onFinish: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberZenythHaptics()
    val steps = remember { setupSteps() }
    val stored by stepPref.state.collectAsStateWithLifecycle()
    val index = stored.coerceIn(0, steps.lastIndex)

    var notificationsOn by remember { mutableStateOf(notificationsAllowed(context)) }
    // Asked twice and refused: Android shows no window any more, so the button
    // leads to the app's notification settings instead.
    var blocked by rememberSaveable { mutableStateOf(false) }
    // Back from the system settings, or from the shade: look again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsOn = notificationsAllowed(context) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted
        if (granted) {
            haptics.decided()
        } else {
            val activity = context.findActivity()
            blocked = activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Not saveable: choosing a language recreates the activity, and the welcome
    // should then be seen in it, not the list again.
    var choosingLanguage by remember { mutableStateOf(false) }

    BackHandler(enabled = index > 0) { stepPref.set(index - 1) }

    SetupContent(
        steps = steps,
        index = index,
        languageLabel = if (AppLocales.supported(context)) languageOnScreen() else null,
        notificationsOn = notificationsOn,
        notificationsBlocked = blocked,
        onLanguage = { choosingLanguage = true },
        onBack = { stepPref.set(index - 1) },
        onNext = { if (index < steps.lastIndex) stepPref.set(index + 1) else onFinish() },
        onAskNotifications = {
            if (blocked) {
                openNotificationSettings(context)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                request.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        // The last step: skipping it is starting.
        onSkipNotifications = onFinish,
    )

    if (choosingLanguage) {
        LanguageSheet(
            current = AppLocales.current(context),
            onChoose = { tag ->
                choosingLanguage = false
                if (tag != AppLocales.current(context)) AppLocales.set(context, tag)
            },
            onDismiss = { choosingLanguage = false },
        )
    }
}

/**
 * The setup as it looks, with nothing held: the top row (back, or the language
 * on the first step; the ring), the step sliding in, the button in the corner.
 */
@Composable
internal fun SetupContent(
    steps: List<SetupStep>,
    index: Int,
    languageLabel: String?,
    notificationsOn: Boolean,
    notificationsBlocked: Boolean,
    onLanguage: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onAskNotifications: () -> Unit,
    onSkipNotifications: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val step = steps[index]
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        when {
                            index > 0 -> RoundBackButton(onBack)
                            languageLabel != null -> LanguagePill(languageLabel, onLanguage)
                        }
                    }
                    if (steps.size > 1) StepProgressRing(index + 1, steps.size)
                }
                AnimatedContent(
                    targetState = index,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(tween(MOTION_QUICK)) togetherWith fadeOut(tween(MOTION_QUICK))
                        } else {
                            // Forward comes in from the right, back from the left, a quarter width.
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(tween(MOTION_PANEL * 2)) { dir * it / 4 } + fadeIn(tween(MOTION_PANEL))) togetherWith
                                (slideOutHorizontally(tween(MOTION_PANEL * 2)) { -dir * it / 4 } + fadeOut(tween(MOTION_QUICK)))
                        }
                    },
                    label = "setupStep",
                ) { i ->
                    StepPage {
                        when (steps[i]) {
                            SetupStep.Welcome -> WelcomeStep()
                            SetupStep.Notifications -> NotificationsStep(
                                on = notificationsOn,
                                blocked = notificationsBlocked,
                                onAsk = onAskNotifications,
                                onSkip = onSkipNotifications,
                            )
                        }
                    }
                }
            }
            OnboardingFab(
                last = index == steps.lastIndex,
                // The permission step waits for an answer (Mornye's); «건너뛰기» is one.
                enabled = step != SetupStep.Notifications || notificationsOn,
                onClick = onNext,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/**
 * One step's page: centred when it is short, from the top and scrolling when
 * it is not (a large font, a phone on its side), with room at the bottom for
 * the button that floats there. At most 480dp wide on a tablet.
 */
@Composable
private fun StepPage(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .heightIn(min = minHeight)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

/**
 * Mornye's first step (its mark, its name, a line) joined with its tour's
 * first page (three things the app does), so one screen says both.
 */
@Composable
private fun WelcomeStep() {
    ZenythMark(Modifier.size(88.dp))
    Spacer(Modifier.height(28.dp))
    Text(
        tr("Zenyth에 오신 것을\n환영합니다", "Welcome to Zenyth", "欢迎使用 Zenyth"),
        style = welcomeTitleStyle(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        tr(
            "폰 안에서 도는 진짜 오목 · 렌주 엔진",
            "A real gomoku & renju engine, on your phone",
            "在手机里运行的真正五子棋 · 连珠引擎",
        ),
        style = stepBodyStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(36.dp))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FeatureRow(
            Icons.Filled.OfflineBolt,
            tr(
                "엔진이 폰 안에서 돌아 서버 · 계정 · 인터넷 없이 동작합니다",
                "The engine runs on the phone — no server, account or internet",
                "引擎在手机里运行，无需服务器、账号或网络",
            ),
        )
        FeatureRow(
            Icons.Outlined.Block,
            tr(
                "렌주 금수(삼삼 · 사사 · 장련)를 엔진이 직접 판정합니다",
                "The engine itself judges renju fouls: 3-3, 4-4 and overlines",
                "连珠禁手（三三 · 四四 · 长连）由引擎直接判定",
            ),
        )
        FeatureRow(
            Icons.Filled.Inventory2,
            tr(
                "엔진이 배운 국면을 남겨 두고 다음에 이어 씁니다",
                "What the engine learns is kept and picked up next time",
                "引擎学到的局面会保存下来，下次接着用",
            ),
        )
    }
}

/**
 * The permission, asked with its reason on screen — it used to be Android's
 * bare window as the app opened. Granted, the two buttons give way to a card
 * that says so.
 */
@Composable
private fun NotificationsStep(on: Boolean, blocked: Boolean, onAsk: () -> Unit, onSkip: () -> Unit) {
    HeroIcon(Icons.Filled.Notifications)
    Spacer(Modifier.height(28.dp))
    Text(
        tr("알림 켜기", "Turn on notifications", "开启通知"),
        style = stepTitleStyle(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        tr(
            "앱을 나가도 엔진이 분석을 이어 가는 동안 알림에 그 상태가 보입니다. 기보 데이터를 만드는 진행도 알림으로 볼 수 있습니다",
            "While the engine keeps analysing with the app closed, its state shows in a notification — and so does the progress of building game data",
            "退出应用后引擎继续分析时，通知里会显示它的状态；生成棋谱数据的进度也会显示在通知里",
        ),
        style = stepBodyStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    AnimatedContent(
        targetState = on,
        transitionSpec = { fadeIn(tween(MOTION_PANEL)) togetherWith fadeOut(tween(MOTION_QUICK)) using SizeTransform(clip = false) },
        contentAlignment = Alignment.Center,
        label = "notificationsAnswer",
    ) { granted ->
        if (granted) {
            DoneCard(tr("알림이 켜졌습니다!", "Notifications are on!", "通知已开启！"))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAsk) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (blocked) tr("설정에서 켜기", "Turn on in Settings", "在设置中开启")
                        else tr("알림 켜기", "Turn on", "开启通知"),
                    )
                }
                TextButton(onClick = onSkip) { Text(tr("건너뛰기", "Skip", "跳过")) }
            }
        }
    }
}

/** The language list in a sheet, as Settings ▸ 언어 has it. */
@Composable
private fun LanguageSheet(current: String, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    ZenythSheet(
        title = tr("언어 선택", "Language", "选择语言"),
        onDismiss = onDismiss,
        confirm = { TextButton(onClick = onDismiss) { Text(tr("닫기", "Close", "关闭")) } },
    ) {
        LanguageList(current, onChoose)
    }
}

/** The sheet's body, apart so it can be drawn on its own. */
@Composable
internal fun LanguageList(current: String, onChoose: (String) -> Unit) {
    Text(
        tr(
            "앱에서 쓸 언어를 고르세요. 나중에 설정에서 바꿀 수 있습니다",
            "Choose the app's language. You can change it later in Settings",
            "选择应用使用的语言。之后可在设置中更改",
        ),
        style = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordBreaks),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppLocales.choices().forEach { choice ->
            LanguageRow(choice, selected = choice.tag == current, onClick = { onChoose(choice.tag) })
        }
    }
}

/** The pill's word: the language the app is showing, whichever way it got there. */
private fun languageOnScreen(): String = when (appLanguage()) {
    AppLanguage.KOREAN -> "한국어"
    AppLanguage.ENGLISH -> "English"
    AppLanguage.CHINESE -> "中文"
}

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
