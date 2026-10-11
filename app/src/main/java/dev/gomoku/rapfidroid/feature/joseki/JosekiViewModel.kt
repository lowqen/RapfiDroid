package dev.gomoku.rapfidroid.feature.joseki

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.rapfidroid.core.common.IncomingLinks
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.joseki.Course
import dev.gomoku.rapfidroid.core.joseki.Curriculum
import dev.gomoku.rapfidroid.core.joseki.JosekiJson
import dev.gomoku.rapfidroid.core.joseki.JosekiMark
import dev.gomoku.rapfidroid.core.joseki.JosekiNode
import dev.gomoku.rapfidroid.core.joseki.JosekiOps
import dev.gomoku.rapfidroid.core.joseki.JosekiRole
import dev.gomoku.rapfidroid.core.joseki.JosekiTree
import dev.gomoku.rapfidroid.core.joseki.Lesson
import dev.gomoku.rapfidroid.core.joseki.LessonLink
import dev.gomoku.rapfidroid.core.joseki.LessonTarget
import dev.gomoku.rapfidroid.core.joseki.Path
import dev.gomoku.rapfidroid.core.joseki.Quiz
import dev.gomoku.rapfidroid.core.joseki.QuizState
import dev.gomoku.rapfidroid.core.model.GameFile
import dev.gomoku.rapfidroid.core.model.GameFileFormat
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.data.game.GameFileIo
import dev.gomoku.rapfidroid.data.joseki.CurriculumSync
import dev.gomoku.rapfidroid.data.joseki.JosekiHit
import dev.gomoku.rapfidroid.data.joseki.JosekiRepository
import dev.gomoku.rapfidroid.data.joseki.LessonProgress
import dev.gomoku.rapfidroid.domain.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the joseki screen is: a tab, a course, a lesson, or a joseki being edited. */
data class JosekiNav(
    val tab: Int = 0,
    val courseId: String? = null,
    val lessonId: String? = null,
    val learn: Boolean = false,
    val editingId: String? = null,
)

data class JosekiUiState(
    val nav: JosekiNav = JosekiNav(),
    val curriculum: Curriculum? = null,
    /** Where the curriculum came from and whether RapfiWeb was reachable. */
    val sync: CurriculumSync = CurriculumSync(),
    val progress: Map<String, LessonProgress> = emptyMap(),
    // ---- lesson ----
    val lessonPath: Path = emptyList(),
    val quiz: QuizState? = null,
    // ---- my joseki ----
    val trees: List<JosekiTree> = emptyList(),
    val hits: List<JosekiHit> = emptyList(),
    val editing: JosekiTree? = null,
    val editPath: Path = emptyList(),
    val notice: String? = null,
) {
    val course: Course? get() = curriculum?.courses?.firstOrNull { it.id == nav.courseId }
    val lesson: Lesson? get() = nav.lessonId?.let { curriculum?.findLesson(it)?.second }
}

