package dev.gomoku.zenyth.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gomoku.zenyth.core.designsystem.component.Button
import dev.gomoku.zenyth.core.designsystem.component.EngineChip
import dev.gomoku.zenyth.core.designsystem.component.MiniBoard
import dev.gomoku.zenyth.core.designsystem.component.PillBadge
import dev.gomoku.zenyth.core.designsystem.component.RoundBackButton
import dev.gomoku.zenyth.core.designsystem.component.TileShape
import dev.gomoku.zenyth.core.designsystem.component.rememberCalmFling
import dev.gomoku.zenyth.core.designsystem.theme.LocalReducedMotion
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.ConnectionState
import dev.gomoku.zenyth.core.model.Move
import kotlinx.coroutines.launch

/**
 * Opens the tour over the whole app. Provided by [OnboardingGate]; null where
 * there is no gate (a preview, a screenshot), and the way in is then not shown.
 */
val LocalOpenTour: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

/** The four places the tour shows, in the bottom bar's order. */
enum class TourPage { Board, Research, Joseki, Engine }

/**
 * The tour of the four places (docs/온보딩-계획.md, O3), as Mornye's tour looks:
 * back and «건너뛰기» on top, a page that slides under the finger, the dots,
 * one full-width pill. Opened from Settings ▸ 정보, never on its own — the
 * first run is the two setup screens and then the board.
 */
