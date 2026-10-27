package dev.gomoku.rapfidroid.feature.joseki

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.rapfidroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.rapfidroid.core.designsystem.component.QuietSwitch
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiSegmented
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiTopBar
import dev.gomoku.rapfidroid.core.i18n.tr

/**
 * 정석 — two tabs, as in RapfiWeb: the **official curriculum** (courses ›
 * lessons, browse or learn by playing, progress kept on the phone) and **my
 * joseki** (an editor for one's own trees, found again from the board's
 * position). Back goes up one level: lesson → course → list, editor → list.
 */
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

    Column(modifier.fillMaxSize()) {
        RapfiTopBar(title = tr("정석", "Joseki", "定式"))
        val lesson = ui.lesson
        val course = ui.course
        val editing = ui.editing
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
                RapfiSegmented(
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
