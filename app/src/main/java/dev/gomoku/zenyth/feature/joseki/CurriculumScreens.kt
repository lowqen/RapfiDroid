package dev.gomoku.zenyth.feature.joseki

import dev.gomoku.zenyth.core.designsystem.component.TileShape
import dev.gomoku.zenyth.core.designsystem.component.RoundBackButton
import dev.gomoku.zenyth.core.designsystem.component.PillBadge
import dev.gomoku.zenyth.core.designsystem.component.LabeledProgress
import dev.gomoku.zenyth.core.designsystem.component.ItemCard
import dev.gomoku.zenyth.core.designsystem.component.rememberCalmFling
import androidx.compose.foundation.lazy.itemsIndexed
import dev.gomoku.zenyth.core.designsystem.component.staggerIn
import dev.gomoku.zenyth.core.designsystem.component.rememberStagger
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import dev.gomoku.zenyth.core.designsystem.component.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import dev.gomoku.zenyth.core.designsystem.component.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.gomoku.zenyth.core.designsystem.component.SmoothProgressBar
import dev.gomoku.zenyth.core.designsystem.component.BoardMark
import dev.gomoku.zenyth.core.designsystem.component.BoardMarkColors
import dev.gomoku.zenyth.core.designsystem.component.BoardRender
import dev.gomoku.zenyth.core.designsystem.component.CalmCard
import dev.gomoku.zenyth.core.designsystem.component.GomokuBoard
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.component.MiniBoard
import dev.gomoku.zenyth.core.designsystem.component.ReadingWidth
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.joseki.Course
import dev.gomoku.zenyth.core.joseki.JosekiOps
import dev.gomoku.zenyth.core.joseki.Lesson
import dev.gomoku.zenyth.core.joseki.Quiz
import dev.gomoku.zenyth.core.joseki.QuizFeedback
import dev.gomoku.zenyth.core.joseki.QuizState
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.data.joseki.CurriculumSource
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import dev.gomoku.zenyth.core.common.plainTextClip
import kotlinx.coroutines.launch

// ---- course list --------------------------------------------------------------

/**
 * The curriculum's front page: one line, then a card per course. Under the
 * title, quietly, where the curriculum came from — it is Zenyth Web's published
 * file, so «웹과 같음» is the normal state and the version says which one.
 */
@Composable
internal fun CourseList(ui: JosekiUiState, onOpen: (String) -> Unit, onRefresh: () -> Unit) {
    val curriculum = ui.curriculum
    // The courses rise in one after another the first time the list is seen (P5).
    val stagger = rememberStagger()
    LazyColumn(
        Modifier.fillMaxSize().widthIn(max = ReadingWidth),
        contentPadding = PaddingValues(ZenythTheme.tokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(ZenythTheme.tokens.listGap),
        flingBehavior = rememberCalmFling(),
    ) {
        item {
            // The page title says «정석» and the segment above says «공식
            // 커리큘럼»; the list itself opens on what it is for.
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Hint(tr("오프닝 정석을 따라 두며 배웁니다", "Learn the opening joseki by playing them", "通过跟着下来学习开局定式"))
                SyncLine(ui, onRefresh)
            }
        }
        if (curriculum == null) {
            item { Hint(tr("불러오는 중", "Loading", "载入中")) }
        } else {
            itemsIndexed(curriculum.courses, key = { _, it -> it.id }) { i, course ->
                Box(Modifier.staggerIn(stagger, i + 1)) {
                    CourseCard(course, ui.progress.keys) { onOpen(course.id) }
                }
            }
        }
    }
}

@Composable
private fun SyncLine(ui: JosekiUiState, onRefresh: () -> Unit) {
    val version = ui.curriculum?.version?.takeIf { it.isNotBlank() }
    val state = when {
        ui.sync.checking -> tr("웹에서 확인하는 중", "Checking the web", "正在联网检查")
        ui.sync.source == CurriculumSource.ONLINE -> tr("Zenyth Web 과 같음", "Same as Zenyth Web", "与 Zenyth Web 一致")
        ui.sync.offline -> tr("오프라인 · 기기에 있는 것", "Offline · the copy on this phone", "离线 · 本机已有的版本")
        else -> tr("기기에 있는 것", "The copy on this phone", "本机已有的版本")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            listOfNotNull(version?.let { tr("버전 $it", "Version $it", "版本 $it") }, state).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (!ui.sync.checking && ui.sync.source != CurriculumSource.ONLINE) {
            TextButton(onClick = onRefresh) { Text(tr("새로 고침", "Refresh", "刷新")) }
        }
    }
}

