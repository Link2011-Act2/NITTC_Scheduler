package jp.linkserver.nittcsc.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import jp.linkserver.nittcsc.R
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import jp.linkserver.nittcsc.calendar.CalendarExporter
import jp.linkserver.nittcsc.calendar.TaskCalendarSync
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.LessonDraft
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.LongBreakEntity
import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.logic.CLASS_SLOTS
import jp.linkserver.nittcsc.logic.ClassSlot
import jp.linkserver.nittcsc.logic.ExportRange
import jp.linkserver.nittcsc.logic.ExportResult
import jp.linkserver.nittcsc.logic.generateClassSlots
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class AppTab(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Output(
        R.string.tab_output,
        Icons.Filled.TableChart,
        Icons.Outlined.TableChart
    ),
    Tasks(
        R.string.tab_tasks,
        Icons.AutoMirrored.Filled.Assignment,
        Icons.AutoMirrored.Outlined.Assignment
    ),
    Plans(
        R.string.tab_plans,
        Icons.Filled.Event,
        Icons.Filled.Event
    ),
    Timetable(
        R.string.tab_timetable,
        Icons.Filled.EditCalendar,
        Icons.Outlined.EditCalendar
    ),
    AbTable(
        R.string.tab_ab_table,
        Icons.Filled.CalendarMonth,
        Icons.Outlined.CalendarMonth
    )
}

private enum class OutputDisplayMode(@StringRes val labelRes: Int) {
    DAY(R.string.display_mode_day),
    WEEK(R.string.display_mode_week)
}

private data class DayTypeVisual(
    val container: Color,
    val content: Color
)