@HiltViewModel
class JosekiViewModel @Inject constructor(
    private val repo: JosekiRepository,
    private val game: GameRepository,
    private val fileIo: GameFileIo,
    private val links: IncomingLinks,
) : ViewModel() {

    private val nav = MutableStateFlow(JosekiNav())
    private val lessonPath = MutableStateFlow<Path>(emptyList())
    private val quiz = MutableStateFlow<QuizState?>(null)
    private val editPath = MutableStateFlow<Path>(emptyList())
    private val notice = MutableStateFlow<String?>(null)

    private data class Local(val nav: JosekiNav, val path: Path, val quiz: QuizState?, val editPath: Path, val notice: String?)

    private val local = combine(nav, lessonPath, quiz, editPath, notice) { n, p, q, e, msg -> Local(n, p, q, e, msg) }

    val uiState: StateFlow<JosekiUiState> =
        combine(local, repo.curriculum, repo.progress, repo.trees, combine(game.position, repo.sync, ::Pair)) { l, c, pr, trees, (pos, sync) ->
            JosekiUiState(
                nav = l.nav,
                curriculum = c,
                sync = sync,
                progress = pr,
                lessonPath = l.path,
                quiz = l.quiz,
                trees = trees,
                hits = repo.search(pos.moves, pos.size),
                editing = trees.firstOrNull { it.id == l.nav.editingId },
                editPath = l.editPath,
                notice = l.notice,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JosekiUiState())

    init {
        repo.load()
        // A curriculum link the app was opened with (`…/joseki?lesson=<id>`).
        viewModelScope.launch {
            links.lesson.filterNotNull().collect { openLink(it) }
        }
    }

    /**
     * Opens a lesson or course link. A lesson the copy on the phone does not
     * have yet (published since the last fetch) gets one fresh look at
     * RapfiWeb before the app says it is missing.
     */
    private suspend fun openLink(target: LessonTarget) {
        links.consumeLesson()
        var c = repo.curriculum.filterNotNull().first()
        fun found(cur: Curriculum) =
            target.lessonId?.let { cur.findLesson(it) != null } ?: cur.courses.any { it.id == target.courseId }
        if (!found(c)) {
            repo.refreshCurriculum(force = true)
            repo.sync.first { !it.checking }
            c = repo.curriculum.value ?: c
        }
        nav.value = JosekiNav(tab = 0)
        when {
            target.lessonId != null && c.findLesson(target.lessonId) != null -> onOpenLesson(target.lessonId, target.learn)
            target.courseId != null && c.courses.any { it.id == target.courseId } -> onOpenCourse(target.courseId)
            else -> notice.value = tr(
                "레슨 «${target.lessonId ?: target.courseId}» 을(를) 찾지 못했습니다",
                "Lesson «${target.lessonId ?: target.courseId}» was not found",
                "未找到课程 «${target.lessonId ?: target.courseId}»",
            )
        }
    }

    /** Checks RapfiWeb for a newer curriculum now. */
    fun onRefreshCurriculum() = repo.refreshCurriculum(force = true)

    /** The RapfiWeb address of the open lesson, in its mode — the web app opens it too. */
    fun lessonLink(): String? = nav.value.lessonId?.let { LessonLink.url(it, nav.value.learn) }

    fun onLinkCopied() {
        notice.value = tr("레슨 링크를 복사했습니다", "Copied the lesson link", "已复制课程链接")
    }

    fun onNoticeShown() {
        notice.value = null
    }

    fun onTab(tab: Int) = nav.update { it.copy(tab = tab) }

    /** One level up: lesson → course → list; editor → list. False at the top. */
    fun onBack(): Boolean {
        val n = nav.value
        when {
            n.lessonId != null -> nav.value = n.copy(lessonId = null, learn = false)
            n.courseId != null -> nav.value = n.copy(courseId = null)
            n.editingId != null -> nav.value = n.copy(editingId = null)
            else -> return false
        }
        return true
    }

    // ---- curriculum ------------------------------------------------------------

    fun onOpenCourse(id: String) = nav.update { it.copy(courseId = id, lessonId = null) }

    fun onOpenLesson(id: String, learn: Boolean = false) {
        val (course, lesson) = repo.curriculum.value?.findLesson(id) ?: return
        nav.update { it.copy(courseId = course.id, lessonId = id, learn = learn) }
        lessonPath.value = emptyList()
        quiz.value = Quiz.start(lesson.root, lesson.blackLearner)
    }

    /** The first lesson of the course not finished yet (or the first). */
    fun onStartCourse(course: Course) {
        val next = course.lessons.firstOrNull { it.id !in repo.progress.value } ?: course.lessons.firstOrNull() ?: return
        onOpenLesson(next.id, learn = false)
    }

    fun onLessonMode(learn: Boolean) = nav.update { it.copy(learn = learn) }

    fun onLessonPath(path: Path) {
        lessonPath.value = path
    }

    fun onLessonTap(move: Move) {
        val lesson = uiState.value.lesson ?: return
        if (nav.value.learn) {
            val before = quiz.value ?: Quiz.start(lesson.root, lesson.blackLearner)
            val after = Quiz.answer(lesson.root, before, move)
            quiz.value = after
            if (after.done && !before.done) repo.record(lesson.id, after.score)
            return
        }
        val node = JosekiOps.nodeAt(lesson.root, lessonPath.value) ?: return
        val i = node.children.indexOfFirst { it.move == move }
        if (i >= 0) lessonPath.value = lessonPath.value + i
    }

    fun onHint() = quiz.update { it?.let(Quiz::showHint) }

    fun onRestartQuiz() {
        val lesson = uiState.value.lesson ?: return
        quiz.value = Quiz.start(lesson.root, lesson.blackLearner)
    }

    fun onNextLesson() {
        val course = uiState.value.course ?: return
        val i = course.lessons.indexOfFirst { it.id == nav.value.lessonId }
        course.lessons.getOrNull(i + 1)?.let { onOpenLesson(it.id, learn = nav.value.learn) }
    }

    /** Put the lesson's line so far on the board. */
    fun onLessonToBoard() {
        val lesson = uiState.value.lesson ?: return
        val path = if (nav.value.learn) quiz.value?.path.orEmpty() else lessonPath.value
        toBoard(JosekiOps.movesTo(lesson.root, path))
    }

    fun onResetProgress() = repo.resetProgress()

    // ---- my joseki -----------------------------------------------------------------

    fun onNewJoseki(fromBoard: Boolean) {
        val tree = if (fromBoard) {
            val moves = game.position.value.moves
            JosekiTree(title = tr("새 정석", "New joseki", "新定式"), root = JosekiOps.addLine(JosekiNode(), moves).root)
        } else {
            JosekiTree(title = tr("새 정석", "New joseki", "新定式"))
        }
        repo.save(tree)
        nav.update { it.copy(editingId = tree.id) }
        editPath.value = if (fromBoard) JosekiOps.mainLineFrom(tree.root) else emptyList()
    }

    fun onEdit(id: String, path: Path = emptyList()) {
        nav.update { it.copy(editingId = id) }
        editPath.value = path
    }

    fun onDelete(id: String) {
        repo.delete(id)
        if (nav.value.editingId == id) nav.update { it.copy(editingId = null) }
    }

    private fun edit(block: (JosekiTree) -> JosekiTree?) {
        val tree = uiState.value.editing ?: repo.trees.value.firstOrNull { it.id == nav.value.editingId } ?: return
        block(tree)?.let { repo.save(it) }
    }

    fun onEditPath(path: Path) {
        editPath.value = path
    }

    fun onEditTap(move: Move) = edit { t ->
        val step = JosekiOps.addMove(t.root, editPath.value, move, t.boardSize)
        if (step == null) {
            notice.value = JosekiOps.illegal(t.root, editPath.value, move, t.boardSize)
            null
        } else {
            editPath.value = step.path
            if (step.added) t.copy(root = step.root) else null
        }
    }

    fun onAddText(text: String) = edit { t ->
        when (val parsed = JosekiOps.parseMoveText(text, t.boardSize)) {
            is JosekiOps.Parsed.Error -> {
                notice.value = parsed.message
                null
            }
            is JosekiOps.Parsed.Ok -> {
                val r = JosekiOps.addLine(t.root, parsed.moves, editPath.value, t.boardSize)
                editPath.value = r.path
                r.stoppedAt?.let { notice.value = tr("${it + 1}번째 수에서 멈췄습니다", "Stopped at move ${it + 1}", "在第 ${it + 1} 手停止") }
                t.copy(root = r.root)
            }
        }
    }

    fun onTitle(title: String) = edit { it.copy(title = title.take(200)) }
    fun onCategory(category: String) = edit { it.copy(category = category.take(200)) }
    fun onComment(comment: String) = edit { it.copy(root = JosekiOps.setComment(it.root, editPath.value, comment.take(20_000))) }
    fun onMark(mark: JosekiMark?) = edit { it.copy(root = JosekiOps.setMark(it.root, editPath.value, mark)) }
    fun onRole(role: JosekiRole?) = edit { it.copy(root = JosekiOps.setRole(it.root, editPath.value, role)) }

    fun onPromote() = edit { t ->
        val (root, path) = JosekiOps.promoteLine(t.root, editPath.value)
        editPath.value = path
        t.copy(root = root)
    }

    fun onMoveSibling(dir: Int) = edit { t ->
        val (root, path) = JosekiOps.moveSibling(t.root, editPath.value, dir)
        editPath.value = path
        t.copy(root = root)
    }

    fun onDeleteBranch() = edit { t ->
        val (root, parent) = JosekiOps.removeAt(t.root, editPath.value) ?: return@edit null
        editPath.value = parent
        t.copy(root = root)
    }

    fun onEditToBoard() {
        val t = uiState.value.editing ?: return
        toBoard(JosekiOps.movesTo(t.root, editPath.value))
    }

    fun psqBytes(): ByteArray? {
        val t = uiState.value.editing ?: return null
        return GameFile.writePsq(JosekiOps.movesTo(t.root, editPath.value), t.boardSize)
    }

    fun onImport(uri: Uri) {
        viewModelScope.launch {
            val bytes = fileIo.read(uri.toString()) ?: run {
                notice.value = tr("파일을 읽지 못했습니다", "Could not read the file", "无法读取文件")
                return@launch
            }
            val name = fileIo.displayName(uri)
            val format = GameFileFormat.of(name)
            if (format != null) {
                // A game file becomes a draft joseki.
                val moves = GameFile.parse(bytes, format, Move.DEFAULT_SIZE).orEmpty()
                if (moves.isEmpty()) {
                    notice.value = tr("기보를 읽지 못했습니다", "Could not read the game", "无法读取棋谱")
                    return@launch
                }
                repo.addAll(listOf(JosekiTree(title = GameFile.baseName(name), root = JosekiOps.addLine(JosekiNode(), moves).root)))
                notice.value = tr("기보를 정석 초안으로 들였습니다", "Imported the game as a draft joseki", "已将棋谱导入为定式草稿")
                return@launch
            }
            when (val r = JosekiJson.import(bytes.decodeToString())) {
                is JosekiJson.Import.Error -> notice.value = r.message
                is JosekiJson.Import.Ok -> {
                    repo.addAll(r.trees)
                    notice.value = tr("정석 ${r.trees.size}개를 가져왔습니다", "Imported ${r.trees.size} joseki", "已导入 ${r.trees.size} 个定式") +
                        if (r.dropped > 0) tr(" · 버린 가지 ${r.dropped}", " · ${r.dropped} branches dropped", " · 丢弃分支 ${r.dropped} 个") else ""
                }
            }
        }
    }

    fun onExport(uri: Uri) {
        viewModelScope.launch {
            notice.value = runCatching { fileIo.write(uri, repo.exportAll().toByteArray()) }.fold(
                onSuccess = { tr("정석을 내보냈습니다", "Joseki exported", "已导出定式") },
                onFailure = { tr("내보내지 못했습니다", "Could not export", "无法导出") },
            )
        }
    }

    fun onExportPsq(uri: Uri) {
        val bytes = psqBytes() ?: return
        viewModelScope.launch {
            notice.value = runCatching { fileIo.write(uri, bytes) }.fold(
                onSuccess = { tr("변화를 .psq 로 저장했습니다", "Saved the line as .psq", "已将变化保存为 .psq") },
                onFailure = { tr("저장하지 못했습니다", "Could not save", "无法保存") },
            )
        }
    }

    private fun toBoard(moves: List<Move>) {
        viewModelScope.launch {
            game.replaceLine(moves)
            notice.value = tr("보드에 올렸습니다 (${moves.size}수)", "Put on the board (${moves.size} moves)", "已放到棋盘上 (${moves.size} 手)")
        }
    }
}