/**
 * A course as an item card (M4): the opening as the tile, the level as the
 * badge — «완료» in the accent once every lesson is done — two lines of what
 * it teaches, and how far the user is, in lessons.
 */
@Composable
private fun CourseCard(course: Course, done: Set<String>, onClick: () -> Unit) {
    val finished = course.lessons.count { it.id in done }
    val total = course.lessons.size
    ItemCard(
        title = course.title,
        onClick = onClick,
        leading = { Thumb(course.lessons.firstOrNull(), 72) },
        badge = {
            if (total > 0 && finished == total) {
                PillBadge(tr("완료", "Done", "已完成"), accent = true)
            } else {
                PillBadge(tr(course.level.ko, course.level.en, course.level.zh))
            }
        },
        description = dev.gomoku.zenyth.core.joseki.Markdown.plainText(course.summary),
        footer = if (total > 0) {
            { LabeledProgress(finished.toFloat() / total, label = "$finished/$total") }
        } else {
            null
        },
    )
}

@Composable
private fun Thumb(lesson: Lesson?, sizeDp: Int) {
    val stones = lesson?.let { JosekiOps.mainLine(it.root) }.orEmpty()
    MiniBoard(stones = stones, modifier = Modifier.size(sizeDp.dp).clip(TileShape), showNumbers = false)
}

// ---- course -------------------------------------------------------------------