private val LegacyTaskBadgeRed = Color(0xFFBA1A1A)
private val LegacyTaskBadgeContainer = Color(0xFF93000A)
private val LegacyTaskBadgeOnContainer = Color(0xFFFFDAD6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NittcSchedulerApp(viewModel: SchedulerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Output) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showVlmImport by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showOssLicenses by rememberSaveable { mutableStateOf(false) }
    var showTaskEditor by rememberSaveable { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var focusedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPlanEditor by rememberSaveable { mutableStateOf(false) }
    var editingPlanId by rememberSaveable { mutableStateOf<Long?>(null) }
    var focusedPlanId by rememberSaveable { mutableStateOf<Long?>(null) }
    var prefillSubject by rememberSaveable { mutableStateOf("") }
    var prefillTeacher by rememberSaveable { mutableStateOf("") }
    var transientTabOrigin by rememberSaveable { mutableStateOf<AppTab?>(null) }
    var transientTabTarget by rememberSaveable { mutableStateOf<AppTab?>(null) }

    fun navigateToTabFromAction(target: AppTab) {
        if (selectedTab != target) {
            transientTabOrigin = selectedTab
            transientTabTarget = target
            selectedTab = target
        }
    }

    fun clearTransientTabNavigation() {
        transientTabOrigin = null
        transientTabTarget = null
    }

    BackHandler(enabled = showOssLicenses) { showOssLicenses = false }
    BackHandler(enabled = showAbout && !showOssLicenses) {
        showAbout = false
        showSettings = true
    }
    BackHandler(enabled = showSettings && !showAbout && !showOssLicenses) { showSettings = false }
    BackHandler(enabled = showVlmImport) { showVlmImport = false }
    BackHandler(enabled = transientTabOrigin != null && transientTabTarget == selectedTab) {
        selectedTab = transientTabOrigin ?: selectedTab
        transientTabOrigin = null
        transientTabTarget = null
        focusedTaskId = null
        focusedPlanId = null
    }
    BackHandler(enabled = showTaskEditor) {
        showTaskEditor = false
        editingTaskId = null
            prefillSubject = ""
            prefillTeacher = ""
    }
    BackHandler(enabled = showPlanEditor) {
        showPlanEditor = false
        editingPlanId = null
            prefillSubject = ""
            prefillTeacher = ""
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    var pendingTaskCalendarSync by remember { mutableStateOf<TaskEntity?>(null) }
    var pendingPlanCalendarSync by remember { mutableStateOf<PlanEntity?>(null) }
    var pendingToggleAddTasksToCalendar by remember { mutableStateOf<Boolean?>(null) }
    var pendingManualCalendarSyncTab by rememberSaveable { mutableStateOf<AppTab?>(null) }
    var showTaskCalendarSyncDialog by rememberSaveable { mutableStateOf(false) }
    val msgTaskCalendarAutoDisabled = stringResource(R.string.msg_task_calendar_auto_disabled)
    val msgTaskCalendarSyncSkipped = stringResource(R.string.msg_task_calendar_sync_skipped)

    // POST_NOTIFICATIONS 権限をリクエスト（Android 13以上）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("NittcSchedulerApp", "通知権限が許可されました")
        } else {
            android.util.Log.w("NittcSchedulerApp", "通知権限が拒否されました")
        }
    }

    // アプリ起動時に通知権限をリクエスト（Android 13以上）
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    var pendingExportRange by remember { mutableStateOf<ExportRange?>(null) }
    val msgNoCalendarPerm = stringResource(R.string.msg_no_calendar_permission)
    val msgNoTasksToSync = stringResource(R.string.msg_no_tasks_to_sync)

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.READ_CALENDAR] == true &&
            result[Manifest.permission.WRITE_CALENDAR] == true

        pendingExportRange?.let { range ->
            pendingExportRange = null
            if (granted) {
                appScope.launch {
                    val lessons = viewModel.generateLessons(range)
                    val exportResult = withContext(Dispatchers.IO) {
                        CalendarExporter(context).export(lessons)
                    }
                    snackbarHostState.showSnackbar(exportResult.message)
                }
            } else {
                appScope.launch { snackbarHostState.showSnackbar(msgNoCalendarPerm) }
            }
        }

        pendingManualCalendarSyncTab?.let { syncTab ->
            pendingManualCalendarSyncTab = null
            if (granted) {
                appScope.launch {
                    val (updatedTasks, updatedPlans, syncedCount) = withContext(Dispatchers.IO) {
                        val sync = TaskCalendarSync(context)
                        val changedTasks = mutableListOf<TaskEntity>()
                        val changedPlans = mutableListOf<PlanEntity>()
                        var successCount = 0
                        when (syncTab) {
                            AppTab.Tasks -> uiState.tasks.forEach { task ->
                                val eventId = sync.upsertTaskEvent(task) ?: return@forEach
                                successCount++
                                if (task.calendarEventId != eventId) {
                                    changedTasks += task.copy(calendarEventId = eventId)
                                }
                            }
                            AppTab.Plans -> uiState.plans.forEach { plan ->
                                val eventId = sync.upsertPlanEvent(plan) ?: return@forEach
                                successCount++
                                if (plan.calendarEventId != eventId) {
                                    changedPlans += plan.copy(calendarEventId = eventId)
                                }
                            }
                            else -> Unit
                        }
                        Triple(changedTasks, changedPlans, successCount)
                    }
                    viewModel.saveTasksSilently(updatedTasks)
                    viewModel.savePlansSilently(updatedPlans)
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.msg_tasks_synced_to_calendar, syncedCount)
                    )
                }
            } else {
                appScope.launch { snackbarHostState.showSnackbar(msgTaskCalendarSyncSkipped) }
            }
        }

        pendingToggleAddTasksToCalendar?.let { enable ->
            pendingToggleAddTasksToCalendar = null
            if (enable && !granted) {
                viewModel.toggleAddTasksToCalendar(false)
                appScope.launch { snackbarHostState.showSnackbar(msgTaskCalendarAutoDisabled) }
            } else {
                viewModel.toggleAddTasksToCalendar(enable)
            }
        }

        pendingTaskCalendarSync?.let { pendingTask ->
            pendingTaskCalendarSync = null
            appScope.launch {
                val taskToSave = if (granted) {
                    withContext(Dispatchers.IO) {
                        val eventId = TaskCalendarSync(context).upsertTaskEvent(pendingTask)
                        if (eventId != null) pendingTask.copy(calendarEventId = eventId) else pendingTask
                    }
                } else {
                    snackbarHostState.showSnackbar(msgTaskCalendarSyncSkipped)
                    pendingTask
                }
                viewModel.saveTask(taskToSave)
            }
        }

        pendingPlanCalendarSync?.let { pendingPlan ->
            pendingPlanCalendarSync = null
            appScope.launch {
                val planToSave = if (granted) {
                    withContext(Dispatchers.IO) {
                        val eventId = TaskCalendarSync(context).upsertPlanEvent(pendingPlan)
                        if (eventId != null) pendingPlan.copy(calendarEventId = eventId) else pendingPlan
                    }
                } else {
                    snackbarHostState.showSnackbar(msgTaskCalendarSyncSkipped)
                    pendingPlan
                }
                viewModel.savePlan(planToSave)
            }
        }
    }

    fun requestCalendarExport(range: ExportRange) {
        if (hasCalendarPermission(context)) {
            appScope.launch {
                val lessons = viewModel.generateLessons(range)
                val exportResult = withContext(Dispatchers.IO) {
                    CalendarExporter(context).export(lessons)
                }
                snackbarHostState.showSnackbar(exportResult.message)
            }
        } else {
            pendingExportRange = range
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    fun syncItemsToCalendarManually(tab: AppTab) {
        val hasItems = when (tab) {
            AppTab.Tasks -> uiState.tasks.isNotEmpty()
            AppTab.Plans -> uiState.plans.isNotEmpty()
            else -> false
        }

        if (!hasItems) {
            appScope.launch { snackbarHostState.showSnackbar(msgNoTasksToSync) }
            return
        }

        if (hasCalendarPermission(context)) {
            appScope.launch {
                val (updatedTasks, updatedPlans, syncedCount) = withContext(Dispatchers.IO) {
                    val sync = TaskCalendarSync(context)
                    val changedTasks = mutableListOf<TaskEntity>()
                    val changedPlans = mutableListOf<PlanEntity>()
                    var successCount = 0
                    when (tab) {
                        AppTab.Tasks -> uiState.tasks.forEach { task ->
                            val eventId = sync.upsertTaskEvent(task) ?: return@forEach
                            successCount++
                            if (task.calendarEventId != eventId) {
                                changedTasks += task.copy(calendarEventId = eventId)
                            }
                        }
                        AppTab.Plans -> uiState.plans.forEach { plan ->
                            val eventId = sync.upsertPlanEvent(plan) ?: return@forEach
                            successCount++
                            if (plan.calendarEventId != eventId) {
                                changedPlans += plan.copy(calendarEventId = eventId)
                            }
                        }
                        else -> Unit
                    }
                    Triple(changedTasks, changedPlans, successCount)
                }
                viewModel.saveTasksSilently(updatedTasks)
                viewModel.savePlansSilently(updatedPlans)
                snackbarHostState.showSnackbar(
                    context.getString(R.string.msg_tasks_synced_to_calendar, syncedCount)
                )
            }
        } else {
            pendingManualCalendarSyncTab = tab
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    val currentScreenResource = when {
        showOssLicenses -> "oss"
        showAbout -> "about"
        showVlmImport -> "vlm"
        showSettings -> "settings"
        else -> "main"
    }

    AnimatedContent(
        targetState = currentScreenResource,
        transitionSpec = {
            val spec = tween<IntOffset>(220, easing = FastOutSlowInEasing)
            if (targetState != "main") {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it / 2 } + fadeOut()
            } else {
                slideInHorizontally { -it / 2 } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            }
        },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            "oss" -> {
                OssLicensesScreen(onBack = { showOssLicenses = false })
            }
            "about" -> {
                AboutScreen(
                    onBack = {
                        showAbout = false
                        showSettings = true
                    },
                    onOssLicenses = { showOssLicenses = true }
                )
            }
            "vlm" -> {
                VlmImportScreen(
                    hfToken = uiState.settings?.hfToken,
                    onUpdateHfToken = viewModel::updateHfToken,
                    onBack = { showVlmImport = false },
                    onLessonsGenerated = { lessons ->
                        lessons.forEach { lesson ->
                            viewModel.saveLesson(lesson.dayOfWeek, lesson.slotIndex, lesson.draft)
                        }
                    },
                    onAbTableGenerated = { abMap ->
                        abMap.forEach { (dateStr, dayType) ->
                            val date = java.time.LocalDate.parse(dateStr)
                            viewModel.saveDayType(date, dayType)
                        }
                    },
                    state = uiState,
                    existingLessons = uiState.lessons,
                    existingDayTypeMap = uiState.dayTypeMap
                )
            }
            "settings" -> {
                SettingsScreen(
                    state = uiState,
                    onBack = { showSettings = false },
                    onAbout = {
                        showSettings = true
                        showAbout = true
                    },
                    onToggleLocalAi = viewModel::toggleLocalAi,
                    onToggleDrawerNavigation = viewModel::toggleDrawerNavigation,
                    onToggleAddTasksToCalendar = { enabled ->
                        if (enabled && !hasCalendarPermission(context)) {
                            pendingToggleAddTasksToCalendar = true
                            calendarPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        } else {
                            viewModel.toggleAddTasksToCalendar(enabled)
                        }
                    },
                    onToggleCurrentTimeMarker = viewModel::toggleCurrentTimeMarker,
                    onToggleUnifyTaskPlanView = viewModel::toggleUnifyTaskPlanView,
                    onUpdateScheduleSettings = viewModel::updateScheduleSettingsSilently,
                    onExportAllAsJson = { viewModel.exportAllData() },
                    onImportAllFromJson = viewModel::importAllData
                )
            }
            "main" -> {
                val useDrawerNavigation = uiState.settings?.useDrawerNavigation ?: false
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val drawerScope = rememberCoroutineScope()

                val taskSubjectSuggestions = uiState.lessons.values
                    .flatMap { lesson ->
                        listOf(
                            lesson.weeklySubject,
                            lesson.aSubject,
                            lesson.bSubject
                        )
                    }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                val taskTeacherCandidates = uiState.lessons.values
                    .flatMap { lesson ->
                        listOf(
                            lesson.weeklySubject to lesson.weeklyTeacher,
                            lesson.aSubject to lesson.aTeacher,
                            lesson.bSubject to lesson.bTeacher
                        )
                    }
                    .map { (subject, teacher) -> subject.trim() to teacher.trim() }
                    .filter { (subject, teacher) -> subject.isNotBlank() && teacher.isNotBlank() }
                    .groupBy(
                        keySelector = { it.first },
                        valueTransform = { it.second }
                    )
                    .mapValues { (_, teachers) -> teachers.distinct().sorted() }
                val editingTask = editingTaskId?.let { id ->
                    uiState.tasks.firstOrNull { it.id == id }
                }
                val editingPlan = editingPlanId?.let { id ->
                    uiState.plans.firstOrNull { it.id == id }
                }
                val prefillDueHour = uiState.settings?.firstPeriodStartHour ?: 8
                val prefillDueMinute = uiState.settings?.firstPeriodStartMinute ?: 40
                val prefillTaskTemplate = if (editingTask == null && !showPlanEditor && prefillSubject.isNotBlank()) {
                    TaskEntity(
                        subject = prefillSubject,
                        teacher = prefillTeacher.takeIf { it.isNotBlank() },
                        title = "",
                        dueDate = LocalDate.now(),
                        dueHour = prefillDueHour,
                        dueMinute = prefillDueMinute,
                        createdDate = LocalDate.now()
                    )
                } else null
                val prefillPlanTemplate = if (editingPlan == null && showPlanEditor && prefillSubject.isNotBlank()) {
                    TaskEntity(
                        subject = prefillSubject,
                        teacher = prefillTeacher.takeIf { it.isNotBlank() },
                        title = "",
                        dueDate = LocalDate.now(),
                        dueHour = prefillDueHour,
                        dueMinute = prefillDueMinute,
                        createdDate = LocalDate.now()
                    )
                } else null
                val isAnyEditorVisible = showTaskEditor || showPlanEditor

                AnimatedContent(
                    targetState = isAnyEditorVisible,
                    transitionSpec = {
                        val spec = tween<IntOffset>(220, easing = FastOutSlowInEasing)
                        if (targetState) {
                            slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it / 3 } + fadeOut()
                        } else {
                            slideInHorizontally { -it / 3 } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                        }
                    },
                    label = "TaskEditorTransition"
                ) { isTaskEditorVisible ->
                    if (isTaskEditorVisible) {
                        AddTaskScreen(
                            task = if (showPlanEditor) (editingPlan?.toTaskEntityLike() ?: prefillPlanTemplate) else (editingTask ?: prefillTaskTemplate),
                            subjectSuggestions = taskSubjectSuggestions,
                            subjectTeacherCandidates = taskTeacherCandidates,
                            defaultDueHour = uiState.settings?.firstPeriodStartHour ?: 8,
                            defaultDueMinute = uiState.settings?.firstPeriodStartMinute ?: 40,
                            isPlan = showPlanEditor,
                            onResolveNextLessonDateTime = { subject, teacher, fromDate, fromTime ->
                                viewModel.calculateNextLessonDateTime(
                                    subject = subject,
                                    teacher = teacher,
                                    useTeacherMatching = true,
                                    fromDate = fromDate,
                                    fromTime = fromTime
                                )
                            },
                            onResolvePreviousLessonDateTime = { subject, teacher, fromDate, fromTime ->
                                viewModel.calculatePreviousLessonDateTime(
                                    subject = subject,
                                    teacher = teacher,
                                    useTeacherMatching = true,
                                    fromDate = fromDate,
                                    currentTime = fromTime
                                )
                            },
                            onResolveNextLessonDateTimeSkipCurrent = { subject, teacher, fromDate, fromTime ->
                                viewModel.calculateNextLessonDateTimeSkipCurrent(
                                    subject = subject,
                                    teacher = teacher,
                                    useTeacherMatching = true,
                                    fromDate = fromDate,
                                    currentTime = fromTime
                                )
                            },
                            onSave = { task ->
                                if (showPlanEditor) {
                                    val origin = editingPlan
                                    val plan = task.toPlanEntityLike(existing = origin)
                                    val shouldSyncTaskToCalendar = uiState.settings?.addTasksToCalendar == true
                                    if (shouldSyncTaskToCalendar && !hasCalendarPermission(context)) {
                                        pendingPlanCalendarSync = plan
                                        calendarPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALENDAR,
                                                Manifest.permission.WRITE_CALENDAR
                                            )
                                        )
                                    } else if (shouldSyncTaskToCalendar) {
                                        appScope.launch {
                                            val planWithCalendar = withContext(Dispatchers.IO) {
                                                val eventId = TaskCalendarSync(context).upsertPlanEvent(plan)
                                                if (eventId != null) plan.copy(calendarEventId = eventId) else plan
                                            }
                                            viewModel.savePlan(planWithCalendar)
                                        }
                                    } else {
                                        viewModel.savePlan(plan)
                                    }
                                    showPlanEditor = false
                                    editingPlanId = null
                                       prefillSubject = ""
                                       prefillTeacher = ""
                                } else {
                                    val shouldSyncTaskToCalendar = uiState.settings?.addTasksToCalendar == true
                                    if (shouldSyncTaskToCalendar && !hasCalendarPermission(context)) {
                                        pendingTaskCalendarSync = task
                                        calendarPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALENDAR,
                                                Manifest.permission.WRITE_CALENDAR
                                            )
                                        )
                                    } else if (shouldSyncTaskToCalendar) {
                                        appScope.launch {
                                            val taskWithCalendar = withContext(Dispatchers.IO) {
                                                val eventId = TaskCalendarSync(context).upsertTaskEvent(task)
                                                if (eventId != null) task.copy(calendarEventId = eventId) else task
                                            }
                                            viewModel.saveTask(taskWithCalendar)
                                        }
                                    } else {
                                        viewModel.saveTask(task)
                                    }
                                    showTaskEditor = false
                                    editingTaskId = null
                                       prefillSubject = ""
                                       prefillTeacher = ""
                                }
                            },
                            onBack = {
                                if (showPlanEditor) {
                                    showPlanEditor = false
                                    editingPlanId = null
                                } else {
                                    showTaskEditor = false
                                    editingTaskId = null
                                }
                                   prefillSubject = ""
                                   prefillTeacher = ""
                            }
                        )
                    } else {
                        val tabContent: @Composable (PaddingValues) -> Unit = { padding ->
                            Crossfade(
                                targetState = selectedTab,
                                modifier = Modifier.fillMaxSize(),
                                label = "tab"
                            ) { tab ->
                                when (tab) {
                                    AppTab.Output -> OutputScreen(
                                        modifier = Modifier.padding(padding),
                                        state = uiState,
                                        dayTypeForDate = { date -> viewModel.dayTypeForDate(date, uiState.dayTypeMap) },
                                        dayTypeEntityForDate = { date -> uiState.dayTypeEntities[date] },
                                        resolveLesson = { date, slot ->
                                            viewModel.resolveLessonForDate(
                                                date = date,
                                                slotIndex = slot,
                                                lessons = uiState.lessons,
                                                dayTypeMap = uiState.dayTypeMap,
                                                dayTypeEntities = uiState.dayTypeEntities
                                            )
                                        },
                                        onShiftDate = viewModel::shiftResultDate,
                                        onPickDate = viewModel::setResultDate,
                                        onSaveLessonOverride = viewModel::saveLessonOverride,
                                        onClearLessonOverride = viewModel::clearLessonOverride,
                                        onOpenTask = { task ->
                                            navigateToTabFromAction(AppTab.Tasks)
                                            editingTaskId = null
                                            showTaskEditor = false
                                            showPlanEditor = false
                                            focusedTaskId = task.id.takeIf { it > 0 }
                                        },
                                        onOpenPlan = { plan ->
                                            navigateToTabFromAction(AppTab.Plans)
                                            editingPlanId = null
                                            showTaskEditor = false
                                            showPlanEditor = false
                                            focusedPlanId = plan.id.takeIf { it > 0 }
                                        },
                                        onExportWithPermission = ::requestCalendarExport,
                                           onAddFromLesson = { subject, teacher, isPlan ->
                                               prefillSubject = subject
                                               prefillTeacher = teacher
                                               if (isPlan) {
                                                   showPlanEditor = true
                                                   showTaskEditor = false
                                                   editingPlanId = null
                                               } else {
                                                   showTaskEditor = true
                                                   showPlanEditor = false
                                                   editingTaskId = null
                                               }
                                           }
                                    )

                                    AppTab.Tasks -> {
                                        val unifyTaskPlanView = uiState.settings?.unifyTaskPlanView ?: false
                                        if (unifyTaskPlanView) {
                                            // 統合ビュー：課題・予定を上部タブで切り替え
                                            UnifiedTaskPlanScreen(
                                                modifier = Modifier.padding(padding),
                                                uiState = uiState,
                                                onOpenTask = { task ->
                                                    editingTaskId = task.id.takeIf { it > 0 }
                                                    showTaskEditor = true
                                                    showPlanEditor = false
                                                },
                                                onDeleteTask = { task ->
                                                    if (task.calendarEventId != null && hasCalendarPermission(context)) {
                                                        appScope.launch(Dispatchers.IO) {
                                                            TaskCalendarSync(context).deleteTaskEvent(task.calendarEventId)
                                                        }
                                                    }
                                                    viewModel.deleteTask(task)
                                                },
                                                onMarkTaskComplete = viewModel::markTaskAsComplete,
                                                onMarkTaskIncomplete = viewModel::markTaskAsIncomplete,
                                                onOpenPlan = { plan ->
                                                    editingPlanId = plan.id.takeIf { it > 0 }
                                                    showPlanEditor = true
                                                    showTaskEditor = false
                                                },
                                                onDeletePlan = { plan ->
                                                    if (plan.calendarEventId != null && hasCalendarPermission(context)) {
                                                        appScope.launch(Dispatchers.IO) {
                                                            TaskCalendarSync(context).deletePlanEvent(plan.calendarEventId)
                                                        }
                                                    }
                                                    viewModel.deletePlan(plan)
                                                },
                                                onMarkPlanComplete = viewModel::markPlanAsComplete,
                                                onMarkPlanIncomplete = viewModel::markPlanAsIncomplete
                                            )
                                        } else {
                                            // 従来のビュー：課題のみ
                                            TaskScreen(
                                                modifier = Modifier.padding(padding),
                                                tasks = uiState.incompleteTasks,
                                                completedTasks = uiState.tasks.filter { it.isCompleted },
                                                focusTaskId = focusedTaskId,
                                                onFocusHandled = { focusedTaskId = null },
                                                onOpenTaskEditor = { task ->
                                                    editingTaskId = task?.id?.takeIf { it > 0 }
                                                    showTaskEditor = true
                                                    showPlanEditor = false
                                                },
                                                onDeleteTask = { task ->
                                                    if (task.calendarEventId != null && hasCalendarPermission(context)) {
                                                        appScope.launch(Dispatchers.IO) {
                                                            TaskCalendarSync(context).deleteTaskEvent(task.calendarEventId)
                                                        }
                                                    }
                                                    viewModel.deleteTask(task)
                                                },
                                                onMarkComplete = viewModel::markTaskAsComplete,
                                                onMarkIncomplete = viewModel::markTaskAsIncomplete
                                            )
                                        }
                                    }

                                    AppTab.Plans -> TaskScreen(
                                        modifier = Modifier.padding(padding),
                                        tasks = uiState.incompletePlans.map { it.toTaskEntityLike() },
                                        completedTasks = uiState.plans.filter { it.isCompleted }.map { it.toTaskEntityLike() },
                                        focusTaskId = focusedPlanId,
                                        onFocusHandled = { focusedPlanId = null },
                                        onOpenTaskEditor = { task ->
                                            editingPlanId = task?.id?.takeIf { it > 0 }
                                            showPlanEditor = true
                                            showTaskEditor = false
                                        },
                                        onDeleteTask = { task ->
                                            uiState.plans.firstOrNull { it.id == task.id }?.let { plan ->
                                                if (plan.calendarEventId != null && hasCalendarPermission(context)) {
                                                    appScope.launch(Dispatchers.IO) {
                                                        TaskCalendarSync(context).deletePlanEvent(plan.calendarEventId)
                                                    }
                                                }
                                                viewModel.deletePlan(plan)
                                            }
                                        },
                                        onMarkComplete = { task ->
                                            uiState.plans.firstOrNull { it.id == task.id }?.let { viewModel.markPlanAsComplete(it) }
                                        },
                                        onMarkIncomplete = { task ->
                                            uiState.plans.firstOrNull { it.id == task.id }?.let { viewModel.markPlanAsIncomplete(it) }
                                        },
                                        isPlan = true
                                    )

                                    AppTab.Timetable -> TimetableInputScreen(
                                        modifier = Modifier.padding(padding),
                                        state = uiState,
                                        onSelectDay = viewModel::selectDayOfWeek,
                                        onAutoSaveLesson = viewModel::saveLessonWithoutNotification,
                                        onSaveLesson = viewModel::saveLesson
                                    )

                                    AppTab.AbTable -> AbTableScreen(
                                        modifier = Modifier.padding(padding),
                                        state = uiState,
                                        onSaveDayTypes = viewModel::saveDayTypes,
                                        onSaveLessonOverride = viewModel::saveLessonOverride,
                                        onClearLessonOverride = viewModel::clearLessonOverride,
                                        onResetFiscalYear = viewModel::resetFiscalYear,
                                        onUpdateTerm = viewModel::updateTerm,
                                        onSaveBreak = viewModel::saveLongBreak,
                                        onDeleteBreak = viewModel::deleteLongBreak,
                                        dayTypeForDate = { date -> viewModel.dayTypeForDate(date, uiState.dayTypeMap) },
                                        dayTypeEntityForDate = { date -> uiState.dayTypeEntities[date] }
                                    )
                                }
                            }
                        }
                        val selectedTabHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

                        val appScaffold: @Composable () -> Unit = {
                            val topBarTitle = if (useDrawerNavigation) {
                                stringResource(selectedTab.labelRes)
                            } else {
                                stringResource(R.string.app_name)
                            }
                            val showTaskSyncAction = (selectedTab == AppTab.Tasks || selectedTab == AppTab.Plans) && uiState.settings?.addTasksToCalendar == true

                            Scaffold(
                                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                                topBar = {
                                    TopAppBar(
                                        title = { Text(topBarTitle) },
                                        navigationIcon = {
                                            if (useDrawerNavigation) {
                                                IconButton(
                                                    onClick = {
                                                        drawerScope.launch {
                                                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_open_navigation_menu))
                                                }
                                            }
                                        },
                                        actions = {
                                            if (showTaskSyncAction) {
                                                IconButton(onClick = { showTaskCalendarSyncDialog = true }) {
                                                    Icon(Icons.Filled.Autorenew, contentDescription = stringResource(R.string.cd_sync_tasks_to_calendar))
                                                }
                                            }
                                            if (uiState.settings?.enableLocalAi == true) {
                                                IconButton(onClick = { showVlmImport = true }) {
                                                    Icon(Icons.Filled.AutoFixHigh, contentDescription = stringResource(R.string.cd_ai_import))
                                                }
                                            }
                                            IconButton(onClick = { showSettings = true }) {
                                                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                                            }
                                        }
                                    )
                                },
                                bottomBar = {
                                    if (!useDrawerNavigation) {
                                        NavigationBar {
                                            val unifyTaskPlanView = uiState.settings?.unifyTaskPlanView ?: false
                                            AppTab.entries.forEach { tab ->
                                                // 統合ビューが有効な場合、Plans タブを非表示
                                                if (unifyTaskPlanView && tab == AppTab.Plans) return@forEach
                                                
                                                val isSelected = selectedTab == tab
                                                val tabLabel = if (unifyTaskPlanView && tab == AppTab.Tasks)
                                                    "ToDo"
                                                else
                                                    stringResource(tab.labelRes)
                                                NavigationBarItem(
                                                    selected = isSelected,
                                                    onClick = {
                                                        clearTransientTabNavigation()
                                                        selectedTab = tab
                                                    },
                                                    colors = NavigationBarItemDefaults.colors(
                                                        indicatorColor = selectedTabHighlight
                                                    ),
                                                    icon = {
                                                        Icon(
                                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                            contentDescription = tabLabel
                                                        )
                                                    },
                                                    label = { Text(tabLabel) }
                                                )
                                            }
                                        }
                                    }
                                }
                            ) { padding ->
                                tabContent(padding)
                            }
                        }

                        if (showTaskCalendarSyncDialog) {
                            AlertDialog(
                                onDismissRequest = { showTaskCalendarSyncDialog = false },
                                title = { Text(stringResource(R.string.dialog_sync_tasks_to_calendar_title)) },
                                text = { Text(stringResource(R.string.dialog_sync_tasks_to_calendar_message)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showTaskCalendarSyncDialog = false
                                        syncItemsToCalendarManually(selectedTab)
                                    }) {
                                        Text(stringResource(R.string.btn_ok))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showTaskCalendarSyncDialog = false }) {
                                        Text(stringResource(R.string.btn_cancel))
                                    }
                                }
                            )
                        }

                        if (useDrawerNavigation) {
                            BackHandler(enabled = drawerState.isOpen) {
                                drawerScope.launch { drawerState.close() }
                            }

                            ModalNavigationDrawer(
                                drawerState = drawerState,
                                drawerContent = {
                                    ModalDrawerSheet {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                                        )

                                        AppTab.entries.forEach { tab ->
                                            val isSelected = selectedTab == tab
                                            val unifyTaskPlanView = uiState.settings?.unifyTaskPlanView ?: false
                                            // 統合ビューが有効な場合、Plans タブを非表示
                                            if (unifyTaskPlanView && tab == AppTab.Plans) return@forEach
                                            val tabLabel = if (unifyTaskPlanView && tab == AppTab.Tasks)
                                                "ToDo"
                                            else
                                                stringResource(tab.labelRes)
                                            NavigationDrawerItem(
                                                label = { Text(tabLabel) },
                                                selected = isSelected,
                                                colors = NavigationDrawerItemDefaults.colors(
                                                    selectedContainerColor = selectedTabHighlight
                                                ),
                                                icon = {
                                                    Icon(
                                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    clearTransientTabNavigation()
                                                    selectedTab = tab
                                                    drawerScope.launch { drawerState.close() }
                                                }
                                            )
                                        }
                                    }
                                }
                            ) {
                                appScaffold()
                            }
                        } else {
                            appScaffold()
                        }
                    }
                }
}
}
}
}