@Composable
fun TourScreen(onFinish: () -> Unit) {
    val pages = TourPage.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    fun go(page: Int) {
        scope.launch { if (reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    }
    // Back walks the pages back, then leaves.
    BackHandler { if (pager.currentPage > 0) go(pager.currentPage - 1) else onFinish() }
    TourContent(
        pages = pages,
        pager = pager,
        onBack = { go(pager.currentPage - 1) },
        onNext = { if (pager.currentPage < pages.lastIndex) go(pager.currentPage + 1) else onFinish() },
        onSelect = ::go,
        onSkip = onFinish,
    )
}

/** The tour as it looks; the pager's state is the only thing it holds. */
@Composable
internal fun TourContent(
    pages: List<TourPage>,
    pager: PagerState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    val current = pager.currentPage
    val last = current == pages.lastIndex
    // Opaque and full-screen: nothing under it — the board, the bars — takes a touch.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (current > 0) RoundBackButton(onBack)
                }
                if (!last) {
                    TextButton(onClick = onSkip) {
                        Text(
                            tr("건너뛰기", "Skip", "跳过"),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                TourPageBody(pages[page])
            }
            PageDots(
                count = pages.size,
                current = current,
                onSelect = onSelect,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 20.dp),
            )
            Button(
                onClick = onNext,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                    .heightIn(min = 56.dp),
            ) {
                Text(
                    if (last) tr("완료", "Done", "完成") else tr("다음", "Next", "下一步"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/** One page: the icon, the title, a sentence, then what is there to see. */
@Composable
private fun TourPageBody(page: TourPage) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (icon, title, body) = page.head()
            HeroIcon(icon)
            Spacer(Modifier.height(28.dp))
            Text(title, style = tourTitleStyle(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                body,
                style = stepBodyStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            when (page) {
                TourPage.Board -> BoardPicture()
                TourPage.Research -> Rows(
                    Triple(Icons.Outlined.History, tr("게임 리뷰", "Game review", "对局复盘"),
                        tr("끝난 판을 되짚으며 평가가 뒤집힌 지점을 짚어 줍니다", "Walks a finished game back and marks where the evaluation turned", "回顾已结束的对局，指出评估翻转的地方")),
                    Triple(Icons.Outlined.Explore, tr("오프닝 익스플로러", "Opening explorer", "开局浏览器"),
                        tr("주형 이름 249개와 흑 5수 유불리를 판 위에 보여 줍니다", "249 opening names and the 5th-move verdicts, on the board", "在棋盘上显示 249 个开局名称和黑第 5 手优劣")),
                    Triple(Icons.Outlined.Verified, tr("국면 증명 · 랭킹", "Proof · rankings", "局面证明 · 排行"),
                        tr("평가가 아니라 증명을 시키고, 26주형을 견줘 봅니다", "Asks for a proof, not an evaluation, and compares the 26 openings", "让引擎证明而不只是评估，并比较 26 种开局")),
                )
                TourPage.Joseki -> Rows(
                    Triple(Icons.Outlined.School, tr("공식 커리큘럼", "Curriculum", "官方课程"),
                        tr("레슨마다 살펴보고, 수순을 직접 맞히며 익힙니다", "Look through each lesson, then learn it by finding the moves", "逐课浏览，再亲手找出着法来学习")),
                    Triple(Icons.Outlined.Bookmarks, tr("내 정석", "My joseki", "我的定式"),
                        tr("갈래 · 평가 기호 · 메모를 달고, 대칭까지 찾습니다", "Branches, marks and notes, found in any symmetry", "添加分支、评价符号和笔记，按对称查找")),
                    Triple(Icons.Outlined.Link, tr("레슨 링크", "Lesson links", "课程链接"),
                        tr("Zenyth Web 의 레슨 링크를 누르면 앱에서 열립니다", "A Zenyth Web lesson link opens in the app", "点击 Zenyth Web 的课程链接会在应用中打开")),
                )
                TourPage.Engine -> {
                    EnginePicture()
                    Spacer(Modifier.height(28.dp))
                    Rows(
                        Triple(Icons.Outlined.Computer, tr("내 PC 엔진", "Your PC's engine", "电脑上的引擎"),
                            tr("엔진 탭에서 PC 에서 도는 엔진 서버에 연결할 수도 있습니다", "The engine tab can also connect to an engine server on your PC", "也可以在引擎页连接电脑上运行的引擎服务器")),
                        Triple(Icons.Outlined.Thermostat, tr("발열 · 절전", "Heat · battery saver", "发热 · 省电"),
                            tr("폰이 뜨겁거나 절전 중이면 스레드를 줄여 버팁니다", "A hot phone or battery saver makes it run on fewer threads", "手机发热或省电时会减少线程继续运行")),
                    )
                }
            }
        }
    }
}

private fun TourPage.head(): Triple<ImageVector, String, String> = when (this) {
    TourPage.Board -> Triple(
        Icons.Filled.GridOn,
        tr("두고 분석하기", "Play and analyse", "落子与分析"),
        tr(
            "보드는 [분석] · [대국] 두 탭입니다. 판이 따로라 섞이지 않고, 둘 다 자동 저장됩니다",
            "The board has two tabs, Analysis and Game. Each keeps its own position, and both are saved as you go",
            "棋盘分为［分析］和［对局］两页。两盘各自独立、互不混杂，并会自动保存",
        ),
    )
    TourPage.Research -> Triple(
        Icons.Filled.Science,
        tr("연구하기", "Research", "研究"),
        tr("판 밖에서 국면과 오프닝을 깊이 파고듭니다", "Dig into positions and openings away from the board", "在棋盘之外深入研究局面和开局"),
    )
    TourPage.Joseki -> Triple(
        Icons.AutoMirrored.Filled.MenuBook,
        tr("정석", "Joseki", "定式"),
        tr("정해진 수순을 배우고, 내 정석을 모읍니다", "Learn the set sequences and keep your own", "学习定式，收藏自己的定式"),
    )
    TourPage.Engine -> Triple(
        Icons.Filled.OfflineBolt,
        tr("엔진", "Engine", "引擎"),
        tr(
            "화면 위 엔진 칩이 상태를 보여 줍니다. 꺼져 있으면 눌러서 켭니다",
            "The engine chip at the top shows its state. When it is off, tap it to start",
            "顶部的引擎标签显示其状态。关闭时点击即可启动",
        ),
    )
}

@Composable
private fun Rows(vararg rows: Triple<ImageVector, String, String>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        rows.forEach { (icon, title, text) -> FeatureRow(icon, text, title = title) }
    }
}

/**
 * A picture of a real part, not a drawing of one (Mornye's search-bar mock):
 * the app's own parts in a quiet framed card, which cannot be pressed — so it
 * always matches the app's theme and board skin, and never does anything.
 */
@Composable
private fun PictureCard(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
            .padding(20.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        content()
        // Over the parts: takes every touch, so a pictured chip is not a live one.
        Box(Modifier.matchParentSize().pointerInput(Unit) {})
    }
}

/** The board page's picture: a small position and what the engine says of it. */
@Composable
private fun BoardPicture() = PictureCard {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MiniBoard(
            stones = SampleStones,
            modifier = Modifier.size(112.dp).clip(TileShape),
        )
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                tr("최선수", "Best move", "最佳着法"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("J10", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp))
            PillBadge(tr("승률 71% · 깊이 18", "Win 71% · depth 18", "胜率 71% · 深度 18"), accent = true)
        }
    }
}

/** The engine page's picture: the chip as it reads ready, searching under heat, and off. */
@Composable
private fun EnginePicture() = PictureCard {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EngineChip(ConnectionState.Ready, onStart = {})
        EngineChip(ConnectionState.Thinking, onStart = {}, thermalThreads = 4)
        EngineChip(ConnectionState.Disconnected, onStart = {})
    }
}

/** Five stones around the centre — enough to read as a game under way. */
private val SampleStones = listOf(Move(7, 7), Move(8, 6), Move(9, 7), Move(8, 7), Move(8, 8))