@Composable
internal fun CoursePage(
    ui: JosekiUiState,
    course: Course,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onLesson: (String) -> Unit,
) {
    val finished = course.lessons.count { it.id in ui.progress }
    LazyColumn(
        Modifier.fillMaxSize().widthIn(max = ReadingWidth),
        contentPadding = PaddingValues(ZenythTheme.tokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(ZenythTheme.tokens.listGap),
        flingBehavior = rememberCalmFling(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundBackButton(onBack, Modifier.padding(end = 12.dp), label = tr("목록으로", "Back to courses", "返回课程列表"))
                Text(tr("코스", "Course", "课程"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            CalmCard {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Thumb(course.lessons.firstOrNull(), 120)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(course.title, style = MaterialTheme.typography.titleLarge)
                        Text(tr(course.level.ko, course.level.en, course.level.zh), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                MarkdownText(course.summary)
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), enabled = course.lessons.isNotEmpty()) {
                    Text(
                        when {
                            finished == 0 -> tr("시작", "Start", "开始")
                            finished < course.lessons.size -> tr("이어서 배우기", "Continue", "继续学习")
                            else -> tr("처음부터 다시", "Start over", "从头再来")
                        },
                    )
                }
            }
        }
        items(course.lessons.size) { i ->
            val lesson = course.lessons[i]
            val best = ui.progress[lesson.id]?.best
            // A lesson as an item card (M4): its number on the title, the best
            // score as the badge once there is one.
            ItemCard(
                title = "${i + 1}. ${lesson.title}",
                onClick = { onLesson(lesson.id) },
                leading = { Thumb(lesson, 56) },
                badge = best?.let { { PillBadge(tr("${it}점", "$it pts", "$it 分"), accent = true) } },
                description = dev.gomoku.zenyth.core.joseki.Markdown.plainText(lesson.summary),
            )
        }
    }
}

// ---- lesson -------------------------------------------------------------------

/**
 * A lesson, chess.com style: the board large on one side, on the other the
 * course › lesson title, [살펴보기 | 학습], the coach's bubble (whose move, the
 * commentary or the verdict) and a foot with progress, hint, restart and moves.
 */
@Composable
internal fun LessonPage(
    ui: JosekiUiState,
    course: Course,
    lesson: Lesson,
    vm: JosekiViewModel,
) {
    val learn = ui.nav.learn
    val quiz = ui.quiz ?: Quiz.start(lesson.root, lesson.blackLearner)
    val path = if (learn) quiz.path else ui.lessonPath
    val node = JosekiOps.nodeAt(lesson.root, path) ?: lesson.root
    val moves = JosekiOps.movesTo(lesson.root, path)
    val marks: Map<Move, BoardMark> = if (!learn) {
        childMarks(node)
    } else {
        buildMap {
            when (val fb = quiz.feedback) {
                is QuizFeedback.Mistake -> {
                    fb.node.move?.let { put(it, BoardMark(fb.node.mark?.symbol ?: "✕", BoardMarkColors.Bad)) }
                    fb.refute?.let { put(it, BoardMark("→", BoardMarkColors.Bad)) }
                }
                is QuizFeedback.Outside -> put(fb.move, BoardMark("✕", BoardMarkColors.Bad))
                else -> Unit
            }
            if (quiz.hint && !quiz.done) Quiz.expected(lesson.root, quiz)?.let { put(it, BoardMark("?", BoardMarkColors.Good)) }
        }
    }
    val render = BoardRender(stones = moves, lastMove = moves.lastOrNull(), marks = marks)
    val index = course.lessons.indexOfFirst { it.id == lesson.id }
    val next = course.lessons.getOrNull(index + 1)

    val board: @Composable (Modifier) -> Unit = { m ->
        GomokuBoard(render = render, modifier = m, onTap = vm::onLessonTap)
    }
    val panel: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBackButton({ vm.onBack() }, Modifier.padding(end = 12.dp), label = tr("코스로 돌아가기", "Back to the course", "返回课程"))
            Column(Modifier.weight(1f)) {
                Text(course.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${index + 1}. ${lesson.title}", style = MaterialTheme.typography.titleMedium, maxLines = 2)
            }
            ui.progress[lesson.id]?.best?.let {
                Text(tr("최고 ${it}점", "Best $it", "最高 $it 分"), style = MaterialTheme.typography.labelMedium.tabular(), color = MaterialTheme.colorScheme.primary)
            }
        }
        LessonTabs(learn, vm::onLessonMode)
        Coach(blackToMove = moves.size % 2 == 0, where = whereText(moves, learn, quiz, lesson)) {
            if (!learn) {
                node.role?.let { Text(it.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) }
                MarkdownText(
                    node.comment.ifEmpty { if (path.isEmpty()) lesson.summary else tr("해설이 없습니다", "No commentary", "暂无讲解") },
                    onCoord = { m ->
                        val i = moves.indexOf(m)
                        if (i >= 0) vm.onLessonPath(path.take(i + 1)) else vm.onLessonTap(m)
                    },
                )
            } else {
                QuizBox(quiz, lesson)
            }
        }
        if (learn && quiz.done) {
            CalmCard {
                Text(tr("완료 · ${quiz.score}점", "Done · ${quiz.score} pts", "完成 · ${quiz.score} 分"), style = MaterialTheme.typography.titleLarge.tabular())
                Hint(tr("첫 시도 ${quiz.firstTry}/${quiz.answered}", "First try ${quiz.firstTry}/${quiz.answered}", "首次即对 ${quiz.firstTry}/${quiz.answered}"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (next != null) Button(onClick = vm::onNextLesson) { Text(tr("다음 레슨", "Next lesson", "下一课")) }
                    OutlinedButton(onClick = vm::onRestartQuiz) { Text(tr("다시 하기", "Again", "再来一次")) }
                }
            }
        }
        if (!learn) {
            Text(tr("수순", "Moves", "手顺"), style = MaterialTheme.typography.titleSmall)
            TreeView(lesson.root, ui.lessonPath, 15, vm::onLessonPath)
        }
        // Foot: progress, tools, moves.
        if (learn) {
            Text(tr("${quiz.answered} / ${quiz.total}수", "${quiz.answered} / ${quiz.total} moves", "${quiz.answered} / ${quiz.total} 手"), style = MaterialTheme.typography.labelMedium.tabular())
            SmoothProgressBar(fraction = if (quiz.total > 0) quiz.answered.toFloat() / quiz.total else 0f, modifier = Modifier.fillMaxWidth())
            if (!quiz.done) {
                val learnerTurn = (quiz.path.size % 2 == 0) == lesson.blackLearner
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::onHint, enabled = !quiz.hint && learnerTurn) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" " + tr("힌트", "Hint", "提示"))
                    }
                    OutlinedButton(onClick = vm::onRestartQuiz) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" " + tr("처음부터", "Restart", "从头开始"))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { vm.onLessonPath(emptyList()) }, enabled = path.isNotEmpty()) { Icon(Icons.Filled.FirstPage, tr("처음", "Start", "开头")) }
                IconButton(onClick = { vm.onLessonPath(path.dropLast(1)) }, enabled = path.isNotEmpty()) { Icon(Icons.Filled.ChevronLeft, tr("뒤로", "Back", "后退")) }
                IconButton(onClick = { vm.onLessonPath(path + 0) }, enabled = node.children.isNotEmpty()) { Icon(Icons.Filled.ChevronRight, tr("앞으로", "Forward", "前进")) }
                IconButton(onClick = { vm.onLessonPath(JosekiOps.mainLineFrom(lesson.root, path)) }, enabled = node.children.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.LastPage, tr("끝으로", "To end", "到末尾"))
                }
            }
        }
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = vm::onLessonToBoard) { Text(tr("보드에 올리기", "Put on the board", "放到棋盘上")) }
            // The same address Zenyth Web shares: opens this lesson on the web, or in the app.
            TextButton(onClick = {
                vm.lessonLink()?.let { link ->
                    scope.launch {
                        clipboard.setClipEntry(plainTextClip(link))
                        vm.onLinkCopied()
                    }
                }
            }) { Text(tr("링크 복사", "Copy link", "复制链接")) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight && maxWidth >= 600.dp) {
            val side = min(maxHeight - 16.dp, maxWidth * 0.58f)
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                board(Modifier.size(side))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = panel,
                )
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling())) {
                board(Modifier.fillMaxWidth())
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = panel)
            }
        }
    }
}

