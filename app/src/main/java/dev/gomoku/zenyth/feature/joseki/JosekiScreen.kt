package dev.gomoku.zenyth.feature.joseki

import dev.gomoku.zenyth.core.designsystem.component.ZenythCollapsingTopBar
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.zenyth.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.zenyth.core.designsystem.component.QuietSwitch
import dev.gomoku.zenyth.core.designsystem.component.ZenythSegmented
import dev.gomoku.zenyth.core.designsystem.component.ZenythTopBar
import dev.gomoku.zenyth.core.i18n.tr

/**
 * 정석 — two tabs, as in Zenyth Web: the **official curriculum** (courses ›
 * lessons, browse or learn by playing, progress kept on the phone) and **my
 * joseki** (an editor for one's own trees, found again from the board's
 * position). Back goes up one level: lesson → course → list, editor → list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JosekiScreen(
    modifier: Modifier = Modifier,
    viewModel: JosekiViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.onNoticeShown()
        }
    }
    val nav = ui.nav
    BackHandler(enabled = nav.lessonId != null || nav.courseId != null || nav.editingId != null) {
        viewModel.onBack()
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImport(uri)
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.onExport(uri)
    }
    val exportPsq = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.onExportPsq(uri)
    }

    val lesson = ui.lesson
    val course = ui.course
    val editing = ui.editing
    // The two lists get the tall, folding page title (M2); a course, a lesson
    // and the editor keep the one-line header — they have a board to fit.
    val onList = lesson == null && course == null && editing == null
    val header = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(
        modifier
            .fillMaxSize()
            .then(if (onList) Modifier.nestedScroll(header.nestedScrollConnection) else Modifier),
    ) {
        if (onList) {
            ZenythCollapsingTopBar(title = tr("정석", "Joseki", "定式"), scrollBehavior = header)
        } else {
            ZenythTopBar(title = tr("정석", "Joseki", "定式"))
        }
        when {
            lesson != null && course != null -> LessonPage(ui, course, lesson, viewModel)
            course != null -> CoursePage(
                ui = ui,
                course = course,
                onBack = { viewModel.onBack() },
                onStart = { viewModel.onStartCourse(course) },
                onLesson = { viewModel.onOpenLesson(it) },
            )
            editing != null -> JosekiEditor(ui, editing, viewModel) {
                exportPsq.launch("${editing.title.ifBlank { "joseki" }}.psq")
            }
            else -> {
                ZenythSegmented(
                    options = listOf(0 to tr("공식 커리큘럼", "Curriculum", "官方课程"), 1 to tr("내 정석", "My joseki", "我的定式")),
                    selection = nav.tab,
                    onSelect = viewModel::onTab,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fill = true,
                )
                QuietSwitch(nav.tab, Modifier.fillMaxSize()) { tab ->
                    if (tab == 0) {
                        CourseList(ui, viewModel::onOpenCourse, viewModel::onRefreshCurriculum)
                    } else {
                        MyJosekiList(
                            ui = ui,
                            vm = viewModel,
                            onImport = { importFile.launch(arrayOf("*/*")) },
                            onExport = { exportFile.launch("rapfi-joseki.json") },
                        )
                    }
                }
            }
        }
    }
}
