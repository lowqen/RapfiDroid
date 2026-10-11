package dev.gomoku.rapfidroid.feature.joseki

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.gomoku.rapfidroid.core.designsystem.component.BoardMark
import dev.gomoku.rapfidroid.core.designsystem.component.BoardRender
import dev.gomoku.rapfidroid.core.designsystem.component.CalmCard
import dev.gomoku.rapfidroid.core.designsystem.component.GomokuBoard
import dev.gomoku.rapfidroid.core.designsystem.component.Hint
import dev.gomoku.rapfidroid.core.designsystem.component.MiniBoard
import dev.gomoku.rapfidroid.core.designsystem.component.ReadingWidth
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme
import dev.gomoku.rapfidroid.core.designsystem.theme.tabular
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.joseki.Course
import dev.gomoku.rapfidroid.core.joseki.JosekiOps
import dev.gomoku.rapfidroid.core.joseki.Lesson
import dev.gomoku.rapfidroid.core.joseki.Quiz
import dev.gomoku.rapfidroid.core.joseki.QuizFeedback
import dev.gomoku.rapfidroid.core.joseki.QuizState
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.data.joseki.CurriculumSource

// ---- course list --------------------------------------------------------------

/**
 * The curriculum's front page: one line, then a card per course. Under the
 * title, quietly, where the curriculum came from — it is RapfiWeb's published
 * file, so «웹과 같음» is the normal state and the version says which one.
 */
@Composable
internal fun CourseList(ui: JosekiUiState, onOpen: (String) -> Unit, onRefresh: () -> Unit) {
    val curriculum = ui.curriculum
    LazyColumn(
        Modifier.fillMaxSize().widthIn(max = ReadingWidth),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(tr("공식 커리큘럼", "Official curriculum"), style = MaterialTheme.typography.headlineSmall)
                Hint(tr("오프닝 정석을 따라 두며 배웁니다", "Learn the opening joseki by playing them"))
                SyncLine(ui, onRefresh)
            }
        }
        if (curriculum == null) {
            item { Hint(tr("불러오는 중", "Loading")) }
        } else {
            items(curriculum.courses, key = { it.id }) { course ->
                CourseCard(course, ui.progress.keys) { onOpen(course.id) }
            }
        }
    }
}

@Composable
private fun SyncLine(ui: JosekiUiState, onRefresh: () -> Unit) {
    val version = ui.curriculum?.version?.takeIf { it.isNotBlank() }
    val state = when {
        ui.sync.checking -> tr("웹에서 확인하는 중", "Checking the web")
        ui.sync.source == CurriculumSource.ONLINE -> tr("RapfiWeb 과 같음", "Same as RapfiWeb")
        ui.sync.offline -> tr("오프라인 · 기기에 있는 것", "Offline · the copy on this phone")
        else -> tr("기기에 있는 것", "The copy on this phone")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            listOfNotNull(version?.let { tr("버전 $it", "Version $it") }, state).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (!ui.sync.checking && ui.sync.source != CurriculumSource.ONLINE) {
            TextButton(onClick = onRefresh) { Text(tr("새로 고침", "Refresh")) }
        }
    }
}