@Composable
private fun LessonTabs(learn: Boolean, onMode: (Boolean) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.height(40.dp)) {
        Row(Modifier.padding(3.dp)) {
            listOf(false to tr("살펴보기", "Browse", "浏览"), true to tr("학습", "Learn", "学习")).forEach { (m, label) ->
                val on = m == learn
                Surface(
                    onClick = { onMode(m) },
                    shape = CircleShape,
                    color = if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** The coach: two stones for a face, a bubble with whose move it is and what to know. */
@Composable
private fun Coach(blackToMove: Boolean, where: String, content: @Composable ColumnScope.() -> Unit) {
    val skin = ZenythTheme.board
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.padding(start = 8.dp, top = 9.dp).size(16.dp).clip(CircleShape).background(skin.blackLow))
            Box(Modifier.padding(start = 17.dp, top = 15.dp).size(16.dp).clip(CircleShape).background(skin.whiteHigh))
        }
        CalmCard(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), spacing = 8.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (blackToMove) skin.blackLow else skin.whiteHigh))
                Text(if (blackToMove) tr("흑 차례", "Black to move", "轮到黑") else tr("백 차례", "White to move", "轮到白"), style = MaterialTheme.typography.labelLarge)
                Text(where, style = MaterialTheme.typography.labelMedium.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

private fun whereText(moves: List<Move>, learn: Boolean, quiz: QuizState, lesson: Lesson): String = when {
    learn && quiz.done -> tr("완료", "Done", "完成")
    learn -> tr("당신은 ${if (lesson.blackLearner) "흑" else "백"}", "You play ${if (lesson.blackLearner) "Black" else "White"}", "你执${if (lesson.blackLearner) "黑" else "白"}")
    moves.isEmpty() -> tr("시작 국면", "Start", "起始局面")
    else -> tr("${moves.size}수 ${moves.last().label()}", "Move ${moves.size} ${moves.last().label()}", "第 ${moves.size} 手 ${moves.last().label()}")
}

@Composable
private fun QuizBox(quiz: QuizState, lesson: Lesson) {
    val here = JosekiOps.nodeAt(lesson.root, quiz.path) ?: lesson.root
    when (val fb = quiz.feedback) {
        QuizFeedback.Start -> MarkdownText(here.comment.ifEmpty { lesson.root.comment })
        is QuizFeedback.Correct -> {
            Text(
                (if (fb.alternative) tr("정답 · 다른 좋은 수", "Right · another good move", "正确 · 另一个好着法") else tr("정답", "Right", "正确")) +
                    " " + fb.node.move?.label().orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = ZenythTheme.colors.positive,
            )
            if (fb.node.comment.isNotEmpty()) MarkdownText(fb.node.comment)
            if (!quiz.done && here !== fb.node && here.comment.isNotEmpty()) MarkdownText(here.comment)
        }
        is QuizFeedback.Mistake -> {
            Text(
                (fb.node.role?.label ?: tr("아쉬운 수", "Not this one", "不是这一手")) + " " + fb.node.move?.label().orEmpty() + fb.node.mark?.symbol.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (fb.node.comment.isNotEmpty()) MarkdownText(fb.node.comment)
            fb.refute?.let { Hint(tr("응징: ${it.label()}", "Refutation: ${it.label()}", "破解: ${it.label()}")) }
            Hint(tr("다시 두어 보세요", "Try again", "再试一次"))
        }
        is QuizFeedback.Outside -> {
            Text(tr("정석에 없는 수입니다 ", "Not in this joseki ", "定式中没有这一手") + fb.move.label(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Hint(tr("다시 두어 보세요", "Try again", "再试一次"))
        }
    }
}
