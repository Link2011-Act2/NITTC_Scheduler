package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.ui.components.AppConnectedButtonGroup
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import jp.linkserver.nittcsc.viewmodel.AiRuntimeUiState
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Render product screens, rather than a palette swatch, under the same theme and viewport. */
class SurfaceAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lessonDialogAndRuntimeCardAcrossColorModes() {
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var dialog by mutableStateOf(true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val run = InstrumentationRegistry.getArguments().getString("motionRun", "current")
        val output = File(context.getExternalFilesDir(null), "surface-refresh/$run").apply { mkdirs() }
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = dark, dynamicColor = dynamic) {
                Surface(Modifier.width(360.dp).height(680.dp).testTag("runtime-screen"),
                    color = MaterialTheme.colorScheme.background) {
                    if (dialog) LessonOverrideDialog(date = LocalDate.now(), currentDayType = DayType.A,
                        currentOverrideDayOfWeek = 3, currentOverrideDayType = DayType.B,
                        currentHolidaySpecialLabel = null, onDismiss = {}, onApply = { _, _, _ -> })
                    else Column(Modifier.padding(16.dp)) {
                        AiRuntimeCard(AiRuntimeUiState(), onDownload = {}, onCancel = {})
                    }
                }
            }
        }
        for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) for (showDialog in listOf(true, false)) {
            compose.runOnIdle { dark = night; dynamic = wallpaper; dialog = showDialog }
            compose.waitForIdle()
            val node = if (showDialog) compose.onNode(isDialog()) else compose.onNodeWithTag("runtime-screen")
            node.assertIsDisplayed()
            File(output, "aux-$night-$wallpaper-${if (showDialog) "dialog" else "runtime"}.png").outputStream().use {
                node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun primaryScreensAcrossDesignsAndColorModes() {
        val today = LocalDate.now()
        val tasks = listOf(
            TaskEntity(id = 1, subject = "数学", teacher = "担当教員", title = "演習レポート",
                description = "問題を解いて提出", dueDate = today.plusDays(3), createdDate = today, priority = 1),
            TaskEntity(id = 2, subject = "英語", title = "単語の復習", dueDate = today.plusDays(5), createdDate = today)
        )
        val completed = tasks.first().copy(id = 3, title = "提出済みの課題", isCompleted = true, completedDate = today)
        val plans = tasks.map { it.toPlanEntityLike().copy(title = "面談の予定") }
        val state = SchedulerUiState(
            settings = SettingsEntity(termStart = today.minusDays(7), termEnd = today.plusDays(28)),
            tasks = tasks + completed, incompleteTasks = tasks, plans = plans, incompletePlans = plans,
            expressiveWarningAcknowledged = true
        )
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3_EXPRESSIVE)
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var page by mutableStateOf("settings")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val run = InstrumentationRegistry.getArguments().getString("motionRun", "current")
        val output = File(context.getExternalFilesDir(null), "surface-refresh/$run").apply { mkdirs() }
        compose.setContent {
            AppTheme(mode, darkTheme = dark, dynamicColor = dynamic) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.width(360.dp).height(680.dp).testTag("appearance-screen")) {
                        key(page, mode, dark, dynamic) {
                            when (page) {
                                "settings", "exam-settings" -> SettingsScreen(
                                    state = state.copy(uiDesignMode = mode), onBack = {}, onAbout = {},
                                    onToggleLocalAi = {}, onToggleDrawerNavigation = {}, onToggleAddTasksToCalendar = {},
                                    onToggleCurrentTimeMarker = {}, onToggleUnifyTaskPlanView = {},
                                    onToggleShowWeekdayOnDates = {}, onToggleAdvancedTimeSettingsUi = {},
                                    timetableSettingsPage = if (page == "exam-settings") TimetableSettingsPage.EXAM else null
                                )
                                "tasks", "completed", "plans", "empty" -> TaskScreen(
                                    tasks = if (page == "empty") emptyList() else if (page == "plans") plans.map { it.toTaskEntityLike() } else tasks,
                                    completedTasks = if (page == "empty") emptyList() else listOf(completed),
                                    focusTaskId = if (page == "completed") completed.id else null,
                                    onOpenTaskEditor = {}, onDeleteTask = {}, onMarkComplete = {}, onMarkIncomplete = {},
                                    isPlan = page == "plans"
                                )
                                "todo-tasks", "todo-plans" -> UnifiedTaskPlanScreen(
                                    uiState = state, selectedTabIndex = if (page == "todo-plans") 1 else 0,
                                    onSelectedTabIndexChange = {}, onOpenTask = {}, onCreateTask = {},
                                    showNaturalLanguageTaskAdd = false, onOpenNaturalLanguageTaskAdd = {},
                                    onDeleteTask = {}, onMarkTaskComplete = {}, onMarkTaskIncomplete = {},
                                    onOpenPlan = {}, onCreatePlan = {}, onDeletePlan = {}, onMarkPlanComplete = {}, onMarkPlanIncomplete = {}
                                )
                                "add-task", "edit-plan" -> AddTaskScreen(
                                    task = if (page == "edit-plan") tasks.first() else null,
                                    isPlan = page == "edit-plan", onSave = {}, onBack = {}
                                )
                                "ab" -> AbTableScreen(
                                    modifier = Modifier, state = state, onSaveDayTypes = { _, _ -> },
                                    onSaveLessonOverride = { _, _, _ -> }, onClearLessonOverride = {},
                                    onUpdateHolidaySpecialLabel = { _, _ -> }, preparedNextAcademicYear = null,
                                    onPrepareNextAcademicYear = {}, onUpdateTerm = { _, _ -> },
                                    onSaveBreak = { _, _, _, _ -> }, onDeleteBreak = {}, onOpenExamTimetables = {},
                                    dayTypeForDate = { if (it.dayOfWeek.value > 5) DayType.HOLIDAY else DayType.A },
                                    dayTypeEntityForDate = { null }
                                )
                                "qr-home" -> QrShareHomeScreen(onShare = {}, onRead = {})
                                "qr-pick" -> QrShareSelectionScreen(state = state, busy = false,
                                    picker = QrShareSection.TASKS, onPick = {}, onPickDone = {}, onGenerate = {})
                                "exam-empty" -> ExamTimetablePeriodListScreen(
                                    periods = emptyList(), configuredDates = emptySet(), onBack = {}, onOpenPeriod = {})
                                "editor" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AppConnectedButtonGroup(listOf("月", "火", "水", "木", "金"), 2, {})
                                    LessonEditorCard(title = "1/2校時", lesson = LessonEntity(id = 1,
                                        academicYear = today.year, dayOfWeek = 3, slotIndex = 0, mode = LessonMode.WEEKLY,
                                        weeklySubject = "数学", weeklyTeacher = "担当教員", aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""),
                                        onAutoSave = {}, onSave = {})
                                    LessonEditorCard(title = "3/4校時", lesson = null, onAutoSave = {}, onSave = {})
                                }
                            }
                        }
                    }
                }
            }
        }
        val pages = listOf("settings", "tasks", "completed", "plans", "todo-tasks", "todo-plans",
            "add-task", "edit-plan", "ab", "qr-home", "qr-pick", "exam-empty", "editor", "empty", "exam-settings")
        for (design in UiDesignMode.entries) for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            for (screen in pages) {
                compose.runOnIdle { mode = design; dark = night; dynamic = wallpaper; page = screen }
                compose.waitForIdle()
                capture(output, "$design-$night-$wallpaper-$screen")
                if (screen in listOf("ab", "add-task", "edit-plan")) {
                    compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
                    compose.waitForIdle()
                    capture(output, "$design-$night-$wallpaper-$screen-scrolled")
                }
            }
        }
    }

    private fun capture(output: File, name: String) {
        compose.onNodeWithTag("appearance-screen").assertExists()
        File(output, "$name.png").outputStream().use {
            compose.onNodeWithTag("appearance-screen").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