@Composable
private fun ScreenHeadline(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TimetableInputScreen(
    modifier: Modifier,
    state: SchedulerUiState,
    onSelectDay: (Int) -> Unit,
    onAutoSaveLesson: (Int, Int, LessonDraft) -> Unit,
    onSaveLesson: (Int, Int, LessonDraft) -> Unit
) {
    val dayLabels = listOf(
        DayOfWeek.MONDAY.value to R.string.weekday_monday,
        DayOfWeek.TUESDAY.value to R.string.weekday_tuesday,
        DayOfWeek.WEDNESDAY.value to R.string.weekday_wednesday,
        DayOfWeek.THURSDAY.value to R.string.weekday_thursday,
        DayOfWeek.FRIDAY.value to R.string.weekday_friday
    )

    Column(modifier = modifier.fillMaxSize()) {
        // 曜日タブ（スクロールしても常に表示）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dayLabels.forEach { (dayValue, labelRes) ->
                val selected = state.selectedDayOfWeek == dayValue
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectDay(dayValue) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        val classSlots = remember(state.settings) { state.settings.toClassSlots() }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(classSlots) { slot ->
                val lesson = state.lessons[state.selectedDayOfWeek to slot.index]
                LessonEditorCard(
                    title = slot.label,
                    lesson = lesson,
                    onAutoSave = { draft -> onAutoSaveLesson(state.selectedDayOfWeek, slot.index, draft) },
                    onSave = { draft -> onSaveLesson(state.selectedDayOfWeek, slot.index, draft) }
                )
            }
        }
    }
}
@Composable
private fun LessonEditorCard(
    title: String,
    lesson: LessonEntity?,
    onAutoSave: (LessonDraft) -> Unit,
    onSave: (LessonDraft) -> Unit
) {
    var mode by remember { mutableStateOf(lesson?.mode ?: LessonMode.WEEKLY) }
    var weeklySubject by remember { mutableStateOf(lesson?.weeklySubject.orEmpty()) }
    var weeklyTeacher by remember { mutableStateOf(lesson?.weeklyTeacher.orEmpty()) }
    var weeklyLocation by remember { mutableStateOf(lesson?.weeklyLocation.orEmpty()) }
    var aSubject by remember { mutableStateOf(lesson?.aSubject.orEmpty()) }
    var aTeacher by remember { mutableStateOf(lesson?.aTeacher.orEmpty()) }
    var aLocation by remember { mutableStateOf(lesson?.aLocation.orEmpty()) }
    var bSubject by remember { mutableStateOf(lesson?.bSubject.orEmpty()) }
    var bTeacher by remember { mutableStateOf(lesson?.bTeacher.orEmpty()) }
    var bLocation by remember { mutableStateOf(lesson?.bLocation.orEmpty()) }

    LaunchedEffect(lesson?.id, title) {
        mode = lesson?.mode ?: LessonMode.WEEKLY
        weeklySubject = lesson?.weeklySubject.orEmpty()
        weeklyTeacher = lesson?.weeklyTeacher.orEmpty()
        weeklyLocation = lesson?.weeklyLocation.orEmpty()
        aSubject = lesson?.aSubject.orEmpty()
        aTeacher = lesson?.aTeacher.orEmpty()
        aLocation = lesson?.aLocation.orEmpty()
        bSubject = lesson?.bSubject.orEmpty()
        bTeacher = lesson?.bTeacher.orEmpty()
        bLocation = lesson?.bLocation.orEmpty()
    }

    // 入力があるたびに500ms後に自動保存
    val isInitialized = remember { mutableStateOf(false) }
    LaunchedEffect(lesson?.id, title) { isInitialized.value = false }
    LaunchedEffect(
        mode, weeklySubject, weeklyTeacher, weeklyLocation,
        aSubject, aTeacher, aLocation, bSubject, bTeacher, bLocation
    ) {
        if (!isInitialized.value) { isInitialized.value = true; return@LaunchedEffect }
        kotlinx.coroutines.delay(500)
        onAutoSave(
            LessonDraft(
                mode = mode,
                weeklySubject = weeklySubject, weeklyTeacher = weeklyTeacher, weeklyLocation = weeklyLocation,
                aSubject = aSubject, aTeacher = aTeacher, aLocation = aLocation,
                bSubject = bSubject, bTeacher = bTeacher, bLocation = bLocation
            )
        )
    }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 校時バッジ
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            if (mode == LessonMode.WEEKLY) {
                Text(
                    stringResource(R.string.label_subject_name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextField(
                    value = weeklySubject,
                    onValueChange = { weeklySubject = it },
                    placeholder = { Text(stringResource(R.string.placeholder_not_set), style = MaterialTheme.typography.titleMedium) },
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = weeklyTeacher,
                        onValueChange = { weeklyTeacher = it },
                        placeholder = { Text(stringResource(R.string.placeholder_teacher)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = fieldColors
                    )
                    TextField(
                        value = weeklyLocation,
                        onValueChange = { weeklyLocation = it },
                        placeholder = { Text(stringResource(R.string.placeholder_location)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = fieldColors
                    )
                }
            } else {
                // A セクション: A: ラベルを左端に固定、右側に授業名+担当/場所
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "A",
                        modifier = Modifier.width(32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextField(
                            value = aSubject,
                            onValueChange = { aSubject = it },
                            placeholder = { Text(stringResource(R.string.placeholder_not_set), style = MaterialTheme.typography.titleMedium) },
                            textStyle = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = fieldColors
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(
                                value = aTeacher,
                                onValueChange = { aTeacher = it },
                                placeholder = { Text(stringResource(R.string.placeholder_teacher)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = fieldColors
                            )
                            TextField(
                                value = aLocation,
                                onValueChange = { aLocation = it },
                                placeholder = { Text(stringResource(R.string.placeholder_location)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = fieldColors
                            )
                        }
                    }
                }

                // B セクション
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "B",
                        modifier = Modifier.width(32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextField(
                            value = bSubject,
                            onValueChange = { bSubject = it },
                            placeholder = { Text(stringResource(R.string.placeholder_not_set), style = MaterialTheme.typography.titleMedium) },
                            textStyle = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = fieldColors
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(
                                value = bTeacher,
                                onValueChange = { bTeacher = it },
                                placeholder = { Text(stringResource(R.string.placeholder_teacher)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = fieldColors
                            )
                            TextField(
                                value = bLocation,
                                onValueChange = { bLocation = it },
                                placeholder = { Text(stringResource(R.string.placeholder_location)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = fieldColors
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // 下部: 隔週チェックボックス + 保存
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = mode == LessonMode.ALTERNATING,
                        onCheckedChange = { mode = if (it) LessonMode.ALTERNATING else LessonMode.WEEKLY }
                    )
                    Text(stringResource(R.string.label_alternating), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(
                    onClick = {
                        onSave(
                            LessonDraft(
                                mode = mode,
                                weeklySubject = weeklySubject,
                                weeklyTeacher = weeklyTeacher,
                                weeklyLocation = weeklyLocation,
                                aSubject = aSubject,
                                aTeacher = aTeacher,
                                aLocation = aLocation,
                                bSubject = bSubject,
                                bTeacher = bTeacher,
                                bLocation = bLocation
                            )
                        )
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            }
        }
    }
}

@Composable
private fun AbTableScreen(
    modifier: Modifier,
    state: SchedulerUiState,
    onSaveDayTypes: (List<LocalDate>, DayType) -> Unit,
    onSaveLessonOverride: (LocalDate, Int, DayType) -> Unit,
    onClearLessonOverride: (LocalDate) -> Unit,
    onResetFiscalYear: () -> Unit,
    onUpdateTerm: (LocalDate, LocalDate) -> Unit,
    onSaveBreak: (Long?, String, LocalDate, LocalDate) -> Unit,
    onDeleteBreak: (LongBreakEntity) -> Unit,
    dayTypeForDate: (LocalDate) -> DayType,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?
) {
    val settings = state.settings
    if (settings == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.label_loading))
        }
        return
    }

    var termStart by remember(settings.termStart) { mutableStateOf(settings.termStart) }
    var termEnd by remember(settings.termEnd) { mutableStateOf(settings.termEnd) }
    var showBreakDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val today = remember { LocalDate.now() }
    val weeks = remember(settings.termStart, settings.termEnd) {
        buildWeekRows(settings.termStart, settings.termEnd)
    }
    val currentWeeks = remember(weeks) { weeks.filter { !it.weekEnd.isBefore(today) } }
    val pastWeeks = remember(weeks) { weeks.filter { it.weekEnd.isBefore(today) } }
    val displayedWeeks = remember(currentWeeks, pastWeeks) {
        currentWeeks.map { DisplayWeekRow(it, false) } + pastWeeks.map { DisplayWeekRow(it, true) }
    }

    // ドラッグ状態（AbTableGrid から AbTableScreen に移動）
    val haptic = LocalHapticFeedback.current
    val dateBounds = remember(displayedWeeks) { HashMap<LocalDate, Rect>() }
    var dragStartDate by remember(displayedWeeks) { mutableStateOf<LocalDate?>(null) }
    var dragCurrentDate by remember(displayedWeeks) { mutableStateOf<LocalDate?>(null) }
    var dragTargetDayType by remember(displayedWeeks) { mutableStateOf<DayType?>(null) }
    var overrideEditingDate by remember(displayedWeeks) { mutableStateOf<LocalDate?>(null) }

    val displayDates = remember(displayedWeeks, settings.termStart, settings.termEnd) {
        displayedWeeks.flatMap { it.row.days }.filter { it in settings.termStart..settings.termEnd }
    }
    val previewDates = remember(displayDates, dragStartDate, dragCurrentDate) {
        val s = dragStartDate
        val c = dragCurrentDate ?: s
        if (s == null || c == null) emptySet()
        else {
            val si = displayDates.indexOf(s)
            val ei = displayDates.indexOf(c)
            if (si == -1 || ei == -1) emptySet()
            else displayDates.subList(minOf(si, ei), maxOf(si, ei) + 1).toSet()
        }
    }

    fun dateAtRoot(rootOffset: Offset): LocalDate? =
        dateBounds.entries.firstOrNull { (_, rect) -> rect.contains(rootOffset) }?.key

    fun resetDragState() { dragStartDate = null; dragCurrentDate = null; dragTargetDayType = null }

    fun dragSelectedDates(): List<LocalDate> {
        val s = dragStartDate ?: return emptyList()
        val c = dragCurrentDate ?: s
        val si = displayDates.indexOf(s)
        val ei = displayDates.indexOf(c)
        if (si == -1 || ei == -1) return emptyList()
        return displayDates.subList(minOf(si, ei), maxOf(si, ei) + 1)
    }

    fun commitDragRange() {
        val t = dragTargetDayType; val d = dragSelectedDates()
        if (t != null && d.isNotEmpty()) onSaveDayTypes(d, t)
        resetDragState()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.label_period_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.label_term_range, settings.termStart.format(dateFormatter), settings.termEnd.format(dateFormatter)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DatePickRow(
                        label = stringResource(R.string.label_start_date_plain),
                        date = termStart,
                        onDateChange = { termStart = it }
                    )
                    DatePickRow(
                        label = stringResource(R.string.label_end_date_plain),
                        date = termEnd,
                        onDateChange = { termEnd = it }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onUpdateTerm(termStart, termEnd) }) {
                            Icon(Icons.Filled.Event, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_apply_to_period))
                        }
                        OutlinedButton(onClick = onResetFiscalYear) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_back_to_original))
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.label_long_holidays), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Button(onClick = { showBreakDialog = true }) { Text(stringResource(R.string.btn_add)) }
                    }

                    if (state.longBreaks.isEmpty()) {
                        Text(stringResource(R.string.label_not_registered), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.longBreaks.forEach { longBreak ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${longBreak.name}: ${longBreak.startDate.format(dateFormatter)} - ${longBreak.endDate.format(dateFormatter)}",
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(onClick = { onDeleteBreak(longBreak) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.btn_delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { DayTypeLegend() }
        item { WeekHeader() }
        items(displayedWeeks, key = { it.row.weekStart }) { displayWeek ->
            WeekRow(
                row = displayWeek.row,
                settingsStart = settings.termStart,
                settingsEnd = settings.termEnd,
                dayTypeForDate = dayTypeForDate,
                dayTypeEntityForDate = dayTypeEntityForDate,
                onSaveDayTypes = onSaveDayTypes,
                onOpenLessonOverride = { date ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    overrideEditingDate = date
                },
                isPast = displayWeek.isPast,
                previewDates = previewDates,
                previewDayType = dragTargetDayType,
                onUpdateDayBounds = { date, rect -> dateBounds[date] = rect },
                onDragStartRoot = { rootOffset ->
                    val startDate = dateAtRoot(rootOffset) ?: return@WeekRow
                    dragStartDate = startDate
                    dragCurrentDate = startDate
                    dragTargetDayType = nextDayType(dayTypeForDate(startDate))
                },
                onDragRoot = { rootOffset ->
                    if (dragStartDate == null) return@WeekRow
                    dateAtRoot(rootOffset)?.let { date ->
                        if (date != dragCurrentDate) {
                            dragCurrentDate = date
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
                onDragEnd = { commitDragRange() },
                onDragCancel = { resetDragState() }
            )
        }
    }

    if (showBreakDialog) {
        AddBreakDialog(
            onDismiss = { showBreakDialog = false },
            onSave = { name, start, end ->
                onSaveBreak(null, name, start, end)
                if (name.isNotBlank()) {
                    showBreakDialog = false
                }
            }
        )
    }

    overrideEditingDate?.let { date ->
        val dayTypeEntity = dayTypeEntityForDate(date)
        LessonOverrideDialog(
            date = date,
            currentDayType = dayTypeForDate(date),
            currentOverrideDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek,
            currentOverrideDayType = dayTypeEntity?.overrideLessonDayType,
            showDayTypeSelector = false,
            onDismiss = { overrideEditingDate = null },
            onApply = { dayOfWeek, dayType ->
                onSaveLessonOverride(date, dayOfWeek, dayType)
                overrideEditingDate = null
            },
            onClear = if (dayTypeEntity?.overrideLessonDayOfWeek != null && dayTypeEntity.overrideLessonDayType != null) {
                {
                    onClearLessonOverride(date)
                    overrideEditingDate = null
                }
            } else {
                null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickRow(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86400000L)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(LocalDate.ofEpochDay(it / 86400000L)) }
                    showPicker = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        ) { DatePicker(state = state) }
    }
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label)
            Text(date.format(dateFormatter), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DayTypeLegend() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.legend_ab_toggle),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.legend_ab_override_long_press),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayChip(stringResource(R.string.daytype_a), dayTypeVisual(DayType.A))
                DayChip(stringResource(R.string.daytype_b), dayTypeVisual(DayType.B))
                DayChip(stringResource(R.string.daytype_holiday), dayTypeVisual(DayType.HOLIDAY))
            }
        }
    }
}

@Composable
private fun DayChip(label: String, visual: DayTypeVisual) {
    Box(
        modifier = Modifier
            .background(visual.container, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = visual.content, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeekHeader() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_period),
                modifier = Modifier.width(110.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            listOf(
                R.string.weekday_monday,
                R.string.weekday_tuesday,
                R.string.weekday_wednesday,
                R.string.weekday_thursday,
                R.string.weekday_friday
            ).forEach { dayRes ->
                Text(
                    text = stringResource(dayRes),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekRow(
    row: WeekRow,
    settingsStart: LocalDate,
    settingsEnd: LocalDate,
    dayTypeForDate: (LocalDate) -> DayType,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?,
    onSaveDayTypes: (List<LocalDate>, DayType) -> Unit,
    onOpenLessonOverride: (LocalDate) -> Unit,
    isPast: Boolean = false,
    previewDates: Set<LocalDate> = emptySet(),
    previewDayType: DayType? = null,
    onUpdateDayBounds: (LocalDate, Rect) -> Unit = { _, _ -> },
    onDragStartRoot: (Offset) -> Unit = {},
    onDragRoot: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {}
) {
    val rowBoundsRef = remember { arrayOfNulls<Rect>(1) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast) 0.38f else 1f)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${row.weekStart.monthValue}/${row.weekStart.dayOfMonth}-${row.weekEnd.monthValue}/${row.weekEnd.dayOfMonth}",
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { rowBoundsRef[0] = it.boundsInRoot() }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { localOffset ->
                            val rb = rowBoundsRef[0] ?: return@detectDragGestures
                            onDragStartRoot(Offset(rb.left + localOffset.x, rb.top + localOffset.y))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val rb = rowBoundsRef[0] ?: return@detectDragGestures
                            onDragRoot(Offset(rb.left + change.position.x, rb.top + change.position.y))
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel
                    )
                }
        ) {

        row.days.forEach { date ->
            if (date < settingsStart || date > settingsEnd) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("-") }
            } else {
                val previewActive = previewDayType != null && date in previewDates
                val shownDayType = if (previewActive) previewDayType else dayTypeForDate(date)
                val visual = dayTypeVisual(shownDayType)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .onGloballyPositioned { onUpdateDayBounds(date, it.boundsInRoot()) }
                        .then(
                            if (previewActive) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            } else {
                                Modifier
                            }
                        )
                        .background(visual.container, RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = { onSaveDayTypes(listOf(date), nextDayType(dayTypeForDate(date))) },
                            onLongClick = { onOpenLessonOverride(date) }
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val label = if (previewActive) {
                        stringResource(dayTypeRes(shownDayType))
                    } else {
                        dayTypeDisplayText(shownDayType, dayTypeEntityForDate(date)?.overrideLessonDayOfWeek)
                    }
                    Text(label, color = visual.content, fontWeight = FontWeight.Bold)
                }
            }
        }
        } // inner Row (drag area)
    } // outer Row
}

private fun nextDayType(current: DayType): DayType = when (current) {
    DayType.A -> DayType.B
    DayType.B -> DayType.HOLIDAY
    DayType.HOLIDAY -> DayType.A
}

@Composable
private fun rememberCurrentTime(): LocalTime {
    val currentTime by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            val delayMs = 60000L - (System.currentTimeMillis() % 60000L) + 50L
            kotlinx.coroutines.delay(delayMs)
        }
    }
    return currentTime
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBreakDialog(
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, LocalDate) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = start.toEpochDay() * 86400000L)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { start = LocalDate.ofEpochDay(it / 86400000L) }
                    showStartPicker = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.btn_cancel)) } }
        ) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = end.toEpochDay() * 86400000L)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { end = LocalDate.ofEpochDay(it / 86400000L) }
                    showEndPicker = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.btn_cancel)) } }
        ) { DatePicker(state = state) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_add_break)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_break_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { showStartPicker = true }) {
                    Text(stringResource(R.string.label_start_date, start.format(dateFormatter)))
                }
                OutlinedButton(onClick = { showEndPicker = true }) {
                    Text(stringResource(R.string.label_end_date, end.format(dateFormatter)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, start, end) }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun OutputScreen(
    modifier: Modifier,
    state: SchedulerUiState,
    dayTypeForDate: (LocalDate) -> DayType,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?,
    resolveLesson: (LocalDate, Int) -> ResolvedLesson?,
    onShiftDate: (Long) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onSaveLessonOverride: (LocalDate, Int, DayType) -> Unit,
    onClearLessonOverride: (LocalDate) -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onOpenPlan: (PlanEntity) -> Unit,
    onExportWithPermission: (ExportRange) -> Unit,
    onAddFromLesson: ((subject: String, teacher: String, isPlan: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val selectedDate = state.selectedResultDate
    var displayMode by rememberSaveable { mutableStateOf(OutputDisplayMode.DAY) }
    val dayType = dayTypeForDate(selectedDate)
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekDates = remember(selectedDate) { (0L..4L).map { weekStart.plusDays(it) } }
    val isCurrentRangeToday = remember(displayMode, selectedDate, weekDates, today) {
        if (displayMode == OutputDisplayMode.DAY) selectedDate == today
        else weekDates.contains(today)
    }
    val showCurrentTimeMarker = state.settings?.showCurrentTimeMarker ?: false
    val shiftUnit = if (displayMode == OutputDisplayMode.DAY) 1L else 7L
    val classSlots = remember(state.settings) { state.settings.toClassSlots() }

    val arrivalMin: Int? = remember(state.settings, classSlots) {
        val s = state.settings
        if (s != null && s.arrivalHour >= 0) s.arrivalHour * 60 + s.arrivalMinute
        else classSlots.firstOrNull()?.let { it.start.hour * 60 }
    }
    val departureMin: Int? = remember(state.settings, classSlots) {
        val s = state.settings
        if (s != null && s.departureHour >= 0) s.departureHour * 60 + s.departureMinute
        else classSlots.lastOrNull()?.let { slot ->
            val endH = slot.end.hour + if (slot.end.minute > 0) 1 else 0
            endH * 60
        }
    }

    var showResultDatePicker by rememberSaveable { mutableStateOf(false) }
    var showCustomExportDialog by rememberSaveable { mutableStateOf(false) }
    var customExportStartMs by rememberSaveable { mutableStateOf(today.toEpochDay() * 86400_000L) }
    var customExportEndMs by rememberSaveable { mutableStateOf(today.plusMonths(1).toEpochDay() * 86400_000L) }
    var customExportPickingStart by rememberSaveable { mutableStateOf(true) }

    if (showCustomExportDialog) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (customExportPickingStart) customExportStartMs else customExportEndMs
        )
        DatePickerDialog(
            onDismissRequest = { showCustomExportDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = pickerState.selectedDateMillis ?: return@TextButton
                    if (customExportPickingStart) {
                        customExportStartMs = selected
                        customExportPickingStart = false
                        // 終了日のダイアログを続けて開く
                    } else {
                        customExportEndMs = selected
                        showCustomExportDialog = false
                        val start = java.time.Instant.ofEpochMilli(customExportStartMs)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        val end = java.time.Instant.ofEpochMilli(customExportEndMs)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        onExportWithPermission(ExportRange.Custom(start, end))
                    }
                }) {
                    Text(if (customExportPickingStart) stringResource(R.string.btn_next) else stringResource(R.string.btn_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (customExportPickingStart) {
                        showCustomExportDialog = false
                    } else {
                        customExportPickingStart = true
                    }
                }) {
                    Text(if (customExportPickingStart) stringResource(R.string.btn_cancel) else stringResource(R.string.btn_back))
                }
            }
        ) {
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        if (customExportPickingStart)
                            stringResource(R.string.label_export_range_start)
                        else
                            stringResource(R.string.label_export_range_end),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 0.dp)
                    )
                }
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (showResultDatePicker) {
                val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate.toEpochDay() * 86400000L)
                DatePickerDialog(
                    onDismissRequest = { showResultDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let { onPickDate(LocalDate.ofEpochDay(it / 86400000L)) }
                            showResultDatePicker = false
                        }) { Text(stringResource(R.string.btn_save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResultDatePicker = false }) { Text(stringResource(R.string.btn_cancel)) }
                    }
                ) { DatePicker(state = state) }
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrentRangeToday) {
                        MaterialTheme.colorScheme.surfaceContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutputDisplayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = displayMode == mode,
                            onClick = { displayMode = mode },
                            label = { Text(stringResource(mode.labelRes)) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onShiftDate(-shiftUnit) }) { Text("<") }
                    if (displayMode == OutputDisplayMode.DAY) {
                        val selectedDayTypeEntity = dayTypeEntityForDate(selectedDate)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.combinedClickable(
                                onClick = { showResultDatePicker = true },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPickDate(today)
                                }
                            )
                        ) {
                            Text(selectedDate.format(dateFormatter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${stringResource(dayOfWeekRes(selectedDate.dayOfWeek))} / ${dayTypeDisplayText(dayType, selectedDayTypeEntity?.overrideLessonDayOfWeek)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        val shortFmt = remember { java.time.format.DateTimeFormatter.ofPattern("MM/dd") }
                        Text(
                            "${weekDates.first().format(shortFmt)}-${weekDates.last().format(shortFmt)}",
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPickDate(today)
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { onShiftDate(shiftUnit) }) { Text(">") }
                }
            }
        }

        item {
            val offsetAnim = remember { Animatable(0f) }
            val swipeScope = rememberCoroutineScope()
            val swipeModifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offsetAnim.value }
                .pointerInput(shiftUnit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            swipeScope.launch {
                                val total = offsetAnim.value
                                val screenW = size.width.toFloat()
                                when {
                                    total < -80f -> {
                                        offsetAnim.animateTo(-screenW, tween(180, easing = FastOutSlowInEasing))
                                        onShiftDate(shiftUnit)
                                        offsetAnim.snapTo(0f)
                                    }
                                    total > 80f -> {
                                        offsetAnim.animateTo(screenW, tween(180, easing = FastOutSlowInEasing))
                                        onShiftDate(-shiftUnit)
                                        offsetAnim.snapTo(0f)
                                    }
                                    else -> offsetAnim.animateTo(0f, spring())
                                }
                            }
                        },
                        onDragCancel = { swipeScope.launch { offsetAnim.animateTo(0f, spring()) } }
                    ) { _, dragAmount -> swipeScope.launch { offsetAnim.snapTo(offsetAnim.value + dragAmount) } }
                }
            if (displayMode == OutputDisplayMode.DAY) {
                DayScheduleTable(
                    date = selectedDate,
                    dayType = dayType,
                    resolveLesson = resolveLesson,
                    tasks = state.tasks,
                    plans = state.plans,
                    onOpenTask = onOpenTask,
                    onOpenPlan = onOpenPlan,
                    classSlots = classSlots,
                    arrivalMin = arrivalMin,
                    departureMin = departureMin,
                    showCurrentTimeMarker = showCurrentTimeMarker,
                    onAddFromLesson = onAddFromLesson,
                    modifier = swipeModifier
                )
            } else {
                WeekScheduleTable(
                    dates = weekDates,
                    dayTypeForDate = dayTypeForDate,
                    dayTypeEntityForDate = dayTypeEntityForDate,
                    resolveLesson = resolveLesson,
                    tasks = state.tasks,
                    plans = state.plans,
                    classSlots = classSlots,
                    showCurrentTimeMarker = showCurrentTimeMarker,
                    onSaveLessonOverride = onSaveLessonOverride,
                    onClearLessonOverride = onClearLessonOverride,
                    onDayClick = { date ->
                        onPickDate(date)
                        displayMode = OutputDisplayMode.DAY
                    },
                    modifier = swipeModifier
                )
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.label_calendar_export), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.msg_download_skip), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onExportWithPermission(ExportRange.ThisWeek) }) { Text(stringResource(R.string.btn_export_this_week)) }
                        OutlinedButton(onClick = {
                            customExportPickingStart = true
                            customExportStartMs = today.toEpochDay() * 86400_000L
                            customExportEndMs = today.plusMonths(1).toEpochDay() * 86400_000L
                            showCustomExportDialog = true
                        }) { Text(stringResource(R.string.btn_export_custom)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayScheduleTable(
    date: LocalDate,
    dayType: DayType,
    resolveLesson: (LocalDate, Int) -> ResolvedLesson?,
    tasks: List<TaskEntity>,
    plans: List<PlanEntity>,
    onOpenTask: (TaskEntity) -> Unit,
    onOpenPlan: (PlanEntity) -> Unit,
    classSlots: List<ClassSlot> = CLASS_SLOTS,
    arrivalMin: Int? = null,
    departureMin: Int? = null,
    showCurrentTimeMarker: Boolean = false,
    onAddFromLesson: ((subject: String, teacher: String, isPlan: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val hapticDaySchedule = LocalHapticFeedback.current
    var lessonForAddDialog by remember { mutableStateOf<ResolvedLesson?>(null) }
    val strAddFromLessonTitle = stringResource(R.string.dialog_add_from_lesson_title)
    val strAddTask = stringResource(R.string.dialog_add_task_from_lesson)
    val strAddPlan = stringResource(R.string.dialog_add_plan_from_lesson)
    val strCancelDialog = stringResource(R.string.btn_cancel)
    if (lessonForAddDialog != null) {
        val lessonSnap = lessonForAddDialog!!
        AlertDialog(
            onDismissRequest = { lessonForAddDialog = null },
            title = null,
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = lessonSnap.subject,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (lessonSnap.teacher.isNotBlank()) {
                        Text(
                            text = lessonSnap.teacher,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = strAddFromLessonTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                lessonForAddDialog = null
                                onAddFromLesson?.invoke(lessonSnap.subject, lessonSnap.teacher, false)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(strAddTask) }
                        OutlinedButton(
                            onClick = {
                                lessonForAddDialog = null
                                onAddFromLesson?.invoke(lessonSnap.subject, lessonSnap.teacher, true)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(strAddPlan) }
                    }
                    TextButton(
                        onClick = { lessonForAddDialog = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strCancelDialog) }
                }
            }
        )
    }
    val currentTime = if (showCurrentTimeMarker) rememberCurrentTime() else null
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val dpPerMinute = 1.3f
    val timelineMarkerCenterOffsetDp = 8f
    val timeColWidth = 52.dp
    val strLunchBreak = stringResource(R.string.label_lunch_break)
    val strNoLesson = stringResource(R.string.label_no_class_short)
    val strHasTask = stringResource(R.string.label_task_exists)
    val strHasPlan = stringResource(R.string.label_plan_exists)

    data class TimeSegment(
        val startMin: Int,
        val durationMin: Int,
        val slotIndex: Int?,
        val breakLabel: String?
    )

    fun slotStartMin(i: Int) = classSlots[i].start.hour * 60 + classSlots[i].start.minute
    fun slotEndMin(i: Int) = classSlots[i].end.hour * 60 + classSlots[i].end.minute
    fun formatMin(totalMin: Int) = "${totalMin / 60}:${(totalMin % 60).toString().padStart(2, '0')}"

    val dateTasks = tasks.filter { it.dueDate == date }
    val datePlans = plans.filter { it.dueDate == date }

    data class DueTick(
        val minuteOfDay: Int,
        val title: String,
        val description: String?,
        val task: TaskEntity? = null,
        val plan: PlanEntity? = null,
        val color: Color
    )

    val taskDueTicks = dateTasks.mapNotNull { task ->
        val dueMinuteOfDay = task.dueHour * 60 + task.dueMinute
        val matchedSlots = classSlots.filter { slot ->
            val lesson = resolveLesson(date, slot.index)
            lesson != null && taskMatchesLesson(task, lesson)
        }
        if (matchedSlots.isEmpty()) {
            DueTick(dueMinuteOfDay, task.title, task.description?.trim()?.ifBlank { null }, task = task, color = MaterialTheme.colorScheme.error)
        } else {
            val overlapsAnyMatchedSlot = matchedSlots.any { slot ->
                val slotStart = slot.start.hour * 60 + slot.start.minute
                val slotEnd = slot.end.hour * 60 + slot.end.minute
                dueMinuteOfDay in slotStart until slotEnd
            }
            if (overlapsAnyMatchedSlot) null else DueTick(dueMinuteOfDay, task.title, task.description?.trim()?.ifBlank { null }, task = task, color = MaterialTheme.colorScheme.error)
        }
    }
    val planDueTicks = datePlans.mapNotNull { plan ->
        val dueMinuteOfDay = plan.dueHour * 60 + plan.dueMinute
        val matchedSlots = classSlots.filter { slot ->
            val lesson = resolveLesson(date, slot.index)
            lesson != null && planMatchesLesson(plan, lesson)
        }
        if (matchedSlots.isEmpty()) {
            DueTick(dueMinuteOfDay, plan.title, plan.description?.trim()?.ifBlank { null }, plan = plan, color = MaterialTheme.colorScheme.primary)
        } else {
            val overlapsAnyMatchedSlot = matchedSlots.any { slot ->
                val slotStart = slot.start.hour * 60 + slot.start.minute
                val slotEnd = slot.end.hour * 60 + slot.end.minute
                dueMinuteOfDay in slotStart until slotEnd
            }
            if (overlapsAnyMatchedSlot) null else DueTick(dueMinuteOfDay, plan.title, plan.description?.trim()?.ifBlank { null }, plan = plan, color = MaterialTheme.colorScheme.primary)
        }
    }
    val outOfSlotDueTicks = (taskDueTicks + planDueTicks).sortedBy { it.minuteOfDay }

    val dayStartMin = arrivalMin
        ?: (classSlots.first().start.hour * 60 + classSlots.first().start.minute - 20).coerceAtLeast(0)
    val timelineStartMin = minOf(dayStartMin, outOfSlotDueTicks.minOfOrNull { it.minuteOfDay } ?: dayStartMin)

    val defaultTermMin = departureMin ?: run {
        val lastEndSlot = classSlots.last().end
        val endH = lastEndSlot.hour + if (lastEndSlot.minute > 0) 1 else 0
        endH * 60
    }
    val tickMaxMin = outOfSlotDueTicks.maxOfOrNull { it.minuteOfDay }
    // 最後の刻み目から次の30分境界まで余白を確保
    val tickEnd = tickMaxMin?.let { ((it + 30) / 30) * 30 }
    val timelineEndMin = if (tickEnd != null) maxOf(defaultTermMin, tickEnd) else defaultTermMin
    val currentMinuteOfDay = currentTime?.let { it.hour * 60 + it.minute }
    val shouldShowCurrentTimeMarker = showCurrentTimeMarker && date == today && currentMinuteOfDay != null && currentMinuteOfDay in timelineStartMin..timelineEndMin

    val segments = buildList {
        val firstStart = slotStartMin(0)
        if (firstStart > timelineStartMin) add(TimeSegment(timelineStartMin, firstStart - timelineStartMin, null, null))
        classSlots.forEachIndexed { i, slot ->
            val start = slotStartMin(i)
            val end = slotEndMin(i)
            add(TimeSegment(start, end - start, slot.index, null))
            if (i < classSlots.lastIndex) {
                val nextStart = slotStartMin(i + 1)
                val gapMin = nextStart - end
                if (gapMin > 0) {
                    add(TimeSegment(end, gapMin, null, if (gapMin >= 30) strLunchBreak else null))
                }
            }
        }
        val lastEnd = slotEndMin(classSlots.lastIndex)
        if (timelineEndMin > lastEnd) {
            add(TimeSegment(lastEnd, timelineEndMin - lastEnd, null, null))
        }
    }

    val lineColor = MaterialTheme.colorScheme.outlineVariant
    // 5時間超のスパンは「300分相当」の見た目に圧縮
    val compressedCapMin = 300
    val compressedCapDp = compressedCapMin * dpPerMinute
    fun timeSpanToDp(spanMin: Int): Float = if (spanMin > compressedCapMin) compressedCapDp else (spanMin * dpPerMinute).coerceAtLeast(0f)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        segments.forEach { seg ->
            val rawHeightDp = (seg.durationMin * dpPerMinute).dp
            val slot = if (seg.slotIndex != null) classSlots.find { it.index == seg.slotIndex } else null
            val lesson = if (seg.slotIndex != null) resolveLesson(date, seg.slotIndex) else null
            val segmentEndMin = seg.startMin + seg.durationMin
            val segmentTicks = outOfSlotDueTicks.filter { it.minuteOfDay in seg.startMin until segmentEndMin }
            // 非授業セグメントに刻み目がある場合、スパンごとに圧縮座標を計算
            val sortedSegTicks = segmentTicks.sortedBy { it.minuteOfDay }
            val showSegmentStartTick = sortedSegTicks.none { it.minuteOfDay == seg.startMin }
            val useCompressedCoords = seg.slotIndex == null && sortedSegTicks.isNotEmpty()
            val keyMinutes: List<Int> = if (useCompressedCoords)
                listOf(seg.startMin) + sortedSegTicks.map { it.minuteOfDay } + listOf(segmentEndMin)
            else emptyList()
            val spanDps: List<Float> = if (useCompressedCoords)
                keyMinutes.zipWithNext { a, b -> timeSpanToDp(b - a) }
            else emptyList()
            val hasCompressedSpan = if (useCompressedCoords)
                keyMinutes.zipWithNext { a, b -> b - a }.any { it > compressedCapMin }
            else
                seg.slotIndex == null && seg.durationMin > compressedCapMin
            val rawDueOffsets = sortedSegTicks.mapIndexed { i, tick ->
                if (useCompressedCoords) spanDps.take(i + 1).sum()
                else ((tick.minuteOfDay - seg.startMin).coerceAtLeast(0) * dpPerMinute)
            }
            // 登校/下校時刻がセグメント途中にある場合の生オフセットを計算
            val rawArrivalMarkOffsetDp: Float? = if (
                seg.slotIndex == null && seg.breakLabel == null &&
                dayStartMin > seg.startMin && dayStartMin < segmentEndMin
            ) {
                if (useCompressedCoords) {
                    var cumDp = 0f
                    var result = 0f
                    for (j in 1 until keyMinutes.size) {
                        val spanStart = keyMinutes[j - 1]
                        val spanEnd = keyMinutes[j]
                        if (dayStartMin <= spanEnd) {
                            val posInSpan = (dayStartMin - spanStart).coerceAtLeast(0)
                            val spanMin = spanEnd - spanStart
                            result = cumDp + if (spanMin > compressedCapMin)
                                compressedCapDp * posInSpan.toFloat() / spanMin.toFloat()
                            else
                                posInSpan * dpPerMinute
                            break
                        }
                        cumDp += spanDps[j - 1]
                    }
                    result
                } else {
                    (dayStartMin - seg.startMin).toFloat() * dpPerMinute
                }
            } else null
            val rawDepartureMarkOffsetDp: Float? = if (
                seg.slotIndex == null && seg.breakLabel == null &&
                defaultTermMin > seg.startMin && defaultTermMin < segmentEndMin
            ) {
                if (useCompressedCoords) {
                    var cumDp = 0f
                    var result = 0f
                    for (j in 1 until keyMinutes.size) {
                        val spanStart = keyMinutes[j - 1]
                        val spanEnd = keyMinutes[j]
                        if (defaultTermMin <= spanEnd) {
                            val posInSpan = (defaultTermMin - spanStart).coerceAtLeast(0)
                            val spanMin = spanEnd - spanStart
                            result = cumDp + if (spanMin > compressedCapMin)
                                compressedCapDp * posInSpan.toFloat() / spanMin.toFloat()
                            else
                                posInSpan * dpPerMinute
                            break
                        }
                        cumDp += spanDps[j - 1]
                    }
                    result
                } else {
                    (defaultTermMin - seg.startMin).toFloat() * dpPerMinute
                }
            } else null
            val currentTimeMarkOffsetDp: Float? = if (
                shouldShowCurrentTimeMarker && currentMinuteOfDay in seg.startMin until segmentEndMin
            ) {
                if (useCompressedCoords) {
                    var cumDp = 0f
                    var result = 0f
                    for (j in 1 until keyMinutes.size) {
                        val spanStart = keyMinutes[j - 1]
                        val spanEnd = keyMinutes[j]
                        if (currentMinuteOfDay <= spanEnd) {
                            val posInSpan = (currentMinuteOfDay - spanStart).coerceAtLeast(0)
                            val spanMin = spanEnd - spanStart
                            result = cumDp + if (spanMin > compressedCapMin)
                                compressedCapDp * posInSpan.toFloat() / spanMin.toFloat()
                            else
                                posInSpan * dpPerMinute
                            break
                        }
                        cumDp += spanDps[j - 1]
                    }
                    result
                } else {
                    (currentMinuteOfDay - seg.startMin).toFloat() * dpPerMinute
                }
            } else null

            // すべての刻み目（開始ラベル/課題/下校時刻）に10dpの最小間隔を適用
            data class TimelineMark(
                val type: Int, // 0=start, 1=due, 2=arrival, 3=departure
                val dueIndex: Int?,
                val rawOffset: Float
            )
            val minMarkGapDp = 10f
            val marks = mutableListOf<TimelineMark>()
            if (showSegmentStartTick) {
                marks += TimelineMark(type = 0, dueIndex = null, rawOffset = 0f)
            }
            rawDueOffsets.forEachIndexed { index, offset ->
                marks += TimelineMark(type = 1, dueIndex = index, rawOffset = offset)
            }
            if (rawArrivalMarkOffsetDp != null) {
                marks += TimelineMark(type = 2, dueIndex = null, rawOffset = rawArrivalMarkOffsetDp)
            }
            if (rawDepartureMarkOffsetDp != null) {
                marks += TimelineMark(type = 3, dueIndex = null, rawOffset = rawDepartureMarkOffsetDp)
            }

            val adjustedDueOffsets = rawDueOffsets.toMutableList()
            var arrivalMarkOffsetDp: Float? = rawArrivalMarkOffsetDp
            var departureMarkOffsetDp: Float? = rawDepartureMarkOffsetDp
            val sortedMarks = marks.withIndex().sortedWith(
                compareBy<IndexedValue<TimelineMark>> { it.value.rawOffset }.thenBy { it.index }
            )
            var prevAdjustedOffset = Float.NEGATIVE_INFINITY
            sortedMarks.forEach { marked ->
                val mark = marked.value
                val adjusted = maxOf(mark.rawOffset, prevAdjustedOffset + minMarkGapDp)
                when (mark.type) {
                    1 -> {
                        val idx = mark.dueIndex ?: return@forEach
                        adjustedDueOffsets[idx] = adjusted
                    }
                    2 -> arrivalMarkOffsetDp = adjusted
                    3 -> departureMarkOffsetDp = adjusted
                }
                prevAdjustedOffset = adjusted
            }

            val compressedTotalDpAdjusted: Float? = if (useCompressedCoords) {
                // 最後の刻み目の調整後位置から下にタイトル+説明文が収まるよう余白を確保
                val lastTickOffset = adjustedDueOffsets.lastOrNull() ?: spanDps.take(sortedSegTicks.size).sum()
                val lastTickBottomPadding = if (sortedSegTicks.lastOrNull()?.description != null) 36f else 12f
                spanDps.sum()
                    .coerceAtLeast(sortedSegTicks.size * 32f)
                    .coerceAtLeast(lastTickOffset + lastTickBottomPadding)
            } else null
            // 授業コマは最低60分相当、刻み目がある非授業セグメントは圧縮座標で高さ確定
            // 5時間超の空白セグメント（刻み目なし）は高さを圧縮
            val isCompressed = seg.slotIndex == null && sortedSegTicks.isEmpty() && seg.durationMin > compressedCapMin
            val heightDp = when {
                isCompressed -> compressedCapDp.dp
                seg.slotIndex != null -> rawHeightDp.coerceAtLeast((60 * dpPerMinute).dp)
                compressedTotalDpAdjusted != null -> compressedTotalDpAdjusted.dp
                else -> rawHeightDp
            }
            val lessonTasks = if (lesson != null && slot != null) {
                dateTasks.filter { task ->
                    taskMatchesLesson(task, lesson) && run {
                        val dueMinuteOfDay = task.dueHour * 60 + task.dueMinute
                        val slotStart = slot.start.hour * 60 + slot.start.minute
                        val slotEnd = slot.end.hour * 60 + slot.end.minute
                        dueMinuteOfDay in slotStart until slotEnd
                    }
                }.sortedWith(compareBy<TaskEntity> { it.dueHour }.thenBy { it.dueMinute })
            } else {
                emptyList()
            }
            val lessonPlans = if (lesson != null && slot != null) {
                datePlans.filter { plan ->
                    planMatchesLesson(plan, lesson) && run {
                        val dueMinuteOfDay = plan.dueHour * 60 + plan.dueMinute
                        val slotStart = slot.start.hour * 60 + slot.start.minute
                        val slotEnd = slot.end.hour * 60 + slot.end.minute
                        dueMinuteOfDay in slotStart until slotEnd
                    }
                }.sortedWith(compareBy<PlanEntity> { it.dueHour }.thenBy { it.dueMinute })
            } else {
                emptyList()
            }
            val hasLessonTask = lessonTasks.isNotEmpty()
            val hasLessonPlan = lessonPlans.isNotEmpty()
            val primaryTask = lessonTasks.firstOrNull()
            val primaryPlan = lessonPlans.firstOrNull()

            Row(modifier = Modifier.fillMaxWidth().height(heightDp)) {
                // 左: 時刻ラベル + 縦線
                Box(modifier = Modifier.width(timeColWidth).fillMaxHeight()) {
                    // 縦線: 刻み目の中心から始まるよう offset でずらす
                    if (hasCompressedSpan) {
                        // 省略セグメント: 縦線は連続のまま中央に省略記号を重ねる
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 8.dp)
                                .width(1.5.dp)
                                .fillMaxHeight()
                                .offset(y = 8.dp)
                                .background(lineColor)
                        )
                        Text(
                            text = "≈",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = 17.25.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 8.dp)
                                .width(1.5.dp)
                                .fillMaxHeight()
                                .offset(y = 8.dp)
                                .background(lineColor)
                        )
                    }
                    // 時刻テキスト + 刻み目を Row で垂直中央揃え（提出期限の開始時刻と被る場合は非表示）
                    if (showSegmentStartTick) {
                        Row(
                            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatMin(seg.startMin),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .width(12.dp)
                                    .height(1.5.dp)
                                    .background(lineColor)
                            )
                        }
                    }
                    // 登校時刻マーク（セグメント途中の場合）
                    if (arrivalMarkOffsetDp != null) {
                        Row(
                            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().offset(y = arrivalMarkOffsetDp.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatMin(dayStartMin),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .width(12.dp)
                                    .height(1.5.dp)
                                    .background(lineColor)
                            )
                        }
                    }
                    // 提出期限の刻み目（課題: 赤 / 予定: 青）
                    sortedSegTicks.forEachIndexed { index, tick ->
                        val yOffset = adjustedDueOffsets.getOrElse(index) {
                            ((tick.minuteOfDay - seg.startMin).coerceAtLeast(0) * dpPerMinute)
                        }.dp
                        Row(
                            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().offset(y = yOffset),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatMin(tick.minuteOfDay),
                                style = MaterialTheme.typography.labelSmall,
                                color = tick.color,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .width(12.dp)
                                    .height(2.dp)
                                    .background(tick.color)
                            )
                        }
                    }
                    // 下校時刻マーク（セグメント途中の場合）
                    if (departureMarkOffsetDp != null) {
                        Row(
                            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().offset(y = departureMarkOffsetDp.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatMin(defaultTermMin),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .width(12.dp)
                                    .height(1.5.dp)
                                    .background(lineColor)
                            )
                        }
                    }
                    if (currentTimeMarkOffsetDp != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = (currentTimeMarkOffsetDp + timelineMarkerCenterOffsetDp).dp)
                                .padding(end = 1.dp)
                                .width(16.dp)
                                .height(2.5.dp)
                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50))
                        )
                    }
                }

                // 右: コンテンツ
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp, end = 4.dp, bottom = 2.dp)
                ) {
                    if (slot != null && !(lesson == null && segmentTicks.isNotEmpty())) {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(y = 8.dp)
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            primaryTask != null -> onOpenTask(primaryTask)
                                            primaryPlan != null -> onOpenPlan(primaryPlan)
                                        }
                                    },
                                    onLongClick = if (onAddFromLesson != null && lesson != null && lesson.subject.isNotBlank()) {
                                        {
                                            hapticDaySchedule.performHapticFeedback(HapticFeedbackType.LongPress)
                                            lessonForAddDialog = lesson
                                        }
                                    } else null
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (lesson != null)
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                else
                                    MaterialTheme.colorScheme.surfaceContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                slot.label,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        if (hasLessonTask) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = LegacyTaskBadgeContainer
                                            ) {
                                                Text(
                                                    text = strHasTask,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = LegacyTaskBadgeOnContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (hasLessonPlan) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = strHasPlan,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    if (lesson != null) {
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier.padding(start = 12.dp)
                                        ) {
                                            Text(lesson.teacher, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (!lesson.location.isNullOrBlank()) {
                                                Text(lesson.location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                ) {
                                    val hasLessonDetails = lessonTasks.isNotEmpty() || lessonPlans.isNotEmpty()
                                    val subjectStyle = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = if (lesson != null) FontWeight.Bold else FontWeight.Normal
                                    )
                                    val detailTitleStyle = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                    val detailNoteStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                    val subjectMinWidth = with(density) {
                                        textMeasurer.measure(
                                            text = "あああ",
                                            style = subjectStyle,
                                            maxLines = 1
                                        ).size.width.toDp()
                                    } + 4.dp
                                    val lessonDetailsMinWidth = 88.dp
                                    val contentGap = if (hasLessonDetails) 8.dp else 0.dp
                                    val availableContentWidth = (maxWidth - contentGap).coerceAtLeast(0.dp)

                                    fun measureWidth(text: String, isNote: Boolean = false): Dp = with(density) {
                                        textMeasurer.measure(
                                            text = text,
                                            style = if (isNote) detailNoteStyle else detailTitleStyle,
                                            maxLines = if (isNote) 2 else 1
                                        ).size.width.toDp()
                                    }

                                    val subjectPreferredWidth = with(density) {
                                        textMeasurer.measure(
                                            text = lesson?.subject ?: strNoLesson,
                                            style = subjectStyle,
                                            maxLines = 1
                                        ).size.width.toDp()
                                    }.coerceAtLeast(subjectMinWidth)

                                    val lessonDetailCandidates = buildList<Dp> {
                                        lessonTasks.take(2).forEach { add(measureWidth(it.title)) }
                                        primaryTask?.description?.trim()?.takeIf { it.isNotBlank() }?.let {
                                            add(measureWidth(it, isNote = true))
                                        }
                                        lessonPlans.take(2).forEach { add(measureWidth(it.title)) }
                                        primaryPlan?.description?.trim()?.takeIf { it.isNotBlank() }?.let {
                                            add(measureWidth(it, isNote = true))
                                        }
                                    }
                                    val lessonDetailsPreferredWidth = lessonDetailCandidates
                                        .maxOrNull()
                                        ?.coerceAtLeast(lessonDetailsMinWidth)
                                        ?: 0.dp
                                    val resolvedLessonDetailsMinWidth = lessonDetailsMinWidth
                                        .coerceAtMost((availableContentWidth * 0.4f).coerceAtLeast(72.dp))

                                    val lessonDetailsWidth = if (!hasLessonDetails) {
                                        0.dp
                                    } else if (subjectPreferredWidth + lessonDetailsPreferredWidth <= availableContentWidth) {
                                        lessonDetailsPreferredWidth
                                    } else if (subjectPreferredWidth + resolvedLessonDetailsMinWidth <= availableContentWidth) {
                                        (availableContentWidth - subjectPreferredWidth)
                                            .coerceAtLeast(resolvedLessonDetailsMinWidth)
                                    } else {
                                        resolvedLessonDetailsMinWidth
                                    }

                                    val subjectWidth = if (!hasLessonDetails) {
                                        maxWidth
                                    } else {
                                        (availableContentWidth - lessonDetailsWidth)
                                            .coerceAtLeast(subjectMinWidth)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Text(
                                            text = lesson?.subject ?: strNoLesson,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = if (lesson != null) FontWeight.Bold else FontWeight.Normal,
                                            color = if (lesson != null) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .width(subjectWidth)
                                                .padding(end = contentGap)
                                        )
                                        if (hasLessonDetails) {
                                            Column(
                                                modifier = Modifier.width(lessonDetailsWidth),
                                                horizontalAlignment = Alignment.End,
                                                verticalArrangement = Arrangement.Bottom
                                            ) {
                                                lessonTasks.take(2).forEach { task ->
                                                    Text(
                                                        text = task.title,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                val primaryNote = primaryTask?.description?.trim().orEmpty()
                                                if (primaryNote.isNotBlank()) {
                                                    Text(
                                                        text = primaryNote,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 10.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                lessonPlans.take(2).forEach { plan ->
                                                    Text(
                                                        text = plan.title,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                                val primaryPlanNote = primaryPlan?.description?.trim().orEmpty()
                                                if (primaryPlanNote.isNotBlank()) {
                                                    Text(
                                                        text = primaryPlanNote,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 10.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (seg.breakLabel != null) {
                        // 昼休みテキストをカード間の視覚的中心に配置（上のカードが8dp食い込む分を考慮）
                        Box(
                            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                seg.breakLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    sortedSegTicks.forEachIndexed { index, tick ->
                        val yOffset = adjustedDueOffsets.getOrElse(index) {
                            ((tick.minuteOfDay - seg.startMin).coerceAtLeast(0) * dpPerMinute)
                        }.dp
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(y = yOffset)
                                .clickable {
                                    tick.task?.let(onOpenTask)
                                    tick.plan?.let(onOpenPlan)
                                }
                        ) {
                            Text(
                                text = tick.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = tick.color,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tick.description != null) {
                                Text(
                                    text = tick.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tick.color,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 終了時刻行
        val termMin = timelineEndMin
        Row(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            Box(modifier = Modifier.width(timeColWidth).fillMaxHeight()) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMin(termMin),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .width(12.dp)
                            .height(1.5.dp)
                            .background(lineColor)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekScheduleTable(
    dates: List<LocalDate>,
    dayTypeForDate: (LocalDate) -> DayType,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?,
    resolveLesson: (LocalDate, Int) -> ResolvedLesson?,
    tasks: List<TaskEntity>,
    plans: List<PlanEntity>,
    classSlots: List<ClassSlot> = CLASS_SLOTS,
    showCurrentTimeMarker: Boolean = false,
    onSaveLessonOverride: (LocalDate, Int, DayType) -> Unit,
    onClearLessonOverride: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val currentTime = if (showCurrentTimeMarker) rememberCurrentTime() else null
    val haptic = LocalHapticFeedback.current
    val slotLabelWidth = 44.dp
    val cellHeight = 140.dp
    val currentMinuteOfDay = currentTime?.let { it.hour * 60 + it.minute }
    var overrideEditingDate by remember(dates) { mutableStateOf<LocalDate?>(null) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // ヘッダー行
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(slotLabelWidth))
            dates.forEach { date ->
                val dayType = dayTypeForDate(date)
                val dayTypeEntity = dayTypeEntityForDate(date)
                val isToday = date == today
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp, vertical = 4.dp)
                        .combinedClickable(
                            onClick = { onDayClick(date) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                overrideEditingDate = date
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val circleModifier = if (isToday)
                        Modifier.width(36.dp).height(36.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50.dp))
                    else
                        Modifier.width(36.dp).height(36.dp)
                    Box(
                        modifier = circleModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(dayOfWeekRes(date.dayOfWeek)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = dayTypeDisplayText(dayType, dayTypeEntity?.overrideLessonDayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // スロット行
        classSlots.forEachIndexed { i, slot ->
            Row(modifier = Modifier.fillMaxWidth().height(cellHeight)) {
                // 左の校時ラベル
                Column(
                    modifier = Modifier
                        .width(slotLabelWidth)
                        .fillMaxHeight()
                        .padding(start = 2.dp, end = 2.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${i + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = slot.start.let { "${it.hour}:${it.minute.toString().padStart(2,'0')}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "↕",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = slot.end.let { "${it.hour}:${it.minute.toString().padStart(2,'0')}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 各曜日のセル
                dates.forEach { date ->
                    val lesson = resolveLesson(date, slot.index)
                    val slotStartMin = slot.start.hour * 60 + slot.start.minute
                    val slotEndMin = slot.end.hour * 60 + slot.end.minute
                    val currentTimeOffsetDp = if (showCurrentTimeMarker && date == today && currentMinuteOfDay != null && currentMinuteOfDay in slotStartMin until slotEndMin) {
                        ((currentMinuteOfDay - slotStartMin).toFloat() / (slotEndMin - slotStartMin).toFloat()) * cellHeight.value
                    } else {
                        null
                    }
                    val hasTask = lesson != null && tasks.any { task ->
                        task.dueDate == date && taskMatchesLesson(task, lesson)
                    }
                    val hasPlan = lesson != null && plans.any { plan ->
                        plan.dueDate == date && planMatchesLesson(plan, lesson)
                    }
                    val bgColor = if (lesson != null) MaterialTheme.colorScheme.surfaceContainerLow
                                  else MaterialTheme.colorScheme.surfaceContainer
                    val contentColor = if (lesson != null) MaterialTheme.colorScheme.onSurface
                                       else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp, vertical = 4.dp)
                    ) {
                        Card(
                            onClick = { onDayClick(date) },
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = bgColor)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                if (lesson != null) {
                                    Column(
                                        modifier = Modifier.align(Alignment.TopStart),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = lesson.subject,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (hasTask) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(8.dp)
                                                        .height(8.dp)
                                                        .background(LegacyTaskBadgeRed, RoundedCornerShape(50))
                                                )
                                            }
                                            if (hasPlan) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(8.dp)
                                                        .height(8.dp)
                                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                                )
                                            }
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.align(Alignment.BottomStart)
                                    ) {
                                        if (lesson.teacher.isNotBlank()) {
                                            Text(
                                                text = lesson.teacher,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = contentColor.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (!lesson.location.isNullOrBlank()) {
                                            Text(
                                                text = lesson.location,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                color = contentColor.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (currentTimeOffsetDp != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .offset(y = currentTimeOffsetDp.dp)
                                    .height(2.5.dp)
                                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50))
                            )
                        }
                    }
                }
            }
        }
    }

    overrideEditingDate?.let { date ->
        val dayTypeEntity = dayTypeEntityForDate(date)
        LessonOverrideDialog(
            date = date,
            currentDayType = dayTypeForDate(date),
            currentOverrideDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek,
            currentOverrideDayType = dayTypeEntity?.overrideLessonDayType,
            onDismiss = { overrideEditingDate = null },
            onApply = { dayOfWeek, dayTypeValue ->
                onSaveLessonOverride(date, dayOfWeek, dayTypeValue)
                overrideEditingDate = null
            },
            onClear = if (dayTypeEntity?.overrideLessonDayOfWeek != null && dayTypeEntity.overrideLessonDayType != null) {
                {
                    onClearLessonOverride(date)
                    overrideEditingDate = null
                }
            } else {
                null
            }
        )
    }
}

@Composable
private fun LessonOverrideDialog(
    date: LocalDate,
    currentDayType: DayType,
    currentOverrideDayOfWeek: Int?,
    currentOverrideDayType: DayType?,
    showDayTypeSelector: Boolean = true,
    onDismiss: () -> Unit,
    onApply: (Int, DayType) -> Unit,
    onClear: (() -> Unit)?
) {
    val effectiveShowDayTypeSelector = showDayTypeSelector || currentDayType == DayType.HOLIDAY
    var selectedDayOfWeek by remember(date, currentOverrideDayOfWeek) {
        mutableStateOf(currentOverrideDayOfWeek ?: date.dayOfWeek.value.coerceIn(1, 5))
    }
    var selectedDayType by remember(date, currentDayType, currentOverrideDayType) {
        mutableStateOf(
            (currentOverrideDayType ?: currentDayType).takeIf { it != DayType.HOLIDAY } ?: DayType.A
        )
    }
    var dayOfWeekExpanded by remember { mutableStateOf(false) }
    var dayTypeExpanded by remember { mutableStateOf(false) }

    val weekdayOptions = remember {
        listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
    val appliedDayType = if (effectiveShowDayTypeSelector) selectedDayType else currentDayType

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_lesson_override_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = date.format(dateFormatter),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${stringResource(dayOfWeekRes(date.dayOfWeek))} / ${stringResource(dayTypeRes(currentDayType))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.label_override_weekday), style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(
                            onClick = { dayOfWeekExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(dayOfWeekRes(DayOfWeek.of(selectedDayOfWeek))))
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = dayOfWeekExpanded,
                        onDismissRequest = { dayOfWeekExpanded = false }
                    ) {
                        weekdayOptions.forEach { dayOfWeek ->
                            DropdownMenuItem(
                                text = { Text(stringResource(dayOfWeekRes(dayOfWeek))) },
                                onClick = {
                                    selectedDayOfWeek = dayOfWeek.value
                                    dayOfWeekExpanded = false
                                }
                            )
                        }
                    }
                }

                if (effectiveShowDayTypeSelector) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.label_override_day_type), style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(
                                onClick = { dayTypeExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(dayTypeRes(selectedDayType)))
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = dayTypeExpanded,
                            onDismissRequest = { dayTypeExpanded = false }
                        ) {
                            listOf(DayType.A, DayType.B).forEach { dayType ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(dayTypeRes(dayType))) },
                                    onClick = {
                                        selectedDayType = dayType
                                        dayTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${stringResource(R.string.label_override_preview)} ${dayTypeDisplayText(appliedDayType, selectedDayOfWeek)}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedDayOfWeek, appliedDayType) }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onClear != null) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.btn_clear_override))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        }
    )
}

@Composable
private fun dayTypeDisplayText(dayType: DayType, overrideLessonDayOfWeek: Int?): String {
    val baseLabel = stringResource(dayTypeRes(dayType))
    if (overrideLessonDayOfWeek == null || dayType == DayType.HOLIDAY) {
        return baseLabel
    }
    return "$baseLabel(${stringResource(dayOfWeekRes(DayOfWeek.of(overrideLessonDayOfWeek)))})"
}

private fun taskMatchesLesson(task: TaskEntity, lesson: ResolvedLesson): Boolean {
    val taskSubject = task.subject.trim()
    val lessonSubject = lesson.subject.trim()
    if (taskSubject.isBlank() || lessonSubject.isBlank()) return false
    return taskSubject.equals(lessonSubject, ignoreCase = true)
}

private fun planMatchesLesson(plan: PlanEntity, lesson: ResolvedLesson): Boolean {
    val planSubject = plan.subject.trim()
    val lessonSubject = lesson.subject.trim()
    if (planSubject.isBlank() || lessonSubject.isBlank()) return false
    return planSubject.equals(lessonSubject, ignoreCase = true)
}

private fun PlanEntity.toTaskEntityLike(): TaskEntity = TaskEntity(
    id = id,
    lessonId = lessonId,
    subject = subject,
    teacher = teacher,
    title = title,
    description = description,
    dueDate = dueDate,
    dueHour = dueHour,
    dueMinute = dueMinute,
    isCompleted = isCompleted,
    completedDate = completedDate,
    createdDate = createdDate,
    priority = priority,
    useTeacherMatching = useTeacherMatching,
    calendarEventId = calendarEventId
)

private fun TaskEntity.toPlanEntityLike(existing: PlanEntity? = null): PlanEntity = PlanEntity(
    id = existing?.id ?: id,
    lessonId = lessonId,
    subject = subject,
    teacher = teacher,
    title = title,
    description = description,
    dueDate = dueDate,
    dueHour = dueHour,
    dueMinute = dueMinute,
    isCompleted = existing?.isCompleted ?: isCompleted,
    completedDate = existing?.completedDate ?: completedDate,
    createdDate = existing?.createdDate ?: createdDate,
    priority = priority,
    useTeacherMatching = useTeacherMatching,
    calendarEventId = existing?.calendarEventId ?: calendarEventId
)

@Composable
private fun UnifiedTaskPlanScreen(
    modifier: Modifier = Modifier,
    uiState: SchedulerUiState,
    onOpenTask: (TaskEntity) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onMarkTaskComplete: (TaskEntity) -> Unit,
    onMarkTaskIncomplete: (TaskEntity) -> Unit,
    onOpenPlan: (PlanEntity) -> Unit,
    onDeletePlan: (PlanEntity) -> Unit,
    onMarkPlanComplete: (PlanEntity) -> Unit,
    onMarkPlanIncomplete: (PlanEntity) -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_tasks) to 0,
        stringResource(R.string.tab_plans) to 1
    )

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEach { (label, index) ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(label) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> TaskScreen(
                modifier = Modifier.fillMaxSize(),
                tasks = uiState.incompleteTasks,
                completedTasks = uiState.tasks.filter { it.isCompleted },
                focusTaskId = null,
                onFocusHandled = {},
                onOpenTaskEditor = { task: TaskEntity? ->
                    task?.let { onOpenTask(it) }
                },
                onDeleteTask = onDeleteTask,
                onMarkComplete = onMarkTaskComplete,
                onMarkIncomplete = onMarkTaskIncomplete
            )
            1 -> TaskScreen(
                modifier = Modifier.fillMaxSize(),
                tasks = uiState.incompletePlans.map { it.toTaskEntityLike() },
                completedTasks = uiState.plans.filter { it.isCompleted }.map { it.toTaskEntityLike() },
                focusTaskId = null,
                onFocusHandled = {},
                onOpenTaskEditor = { task: TaskEntity? ->
                    task?.let { taskLike ->
                        uiState.plans.firstOrNull { it.id == taskLike.id }?.let { onOpenPlan(it) }
                    }
                },
                onDeleteTask = { task ->
                    uiState.plans.firstOrNull { it.id == task.id }?.let { onDeletePlan(it) }
                },
                onMarkComplete = { task ->
                    uiState.plans.firstOrNull { it.id == task.id }?.let { onMarkPlanComplete(it) }
                },
                onMarkIncomplete = { task ->
                    uiState.plans.firstOrNull { it.id == task.id }?.let { onMarkPlanIncomplete(it) }
                },
                isPlan = true
            )
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TableCell(
    width: Dp,
    borderColor: Color,
    textColor: Color,
    background: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(1.dp, borderColor)
            .background(background)
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                content()
            }
        }
    }
}

private fun hasCalendarPermission(context: Context): Boolean {
    val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
    val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
    return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
}

private data class WeekRow(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val days: List<LocalDate>
)

private data class DisplayWeekRow(
    val row: WeekRow,
    val isPast: Boolean
)

private fun buildWeekRows(startDate: LocalDate, endDate: LocalDate): List<WeekRow> {
    val firstMonday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastFriday = endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))

    val rows = mutableListOf<WeekRow>()
    var cursor = firstMonday
    while (!cursor.isAfter(lastFriday)) {
        val days = (0L..4L).map { cursor.plusDays(it) }
        rows += WeekRow(weekStart = days.first(), weekEnd = days.last(), days = days)
        cursor = cursor.plusWeeks(1)
    }
    return rows
}

@Composable
private fun dayTypeVisual(dayType: DayType): DayTypeVisual {
    val colorScheme = MaterialTheme.colorScheme
    return when (dayType) {
        DayType.A -> DayTypeVisual(colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
        DayType.B -> DayTypeVisual(colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
        DayType.HOLIDAY -> DayTypeVisual(colorScheme.surfaceVariant, colorScheme.onSurfaceVariant)
    }
}

@StringRes
private fun dayTypeRes(dayType: DayType): Int = when (dayType) {
    DayType.A -> R.string.daytype_a
    DayType.B -> R.string.daytype_b
    DayType.HOLIDAY -> R.string.daytype_holiday
}

@StringRes
private fun dayOfWeekRes(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> R.string.weekday_monday
    DayOfWeek.TUESDAY -> R.string.weekday_tuesday
    DayOfWeek.WEDNESDAY -> R.string.weekday_wednesday
    DayOfWeek.THURSDAY -> R.string.weekday_thursday
    DayOfWeek.FRIDAY -> R.string.weekday_friday
    DayOfWeek.SATURDAY -> R.string.weekday_saturday
    DayOfWeek.SUNDAY -> R.string.weekday_sunday
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

private fun SettingsEntity?.toClassSlots(): List<ClassSlot> {
    val s = this ?: return CLASS_SLOTS
    return generateClassSlots(
        s.periodsPerDay, s.periodDurationMin, s.breakBetweenPeriodsMin,
        s.lunchBreakMin, s.firstPeriodStartHour, s.firstPeriodStartMinute,
        s.useKosenMode, s.lunchAfterPeriod
    )
}