@Composable
private fun CourseCard(course: Course, done: Set<String>, onClick: () -> Unit) {
    val finished = course.lessons.count { it.id in done }
    CalmCard(Modifier.clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Thumb(course.lessons.firstOrNull(), 88)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    tr("${course.level.ko} · 레슨 ${course.lessons.size}개 · 완료 $finished", "${course.level.en} · ${course.lessons.size} lessons · $finished done"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    dev.gomoku.rapfidroid.core.joseki.Markdown.plainText(course.summary),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (course.lessons.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { finished.toFloat() / course.lessons.size },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumb(lesson: Lesson?, sizeDp: Int) {
    val stones = lesson?.let { JosekiOps.mainLine(it.root) }.orEmpty()
    MiniBoard(stones = stones, modifier = Modifier.size(sizeDp.dp), showNumbers = false)
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
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("목록으로", "Back to courses")) }
                Text(tr("코스", "Course"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            CalmCard {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Thumb(course.lessons.firstOrNull(), 120)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(course.title, style = MaterialTheme.typography.titleLarge)
                        Text(tr(course.level.ko, course.level.en), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                MarkdownText(course.summary)
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), enabled = course.lessons.isNotEmpty()) {
                    Text(
                        when {
                            finished == 0 -> tr("시작", "Start")
                            finished < course.lessons.size -> tr("이어서 배우기", "Continue")
                            else -> tr("처음부터 다시", "Start over")
                        },
                    )
                }
            }
        }
        items(course.lessons.size) { i ->
            val lesson = course.lessons[i]
            val best = ui.progress[lesson.id]?.best
            CalmCard(Modifier.clickable { onLesson(lesson.id) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleLarge.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Thumb(lesson, 64)
                    Column(Modifier.weight(1f)) {
                        Text(lesson.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            dev.gomoku.rapfidroid.core.joseki.Markdown.plainText(lesson.summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    best?.let {
                        Text(tr("${it}점", "$it pts"), style = MaterialTheme.typography.labelLarge.tabular(), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
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
                    fb.node.move?.let { put(it, BoardMark(fb.node.mark?.symbol ?: "✕", Color(0xFFFF6B5E))) }
                    fb.refute?.let { put(it, BoardMark("→", Color(0xFFFF6B5E))) }
                }
                is QuizFeedback.Outside -> put(fb.move, BoardMark("✕", Color(0xFFFF6B5E)))
                else -> Unit
            }
            if (quiz.hint && !quiz.done) Quiz.expected(lesson.root, quiz)?.let { put(it, BoardMark("?", Color(0xFF6BE38A))) }
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
            IconButton(onClick = { vm.onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("코스로 돌아가기", "Back to the course")) }
            Column(Modifier.weight(1f)) {
                Text(course.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${index + 1}. ${lesson.title}", style = MaterialTheme.typography.titleMedium, maxLines = 2)
            }
            ui.progress[lesson.id]?.best?.let {
                Text(tr("최고 ${it}점", "Best $it"), style = MaterialTheme.typography.labelMedium.tabular(), color = MaterialTheme.colorScheme.primary)
            }
        }
        LessonTabs(learn, vm::onLessonMode)
        Coach(blackToMove = moves.size % 2 == 0, where = whereText(moves, learn, quiz, lesson)) {
            if (!learn) {
                node.role?.let { Text(it.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) }
                MarkdownText(
                    node.comment.ifEmpty { if (path.isEmpty()) lesson.summary else tr("해설이 없습니다", "No commentary") },
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
                Text(tr("완료 · ${quiz.score}점", "Done · ${quiz.score} pts"), style = MaterialTheme.typography.titleLarge.tabular())
                Hint(tr("첫 시도 ${quiz.firstTry}/${quiz.answered}", "First try ${quiz.firstTry}/${quiz.answered}"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (next != null) Button(onClick = vm::onNextLesson) { Text(tr("다음 레슨", "Next lesson")) }
                    OutlinedButton(onClick = vm::onRestartQuiz) { Text(tr("다시 하기", "Again")) }
                }
            }
        }
        if (!learn) {
            Text(tr("수순", "Moves"), style = MaterialTheme.typography.titleSmall)
            TreeView(lesson.root, ui.lessonPath, 15, vm::onLessonPath)
        }
        // Foot: progress, tools, moves.
        if (learn) {
            Text(tr("${quiz.answered} / ${quiz.total}수", "${quiz.answered} / ${quiz.total} moves"), style = MaterialTheme.typography.labelMedium.tabular())
            LinearProgressIndicator(progress = { if (quiz.total > 0) quiz.answered.toFloat() / quiz.total else 0f }, modifier = Modifier.fillMaxWidth())
            if (!quiz.done) {
                val learnerTurn = (quiz.path.size % 2 == 0) == lesson.blackLearner
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::onHint, enabled = !quiz.hint && learnerTurn) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" " + tr("힌트", "Hint"))
                    }
                    OutlinedButton(onClick = vm::onRestartQuiz) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" " + tr("처음부터", "Restart"))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { vm.onLessonPath(emptyList()) }, enabled = path.isNotEmpty()) { Icon(Icons.Filled.FirstPage, tr("처음", "Start")) }
                IconButton(onClick = { vm.onLessonPath(path.dropLast(1)) }, enabled = path.isNotEmpty()) { Icon(Icons.Filled.ChevronLeft, tr("뒤로", "Back")) }
                IconButton(onClick = { vm.onLessonPath(path + 0) }, enabled = node.children.isNotEmpty()) { Icon(Icons.Filled.ChevronRight, tr("앞으로", "Forward")) }
                IconButton(onClick = { vm.onLessonPath(JosekiOps.mainLineFrom(lesson.root, path)) }, enabled = node.children.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.LastPage, tr("끝으로", "To end"))
                }
            }
        }
        val clipboard = LocalClipboardManager.current
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = vm::onLessonToBoard) { Text(tr("보드에 올리기", "Put on the board")) }
            // The same address RapfiWeb shares: opens this lesson on the web, or in the app.
            TextButton(onClick = {
                vm.lessonLink()?.let {
                    clipboard.setText(AnnotatedString(it))
                    vm.onLinkCopied()
                }
            }) { Text(tr("링크 복사", "Copy link")) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight && maxWidth >= 600.dp) {
            val side = min(maxHeight - 16.dp, maxWidth * 0.58f)
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                board(Modifier.size(side))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = panel,
                )
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
            listOf(false to tr("살펴보기", "Browse"), true to tr("학습", "Learn")).forEach { (m, label) ->
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
    val skin = RapfiTheme.board
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.padding(start = 8.dp, top = 9.dp).size(16.dp).clip(CircleShape).background(skin.blackLow))
            Box(Modifier.padding(start = 17.dp, top = 15.dp).size(16.dp).clip(CircleShape).background(skin.whiteHigh))
        }
        CalmCard(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), spacing = 8.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (blackToMove) skin.blackLow else skin.whiteHigh))
                Text(if (blackToMove) tr("흑 차례", "Black to move") else tr("백 차례", "White to move"), style = MaterialTheme.typography.labelLarge)
                Text(where, style = MaterialTheme.typography.labelMedium.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

private fun whereText(moves: List<Move>, learn: Boolean, quiz: QuizState, lesson: Lesson): String = when {
    learn && quiz.done -> tr("완료", "Done")
    learn -> tr("당신은 ${if (lesson.blackLearner) "흑" else "백"}", "You play ${if (lesson.blackLearner) "Black" else "White"}")
    moves.isEmpty() -> tr("시작 국면", "Start")
    else -> tr("${moves.size}수 ${moves.last().label()}", "Move ${moves.size} ${moves.last().label()}")
}

@Composable
private fun QuizBox(quiz: QuizState, lesson: Lesson) {
    val here = JosekiOps.nodeAt(lesson.root, quiz.path) ?: lesson.root
    when (val fb = quiz.feedback) {
        QuizFeedback.Start -> MarkdownText(here.comment.ifEmpty { lesson.root.comment })
        is QuizFeedback.Correct -> {
            Text(
                (if (fb.alternative) tr("정답 · 다른 좋은 수", "Right · another good move") else tr("정답", "Right")) +
                    " " + fb.node.move?.label().orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF2E8B57),
            )
            if (fb.node.comment.isNotEmpty()) MarkdownText(fb.node.comment)
            if (!quiz.done && here !== fb.node && here.comment.isNotEmpty()) MarkdownText(here.comment)
        }
        is QuizFeedback.Mistake -> {
            Text(
                (fb.node.role?.label ?: tr("아쉬운 수", "Not this one")) + " " + fb.node.move?.label().orEmpty() + fb.node.mark?.symbol.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (fb.node.comment.isNotEmpty()) MarkdownText(fb.node.comment)
            fb.refute?.let { Hint(tr("응징: ${it.label()}", "Refutation: ${it.label()}")) }
            Hint(tr("다시 두어 보세요", "Try again"))
        }
        is QuizFeedback.Outside -> {
            Text(tr("정석에 없는 수입니다 ", "Not in this joseki ") + fb.move.label(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Hint(tr("다시 두어 보세요", "Try again"))
        }
    }
}

@Suppress("unused")
private val unusedWidth = Modifier.width(0.dp)
