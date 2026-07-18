package jp.linkserver.nittcsc.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.InternalFeatureFlags
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
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
import jp.linkserver.nittcsc.data.ChangedLessonEntity
import jp.linkserver.nittcsc.data.ExamLessonEntity
import jp.linkserver.nittcsc.data.hasEnteredContent
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import jp.linkserver.nittcsc.data.LessonDraft
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.LessonNoteEntity
import jp.linkserver.nittcsc.data.LongBreakEntity
import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.logic.CLASS_SLOTS
import jp.linkserver.nittcsc.logic.ClassSlot
import jp.linkserver.nittcsc.logic.ExportRange
import jp.linkserver.nittcsc.logic.ExportResult
import jp.linkserver.nittcsc.logic.NaturalLanguageLessonCandidate
import jp.linkserver.nittcsc.logic.NaturalLanguageTaskParser
import jp.linkserver.nittcsc.logic.generateClassSlots
import jp.linkserver.nittcsc.logic.japaneseDayOfWeekSearchText
import jp.linkserver.nittcsc.logic.matchesTaskPlanSearch
import jp.linkserver.nittcsc.logic.normalizeSearchText
import jp.linkserver.nittcsc.logic.tokenizeSearchQuery
import jp.linkserver.nittcsc.ml.ModelDownloadManager
import jp.linkserver.nittcsc.ml.VlmInferenceEngine
import jp.linkserver.nittcsc.ml.VlmInferenceService
import jp.linkserver.nittcsc.reminder.LessonStartNotificationWorker
import jp.linkserver.nittcsc.reminder.PlanReminderWorker
import jp.linkserver.nittcsc.reminder.TaskReminderWorker
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import jp.linkserver.nittcsc.sync.NearbyPhase
import jp.linkserver.nittcsc.update.AppUpdateInfo
import jp.linkserver.nittcsc.update.checkGitHubReleaseUpdate
import jp.linkserver.nittcsc.update.dismissUpdateNotificationUntilNextVersion
import jp.linkserver.nittcsc.update.isShowLatestReleaseForTestingEnabled
import jp.linkserver.nittcsc.update.isUpdateNotificationDismissed
import jp.linkserver.nittcsc.update.markUpdateCheckFinished
import jp.linkserver.nittcsc.update.resolveUpdateCurrentVersionForTesting
import jp.linkserver.nittcsc.update.shouldCheckForUpdates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

enum class AppTab(
    @param:StringRes val labelRes: Int,
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
        Icons.Outlined.Event
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

private enum class OutputDisplayMode(@param:StringRes val labelRes: Int) {
    DAY(R.string.display_mode_day),
    WEEK(R.string.display_mode_week)
}

private enum class LessonSearchDisplayMode(@param:StringRes val labelRes: Int) {
    CALENDAR(R.string.search_display_calendar),
    LIST(R.string.search_display_list)
}


private data class LessonChangeEditorState(
    val date: LocalDate,
    val slotIndex: Int,
    val originalLesson: ResolvedLesson,
    val currentLesson: ResolvedLesson,
    val existingChangedLesson: ChangedLessonEntity?
)

private data class LessonMoveTargetItem(
    val title: String,
    val fromDate: LocalDate,
    val fromHour: Int,
    val fromMinute: Int
)

private data class PendingLessonMoveDialogState(
    val targetDate: LocalDate,
    val targetTime: LocalTime,
    val tasks: List<TaskEntity>,
    val plans: List<PlanEntity>,
    val items: List<LessonMoveTargetItem>
)

private data class LessonAutocompleteEntry(
    val subject: String,
    val teacher: String,
    val location: String?
)


private data class LessonSearchResult(
    val date: LocalDate,
    val slot: ClassSlot,
    val lesson: ResolvedLesson,
    val isChanged: Boolean
)

private val LegacyTaskBadgeRed = Color(0xFFBA1A1A)
private val LegacyTaskBadgeContainer = Color(0xFF93000A)
private val LegacyTaskBadgeOnContainer = Color(0xFFFFDAD6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NittcSchedulerApp(viewModel: SchedulerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val naturalLanguageInferenceEngine = remember { VlmInferenceEngine(context) }
    DisposableEffect(naturalLanguageInferenceEngine) {
        onDispose { naturalLanguageInferenceEngine.cancelInference() }
    }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Output) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSync by rememberSaveable { mutableStateOf(false) }
    var showSyncDiscovery by rememberSaveable { mutableStateOf(false) }
    var showNearbySync by rememberSaveable { mutableStateOf(false) }
    var showNearbyPermissionRationale by rememberSaveable { mutableStateOf(false) }
    var pendingNearbyPerms by remember { mutableStateOf<Array<String>>(emptyArray()) }
    var showVlmImport by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateOverview by rememberSaveable { mutableStateOf(false) }
    var updateNotification by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showOssLicenses by rememberSaveable { mutableStateOf(false) }
    var showLessonSearch by rememberSaveable { mutableStateOf(false) }
    var requestedOutputDayEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showTaskPlanCalendar by rememberSaveable { mutableStateOf(false) }
    var showExamTimetablePeriods by rememberSaveable { mutableStateOf(false) }
    var selectedExamPeriodStartEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showTaskEditor by rememberSaveable { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var focusedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPlanEditor by rememberSaveable { mutableStateOf(false) }
    var editingPlanId by rememberSaveable { mutableStateOf<Long?>(null) }
    var focusedPlanId by rememberSaveable { mutableStateOf<Long?>(null) }
    var prefillSubject by rememberSaveable { mutableStateOf("") }
    var prefillTeacher by rememberSaveable { mutableStateOf("") }
    var prefillDueDateEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var prefillDueHour by rememberSaveable { mutableStateOf<Int?>(null) }
    var prefillDueMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var showNaturalLanguageTaskAddDialog by rememberSaveable { mutableStateOf(false) }
    var naturalLanguageTaskDraft by remember { mutableStateOf<TaskEntity?>(null) }
    var transientTabOrigin by rememberSaveable { mutableStateOf<AppTab?>(null) }
    var transientTabTarget by rememberSaveable { mutableStateOf<AppTab?>(null) }
    var unifiedTaskPlanSelectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var lessonChangeEditor by remember { mutableStateOf<LessonChangeEditorState?>(null) }
    var pendingLessonMoveDialog by remember { mutableStateOf<PendingLessonMoveDialogState?>(null) }

    fun clearLessonTaskPrefill() {
        prefillSubject = ""
        prefillTeacher = ""
        prefillDueDateEpochDay = null
        prefillDueHour = null
        prefillDueMinute = null
    }

    fun navigateToTabFromAction(target: AppTab) {
        val unifyTaskPlanView = uiState.settings?.unifyTaskPlanView == true
        if (unifyTaskPlanView) {
            unifiedTaskPlanSelectedTabIndex = when (target) {
                AppTab.Plans -> 1
                AppTab.Tasks -> 0
                else -> unifiedTaskPlanSelectedTabIndex
            }
        }
        val resolvedTarget = if (unifyTaskPlanView && target == AppTab.Plans) {
            AppTab.Tasks
        } else {
            target
        }
        if (selectedTab != resolvedTarget) {
            transientTabOrigin = selectedTab
            transientTabTarget = resolvedTarget
            selectedTab = resolvedTarget
        }
    }

    fun clearTransientTabNavigation() {
        transientTabOrigin = null
        transientTabTarget = null
    }

    fun openUpdateOverview(updateInfo: AppUpdateInfo) {
        availableUpdate = updateInfo
        updateNotification = null
        showOssLicenses = false
        showAbout = false
        showSettings = false
        showUpdateOverview = true
    }

    BackHandler(enabled = showOssLicenses) { showOssLicenses = false }
    BackHandler(enabled = showUpdateOverview) { showUpdateOverview = false }
    BackHandler(enabled = showNearbySync) { showNearbySync = false }

    // スタンバイ広告中に相手から接続要求が来たら自動的に Nearby 画面へ遷移
    val nearbyPhase by viewModel.nearbyState.collectAsState()
    LaunchedEffect(nearbyPhase.phase) {
        if (nearbyPhase.phase == NearbyPhase.AUTH_CONFIRM && !showNearbySync) {
            showNearbySync = true
        }
    }
    BackHandler(enabled = showSyncDiscovery) { showSyncDiscovery = false }
    BackHandler(enabled = showSync && !showSyncDiscovery && !showNearbySync) {
        showSync = false
    }
    BackHandler(enabled = showAbout && !showOssLicenses) {
        showAbout = false
        showSettings = true
    }
    BackHandler(enabled = showSettings && !showAbout && !showOssLicenses) { showSettings = false }
    BackHandler(enabled = showVlmImport) { showVlmImport = false }
    BackHandler(enabled = showLessonSearch) { showLessonSearch = false }
    BackHandler(enabled = showTaskPlanCalendar) { showTaskPlanCalendar = false }
    BackHandler(enabled = selectedExamPeriodStartEpochDay != null) {
        selectedExamPeriodStartEpochDay = null
    }
    BackHandler(enabled = showExamTimetablePeriods && selectedExamPeriodStartEpochDay == null) {
        showExamTimetablePeriods = false
    }
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
        clearLessonTaskPrefill()
        naturalLanguageTaskDraft = null
    }
    BackHandler(enabled = showPlanEditor) {
        showPlanEditor = false
        editingPlanId = null
        clearLessonTaskPrefill()
        naturalLanguageTaskDraft = null
    }
    BackHandler(enabled = lessonChangeEditor != null) {
        lessonChangeEditor = null
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val currentClassSlots = remember(uiState.settings) { uiState.settings.toClassSlots() }
    var pendingTaskCalendarSync by remember { mutableStateOf<TaskEntity?>(null) }
    var pendingPlanCalendarSync by remember { mutableStateOf<PlanEntity?>(null) }
    var pendingToggleAddTasksToCalendar by remember { mutableStateOf<Boolean?>(null) }
    var pendingToggleSyncLessonsToCalendar by remember { mutableStateOf<Boolean?>(null) }
    var pendingEnableLessonCalendarSyncRange by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var pendingClearLessonCalendarEvents by rememberSaveable { mutableStateOf(false) }
    var pendingClearDeadlineCalendarEvents by rememberSaveable { mutableStateOf(false) }
    var pendingClearReminderCalendarEvents by rememberSaveable { mutableStateOf(false) }
    var showCalendarDeletePreviewDialog by rememberSaveable { mutableStateOf(false) }
    var calendarDeletePreviewCount by rememberSaveable { mutableStateOf(0) }
    var previewClearLessonCalendarEvents by rememberSaveable { mutableStateOf(false) }
    var previewClearDeadlineCalendarEvents by rememberSaveable { mutableStateOf(false) }
    var previewClearReminderCalendarEvents by rememberSaveable { mutableStateOf(false) }
    var pendingManualCalendarSyncTab by rememberSaveable { mutableStateOf<AppTab?>(null) }
    var showTaskCalendarSyncDialog by rememberSaveable { mutableStateOf(false) }
    val msgTaskCalendarAutoDisabled = stringResource(R.string.msg_task_calendar_auto_disabled)
    val msgTaskCalendarSyncSkipped = stringResource(R.string.msg_task_calendar_sync_skipped)
    val msgCalendarDeletePermissionDenied = stringResource(R.string.msg_calendar_delete_permission_denied)

    LaunchedEffect(
        uiState.initialized,
        uiState.settings?.lessonStartNotificationEnabled,
        uiState.settings?.lessonStartNotificationMinutesBefore,
        uiState.settings?.lessonStartNotificationLiveUpdatesEnabled,
        uiState.settings?.lessonStartNotificationProgressCountsDown,
        uiState.settings?.lessonStartNotificationLiveUpdateEarlyMinutes,
        uiState.settings?.enableExamTimetable,
        uiState.lessonNotificationExclusions,
        uiState.lessons,
        uiState.dayTypeEntities,
        uiState.changedLessons,
        uiState.cancelledLessons,
        uiState.examDaySchedules,
        uiState.examLessons
    ) {
        if (uiState.initialized) {
            LessonStartNotificationWorker.rescheduleAll(context)
        }
    }

    LaunchedEffect(
        uiState.initialized,
        uiState.settings?.syncLessonsToCalendar,
        uiState.settings?.lessonCalendarSyncStart,
        uiState.settings?.lessonCalendarSyncEnd,
        uiState.settings?.termStart,
        uiState.settings?.termEnd,
        uiState.settings?.periodsPerDay,
        uiState.settings?.periodDurationMin,
        uiState.settings?.breakBetweenPeriodsMin,
        uiState.settings?.lunchBreakMin,
        uiState.settings?.lunchAfterPeriod,
        uiState.settings?.firstPeriodStartHour,
        uiState.settings?.firstPeriodStartMinute,
        uiState.settings?.useKosenMode,
        uiState.settings?.enableExamTimetable,
        uiState.lessons,
        uiState.dayTypeEntities,
        uiState.changedLessons,
        uiState.cancelledLessons,
        uiState.examDaySchedules,
        uiState.examLessons
    ) {
        val settings = uiState.settings ?: return@LaunchedEffect
        if (!uiState.initialized || !hasCalendarPermission(context)) return@LaunchedEffect
        kotlinx.coroutines.delay(500)

        if (settings.syncLessonsToCalendar) {
            val start = settings.lessonCalendarSyncStart ?: settings.termStart
            val end = settings.lessonCalendarSyncEnd ?: settings.termEnd
            if (start <= end) {
                val lessons = viewModel.generateLessons(ExportRange.Custom(start, end))
                withContext(Dispatchers.IO) {
                    CalendarExporter(context).sync(lessons)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching { CalendarExporter(context).clearSyncedLessons() }
            }
        }
    }

    // POST_NOTIFICATIONS 権限をリクエスト（Android 13以上）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("NittcSchedulerApp", "通知権限が許可されました")
            appScope.launch {
                LessonStartNotificationWorker.rescheduleAll(context)
            }
        } else {
            android.util.Log.w("NittcSchedulerApp", "通知権限が拒否されました")
        }
    }

    // Nearby Connections 権限をリクエスト（スタンバイ広告に必要）
    val nearbyStandbyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            // 権限が付与されたらスタンバイ広告を開始
            viewModel.retryStandbyAdvertising()
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

    // アプリ起動時にNearby Connections権限を確認し、スタンバイ広告を確実に開始する
    LaunchedEffect(Unit) {
        val nearbyPerms = buildList {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val allGranted = nearbyPerms.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            // すでに権限がある場合はスタンバイ広告を再試行（起動時の失敗をリカバリ）
            viewModel.retryStandbyAdvertising()
        } else {
            // 未付与の場合は先に説明ダイアログを表示してからリクエスト
            pendingNearbyPerms = nearbyPerms.toTypedArray()
            showNearbyPermissionRationale = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        val currentVersionName = runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "unknown"
        }.getOrDefault("unknown")
        if (shouldCheckForUpdates(context, currentVersionName)) {
            val repositoryUrl = resources.getString(R.string.about_support_site_url)
            val result = withContext(Dispatchers.IO) {
                checkGitHubReleaseUpdate(
                    repositoryUrl = repositoryUrl,
                    currentVersion = resolveUpdateCurrentVersionForTesting(context, currentVersionName),
                    showLatestForTesting = isShowLatestReleaseForTestingEnabled(context, currentVersionName)
                )
            }
            markUpdateCheckFinished(context)
            result.onSuccess { updateInfo ->
                if (updateInfo != null && !isUpdateNotificationDismissed(context, updateInfo.tagName)) {
                    availableUpdate = updateInfo
                    updateNotification = updateInfo
                }
            }
        }
    }

    // Nearby権限の使用目的説明ダイアログ
    if (showNearbyPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showNearbyPermissionRationale = false },
            title = { Text("近くの端末との同期について") },
            text = {
                Text(
                    "このアプリは近くの端末と直接通信してデータを同期する機能を備えています。\n\n" +
                    "この機能のために、Bluetooth・Wi-Fi・位置情報の権限が必要です。" +
                    "位置情報は近くのデバイスを検出するためにのみ使用され、現在地の取得や記録には使用しません。\n\n" +
                    "同期機能を使用しない場合は「スキップ」を選択してください。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNearbyPermissionRationale = false
                    nearbyStandbyPermissionLauncher.launch(pendingNearbyPerms)
                }) {
                    Text("許可する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNearbyPermissionRationale = false }) {
                    Text("スキップ")
                }
            }
        )
    }


    val msgNoTasksToSync = stringResource(R.string.msg_no_tasks_to_sync)

    fun performClearAppCalendarEvents(
        clearLessons: Boolean,
        clearDeadlines: Boolean,
        clearReminders: Boolean
    ) {
        appScope.launch {
            val deletedCount = withContext(Dispatchers.IO) {
                var count = 0
                if (clearLessons) {
                    count += CalendarExporter(context).clearAppCreatedLessonEvents()
                }
                if (clearDeadlines || clearReminders) {
                    val taskCalendarSync = TaskCalendarSync(context)
                    if (clearDeadlines) {
                        count += taskCalendarSync.clearDeadlineEvents()
                    }
                    if (clearReminders) {
                        count += taskCalendarSync.clearReminderEvents()
                    }
                }
                count
            }
            snackbarHostState.showSnackbar(
                resources.getString(R.string.msg_app_calendar_events_deleted, deletedCount)
            )
        }
    }

    fun previewClearAppCalendarEvents(
        clearLessons: Boolean,
        clearDeadlines: Boolean,
        clearReminders: Boolean
    ) {
        appScope.launch {
            val eventCount = withContext(Dispatchers.IO) {
                var count = 0
                if (clearLessons) {
                    count += CalendarExporter(context).countAppCreatedLessonEvents()
                }
                if (clearDeadlines || clearReminders) {
                    val taskCalendarSync = TaskCalendarSync(context)
                    if (clearDeadlines) {
                        count += taskCalendarSync.countDeadlineEvents()
                    }
                    if (clearReminders) {
                        count += taskCalendarSync.countReminderEvents()
                    }
                }
                count
            }
            previewClearLessonCalendarEvents = clearLessons
            previewClearDeadlineCalendarEvents = clearDeadlines
            previewClearReminderCalendarEvents = clearReminders
            calendarDeletePreviewCount = eventCount
            showCalendarDeletePreviewDialog = true
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.READ_CALENDAR] == true &&
            result[Manifest.permission.WRITE_CALENDAR] == true

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
                                val syncedTask = sync.syncTask(task)
                                successCount++
                                if (task != syncedTask) {
                                    changedTasks += syncedTask
                                }
                            }
                            AppTab.Plans -> uiState.plans.forEach { plan ->
                                val syncedPlan = sync.syncPlan(plan)
                                successCount++
                                if (plan != syncedPlan) {
                                    changedPlans += syncedPlan
                                }
                            }
                            else -> Unit
                        }
                        Triple(changedTasks, changedPlans, successCount)
                    }
                    viewModel.saveTasksSilently(updatedTasks)
                    viewModel.savePlansSilently(updatedPlans)
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.msg_tasks_synced_to_calendar, syncedCount)
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

        pendingEnableLessonCalendarSyncRange?.let { (start, end) ->
            pendingEnableLessonCalendarSyncRange = null
            if (granted) {
                viewModel.enableSyncLessonsToCalendar(start, end)
            } else {
                appScope.launch { snackbarHostState.showSnackbar(msgTaskCalendarAutoDisabled) }
            }
        }

        if (pendingClearLessonCalendarEvents ||
            pendingClearDeadlineCalendarEvents ||
            pendingClearReminderCalendarEvents
        ) {
            val clearLessons = pendingClearLessonCalendarEvents
            val clearDeadlines = pendingClearDeadlineCalendarEvents
            val clearReminders = pendingClearReminderCalendarEvents
            pendingClearLessonCalendarEvents = false
            pendingClearDeadlineCalendarEvents = false
            pendingClearReminderCalendarEvents = false
            if (granted) {
                previewClearAppCalendarEvents(
                    clearLessons = clearLessons,
                    clearDeadlines = clearDeadlines,
                    clearReminders = clearReminders
                )
            } else {
                appScope.launch { snackbarHostState.showSnackbar(msgCalendarDeletePermissionDenied) }
            }
        }

        pendingToggleSyncLessonsToCalendar?.let { enable ->
            pendingToggleSyncLessonsToCalendar = null
            viewModel.toggleSyncLessonsToCalendar(enable && granted)
            if (enable && !granted) {
                appScope.launch { snackbarHostState.showSnackbar(msgTaskCalendarAutoDisabled) }
            }
        }

        pendingTaskCalendarSync?.let { pendingTask ->
            pendingTaskCalendarSync = null
            appScope.launch {
                val taskToSave = if (granted) {
                    withContext(Dispatchers.IO) {
                        TaskCalendarSync(context).syncTask(pendingTask)
                    }
                } else {
                    snackbarHostState.showSnackbar(msgTaskCalendarSyncSkipped)
                    pendingTask
                }
                viewModel.saveTaskDirect(taskToSave)
                TaskReminderWorker.syncTaskReminder(context, taskToSave)
            }
        }

        pendingPlanCalendarSync?.let { pendingPlan ->
            pendingPlanCalendarSync = null
            appScope.launch {
                val planToSave = if (granted) {
                    withContext(Dispatchers.IO) {
                        TaskCalendarSync(context).syncPlan(pendingPlan)
                    }
                } else {
                    snackbarHostState.showSnackbar(msgTaskCalendarSyncSkipped)
                    pendingPlan
                }
                viewModel.savePlanDirect(planToSave)
                PlanReminderWorker.syncPlanReminder(context, planToSave)
            }
        }
    }

    fun clearAppCalendarEvents(
        clearLessons: Boolean,
        clearDeadlines: Boolean,
        clearReminders: Boolean
    ) {
        if (hasCalendarPermission(context)) {
            previewClearAppCalendarEvents(
                clearLessons = clearLessons,
                clearDeadlines = clearDeadlines,
                clearReminders = clearReminders
            )
        } else {
            pendingClearLessonCalendarEvents = clearLessons
            pendingClearDeadlineCalendarEvents = clearDeadlines
            pendingClearReminderCalendarEvents = clearReminders
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    if (showCalendarDeletePreviewDialog) {
        val selectedDeleteCategories = listOfNotNull(
            if (previewClearLessonCalendarEvents) stringResource(R.string.label_clear_calendar_lessons) else null,
            if (previewClearDeadlineCalendarEvents) stringResource(R.string.label_clear_calendar_deadlines) else null,
            if (previewClearReminderCalendarEvents) stringResource(R.string.label_clear_calendar_reminders) else null
        ).joinToString("、")
        AlertDialog(
            onDismissRequest = { showCalendarDeletePreviewDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_app_calendar_events_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.dialog_clear_app_calendar_events_confirm_message,
                            calendarDeletePreviewCount
                        )
                    )
                    Text(
                        text = stringResource(
                            R.string.dialog_clear_app_calendar_events_confirm_categories,
                            selectedDeleteCategories
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCalendarDeletePreviewDialog = false
                        performClearAppCalendarEvents(
                            clearLessons = previewClearLessonCalendarEvents,
                            clearDeadlines = previewClearDeadlineCalendarEvents,
                            clearReminders = previewClearReminderCalendarEvents
                        )
                    }
                ) {
                    Text(stringResource(R.string.btn_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalendarDeletePreviewDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    suspend fun saveTaskWithIntegrations(task: TaskEntity, syncCalendar: Boolean): TaskEntity {
        val savedTask = viewModel.saveTaskDirect(task)
        val taskWithCalendar = if (syncCalendar && hasCalendarPermission(context)) {
            val syncedTask = withContext(Dispatchers.IO) {
                TaskCalendarSync(context).syncTask(savedTask)
            }
            if (syncedTask != savedTask) {
                viewModel.saveTaskDirect(syncedTask)
            } else {
                syncedTask
            }
        } else {
            savedTask
        }
        TaskReminderWorker.syncTaskReminder(context, taskWithCalendar)
        return taskWithCalendar
    }

    suspend fun savePlanWithIntegrations(plan: PlanEntity, syncCalendar: Boolean): PlanEntity {
        val savedPlan = viewModel.savePlanDirect(plan)
        val planWithCalendar = if (syncCalendar && hasCalendarPermission(context)) {
            val syncedPlan = withContext(Dispatchers.IO) {
                TaskCalendarSync(context).syncPlan(savedPlan)
            }
            if (syncedPlan != savedPlan) {
                viewModel.savePlanDirect(syncedPlan)
            } else {
                syncedPlan
            }
        } else {
            savedPlan
        }
        PlanReminderWorker.syncPlanReminder(context, planWithCalendar)
        return planWithCalendar
    }

    fun deleteTaskWithIntegrations(task: TaskEntity) {
        TaskReminderWorker.cancel(context, task.id)
        if (task.calendarEventId != null && hasCalendarPermission(context)) {
            appScope.launch(Dispatchers.IO) {
                TaskCalendarSync(context).deleteTaskEvent(task.calendarEventId)
            }
        }
        if (task.reminderCalendarEventId != null && hasCalendarPermission(context)) {
            appScope.launch(Dispatchers.IO) {
                TaskCalendarSync(context).deleteTaskEvent(task.reminderCalendarEventId)
            }
        }
        viewModel.deleteTask(task)
    }

    fun deletePlanWithIntegrations(plan: PlanEntity) {
        PlanReminderWorker.cancel(context, plan.id)
        if (plan.calendarEventId != null && hasCalendarPermission(context)) {
            appScope.launch(Dispatchers.IO) {
                TaskCalendarSync(context).deletePlanEvent(plan.calendarEventId)
            }
        }
        if (plan.reminderCalendarEventId != null && hasCalendarPermission(context)) {
            appScope.launch(Dispatchers.IO) {
                TaskCalendarSync(context).deletePlanEvent(plan.reminderCalendarEventId)
            }
        }
        viewModel.deletePlan(plan)
    }

    fun markTaskCompleteWithIntegrations(task: TaskEntity) {
        TaskReminderWorker.cancel(context, task.id)
        if (task.reminderCalendarEventId != null && hasCalendarPermission(context)) {
            appScope.launch(Dispatchers.IO) {
                TaskCalendarSync(context).deleteTaskEvent(task.reminderCalendarEventId)
            }
        }
        viewModel.markTaskAsComplete(task)
    }

    fun markPlanCompleteWithIntegrations(plan: PlanEntity) {
        PlanReminderWorker.cancel(context, plan.id)
        if (plan.reminderCalendarEventId != null && hasCalendarPermission(context)) {
            appScope.launch(Dispatchers.IO) {
                TaskCalendarSync(context).deletePlanEvent(plan.reminderCalendarEventId)
            }
        }
        viewModel.markPlanAsComplete(plan)
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
                            val syncedTask = sync.syncTask(task)
                            successCount++
                            if (task != syncedTask) {
                                changedTasks += syncedTask
                            }
                        }
                        AppTab.Plans -> uiState.plans.forEach { plan ->
                            val syncedPlan = sync.syncPlan(plan)
                            successCount++
                            if (plan != syncedPlan) {
                                changedPlans += syncedPlan
                            }
                        }
                        else -> Unit
                    }
                    Triple(changedTasks, changedPlans, successCount)
                }
                viewModel.saveTasksSilently(updatedTasks)
                viewModel.savePlansSilently(updatedPlans)
                snackbarHostState.showSnackbar(
                    resources.getString(R.string.msg_tasks_synced_to_calendar, syncedCount)
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

    fun resolveOriginalLesson(date: LocalDate, slot: Int): ResolvedLesson? {
        return viewModel.resolveBaseLessonForDate(
            date = date,
            slotIndex = slot,
            lessons = uiState.lessons,
            dayTypeMap = uiState.dayTypeMap,
            dayTypeEntities = uiState.dayTypeEntities
        )
    }

    fun resolveDisplayedLesson(date: LocalDate, slot: Int): ResolvedLesson? {
        return viewModel.resolveLessonForDate(
            date = date,
            slotIndex = slot,
            lessons = uiState.lessons,
            dayTypeMap = uiState.dayTypeMap,
            dayTypeEntities = uiState.dayTypeEntities,
            changedLessons = uiState.changedLessons
        )
    }

    fun openLessonChangeEditor(date: LocalDate, slotIndex: Int) {
        val originalLesson = resolveOriginalLesson(date, slotIndex) ?: return
        val currentLesson = resolveDisplayedLesson(date, slotIndex) ?: originalLesson
        lessonChangeEditor = LessonChangeEditorState(
            date = date,
            slotIndex = slotIndex,
            originalLesson = originalLesson,
            currentLesson = currentLesson,
            existingChangedLesson = uiState.changedLessons[date to slotIndex]
        )
    }

    suspend fun moveLessonItems(dialogState: PendingLessonMoveDialogState) {
        val shouldSyncCalendar = uiState.settings?.addTasksToCalendar == true && hasCalendarPermission(context)
        dialogState.tasks.forEach { task ->
            saveTaskWithIntegrations(
                task = task.copy(
                    dueDate = dialogState.targetDate,
                    dueHour = dialogState.targetTime.hour,
                    dueMinute = dialogState.targetTime.minute
                ),
                syncCalendar = shouldSyncCalendar
            )
        }
        dialogState.plans.forEach { plan ->
            savePlanWithIntegrations(
                plan = plan.copy(
                    dueDate = dialogState.targetDate,
                    dueHour = dialogState.targetTime.hour,
                    dueMinute = dialogState.targetTime.minute
                ),
                syncCalendar = shouldSyncCalendar
            )
        }
    }

    fun saveChangedLessonWithPrompt(
        editorState: LessonChangeEditorState,
        subject: String,
        teacher: String,
        location: String?
    ) {
        appScope.launch {
            val slot = currentClassSlots.firstOrNull { it.index == editorState.slotIndex }
            val slotStart = slot?.start
            val affectedTasks = if (slotStart != null) {
                uiState.tasks.filter { task ->
                    !task.isCompleted &&
                    task.dueDate == editorState.date &&
                        task.dueHour == slotStart.hour &&
                        task.dueMinute == slotStart.minute &&
                        taskMatchesLesson(task, editorState.originalLesson)
                }
            } else {
                emptyList()
            }
            val affectedPlans = if (slotStart != null) {
                uiState.plans.filter { plan ->
                    !plan.isCompleted &&
                    plan.dueDate == editorState.date &&
                        plan.dueHour == slotStart.hour &&
                        plan.dueMinute == slotStart.minute &&
                        planMatchesLesson(plan, editorState.originalLesson)
                }
            } else {
                emptyList()
            }
            val nextLessonDateTime = if (slotStart != null && (affectedTasks.isNotEmpty() || affectedPlans.isNotEmpty())) {
                viewModel.calculateNextLessonDateTimeSkipCurrent(
                    subject = editorState.originalLesson.subject,
                    teacher = editorState.originalLesson.teacher.takeIf { it.isNotBlank() },
                    useTeacherMatching = editorState.originalLesson.teacher.isNotBlank(),
                    fromDate = editorState.date,
                    currentTime = slotStart
                )
            } else {
                null
            }

            viewModel.saveChangedLessonDirect(editorState.date, editorState.slotIndex, subject, teacher, location)
            lessonChangeEditor = null

            if ((affectedTasks.isNotEmpty() || affectedPlans.isNotEmpty()) && nextLessonDateTime != null) {
                pendingLessonMoveDialog = PendingLessonMoveDialogState(
                    targetDate = nextLessonDateTime.first,
                    targetTime = nextLessonDateTime.second,
                    tasks = affectedTasks,
                    plans = affectedPlans,
                    items = buildList {
                        affectedTasks.forEach { task ->
                            add(LessonMoveTargetItem(task.title, task.dueDate, task.dueHour, task.dueMinute))
                        }
                        affectedPlans.forEach { plan ->
                            add(LessonMoveTargetItem(plan.title, plan.dueDate, plan.dueHour, plan.dueMinute))
                        }
                    }
                )
            } else if (affectedTasks.isNotEmpty() || affectedPlans.isNotEmpty()) {
                snackbarHostState.showSnackbar(resources.getString(R.string.msg_lesson_change_move_target_not_found))
            } else {
                snackbarHostState.showSnackbar(resources.getString(R.string.msg_lesson_change_saved))
            }
        }
    }

    fun setLessonCancelledWithPrompt(date: LocalDate, slotIndex: Int, cancelled: Boolean) {
        if (!cancelled) {
            viewModel.setLessonCancelled(date, slotIndex, false)
            return
        }
        val lesson = resolveDisplayedLesson(date, slotIndex) ?: run {
            viewModel.setLessonCancelled(date, slotIndex, true)
            return
        }
        appScope.launch {
            val slot = currentClassSlots.firstOrNull { it.index == slotIndex }
            val slotStart = slot?.start
            val affectedTasks = if (slotStart != null) {
                uiState.tasks.filter { task ->
                    !task.isCompleted &&
                        task.dueDate == date &&
                        task.dueHour == slotStart.hour &&
                        task.dueMinute == slotStart.minute &&
                        taskMatchesLesson(task, lesson)
                }
            } else {
                emptyList()
            }
            val affectedPlans = if (slotStart != null) {
                uiState.plans.filter { plan ->
                    !plan.isCompleted &&
                        plan.dueDate == date &&
                        plan.dueHour == slotStart.hour &&
                        plan.dueMinute == slotStart.minute &&
                        planMatchesLesson(plan, lesson)
                }
            } else {
                emptyList()
            }
            viewModel.setLessonCancelled(date, slotIndex, true)
            val nextLessonDateTime = if (slotStart != null && (affectedTasks.isNotEmpty() || affectedPlans.isNotEmpty())) {
                viewModel.calculateNextLessonDateTimeSkipCurrent(
                    subject = lesson.subject,
                    teacher = lesson.teacher.takeIf { it.isNotBlank() },
                    useTeacherMatching = lesson.teacher.isNotBlank(),
                    fromDate = date,
                    currentTime = slotStart
                )
            } else {
                null
            }

            if ((affectedTasks.isNotEmpty() || affectedPlans.isNotEmpty()) && nextLessonDateTime != null) {
                pendingLessonMoveDialog = PendingLessonMoveDialogState(
                    targetDate = nextLessonDateTime.first,
                    targetTime = nextLessonDateTime.second,
                    tasks = affectedTasks,
                    plans = affectedPlans,
                    items = buildList {
                        affectedTasks.forEach { task ->
                            add(LessonMoveTargetItem(task.title, task.dueDate, task.dueHour, task.dueMinute))
                        }
                        affectedPlans.forEach { plan ->
                            add(LessonMoveTargetItem(plan.title, plan.dueDate, plan.dueHour, plan.dueMinute))
                        }
                    }
                )
            } else if (affectedTasks.isNotEmpty() || affectedPlans.isNotEmpty()) {
                snackbarHostState.showSnackbar(resources.getString(R.string.msg_lesson_cancel_move_target_not_found))
            }
        }
    }

    val examPeriods = remember(uiState.dayTypeEntities) {
        buildExamPeriods(uiState.dayTypeEntities.values)
    }
    val selectedExamPeriod = selectedExamPeriodStartEpochDay?.let { epochDay ->
        examPeriods.firstOrNull { it.startDate.toEpochDay() == epochDay }
    }

    val currentScreenResource = when {
        showOssLicenses -> "oss"
        showUpdateOverview -> "update"
        showAbout -> "about"
        showNearbySync -> "nearbySync"
        showSyncDiscovery -> "syncDiscovery"
        showSync -> "sync"
        showVlmImport -> "vlm"
        showSettings -> "settings"
        lessonChangeEditor != null -> "lessonChange"
        selectedExamPeriod != null -> "examTimetableEditor"
        showExamTimetablePeriods -> "examTimetablePeriods"
        showLessonSearch -> "lessonSearch"
        showTaskPlanCalendar -> "taskPlanCalendar"
        else -> "main"
    }

    pendingLessonMoveDialog?.let { dialogState ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.dialog_move_lesson_items_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.dialog_move_lesson_items_message))
                    Text(
                        text = stringResource(
                            R.string.dialog_move_lesson_items_target,
                            formatDateTimeForDisplay(
                                dialogState.targetDate,
                                dialogState.targetTime.hour,
                                dialogState.targetTime.minute,
                                uiState.settings?.showWeekdayOnDates ?: false
                            )
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dialogState.items.forEach { item ->
                                Text(
                                    text = stringResource(
                                        R.string.dialog_move_lesson_items_entry,
                                        item.title,
                                        formatDateTimeForDisplay(
                                            item.fromDate,
                                            item.fromHour,
                                            item.fromMinute,
                                            uiState.settings?.showWeekdayOnDates ?: false
                                        )
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentDialog = dialogState
                        pendingLessonMoveDialog = null
                        appScope.launch {
                            moveLessonItems(currentDialog)
                            snackbarHostState.showSnackbar(resources.getString(R.string.msg_lesson_change_items_moved))
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_move_items))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        pendingLessonMoveDialog = null
                        appScope.launch {
                            snackbarHostState.showSnackbar(resources.getString(R.string.msg_lesson_change_saved))
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_keep_items))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        onOssLicenses = { showOssLicenses = true },
                        onUpdateAvailable = ::openUpdateOverview
                    )
                }
                "update" -> {
                    availableUpdate?.let { updateInfo ->
                        UpdateOverviewScreen(
                            updateInfo = updateInfo,
                            onBack = { showUpdateOverview = false }
                        )
                    }
                }
                "lessonChange" -> {
                    val editorState = lessonChangeEditor
                    if (editorState != null) {
                        val lessonAutocompleteEntries = uiState.lessons.values.flatMap { lesson ->
                            when (lesson.mode) {
                                LessonMode.WEEKLY -> listOf(
                                    LessonAutocompleteEntry(
                                        subject = lesson.weeklySubject.trim(),
                                        teacher = lesson.weeklyTeacher.trim(),
                                        location = lesson.weeklyLocation?.trim()?.takeIf { it.isNotEmpty() }
                                    )
                                )
                                LessonMode.ALTERNATING -> listOf(
                                    LessonAutocompleteEntry(
                                        subject = lesson.aSubject.trim(),
                                        teacher = lesson.aTeacher.trim(),
                                        location = lesson.aLocation?.trim()?.takeIf { it.isNotEmpty() }
                                    ),
                                    LessonAutocompleteEntry(
                                        subject = lesson.bSubject.trim(),
                                        teacher = lesson.bTeacher.trim(),
                                        location = lesson.bLocation?.trim()?.takeIf { it.isNotEmpty() }
                                    )
                                )
                            }
                        }.filter { it.subject.isNotBlank() }
                        val subjectSuggestions = lessonAutocompleteEntries
                            .map { it.subject }
                            .distinct()
                            .sorted()
                        val subjectTeacherCandidates = lessonAutocompleteEntries
                            .filter { it.teacher.isNotBlank() }
                            .groupBy({ it.subject }, { it.teacher })
                            .mapValues { (_, teachers) -> teachers.distinct().sorted() }
                        val subjectTeacherLocationCandidates = lessonAutocompleteEntries
                            .filter { it.teacher.isNotBlank() && !it.location.isNullOrBlank() }
                            .groupBy(
                                keySelector = { it.subject to it.teacher },
                                valueTransform = { it.location.orEmpty() }
                            )
                            .mapValues { (_, locations) -> locations.filter { it.isNotBlank() }.distinct().sorted() }
                        val subjectLocationCandidates = lessonAutocompleteEntries
                            .filter { !it.location.isNullOrBlank() }
                            .groupBy(
                                keySelector = { it.subject },
                                valueTransform = { it.location.orEmpty() }
                            )
                            .mapValues { (_, locations) -> locations.filter { it.isNotBlank() }.distinct().sorted() }

                        ChangeLessonScreen(
                            date = editorState.date,
                            slotLabel = currentClassSlots.firstOrNull { it.index == editorState.slotIndex }?.label
                                ?: "${editorState.slotIndex + 1}限",
                            originalLesson = editorState.originalLesson,
                            initialLesson = editorState.currentLesson,
                            subjectSuggestions = subjectSuggestions,
                            subjectTeacherCandidates = subjectTeacherCandidates,
                            subjectTeacherLocationCandidates = subjectTeacherLocationCandidates,
                            subjectLocationCandidates = subjectLocationCandidates,
                            canClear = editorState.existingChangedLesson != null,
                            onSave = { subject, teacher, location ->
                                saveChangedLessonWithPrompt(editorState, subject, teacher, location)
                            },
                            onClear = {
                                appScope.launch {
                                    viewModel.clearChangedLessonDirect(editorState.date, editorState.slotIndex)
                                    lessonChangeEditor = null
                                    snackbarHostState.showSnackbar(resources.getString(R.string.msg_lesson_change_cleared))
                                }
                            },
                            onBack = { lessonChangeEditor = null }
                        )
                    }
                }
                "lessonSearch" -> {
                    LessonSearchScreen(
                        state = uiState,
                        classSlots = currentClassSlots,
                        resolveLesson = ::resolveDisplayedLesson,
                        dayTypeEntityForDate = { date -> uiState.dayTypeEntities[date] },
                        changedLessonForDate = { date, slotIndex ->
                            uiState.changedLessons[date to slotIndex]
                        },
                        isLessonCancelled = { date, slotIndex ->
                            viewModel.isLessonCancelled(date, slotIndex, uiState.cancelledLessons)
                        },
                        onOpenDate = { date ->
                            viewModel.setResultDate(date)
                            requestedOutputDayEpochDay = date.toEpochDay()
                            selectedTab = AppTab.Output
                            showLessonSearch = false
                        },
                        onBack = { showLessonSearch = false }
                    )
                }
                "examTimetablePeriods" -> {
                    ExamTimetablePeriodListScreen(
                        periods = examPeriods,
                        configuredDates = uiState.examLessons.values
                            .filter { it.date in uiState.examDaySchedules && it.hasEnteredContent() }
                            .mapTo(mutableSetOf()) { it.date },
                        examNames = uiState.examDaySchedules.mapValues { it.value.examName },
                        onBack = { showExamTimetablePeriods = false },
                        onOpenPeriod = { period ->
                            selectedExamPeriodStartEpochDay = period.startDate.toEpochDay()
                        }
                    )
                }
                "examTimetableEditor" -> {
                    val period = selectedExamPeriod
                    val settings = uiState.settings
                    if (period != null && settings != null) {
                        ExamTimetableEditorScreen(
                            period = period,
                            settings = settings,
                            existingSchedules = uiState.examDaySchedules,
                            existingLessons = uiState.examLessons,
                            onBack = { selectedExamPeriodStartEpochDay = null },
                            onSave = { schedules, lessons ->
                                viewModel.saveExamPeriodSchedules(schedules, lessons)
                                selectedExamPeriodStartEpochDay = null
                            }
                        )
                    }
                }
                "taskPlanCalendar" -> {
                    TaskPlanCalendarScreen(
                        tasks = uiState.tasks,
                        plans = uiState.plans,
                        showWeekdayOnDates = uiState.settings?.showWeekdayOnDates ?: false,
                        dayTypeEntityForDate = { date -> uiState.dayTypeEntities[date] },
                        onBack = { showTaskPlanCalendar = false },
                        onOpenTask = { task ->
                            showTaskPlanCalendar = false
                            navigateToTabFromAction(AppTab.Tasks)
                            focusedTaskId = task.id.takeIf { it > 0 }
                        },
                        onOpenPlan = { plan ->
                            showTaskPlanCalendar = false
                            navigateToTabFromAction(AppTab.Plans)
                            focusedPlanId = plan.id.takeIf { it > 0 }
                        }
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
                val settingsSubjectSuggestions = uiState.lessons.values
                    .flatMap { lesson ->
                        when (lesson.mode) {
                            LessonMode.WEEKLY -> listOf(lesson.weeklySubject)
                            LessonMode.ALTERNATING -> listOf(lesson.aSubject, lesson.bSubject)
                        }
                    }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                val settingsTeacherCandidates = uiState.lessons.values
                    .flatMap { lesson ->
                        when (lesson.mode) {
                            LessonMode.WEEKLY -> listOf(lesson.weeklySubject to lesson.weeklyTeacher)
                            LessonMode.ALTERNATING -> listOf(
                                lesson.aSubject to lesson.aTeacher,
                                lesson.bSubject to lesson.bTeacher
                            )
                        }
                    }
                    .map { (subject, teacher) -> subject.trim() to teacher.trim() }
                    .filter { (subject, teacher) -> subject.isNotBlank() && teacher.isNotBlank() }
                    .groupBy(
                        keySelector = { it.first },
                        valueTransform = { it.second }
                    )
                    .mapValues { (_, teachers) -> teachers.distinct().sorted() }
                SettingsScreen(
                    state = uiState,
                    onBack = { showSettings = false },
                    onAbout = {
                        showSettings = true
                        showAbout = true
                    },
                    onToggleLocalAi = viewModel::toggleLocalAi,
                    onToggleNaturalLanguageTaskAdd = viewModel::toggleNaturalLanguageTaskAdd,
                    onToggleLessonNotes = viewModel::toggleLessonNotes,
                    onToggleExamTimetable = viewModel::toggleExamTimetable,
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
                    onToggleSyncLessonsToCalendar = { enabled ->
                        if (enabled && !hasCalendarPermission(context)) {
                            pendingToggleSyncLessonsToCalendar = true
                            calendarPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        } else {
                            viewModel.toggleSyncLessonsToCalendar(enabled)
                        }
                    },
                    onEnableSyncLessonsToCalendar = { start, end ->
                        if (!hasCalendarPermission(context)) {
                            pendingEnableLessonCalendarSyncRange = start to end
                            calendarPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        } else {
                            viewModel.enableSyncLessonsToCalendar(start, end)
                        }
                    },
                    onUpdateLessonCalendarSyncRange = viewModel::updateLessonCalendarSyncRange,
                    onClearAppCalendarEvents = ::clearAppCalendarEvents,
                    onToggleCurrentTimeMarker = viewModel::toggleCurrentTimeMarker,
                    onToggleUnifyTaskPlanView = viewModel::toggleUnifyTaskPlanView,
                    onToggleShowWeekdayOnDates = viewModel::toggleShowWeekdayOnDates,
                    onToggleAdvancedTimeSettingsUi = viewModel::toggleAdvancedTimeSettingsUi,
                    subjectSuggestions = settingsSubjectSuggestions,
                    subjectTeacherCandidates = settingsTeacherCandidates,
                    onToggleLessonStartNotifications = viewModel::toggleLessonStartNotifications,
                    onUpdateLessonStartNotificationMinutesBefore = viewModel::updateLessonStartNotificationMinutesBefore,
                    onToggleLessonStartNotificationLiveUpdates = viewModel::toggleLessonStartNotificationLiveUpdates,
                    onToggleLessonStartNotificationProgressCountsDown = viewModel::toggleLessonStartNotificationProgressCountsDown,
                    onUpdateLessonStartNotificationLiveUpdateEarlyMinutes = viewModel::updateLessonStartNotificationLiveUpdateEarlyMinutes,
                    onUpdateLessonStartNotificationChipMode = viewModel::updateLessonStartNotificationChipMode,
                    onAddLessonNotificationExclusion = viewModel::addLessonNotificationExclusion,
                    onDeleteLessonNotificationExclusion = viewModel::deleteLessonNotificationExclusion,
                    onUpdateScheduleSettings = viewModel::updateScheduleSettingsSilently,
                    onUpdateExamTimetableSettings = viewModel::updateExamTimetableSettings,
                    onExportAllAsJson = { viewModel.exportAllData() },
                    onImportAllFromJson = viewModel::importAllData
                )
            }
            "sync" -> {
                SyncScreen(
                    state = uiState,
                    onBack = {
                        showSync = false
                    },
                    onSaveProfile = { nickname, name, pw, autoSync, conflictAuto ->
                        viewModel.saveSyncProfile(nickname, name, pw, autoSync, conflictAuto)
                    },
                    onOpenDiscovery = { showSyncDiscovery = true },
                    onOpenNearbySync = { showNearbySync = true },
                    onToggleTlsSync = viewModel::toggleTlsSync,
                    onPrepareTrustedSync = viewModel::prepareTrustedSync,
                    onApplyPreparedSync = viewModel::applyPreparedSync,
                    onDeleteRegisteredDevice = viewModel::removeTrustedSyncDevice,
                    formatTimestamp = viewModel::formatSyncTimestamp
                )
            }
            "nearbySync" -> {
                val nearbyState by viewModel.nearbyState.collectAsState()
                NearbySyncScreen(
                    nearbyState = nearbyState,
                    onBack = { showNearbySync = false },
                    onStartSearching = viewModel::startNearbySearch,
                    onConnectToEndpoint = viewModel::connectToNearbyEndpoint,
                    onAcceptConnection = viewModel::acceptNearbyConnection,
                    onRejectConnection = viewModel::rejectNearbyConnection,
                    onStopAll = viewModel::stopNearbySync,
                    onApplyConflictResolutions = viewModel::applyNearbyConflictResolutions,
                    formatTimestamp = viewModel::formatSyncTimestamp
                )
            }
            "syncDiscovery" -> {
                val localListeningPort by produceState(initialValue = 0) {
                    value = runCatching { viewModel.getSyncListeningPort() }.getOrDefault(0)
                }
                SyncDeviceDiscoveryScreen(
                    registeredDevices = uiState.registeredDevices,
                    conflictAutoNewerFirst = uiState.syncProfile?.conflictAutoNewerFirst ?: false,
                    localListeningPort = localListeningPort,
                    diagnostics = uiState.syncDiagnostics,
                    onBack = { showSyncDiscovery = false },
                    onDiscoverDevices = viewModel::discoverSyncDevices,
                    onConnectToHost = viewModel::connectToSyncHost,
                    onRunSelfConnectivityTest = viewModel::runSyncSelfConnectivityTest,
                    onPreparePasswordSync = viewModel::preparePasswordSync,
                    onPrepareTrustedSync = viewModel::prepareTrustedSync,
                    onApplyPreparedSync = viewModel::applyPreparedSync,
                    onRegisterTrustedDevice = viewModel::registerTrustedSyncDevice,
                    formatTimestamp = viewModel::formatSyncTimestamp
                )
            }
            "main" -> {
                val useDrawerNavigation = uiState.settings?.useDrawerNavigation ?: false
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val drawerScope = rememberCoroutineScope()

                val taskSubjectSuggestions = uiState.lessons.values
                    .flatMap { lesson ->
                        when (lesson.mode) {
                            LessonMode.WEEKLY -> listOf(lesson.weeklySubject)
                            LessonMode.ALTERNATING -> listOf(lesson.aSubject, lesson.bSubject)
                        }
                    }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                val taskTeacherCandidates = uiState.lessons.values
                    .flatMap { lesson ->
                        when (lesson.mode) {
                            LessonMode.WEEKLY -> listOf(lesson.weeklySubject to lesson.weeklyTeacher)
                            LessonMode.ALTERNATING -> listOf(
                                lesson.aSubject to lesson.aTeacher,
                                lesson.bSubject to lesson.bTeacher
                            )
                        }
                    }
                    .map { (subject, teacher) -> subject.trim() to teacher.trim() }
                    .filter { (subject, teacher) -> subject.isNotBlank() && teacher.isNotBlank() }
                    .groupBy(
                        keySelector = { it.first },
                        valueTransform = { it.second }
                    )
                    .mapValues { (_, teachers) -> teachers.distinct().sorted() }
                val naturalLanguageLessonCandidates = uiState.lessons.values
                    .flatMap { lesson ->
                        when (lesson.mode) {
                            LessonMode.WEEKLY -> listOf(
                                NaturalLanguageLessonCandidate(
                                    subject = lesson.weeklySubject.trim(),
                                    teacher = lesson.weeklyTeacher.trim().takeIf { it.isNotBlank() }
                                )
                            )
                            LessonMode.ALTERNATING -> listOf(
                                NaturalLanguageLessonCandidate(
                                    subject = lesson.aSubject.trim(),
                                    teacher = lesson.aTeacher.trim().takeIf { it.isNotBlank() }
                                ),
                                NaturalLanguageLessonCandidate(
                                    subject = lesson.bSubject.trim(),
                                    teacher = lesson.bTeacher.trim().takeIf { it.isNotBlank() }
                                )
                            )
                        }
                    }
                    .filter { it.subject.isNotBlank() }
                    .distinctBy { it.subject to it.teacher }
                val naturalLanguageModelFamilies = rememberModelFamilies()
                val naturalLanguageModelManager = remember { ModelDownloadManager(context) }
                val naturalLanguageModelOptions = remember(
                    showNaturalLanguageTaskAddDialog,
                    naturalLanguageModelFamilies
                ) {
                    val downloadedFiles = naturalLanguageModelManager.getDownloadedModels()
                        .filter { it.isFile && it.length() > 0L }
                    val downloadedByName = downloadedFiles.associateBy { it.name }
                    val knownModels = naturalLanguageModelFamilies.values.flatten()
                    val knownOptions = knownModels.mapNotNull { model ->
                        val primaryName = model.assets.firstOrNull()?.fileName ?: return@mapNotNull null
                        val modelFile = downloadedByName[primaryName] ?: return@mapNotNull null
                        NaturalLanguageModelOption(model.name, modelFile)
                    }
                    val knownPrimaryNames = knownModels.mapNotNull { it.assets.firstOrNull()?.fileName }.toSet()
                    val unknownOptions = downloadedFiles
                        .filter {
                            it.extension.equals("gguf", ignoreCase = true) &&
                                !it.name.startsWith("mmproj", ignoreCase = true) &&
                                it.name !in knownPrimaryNames
                        }
                        .map { NaturalLanguageModelOption(it.nameWithoutExtension, it) }
                    (knownOptions + unknownOptions).distinctBy { it.modelFile.name }
                }
                val editingTask = editingTaskId?.let { id ->
                    uiState.tasks.firstOrNull { it.id == id }
                }
                val editingPlan = editingPlanId?.let { id ->
                    uiState.plans.firstOrNull { it.id == id }
                }
                val prefillDueDate = prefillDueDateEpochDay?.let(LocalDate::ofEpochDay)
                val templateDueHour = prefillDueHour ?: uiState.settings?.firstPeriodStartHour ?: 8
                val templateDueMinute = prefillDueMinute ?: uiState.settings?.firstPeriodStartMinute ?: 40
                val prefillTaskTemplate = when {
                    editingTask != null || showPlanEditor -> null
                    naturalLanguageTaskDraft != null -> naturalLanguageTaskDraft
                    prefillSubject.isNotBlank() -> TaskEntity(
                        subject = prefillSubject,
                        teacher = prefillTeacher.takeIf { it.isNotBlank() },
                        title = "",
                        dueDate = prefillDueDate ?: LocalDate.now(),
                        dueHour = templateDueHour,
                        dueMinute = templateDueMinute,
                        createdDate = LocalDate.now()
                    )
                    else -> null
                }
                val prefillPlanTemplate = if (editingPlan == null && showPlanEditor && prefillSubject.isNotBlank()) {
                    TaskEntity(
                        subject = prefillSubject,
                        teacher = prefillTeacher.takeIf { it.isNotBlank() },
                        title = "",
                        dueDate = prefillDueDate ?: LocalDate.now(),
                        dueHour = templateDueHour,
                        dueMinute = templateDueMinute,
                        createdDate = LocalDate.now()
                    )
                } else null
                val isAnyEditorVisible = showTaskEditor || showPlanEditor

                suspend fun openNaturalLanguageTaskAdd(
                    input: String,
                    modelOption: NaturalLanguageModelOption,
                    onStatusUpdate: (String) -> Unit
                ): Boolean {
                    val fallbackResult = NaturalLanguageTaskParser.parse(
                        input = input,
                        candidates = naturalLanguageLessonCandidates,
                        today = LocalDate.now(),
                        now = LocalTime.now()
                    ) ?: return false
                    val serviceIntent = android.content.Intent(context, VlmInferenceService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                    val aiResult = try {
                        naturalLanguageInferenceEngine.analyzeNaturalLanguageTask(
                            modelFile = modelOption.modelFile,
                            input = input,
                            candidates = naturalLanguageLessonCandidates,
                            now = LocalDateTime.now(),
                            onStatusUpdate = onStatusUpdate
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.msg_natural_language_ai_fallback)
                        )
                        null
                    } finally {
                        context.stopService(serviceIntent)
                    }

                    val matchedAiCandidate = aiResult?.subject
                        ?.takeIf { it.isNotBlank() }
                        ?.let { aiSubject ->
                            naturalLanguageLessonCandidates.firstOrNull { candidate ->
                                candidate.subject.equals(aiSubject, ignoreCase = true) &&
                                    (
                                        aiResult.teacher.isNullOrBlank() ||
                                            candidate.teacher.equals(aiResult.teacher, ignoreCase = true)
                                        )
                            } ?: naturalLanguageLessonCandidates.firstOrNull { candidate ->
                                candidate.subject.equals(aiSubject, ignoreCase = true)
                            }
                        }
                    val resolvedSubject = matchedAiCandidate?.subject ?: fallbackResult.subject
                    val resolvedTeacher = when {
                        matchedAiCandidate == null -> fallbackResult.teacher
                        aiResult.teacher.isNullOrBlank() -> matchedAiCandidate.teacher ?: fallbackResult.teacher
                        matchedAiCandidate.teacher.equals(aiResult.teacher, ignoreCase = true) ->
                            matchedAiCandidate.teacher
                        else -> fallbackResult.teacher
                    }
                    val resolvedTime = if (aiResult?.dueHour != null && aiResult.dueMinute != null) {
                        aiResult.dueHour to aiResult.dueMinute
                    } else {
                        fallbackResult.dueHour to fallbackResult.dueMinute
                    }
                    naturalLanguageTaskDraft = TaskEntity(
                        subject = resolvedSubject,
                        teacher = resolvedTeacher,
                        title = aiResult?.title?.takeIf { it.isNotBlank() } ?: fallbackResult.title,
                        description = aiResult?.description ?: fallbackResult.description,
                        dueDate = aiResult?.dueDate ?: fallbackResult.dueDate,
                        dueHour = resolvedTime.first,
                        dueMinute = resolvedTime.second,
                        createdDate = LocalDate.now(),
                        useTeacherMatching = resolvedTeacher != null
                    )
                    clearLessonTaskPrefill()
                    editingTaskId = null
                    editingPlanId = null
                    showPlanEditor = false
                    showTaskEditor = true
                    return true
                }

                if (
                    InternalFeatureFlags.NATURAL_LANGUAGE_TASK_ADD &&
                    showNaturalLanguageTaskAddDialog
                ) {
                    NaturalLanguageTaskAddDialog(
                        modelOptions = naturalLanguageModelOptions,
                        onDismiss = { showNaturalLanguageTaskAddDialog = false },
                        onCancelInference = naturalLanguageInferenceEngine::cancelInference,
                        onCreateDraft = ::openNaturalLanguageTaskAdd
                    )
                }

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
                            autoResolveInitialSubject = naturalLanguageTaskDraft == null && prefillDueDateEpochDay == null,
                            showWeekdayOnDates = uiState.settings?.showWeekdayOnDates ?: false,
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
                                    appScope.launch {
                                        val savedPlan = savePlanWithIntegrations(
                                            plan = plan,
                                            syncCalendar = shouldSyncTaskToCalendar && hasCalendarPermission(context)
                                        )
                                        snackbarHostState.showSnackbar(resources.getString(R.string.msg_plan_saved))
                                        if (shouldSyncTaskToCalendar && !hasCalendarPermission(context)) {
                                            pendingPlanCalendarSync = savedPlan
                                            calendarPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_CALENDAR,
                                                    Manifest.permission.WRITE_CALENDAR
                                                )
                                            )
                                        }
                                    }
                                    showPlanEditor = false
                                    editingPlanId = null
                                    clearLessonTaskPrefill()
                                    naturalLanguageTaskDraft = null
                                } else {
                                    val shouldSyncTaskToCalendar = uiState.settings?.addTasksToCalendar == true
                                    appScope.launch {
                                        val savedTask = saveTaskWithIntegrations(
                                            task = task,
                                            syncCalendar = shouldSyncTaskToCalendar && hasCalendarPermission(context)
                                        )
                                        snackbarHostState.showSnackbar(resources.getString(R.string.msg_task_saved))
                                        if (shouldSyncTaskToCalendar && !hasCalendarPermission(context)) {
                                            pendingTaskCalendarSync = savedTask
                                            calendarPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_CALENDAR,
                                                    Manifest.permission.WRITE_CALENDAR
                                                )
                                            )
                                        }
                                    }
                                    showTaskEditor = false
                                    editingTaskId = null
                                    clearLessonTaskPrefill()
                                    naturalLanguageTaskDraft = null
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
                                clearLessonTaskPrefill()
                                naturalLanguageTaskDraft = null
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
                                        resolveLesson = ::resolveDisplayedLesson,
                                        resolveOriginalLesson = ::resolveOriginalLesson,
                                        changedLessonForDate = { date, slot -> uiState.changedLessons[date to slot] },
                                        lessonNotes = uiState.lessonNotes,
                                        onShiftDate = viewModel::shiftResultDate,
                                        onPickDate = viewModel::setResultDate,
                                        requestedDayViewEpochDay = requestedOutputDayEpochDay,
                                        onRequestedDayViewHandled = {
                                            requestedOutputDayEpochDay = null
                                        },
                                        onOpenLessonSearch = { showLessonSearch = true },
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
                                           onAddFromLesson = { subject, teacher, isPlan, date, time ->
                                               prefillSubject = subject
                                               prefillTeacher = teacher
                                               val lessonDateTime = LocalDateTime.of(date, time)
                                               if (lessonDateTime.isAfter(LocalDateTime.now())) {
                                                   prefillDueDateEpochDay = date.toEpochDay()
                                                   prefillDueHour = time.hour
                                                   prefillDueMinute = time.minute
                                               } else {
                                                   prefillDueDateEpochDay = null
                                                   prefillDueHour = null
                                                   prefillDueMinute = null
                                               }
                                               if (isPlan) {
                                                   showPlanEditor = true
                                                   showTaskEditor = false
                                                   editingPlanId = null
                                               } else {
                                                   showTaskEditor = true
                                                   showPlanEditor = false
                                                   editingTaskId = null
                                               }
                                           },
                                        onSetLessonCancelled = ::setLessonCancelledWithPrompt,
                                        onEditChangedLesson = ::openLessonChangeEditor,
                                        onSaveLessonNote = viewModel::saveLessonNote,
                                        onDeleteLessonNote = viewModel::deleteLessonNote,
                                        isLessonCancelled = { date, slotIndex ->
                                            viewModel.isLessonCancelled(date, slotIndex, uiState.cancelledLessons)
                                        }
                                    )

                                    AppTab.Tasks -> {
                                        val unifyTaskPlanView = uiState.settings?.unifyTaskPlanView ?: false
                                        if (unifyTaskPlanView) {
                                            // 統合ビュー：課題・予定を上部タブで切り替え
                                            UnifiedTaskPlanScreen(
                                                modifier = Modifier.padding(padding),
                                                uiState = uiState,
                                                selectedTabIndex = unifiedTaskPlanSelectedTabIndex,
                                                onSelectedTabIndexChange = { unifiedTaskPlanSelectedTabIndex = it },
                                                onOpenTask = { task ->
                                                    editingTaskId = task.id.takeIf { it > 0 }
                                                    showTaskEditor = true
                                                    showPlanEditor = false
                                                    naturalLanguageTaskDraft = null
                                                },
                                                onCreateTask = {
                                                    editingTaskId = null
                                                    showTaskEditor = true
                                                    showPlanEditor = false
                                                    naturalLanguageTaskDraft = null
                                                },
                                                showNaturalLanguageTaskAdd =
                                                    InternalFeatureFlags.NATURAL_LANGUAGE_TASK_ADD &&
                                                        uiState.settings?.enableNaturalLanguageTaskAdd == true,
                                                onOpenNaturalLanguageTaskAdd = { showNaturalLanguageTaskAddDialog = true },
                                                onDeleteTask = ::deleteTaskWithIntegrations,
                                                onMarkTaskComplete = ::markTaskCompleteWithIntegrations,
                                                onMarkTaskIncomplete = viewModel::markTaskAsIncomplete,
                                                onOpenPlan = { plan ->
                                                    editingPlanId = plan.id.takeIf { it > 0 }
                                                    showPlanEditor = true
                                                    showTaskEditor = false
                                                    naturalLanguageTaskDraft = null
                                                },
                                                onCreatePlan = {
                                                    editingPlanId = null
                                                    showPlanEditor = true
                                                    showTaskEditor = false
                                                    naturalLanguageTaskDraft = null
                                                },
                                                onDeletePlan = { plan ->
                                                    deletePlanWithIntegrations(plan)
                                                },
                                                onMarkPlanComplete = ::markPlanCompleteWithIntegrations,
                                                onMarkPlanIncomplete = viewModel::markPlanAsIncomplete
                                            )
                                        } else {
                                            // 従来のビュー：課題のみ
                                            TaskScreen(
                                                modifier = Modifier.padding(padding),
                                                tasks = uiState.incompleteTasks,
                                                completedTasks = uiState.tasks.filter { it.isCompleted },
                                                showWeekdayOnDates = uiState.settings?.showWeekdayOnDates ?: false,
                                                focusTaskId = focusedTaskId,
                                                onFocusHandled = { focusedTaskId = null },
                                                onOpenTaskEditor = { task ->
                                                    editingTaskId = task?.id?.takeIf { it > 0 }
                                                    showTaskEditor = true
                                                    showPlanEditor = false
                                                    naturalLanguageTaskDraft = null
                                                },
                                                showNaturalLanguageTaskAdd =
                                                    InternalFeatureFlags.NATURAL_LANGUAGE_TASK_ADD &&
                                                        uiState.settings?.enableNaturalLanguageTaskAdd == true,
                                                onOpenNaturalLanguageTaskAdd = { showNaturalLanguageTaskAddDialog = true },
                                                onDeleteTask = ::deleteTaskWithIntegrations,
                                                onMarkComplete = ::markTaskCompleteWithIntegrations,
                                                onMarkIncomplete = viewModel::markTaskAsIncomplete
                                            )
                                        }
                                    }

                                    AppTab.Plans -> TaskScreen(
                                        modifier = Modifier.padding(padding),
                                        tasks = uiState.incompletePlans.map { it.toTaskEntityLike() },
                                        completedTasks = uiState.plans.filter { it.isCompleted }.map { it.toTaskEntityLike() },
                                        showWeekdayOnDates = uiState.settings?.showWeekdayOnDates ?: false,
                                        focusTaskId = focusedPlanId,
                                        onFocusHandled = { focusedPlanId = null },
                                        onOpenTaskEditor = { task ->
                                            editingPlanId = task?.id?.takeIf { it > 0 }
                                            showPlanEditor = true
                                            showTaskEditor = false
                                            naturalLanguageTaskDraft = null
                                        },
                                        onDeleteTask = { task ->
                                            uiState.plans.firstOrNull { it.id == task.id }?.let { plan ->
                                                deletePlanWithIntegrations(plan)
                                            }
                                        },
                                        onMarkComplete = { task ->
                                            uiState.plans.firstOrNull { it.id == task.id }?.let { markPlanCompleteWithIntegrations(it) }
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
                                        onUpdateHolidaySpecialLabel = viewModel::updateHolidaySpecialLabel,
                                        onResetFiscalYear = viewModel::resetFiscalYear,
                                        onUpdateTerm = viewModel::updateTerm,
                                        onSaveBreak = viewModel::saveLongBreak,
                                        onDeleteBreak = viewModel::deleteLongBreak,
                                        onOpenExamTimetables = {
                                            showExamTimetablePeriods = true
                                        },
                                        dayTypeForDate = { date -> viewModel.dayTypeForDate(date, uiState.dayTypeMap) },
                                        dayTypeEntityForDate = { date -> uiState.dayTypeEntities[date] }
                                    )
                                }
                            }
                        }
                        val selectedTabHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

                        val appScaffold: @Composable () -> Unit = {
                            val unifyTaskPlanView = uiState.settings?.unifyTaskPlanView ?: false
                            val topBarTitle = if (useDrawerNavigation) {
                                if (unifyTaskPlanView && selectedTab == AppTab.Tasks) {
                                    "ToDo"
                                } else {
                                    stringResource(selectedTab.labelRes)
                                }
                            } else {
                                stringResource(R.string.app_name)
                            }
                            val showTaskPlanActions =
                                selectedTab == AppTab.Tasks || selectedTab == AppTab.Plans

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
                                            if (
                                                selectedTab == AppTab.Timetable &&
                                                uiState.settings?.enableExamTimetable == true
                                            ) {
                                                IconButton(onClick = { showExamTimetablePeriods = true }) {
                                                    Icon(
                                                        Icons.Filled.Quiz,
                                                        contentDescription = stringResource(R.string.btn_create_exam_timetable),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            if (showTaskPlanActions) {
                                                IconButton(
                                                    onClick = { showTaskPlanCalendar = true }
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Search,
                                                        contentDescription = stringResource(R.string.cd_open_task_search),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            if (uiState.settings?.enableLocalAi == true) {
                                                IconButton(onClick = { showVlmImport = true }) {
                                                    Icon(Icons.Filled.AutoFixHigh, contentDescription = stringResource(R.string.cd_ai_import))
                                                }
                                            }
                                            IconButton(onClick = { showSync = true }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.sync_desktop),
                                                    contentDescription = stringResource(R.string.cd_open_local_sync)
                                                )
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
                                                            contentDescription = tabLabel,
                                                            modifier = if (!isSelected && tab == AppTab.Plans) {
                                                                Modifier.offset(x = (-0.5).dp)
                                                            } else {
                                                                Modifier
                                                            }
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

        AnimatedVisibility(
            visible = updateNotification != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            updateNotification?.let { updateInfo ->
                UpdateNotificationBanner(
                    updateInfo = updateInfo,
                    onOpen = { openUpdateOverview(updateInfo) },
                    onDismiss = {
                        dismissUpdateNotificationUntilNextVersion(context, updateInfo.tagName)
                        updateNotification = null
                    }
                )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun OutputScreen(
    modifier: Modifier,
    state: SchedulerUiState,
    dayTypeForDate: (LocalDate) -> DayType,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?,
    resolveLesson: (LocalDate, Int) -> ResolvedLesson?,
    resolveOriginalLesson: (LocalDate, Int) -> ResolvedLesson?,
    changedLessonForDate: (LocalDate, Int) -> ChangedLessonEntity?,
    lessonNotes: List<LessonNoteEntity>,
    onShiftDate: (Long) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    requestedDayViewEpochDay: Long?,
    onRequestedDayViewHandled: () -> Unit,
    onOpenLessonSearch: () -> Unit,
    onSaveLessonOverride: (LocalDate, Int, DayType) -> Unit,
    onClearLessonOverride: (LocalDate) -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onOpenPlan: (PlanEntity) -> Unit,
    onAddFromLesson: ((subject: String, teacher: String, isPlan: Boolean, date: LocalDate, time: LocalTime) -> Unit)? = null,
    onSetLessonCancelled: (LocalDate, Int, Boolean) -> Unit,
    onEditChangedLesson: (LocalDate, Int) -> Unit,
    onSaveLessonNote: (LocalDate, Int, String) -> Unit,
    onDeleteLessonNote: (LocalDate, Int) -> Unit,
    isLessonCancelled: (LocalDate, Int) -> Boolean
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val defaultWeekFocusDate = remember(today) {
        when (today.dayOfWeek) {
            DayOfWeek.SATURDAY -> today.plusDays(2)
            DayOfWeek.SUNDAY -> today.plusDays(1)
            else -> today
        }
    }
    val selectedDate = state.selectedResultDate
    var displayMode by rememberSaveable { mutableStateOf(OutputDisplayMode.DAY) }
    LaunchedEffect(requestedDayViewEpochDay) {
        if (requestedDayViewEpochDay != null) {
            displayMode = OutputDisplayMode.DAY
            onRequestedDayViewHandled()
        }
    }
    val isTodayWeekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
    val currentWeekReferenceDate = if (isTodayWeekend) defaultWeekFocusDate else today
    val weekDisplayReferenceDate = if (
        displayMode == OutputDisplayMode.WEEK &&
        selectedDate == today &&
        isTodayWeekend
    ) {
        currentWeekReferenceDate
    } else {
        selectedDate
    }
    val dateNavigationBase = if (displayMode == OutputDisplayMode.WEEK) {
        weekDisplayReferenceDate
    } else {
        selectedDate
    }
    val dayType = dayTypeForDate(selectedDate)
    val weekStart = weekDisplayReferenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekDates = remember(weekDisplayReferenceDate) { (0L..4L).map { weekStart.plusDays(it) } }
    val tasksByDueDate = remember(state.tasks) { state.tasks.groupBy { it.dueDate } }
    val plansByDueDate = remember(state.plans) { state.plans.groupBy { it.dueDate } }
    val isCurrentRangeToday = remember(displayMode, selectedDate, weekDates, today) {
        if (displayMode == OutputDisplayMode.DAY) selectedDate == today
        else weekDates.contains(currentWeekReferenceDate)
    }
    val displayModeIndicatorOffset by animateDpAsState(
        targetValue = if (displayMode == OutputDisplayMode.DAY) 0.dp else 42.dp,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "DisplayModeIndicatorOffset"
    )
    val showCurrentTimeMarker = state.settings?.showCurrentTimeMarker ?: false
    val lessonNotesEnabled = state.settings?.enableLessonNotes ?: false
    val shiftUnit = if (displayMode == OutputDisplayMode.DAY) 1L else 7L
    val classSlots = remember(state.settings) { state.settings.toClassSlots() }
    val examLessonsByDate = remember(state.examLessons) {
        state.examLessons.values.groupBy { it.date }
    }
    fun isExamScheduleDate(date: LocalDate): Boolean {
        val label = state.dayTypeEntities[date]?.holidaySpecialLabel
        return state.settings?.enableExamTimetable == true &&
            state.examDaySchedules.containsKey(date) &&
            examLessonsByDate[date].orEmpty().any { it.hasEnteredContent() } &&
            (label == HolidaySpecialLabel.MIDTERM || label == HolidaySpecialLabel.FINAL)
    }
    fun examSlotsForDate(date: LocalDate): List<ClassSlot> {
        val savedLessons = examLessonsByDate[date].orEmpty().sortedBy { it.slotIndex }
        if (savedLessons.isNotEmpty()) {
            return savedLessons.map { lesson ->
                ClassSlot(
                    index = lesson.slotIndex,
                    label = "${lesson.slotIndex + 1}時間目",
                    start = LocalTime.of(lesson.startHour, lesson.startMinute),
                    end = LocalTime.of(lesson.endHour, lesson.endMinute)
                )
            }
        }
        val settings = state.settings ?: return classSlots
        return generateClassSlots(
            periodsPerDay = settings.examPeriodsPerDay,
            periodDurationMin = settings.examPeriodDurationMin,
            breakBetweenPeriodsMin = settings.examBreakBetweenPeriodsMin,
            lunchBreakMin = settings.examLunchBreakMin,
            firstPeriodStartHour = settings.examFirstPeriodStartHour,
            firstPeriodStartMinute = settings.examFirstPeriodStartMinute,
            useKosenMode = false,
            lunchAfterPeriod = settings.examLunchAfterPeriod
        )
    }
    fun resolveExamLesson(date: LocalDate, slotIndex: Int): ResolvedLesson? {
        val lesson = state.examLessons[date to slotIndex] ?: return null
        if (lesson.subject.isBlank()) return null
        return ResolvedLesson(
            subject = lesson.subject,
            teacher = lesson.teacher,
            location = lesson.location.takeIf { it.isNotBlank() }
        )
    }
    val defaultViewConfiguration = LocalViewConfiguration.current
    val timetablePagerViewConfiguration = remember(defaultViewConfiguration) {
        object : ViewConfiguration by defaultViewConfiguration {
            override val touchSlop: Float = defaultViewConfiguration.touchSlop * 1.75f
        }
    }

    val arrivalMin: Int? = remember(state.settings, classSlots) {
        val s = state.settings
        if (s != null && s.arrivalHour >= 0) s.arrivalHour * 60 + s.arrivalMinute
        else 8 * 60 + 30
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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
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
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    CircleShape
                                )
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset(x = displayModeIndicatorOffset)
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutputDisplayMode.entries.forEach { mode ->
                                    val selected = displayMode == mode
                                    val iconTint by animateColorAsState(
                                        targetValue = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                                        label = "DisplayModeIconTint"
                                    )
                                    IconButton(
                                        onClick = {
                                            displayMode = mode
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (mode) {
                                                OutputDisplayMode.DAY -> Icons.Filled.Event
                                                OutputDisplayMode.WEEK -> Icons.Filled.TableChart
                                            },
                                            contentDescription = stringResource(mode.labelRes),
                                            tint = iconTint
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = { onPickDate(dateNavigationBase.minusDays(shiftUnit)) }) { Text("<") }
                                if (displayMode == OutputDisplayMode.DAY) {
                                    val selectedDayTypeEntity = dayTypeEntityForDate(selectedDate)
                                    val selectedExamName = if (isExamScheduleDate(selectedDate)) {
                                        state.examDaySchedules[selectedDate]
                                            ?.examName
                                            ?.trim()
                                            ?.takeIf { it.isNotBlank() }
                                    } else {
                                        null
                                    }
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
                                            "${stringResource(dayOfWeekRes(selectedDate.dayOfWeek))} / ${
                                                selectedExamName ?: dayTypeDisplayText(
                                                    dayType,
                                                    selectedDayTypeEntity?.overrideLessonDayOfWeek,
                                                    selectedDayTypeEntity?.holidaySpecialLabel
                                                )
                                            }",
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
                                IconButton(onClick = { onPickDate(dateNavigationBase.plusDays(shiftUnit)) }) { Text(">") }
                            }
                        }
                        IconButton(
                            onClick = onOpenLessonSearch
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.cd_search_timetable)
                            )
                        }
                    }
                }
        }

        item {
                val pagerPageCount = 51
                val centerPage = pagerPageCount / 2
                var pagerAnchorDate by remember { mutableStateOf(dateNavigationBase) }
                var pagerDisplayMode by remember { mutableStateOf(displayMode) }
                var pendingPagerSelectedDateEpochDays by remember { mutableStateOf<List<Long>>(emptyList()) }
                val pagerState = rememberPagerState(
                    initialPage = centerPage,
                    pageCount = { pagerPageCount }
                )
                val pagerRenderAnchorDate = if (pagerDisplayMode != displayMode) {
                    dateNavigationBase
                } else {
                    pagerAnchorDate
                }

                LaunchedEffect(pagerState.settledPage) {
                    val pageDelta = pagerState.settledPage.toLong() - centerPage.toLong()
                    val settledDate = pagerAnchorDate.plusDays(pageDelta * shiftUnit)
                    val isWeekendCurrentWeekCenter =
                        pageDelta == 0L &&
                            displayMode == OutputDisplayMode.WEEK &&
                            selectedDate == today &&
                            isTodayWeekend &&
                            settledDate == currentWeekReferenceDate
                    if (isWeekendCurrentWeekCenter) return@LaunchedEffect
                    if (settledDate != selectedDate) {
                        val settledEpochDay = settledDate.toEpochDay()
                        pendingPagerSelectedDateEpochDays =
                            (pendingPagerSelectedDateEpochDays + settledEpochDay).takeLast(16)
                        onPickDate(settledDate)
                    }
                    if (pageDelta == 0L) return@LaunchedEffect

                    if (
                        pagerState.settledPage == 0 ||
                        pagerState.settledPage == pagerPageCount - 1
                    ) {
                        pagerAnchorDate = settledDate
                        pagerState.scrollToPage(centerPage)
                    }
                }

                LaunchedEffect(selectedDate, displayMode, dateNavigationBase) {
                    if (pagerDisplayMode != displayMode) {
                        pagerDisplayMode = displayMode
                        pagerAnchorDate = dateNavigationBase
                        pendingPagerSelectedDateEpochDays = emptyList()
                        if (pagerState.currentPage != centerPage) {
                            pagerState.scrollToPage(centerPage)
                        }
                        return@LaunchedEffect
                    }
                    val selectedEpochDay = selectedDate.toEpochDay()
                    if (selectedEpochDay in pendingPagerSelectedDateEpochDays) {
                        pendingPagerSelectedDateEpochDays =
                            pendingPagerSelectedDateEpochDays - selectedEpochDay
                        return@LaunchedEffect
                    }
                    if (pagerAnchorDate != dateNavigationBase) {
                        pagerAnchorDate = dateNavigationBase
                    }
                    if (pagerState.currentPage != centerPage) {
                        pagerState.scrollToPage(centerPage)
                    }
                }

                CompositionLocalProvider(LocalViewConfiguration provides timetablePagerViewConfiguration) {
                    HorizontalPager(
                        state = pagerState,
                        pageSpacing = 12.dp,
                        verticalAlignment = Alignment.Top,
                        beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            pagerSnapDistance = PagerSnapDistance.atMost(1)
                        ),
                        key = { it },
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val pageDelta = page.toLong() - centerPage.toLong()
                        val pageDate = pagerRenderAnchorDate.plusDays(pageDelta * shiftUnit)
                        val shouldRenderPageDetails = page == pagerState.settledPage

                        if (displayMode == OutputDisplayMode.DAY) {
                            val pageTasks = tasksByDueDate[pageDate].orEmpty()
                            val pagePlans = plansByDueDate[pageDate].orEmpty()
                            val isExamDate = isExamScheduleDate(pageDate)
                            val pageClassSlots = if (isExamDate) examSlotsForDate(pageDate) else classSlots
                            val examSchedule = state.examDaySchedules[pageDate]
                            val examMemos = if (isExamDate) {
                                examLessonsByDate[pageDate]
                                    .orEmpty()
                                    .mapNotNull { lesson ->
                                        lesson.memo.trim().takeIf { it.isNotBlank() }?.let { lesson.slotIndex to it }
                                    }
                                    .toMap()
                            } else {
                                emptyMap()
                            }
                            DayScheduleTable(
                                date = pageDate,
                                dayType = dayTypeForDate(pageDate),
                                resolveLesson = { date, slotIndex ->
                                    if (isExamDate && date == pageDate) {
                                        resolveExamLesson(date, slotIndex)
                                    } else {
                                        resolveLesson(date, slotIndex)
                                    }
                                },
                                resolveOriginalLesson = resolveOriginalLesson,
                                changedLessonForDate = changedLessonForDate,
                                tasks = pageTasks,
                                plans = pagePlans,
                                lessonNotes = lessonNotes,
                                lessonNotesEnabled = lessonNotesEnabled,
                                onOpenTask = onOpenTask,
                                onOpenPlan = onOpenPlan,
                                classSlots = pageClassSlots,
                                arrivalMin = if (isExamDate && examSchedule != null) {
                                    examSchedule.arrivalHour * 60 + examSchedule.arrivalMinute
                                } else {
                                    arrivalMin
                                },
                                departureMin = if (isExamDate) null else departureMin,
                                isExamSchedule = isExamDate,
                                examMemos = examMemos,
                                showCurrentTimeMarker = showCurrentTimeMarker && pageDate == today,
                                showTaskPlanDetails = shouldRenderPageDetails,
                                onAddFromLesson = onAddFromLesson,
                                onSetLessonCancelled = onSetLessonCancelled,
                                onEditChangedLesson = onEditChangedLesson,
                                onSaveLessonNote = onSaveLessonNote,
                                onDeleteLessonNote = onDeleteLessonNote,
                                isLessonCancelled = isLessonCancelled,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            val pageWeekStart = pageDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            val pageWeekDates = remember(pageDate) {
                                (0L..4L).map { pageWeekStart.plusDays(it) }
                            }
                            val pageWeekSlotsByDate = remember(
                                pageWeekDates,
                                classSlots,
                                state.settings,
                                state.dayTypeEntities,
                                state.examDaySchedules,
                                state.examLessons
                            ) {
                                pageWeekDates.associateWith { date ->
                                    if (isExamScheduleDate(date)) examSlotsForDate(date) else classSlots
                                }
                            }
                            val pageWeekClassSlots = remember(pageWeekSlotsByDate, classSlots) {
                                pageWeekSlotsByDate.values
                                    .flatten()
                                    .map { it.index }
                                    .distinct()
                                    .sorted()
                                    .mapNotNull { slotIndex ->
                                        classSlots.firstOrNull { it.index == slotIndex }
                                            ?: pageWeekSlotsByDate.values
                                                .asSequence()
                                                .flatten()
                                                .firstOrNull { it.index == slotIndex }
                                    }
                            }
                            val pageTasks = remember(pageWeekDates, tasksByDueDate) {
                                pageWeekDates.flatMap { tasksByDueDate[it].orEmpty() }
                            }
                            val pagePlans = remember(pageWeekDates, plansByDueDate) {
                                pageWeekDates.flatMap { plansByDueDate[it].orEmpty() }
                            }
                            WeekScheduleTable(
                                dates = pageWeekDates,
                                dayTypeForDate = dayTypeForDate,
                                dayTypeEntityForDate = dayTypeEntityForDate,
                                resolveLesson = resolveLesson,
                                isExamScheduleDate = ::isExamScheduleDate,
                                resolveExamLesson = ::resolveExamLesson,
                                examNameForDate = { date ->
                                    state.examDaySchedules[date]?.examName?.trim()?.takeIf { it.isNotBlank() }
                                },
                                examSlotForDate = { date, slotIndex ->
                                    pageWeekSlotsByDate[date]?.firstOrNull { it.index == slotIndex }
                                },
                                examMemoForDate = { date, slotIndex ->
                                    state.examLessons[date to slotIndex]?.memo?.trim()?.ifBlank { null }
                                },
                                resolveOriginalLesson = resolveOriginalLesson,
                                changedLessonForDate = changedLessonForDate,
                                tasks = pageTasks,
                                plans = pagePlans,
                                lessonNotes = lessonNotes,
                                lessonNotesEnabled = lessonNotesEnabled,
                                classSlots = pageWeekClassSlots,
                                showCurrentTimeMarker = showCurrentTimeMarker && pageWeekDates.contains(today),
                                onSaveLessonOverride = onSaveLessonOverride,
                                onClearLessonOverride = onClearLessonOverride,
                                onAddFromLesson = onAddFromLesson,
                                onSetLessonCancelled = onSetLessonCancelled,
                                onEditChangedLesson = onEditChangedLesson,
                                onSaveLessonNote = onSaveLessonNote,
                                onDeleteLessonNote = onDeleteLessonNote,
                                isLessonCancelled = isLessonCancelled,
                                onDayClick = { date ->
                                    onPickDate(date)
                                    displayMode = OutputDisplayMode.DAY
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TaskPlanCalendarScreen(
    tasks: List<TaskEntity>,
    plans: List<PlanEntity>,
    showWeekdayOnDates: Boolean,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?,
    onBack: () -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onOpenPlan: (PlanEntity) -> Unit
) {
    val today = LocalDate.now()
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val firstDayOfWeek = remember(locale) {
        WeekFields.of(locale).firstDayOfWeek
    }
    val orderedDaysOfWeek = remember(firstDayOfWeek) {
        (0 until 7).map { offset ->
            DayOfWeek.of(((firstDayOfWeek.value - 1 + offset) % 7) + 1)
        }
    }
    val scope = rememberCoroutineScope()
    val monthPagerPageCount = 4_001
    val monthPagerCenterPage = monthPagerPageCount / 2
    val monthPagerAnchor = remember { YearMonth.from(today) }
    val monthPagerState = rememberPagerState(
        initialPage = monthPagerCenterPage,
        pageCount = { monthPagerPageCount }
    )
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchTokens = remember(searchQuery) { tokenizeSearchQuery(searchQuery) }
    val filteredTasks = remember(tasks, searchTokens) {
        if (searchTokens.isEmpty()) tasks else tasks.filter { it.matchesTaskPlanSearch(searchTokens) }
    }
    val filteredPlans = remember(plans, searchTokens) {
        if (searchTokens.isEmpty()) plans else plans.filter { it.matchesTaskPlanSearch(searchTokens) }
    }
    var observedMonthPagerPage by remember { mutableStateOf(monthPagerState.settledPage) }
    var selectedDateEpochDay by rememberSaveable {
        mutableStateOf<Long?>(today.toEpochDay())
    }
    val selectedDate = selectedDateEpochDay?.let(LocalDate::ofEpochDay)
    val tasksByDate = remember(filteredTasks) { filteredTasks.groupBy { it.dueDate } }
    val plansByDate = remember(filteredPlans) { filteredPlans.groupBy { it.dueDate } }
    val selectedTasks = selectedDate
        ?.let { tasksByDate[it] }
        .orEmpty()
        .sortedWith(compareBy<TaskEntity> { it.dueHour }.thenBy { it.dueMinute })
    val selectedPlans = selectedDate
        ?.let { plansByDate[it] }
        .orEmpty()
        .sortedWith(compareBy<PlanEntity> { it.dueHour }.thenBy { it.dueMinute })
    LaunchedEffect(monthPagerState.settledPage) {
        val settledPage = monthPagerState.settledPage
        if (settledPage != observedMonthPagerPage) {
            observedMonthPagerPage = settledPage
            selectedDateEpochDay = null
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_task_plan_calendar)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    label = { Text(stringResource(R.string.label_task_plan_calendar_query)) },
                    placeholder = { Text(stringResource(R.string.hint_task_plan_calendar_query)) }
                )
            }

            item {
                CalendarPagerCard(
                    state = monthPagerState,
                    anchorMonth = monthPagerAnchor.minusMonths(monthPagerCenterPage.toLong()),
                    orderedDaysOfWeek = orderedDaysOfWeek,
                    onPreviousMonth = {
                        scope.launch {
                            monthPagerState.animateScrollToPage(
                                (monthPagerState.settledPage - 1).coerceAtLeast(0)
                            )
                        }
                    },
                    onNextMonth = {
                        scope.launch {
                            monthPagerState.animateScrollToPage(
                                (monthPagerState.settledPage + 1)
                                    .coerceAtMost(monthPagerPageCount - 1)
                            )
                        }
                    }
                ) { _, date, cellModifier ->
                    val dateTasksForCell = tasksByDate[date].orEmpty()
                    val datePlansForCell = plansByDate[date].orEmpty()
                    TaskPlanCalendarDayCell(
                        date = date,
                        taskCount = dateTasksForCell.size,
                        planCount = datePlansForCell.size,
                        allTasksCompleted = dateTasksForCell.isNotEmpty() &&
                            dateTasksForCell.all { it.isCompleted },
                        allPlansCompleted = datePlansForCell.isNotEmpty() &&
                            datePlansForCell.all { it.isCompleted },
                        dayTypeEntity = dayTypeEntityForDate(date),
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        onClick = {
                            selectedDateEpochDay = date.toEpochDay()
                        },
                        modifier = cellModifier
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedDate != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = formatDateForDisplay(selectedDate, showWeekdayOnDates),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.msg_task_plan_calendar_select_date),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TaskPlanCountChip(
                        label = stringResource(R.string.tab_tasks),
                        count = selectedTasks.size,
                        color = MaterialTheme.colorScheme.error
                    )
                    TaskPlanCountChip(
                        label = stringResource(R.string.tab_plans),
                        count = selectedPlans.size,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (selectedDate == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.msg_task_plan_calendar_select_date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (selectedTasks.isEmpty() && selectedPlans.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchTokens.isEmpty()) {
                                stringResource(R.string.msg_task_plan_calendar_empty)
                            } else {
                                stringResource(R.string.msg_task_plan_calendar_search_empty)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(selectedTasks, key = { "task-${it.id}" }) { task ->
                TaskPlanCalendarItem(
                    typeLabel = stringResource(R.string.tab_tasks),
                    title = task.title,
                    subject = task.subject,
                    teacher = task.teacher,
                    hour = task.dueHour,
                    minute = task.dueMinute,
                    completed = task.isCompleted,
                    accentColor = MaterialTheme.colorScheme.error,
                    onClick = { onOpenTask(task) }
                )
            }

            items(selectedPlans, key = { "plan-${it.id}" }) { plan ->
                TaskPlanCalendarItem(
                    typeLabel = stringResource(R.string.tab_plans),
                    title = plan.title,
                    subject = plan.subject,
                    teacher = plan.teacher,
                    hour = plan.dueHour,
                    minute = plan.dueMinute,
                    completed = plan.isCompleted,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onOpenPlan(plan) }
                )
            }
        }
    }
}

@Composable
private fun TaskPlanCalendarDayCell(
    date: LocalDate,
    taskCount: Int,
    planCount: Int,
    allTasksCompleted: Boolean,
    allPlansCompleted: Boolean,
    dayTypeEntity: DayTypeEntity?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isToday -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val examLabelText = examCalendarLabelText(dayTypeEntity?.holidaySpecialLabel)
    val dateTextColor = calendarDateTextColor(date, dayTypeEntity, contentColor)

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = dateTextColor,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
            )
            if (examLabelText != null) {
                Text(
                    text = examLabelText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (taskCount > 0) {
                    CalendarItemCountDot(
                        count = taskCount,
                        color = if (allTasksCompleted) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                if (planCount > 0) {
                    CalendarItemCountDot(
                        count = planCount,
                        color = if (allPlansCompleted) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarItemCountDot(count: Int, color: Color) {
    Surface(
        shape = CircleShape,
        color = color
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TaskPlanCountChip(
    label: String,
    count: Int,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.13f)
    ) {
        Text(
            text = "$label $count",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TaskPlanCalendarItem(
    typeLabel: String,
    title: String,
    subject: String,
    teacher: String?,
    hour: Int,
    minute: Int,
    completed: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (completed) 0.42f else 1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = accentColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = typeLabel,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (completed) {
                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                    } else {
                        null
                    }
                )
                val detail = listOfNotNull(
                    subject.takeIf { it.isNotBlank() },
                    teacher?.takeIf { it.isNotBlank() }
                ).joinToString(" / ")
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "%02d:%02d".format(hour, minute),
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonSearchScreen(
    state: SchedulerUiState,
    classSlots: List<ClassSlot>,
    resolveLesson: (LocalDate, Int) -> ResolvedLesson?,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?,
    changedLessonForDate: (LocalDate, Int) -> ChangedLessonEntity?,
    isLessonCancelled: (LocalDate, Int) -> Boolean,
    onOpenDate: (LocalDate) -> Unit,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var effectiveQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(180)
        effectiveQuery = query
    }
    val today = LocalDate.now()
    val configuredStart = state.settings?.termStart ?: today
    val configuredEnd = state.settings?.termEnd ?: today.plusMonths(6)
    val searchStart = minOf(configuredStart, configuredEnd)
    val searchEnd = maxOf(configuredStart, configuredEnd)
    val initialSearchMonth = remember(searchStart, searchEnd, today) {
        YearMonth.from(today).coerceIn(
            YearMonth.from(searchStart),
            YearMonth.from(searchEnd)
        )
    }
    val initialSelectedSearchDate = remember(searchStart, searchEnd, today) {
        when {
            today.isBefore(searchStart) -> searchStart
            today.isAfter(searchEnd) -> searchEnd
            else -> today
        }
    }
    var displayedMonthText by rememberSaveable { mutableStateOf(initialSearchMonth.toString()) }
    var selectedSearchDateEpochDay by rememberSaveable {
        mutableStateOf(initialSelectedSearchDate.toEpochDay())
    }
    var searchDisplayMode by rememberSaveable { mutableStateOf(LessonSearchDisplayMode.CALENDAR) }
    val displayedMonth = remember(displayedMonthText) {
        runCatching { YearMonth.parse(displayedMonthText) }.getOrElse { YearMonth.from(searchStart) }
    }
    val selectedSearchDate = remember(selectedSearchDateEpochDay, searchStart, searchEnd) {
        val rawDate = LocalDate.ofEpochDay(selectedSearchDateEpochDay)
        when {
            rawDate.isBefore(searchStart) -> searchStart
            rawDate.isAfter(searchEnd) -> searchEnd
            else -> rawDate
        }
    }
    LaunchedEffect(selectedSearchDate, selectedSearchDateEpochDay) {
        if (selectedSearchDate.toEpochDay() != selectedSearchDateEpochDay) {
            selectedSearchDateEpochDay = selectedSearchDate.toEpochDay()
        }
    }
    val results = remember(
        effectiveQuery,
        state.settings,
        state.lessons,
        state.dayTypeMap,
        state.dayTypeEntities,
        state.changedLessons,
        state.cancelledLessons,
        classSlots
    ) {
        val tokens = tokenizeSearchQuery(effectiveQuery)

        if (tokens.isEmpty()) {
            emptyList()
        } else {
            val matches = mutableListOf<LessonSearchResult>()
            var date = searchStart
            var scannedDays = 0

            while (
                !date.isAfter(searchEnd) &&
                scannedDays < 730
            ) {
                classSlots.forEach { slot ->
                    if (isLessonCancelled(date, slot.index)) return@forEach
                    val lesson = resolveLesson(date, slot.index) ?: return@forEach
                    if (lesson.subject.isBlank()) return@forEach

                    val searchableText = normalizeSearchText(
                        listOf(
                            lesson.subject,
                            lesson.teacher,
                            lesson.location.orEmpty(),
                            japaneseDayOfWeekSearchText(date.dayOfWeek),
                            slot.label,
                            date.toString()
                        ).joinToString(" ")
                    )
                    if (tokens.all(searchableText::contains)) {
                        matches += LessonSearchResult(
                            date = date,
                            slot = slot,
                            lesson = lesson,
                            isChanged = changedLessonForDate(date, slot.index) != null
                        )
                    }
                }
                date = date.plusDays(1)
                scannedDays++
            }
            matches
        }
    }
    val resultsByDate = remember(results) { results.groupBy { it.date } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_search_timetable)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                label = { Text(stringResource(R.string.label_search_timetable_query)) },
                placeholder = { Text(stringResource(R.string.hint_search_timetable)) }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LessonSearchDisplayMode.entries.forEach { mode ->
                    FilterChip(
                        selected = searchDisplayMode == mode,
                        onClick = { searchDisplayMode = mode },
                        label = { Text(stringResource(mode.labelRes)) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (mode == LessonSearchDisplayMode.CALENDAR) {
                                    Icons.Filled.CalendarMonth
                                } else {
                                    Icons.Filled.TableChart
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = searchDisplayMode,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val spec = tween<IntOffset>(220, easing = FastOutSlowInEasing)
                    if (targetState == LessonSearchDisplayMode.CALENDAR) {
                        slideInHorizontally(animationSpec = spec) { -it / 3 } + fadeIn() togetherWith
                            slideOutHorizontally(animationSpec = spec) { it } + fadeOut()
                    } else {
                        slideInHorizontally(animationSpec = spec) { it } + fadeIn() togetherWith
                            slideOutHorizontally(animationSpec = spec) { -it / 3 } + fadeOut()
                    }
                },
                label = "LessonSearchDisplayModeTransition"
            ) { mode ->
                when (mode) {
                    LessonSearchDisplayMode.CALENDAR -> LessonSearchCalendarView(
                        modifier = Modifier.fillMaxSize(),
                        query = query,
                        displayedMonth = displayedMonth,
                        searchStart = searchStart,
                        searchEnd = searchEnd,
                        today = today,
                        selectedDate = selectedSearchDate,
                        results = results,
                        resultsByDate = resultsByDate,
                        classSlots = classSlots,
                        resolveLesson = resolveLesson,
                        dayTypeEntityForDate = dayTypeEntityForDate,
                        changedLessonForDate = changedLessonForDate,
                        isLessonCancelled = isLessonCancelled,
                        onDisplayedMonthChange = { month ->
                            displayedMonthText = month.toString()
                        },
                        onSelectDate = { date ->
                            if (date == selectedSearchDate) {
                                onOpenDate(date)
                            } else {
                                selectedSearchDateEpochDay = date.toEpochDay()
                            }
                        },
                        onOpenDate = { date ->
                            onOpenDate(date)
                        }
                    )
                    LessonSearchDisplayMode.LIST -> LessonSearchListView(
                        modifier = Modifier.fillMaxSize(),
                        query = query,
                        results = results.filter { !it.date.isBefore(today) },
                        onSelectDate = { date ->
                            displayedMonthText = YearMonth.from(date).toString()
                            selectedSearchDateEpochDay = date.toEpochDay()
                            searchDisplayMode = LessonSearchDisplayMode.CALENDAR
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LessonSearchCalendarView(
    query: String,
    displayedMonth: YearMonth,
    searchStart: LocalDate,
    searchEnd: LocalDate,
    today: LocalDate,
    selectedDate: LocalDate,
    results: List<LessonSearchResult>,
    resultsByDate: Map<LocalDate, List<LessonSearchResult>>,
    classSlots: List<ClassSlot>,
    resolveLesson: (LocalDate, Int) -> ResolvedLesson?,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?,
    changedLessonForDate: (LocalDate, Int) -> ChangedLessonEntity?,
    isLessonCancelled: (LocalDate, Int) -> Boolean,
    onDisplayedMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val firstDayOfWeek = remember(locale) {
        WeekFields.of(locale).firstDayOfWeek
    }
    val orderedDaysOfWeek = remember(firstDayOfWeek) {
        (0 until 7).map { offset ->
            DayOfWeek.of(((firstDayOfWeek.value - 1 + offset) % 7) + 1)
        }
    }
    val firstMonth = YearMonth.from(searchStart)
    val lastMonth = YearMonth.from(searchEnd)
    val pageCount = (
        java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, lastMonth) + 1L
        ).toInt().coerceAtLeast(1)
    val initialPage = java.time.temporal.ChronoUnit.MONTHS
        .between(firstMonth, displayedMonth)
        .toInt()
        .coerceIn(0, pageCount - 1)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount }
    )

    LaunchedEffect(displayedMonth, firstMonth, pageCount) {
        val targetPage = java.time.temporal.ChronoUnit.MONTHS
            .between(firstMonth, displayedMonth)
            .toInt()
            .coerceIn(0, pageCount - 1)
        if (targetPage != pagerState.settledPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        onDisplayedMonthChange(firstMonth.plusMonths(pagerState.settledPage.toLong()))
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CalendarPagerCard(
                state = pagerState,
                anchorMonth = firstMonth,
                orderedDaysOfWeek = orderedDaysOfWeek,
                previousEnabled = pagerState.settledPage > 0,
                nextEnabled = pagerState.settledPage < pageCount - 1,
                onPreviousMonth = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.settledPage - 1).coerceAtLeast(0)
                        )
                    }
                },
                onNextMonth = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.settledPage + 1).coerceAtMost(pageCount - 1)
                        )
                    }
                }
            ) { _, date, cellModifier ->
                SearchCalendarDayCell(
                    date = date,
                    matchCount = resultsByDate[date]?.size ?: 0,
                    isToday = date == today,
                    isSelected = date == selectedDate,
                    dayTypeEntity = dayTypeEntityForDate(date),
                    enabled = date in searchStart..searchEnd,
                    onClick = { onSelectDate(date) },
                    modifier = cellModifier
                )
            }
        }

        item {
            Text(
                text = when {
                    query.isBlank() -> stringResource(R.string.desc_search_calendar)
                    results.isEmpty() -> stringResource(R.string.msg_search_timetable_no_results)
                    else -> stringResource(
                        R.string.label_search_calendar_summary,
                        resultsByDate.size,
                        results.size
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            LessonSearchSelectedDaySchedule(
                date = selectedDate,
                query = query,
                classSlots = classSlots,
                resolveLesson = resolveLesson,
                changedLessonForDate = changedLessonForDate,
                isLessonCancelled = isLessonCancelled,
                onOpenDate = onOpenDate
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarPagerCard(
    state: PagerState,
    anchorMonth: YearMonth,
    orderedDaysOfWeek: List<DayOfWeek>,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    dayContent: @Composable (month: YearMonth, date: LocalDate, modifier: Modifier) -> Unit
) {
    val settledMonth = anchorMonth.plusMonths(state.settledPage.toLong())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = previousEnabled,
                    onClick = onPreviousMonth
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_previous_month)
                    )
                }
                Text(
                    text = stringResource(
                        R.string.label_search_calendar_month,
                        settledMonth.year,
                        settledMonth.monthValue
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(
                    enabled = nextEnabled,
                    onClick = onNextMonth
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.cd_next_month)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                orderedDaysOfWeek.forEach { dayOfWeek ->
                    Text(
                        text = stringResource(dayOfWeekRes(dayOfWeek)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (dayOfWeek) {
                            DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primary
                            DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cellSize = (maxWidth - 24.dp) / 7
                val calendarHeight = cellSize * 6 + 50.dp
                HorizontalPager(
                    state = state,
                    pageSpacing = 12.dp,
                    beyondViewportPageCount = 1,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = state,
                        pagerSnapDistance = PagerSnapDistance.atMost(1)
                    ),
                    key = { it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(calendarHeight)
                ) { page ->
                    val pageMonth = anchorMonth.plusMonths(page.toLong())
                    CalendarMonthGrid(
                        month = pageMonth,
                        firstDayOfWeek = orderedDaysOfWeek.first()
                    ) { date, cellModifier ->
                        dayContent(pageMonth, date, cellModifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    dayContent: @Composable (date: LocalDate, modifier: Modifier) -> Unit
) {
    val monthCells = remember(month, firstDayOfWeek) {
        val leadingEmptyDays =
            (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        List<LocalDate?>(42) { cellIndex ->
            val dayOfMonth = cellIndex - leadingEmptyDays + 1
            dayOfMonth
                .takeIf { it in 1..month.lengthOfMonth() }
                ?.let(month::atDay)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) { weekIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(7) { dayIndex ->
                    val cellIndex = weekIndex * 7 + dayIndex
                    val date = monthCells[cellIndex]
                    if (date == null) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    } else {
                        dayContent(
                            date,
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCalendarDayCell(
    date: LocalDate,
    matchCount: Int,
    isToday: Boolean,
    isSelected: Boolean,
    dayTypeEntity: DayTypeEntity?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val containerColor = when {
        matchCount > 0 -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        matchCount > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        isToday -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val examLabelText = examCalendarLabelText(dayTypeEntity?.holidaySpecialLabel)
    val dateTextColor = calendarDateTextColor(date, dayTypeEntity, contentColor)

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                when {
                    isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    isToday -> Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                    else -> Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = dateTextColor,
                fontWeight = if (isSelected || matchCount > 0 || isToday) FontWeight.Bold else FontWeight.Normal
            )
            if (examLabelText != null) {
                Text(
                    text = examLabelText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            if (matchCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = matchCount.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonSearchListView(
    query: String,
    results: List<LessonSearchResult>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("H:mm") }

    when {
        query.isBlank() -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = stringResource(R.string.desc_search_timetable),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        results.isEmpty() -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.msg_search_timetable_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.label_search_timetable_results, results.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(
                items = results,
                key = { "${it.date}-${it.slot.index}" }
            ) { result ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDate(result.date) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = result.lesson.subject,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (result.isChanged) {
                                Text(
                                    text = stringResource(R.string.label_changed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (result.lesson.teacher.isNotBlank()) {
                            Text(
                                text = result.lesson.teacher,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!result.lesson.location.isNullOrBlank()) {
                            Text(
                                text = result.lesson.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.label_search_timetable_date_chip,
                                        result.date.format(dateFormatter),
                                        stringResource(dayOfWeekRes(result.date.dayOfWeek))
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.label_search_timetable_time,
                                    result.slot.label,
                                    result.slot.start.format(timeFormatter),
                                    result.slot.end.format(timeFormatter)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonSearchSelectedDaySchedule(
    date: LocalDate,
    query: String,
    classSlots: List<ClassSlot>,
    resolveLesson: (LocalDate, Int) -> ResolvedLesson?,
    changedLessonForDate: (LocalDate, Int) -> ChangedLessonEntity?,
    isLessonCancelled: (LocalDate, Int) -> Boolean,
    onOpenDate: (LocalDate) -> Unit
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("H:mm") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = stringResource(
                        R.string.label_search_timetable_date_chip,
                        date.format(dateFormatter),
                        stringResource(dayOfWeekRes(date.dayOfWeek))
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.title_search_day_preview),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (query.isNotBlank()) {
            Text(
                text = stringResource(R.string.desc_search_day_preview, query),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        classSlots.forEach { slot ->
            key(slot.index) {
                val lesson = resolveLesson(date, slot.index)
                val cancelled = isLessonCancelled(date, slot.index)
                val changed = changedLessonForDate(date, slot.index) != null
                val matches = lesson != null && lessonMatchesSearchQuery(query, date, slot, lesson)
                val cardColor = if (matches) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
                val mainContentColor = if (matches) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDate(date) },
                    shape = RoundedCornerShape(20.dp),
                    color = cardColor
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = slot.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(
                                        R.string.label_search_slot_time,
                                        slot.start.format(timeFormatter),
                                        slot.end.format(timeFormatter)
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lesson?.subject ?: stringResource(R.string.label_no_class_short),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = mainContentColor.copy(alpha = if (cancelled) 0.55f else 1f),
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = if (cancelled) {
                                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    } else {
                                        null
                                    }
                                )
                                if (matches) {
                                    Text(
                                        text = stringResource(R.string.label_search_match),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (!lesson?.teacher.isNullOrBlank()) {
                                Text(
                                    text = lesson.teacher,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = mainContentColor.copy(alpha = 0.72f)
                                )
                            }
                            if (!lesson?.location.isNullOrBlank()) {
                                Text(
                                    text = lesson.location,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mainContentColor.copy(alpha = 0.72f)
                                )
                            }
                            if (cancelled || changed) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (cancelled) {
                                        Text(
                                            text = stringResource(R.string.label_cancelled),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (changed) {
                                        Text(
                                            text = stringResource(R.string.label_changed),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun lessonMatchesSearchQuery(
    query: String,
    date: LocalDate,
    slot: ClassSlot,
    lesson: ResolvedLesson
): Boolean {
    val tokens = tokenizeSearchQuery(query)
    if (tokens.isEmpty()) return false

    val searchableText = normalizeSearchText(
        listOf(
            lesson.subject,
            lesson.teacher,
            lesson.location.orEmpty(),
            japaneseDayOfWeekSearchText(date.dayOfWeek),
            slot.label,
            date.toString()
        ).joinToString(" ")
    )
    return tokens.all(searchableText::contains)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayScheduleTable(
    date: LocalDate,
    dayType: DayType,
    resolveLesson: (LocalDate, Int) -> ResolvedLesson?,
    resolveOriginalLesson: (LocalDate, Int) -> ResolvedLesson?,
    changedLessonForDate: (LocalDate, Int) -> ChangedLessonEntity?,
    tasks: List<TaskEntity>,
    plans: List<PlanEntity>,
    lessonNotes: List<LessonNoteEntity>,
    lessonNotesEnabled: Boolean = false,
    onOpenTask: (TaskEntity) -> Unit,
    onOpenPlan: (PlanEntity) -> Unit,
    classSlots: List<ClassSlot> = CLASS_SLOTS,
    arrivalMin: Int? = null,
    departureMin: Int? = null,
    isExamSchedule: Boolean = false,
    examMemos: Map<Int, String> = emptyMap(),
    showCurrentTimeMarker: Boolean = false,
    showTaskPlanDetails: Boolean = true,
    onAddFromLesson: ((subject: String, teacher: String, isPlan: Boolean, date: LocalDate, time: LocalTime) -> Unit)? = null,
    onSetLessonCancelled: (LocalDate, Int, Boolean) -> Unit,
    onEditChangedLesson: (LocalDate, Int) -> Unit,
    onSaveLessonNote: (LocalDate, Int, String) -> Unit,
    onDeleteLessonNote: (LocalDate, Int) -> Unit,
    isLessonCancelled: (LocalDate, Int) -> Boolean,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val hapticDaySchedule = LocalHapticFeedback.current
    data class LessonActionDialogData(
        val lesson: ResolvedLesson,
        val originalLesson: ResolvedLesson?,
        val isChanged: Boolean,
        val slotIndex: Int,
        val lessonStartTime: LocalTime,
        val cancelled: Boolean
    )
    data class LessonNoteEditorData(
        val date: LocalDate,
        val slotIndex: Int,
        val lesson: ResolvedLesson,
        val currentText: String?
    )
    var lessonActionDialog by remember { mutableStateOf<LessonActionDialogData?>(null) }
    var lessonNoteEditor by remember { mutableStateOf<LessonNoteEditorData?>(null) }
    val strAddFromLessonTitle = stringResource(R.string.dialog_add_from_lesson_title)
    if (lessonActionDialog != null) {
        val lessonSnap = lessonActionDialog!!
        LessonActionDialog(
            date = date,
            slotIndex = lessonSnap.slotIndex,
            lesson = lessonSnap.lesson,
            originalLesson = lessonSnap.originalLesson,
            isChanged = lessonSnap.isChanged,
            cancelled = lessonSnap.cancelled,
            onDismiss = { lessonActionDialog = null },
            onAddTask = onAddFromLesson?.let {
                {
                    lessonActionDialog = null
                    it(lessonSnap.lesson.subject, lessonSnap.lesson.teacher, false, date, lessonSnap.lessonStartTime)
                }
            },
            onAddPlan = onAddFromLesson?.let {
                {
                    lessonActionDialog = null
                    it(lessonSnap.lesson.subject, lessonSnap.lesson.teacher, true, date, lessonSnap.lessonStartTime)
                }
            },
            onToggleCancelled = {
                onSetLessonCancelled(date, lessonSnap.slotIndex, !lessonSnap.cancelled)
                lessonActionDialog = null
            },
            onChangeLesson = {
                lessonActionDialog = null
                onEditChangedLesson(date, lessonSnap.slotIndex)
            },
            onEditNote = if (lessonNotesEnabled) {
                {
                    lessonActionDialog = null
                    val currentNote = lessonNotes.firstOrNull { it.date == date && it.slotIndex == lessonSnap.slotIndex }
                    lessonNoteEditor = LessonNoteEditorData(
                        date = date,
                        slotIndex = lessonSnap.slotIndex,
                        lesson = lessonSnap.lesson,
                        currentText = currentNote?.text
                    )
                }
            } else {
                null
            }
        )
    }
    if (lessonNotesEnabled) lessonNoteEditor?.let { editor ->
        LessonNoteDialog(
            date = editor.date,
            slotIndex = editor.slotIndex,
            lesson = editor.lesson,
            currentText = editor.currentText,
            onDismiss = { lessonNoteEditor = null },
            onSave = { text ->
                onSaveLessonNote(editor.date, editor.slotIndex, text)
                lessonNoteEditor = null
            },
            onDelete = editor.currentText?.let {
                {
                    onDeleteLessonNote(editor.date, editor.slotIndex)
                    lessonNoteEditor = null
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
    val strNoLesson = stringResource(
        if (isExamSchedule) R.string.label_exam_no_test else R.string.label_no_class_short
    )
    val strHasTask = stringResource(R.string.label_task_exists)
    val strHasPlan = stringResource(R.string.label_plan_exists)
    val strCancelled = stringResource(R.string.label_cancelled)
    val isHoliday = dayType == DayType.HOLIDAY && !isExamSchedule

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
    val lessonNotesBySlot = remember(lessonNotes, date, lessonNotesEnabled) {
        if (lessonNotesEnabled) {
            lessonNotes
                .filter { it.date == date && it.text.isNotBlank() }
                .associateBy { it.slotIndex }
        } else {
            emptyMap()
        }
    }

    data class DueTick(
        val minuteOfDay: Int,
        val title: String,
        val description: String?,
        val task: TaskEntity? = null,
        val plan: PlanEntity? = null,
        val color: Color
    )

    val taskDueTicks = if (showTaskPlanDetails) {
        dateTasks.mapNotNull { task ->
            val dueMinuteOfDay = task.dueHour * 60 + task.dueMinute
            val matchedSlots = if (isHoliday) {
                emptyList()
            } else {
                classSlots.filter { slot ->
                    val lesson = resolveLesson(date, slot.index)
                    lesson != null && taskMatchesLesson(task, lesson)
                }
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
    } else {
        emptyList()
    }
    val planDueTicks = if (showTaskPlanDetails) {
        datePlans.mapNotNull { plan ->
            val dueMinuteOfDay = plan.dueHour * 60 + plan.dueMinute
            val matchedSlots = if (isHoliday) {
                emptyList()
            } else {
                classSlots.filter { slot ->
                    val lesson = resolveLesson(date, slot.index)
                    lesson != null && planMatchesLesson(plan, lesson)
                }
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
    } else {
        emptyList()
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
            val lesson = if (!isHoliday && seg.slotIndex != null) resolveLesson(date, seg.slotIndex) else null
            val changedLesson = if (!isHoliday && !isExamSchedule && seg.slotIndex != null) changedLessonForDate(date, seg.slotIndex) else null
            val originalLesson = if (!isHoliday && !isExamSchedule && seg.slotIndex != null && changedLesson != null) resolveOriginalLesson(date, seg.slotIndex) else null
            val isChangedLesson = changedLesson != null && originalLesson != null
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
            var prevMarkMinHeight = minMarkGapDp
            sortedMarks.forEach { marked ->
                val mark = marked.value
                val adjusted = maxOf(mark.rawOffset, prevAdjustedOffset + prevMarkMinHeight)
                when (mark.type) {
                    1 -> {
                        val idx = mark.dueIndex ?: return@forEach
                        adjustedDueOffsets[idx] = adjusted
                        // タイトル行(~14dp) + 備考がある場合は2行分(~22dp)を加算
                        val hasDes = sortedSegTicks.getOrNull(idx)?.description != null
                        prevMarkMinHeight = if (hasDes) 36f else 14f
                    }
                    2 -> { arrivalMarkOffsetDp = adjusted; prevMarkMinHeight = minMarkGapDp }
                    3 -> { departureMarkOffsetDp = adjusted; prevMarkMinHeight = minMarkGapDp }
                    else -> prevMarkMinHeight = minMarkGapDp
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
            val lessonTasksForBadges = if (lesson != null && slot != null) {
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
            val lessonPlansForBadges = if (lesson != null && slot != null) {
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
            val lessonTasks = if (showTaskPlanDetails) lessonTasksForBadges else emptyList()
            val lessonPlans = if (showTaskPlanDetails) lessonPlansForBadges else emptyList()
            val lessonMemo = slot?.let {
                examMemos[it.index]?.trim()?.ifBlank { null }
                    ?: lessonNotesBySlot[it.index]?.text?.trim()?.ifBlank { null }
            }
            val hasLessonTask = lessonTasksForBadges.isNotEmpty()
            val hasLessonPlan = lessonPlansForBadges.isNotEmpty()
            val hasLessonMemo = lessonMemo != null
            val hasLessonDetails = showTaskPlanDetails && (lessonTasks.isNotEmpty() || lessonPlans.isNotEmpty() || hasLessonMemo)
            val primaryTask = lessonTasksForBadges.firstOrNull()
            val primaryPlan = lessonPlansForBadges.firstOrNull()
            val lessonNoteCount = lessonTasks.count { !it.description.isNullOrBlank() } +
                lessonPlans.count { !it.description.isNullOrBlank() }
            val shouldShowLessonNotes = lessonNoteCount < 2
            data class LessonDetailItem(
                val dueHour: Int,
                val dueMinute: Int,
                val title: String,
                val note: String?,
                val isCompleted: Boolean,
                val titleColor: Color,
                val noteColor: Color,
                val titleFontWeight: FontWeight = FontWeight.Bold
            )
            val lessonDetailItems = buildList {
                lessonTasks.forEach { task ->
                    add(
                        LessonDetailItem(
                            dueHour = task.dueHour,
                            dueMinute = task.dueMinute,
                            title = task.title,
                            note = task.description?.trim()?.ifBlank { null },
                            isCompleted = task.isCompleted,
                            titleColor = if (task.isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            noteColor = if (task.isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    )
                }
                lessonPlans.forEach { plan ->
                    add(
                        LessonDetailItem(
                            dueHour = plan.dueHour,
                            dueMinute = plan.dueMinute,
                            title = plan.title,
                            note = plan.description?.trim()?.ifBlank { null },
                            isCompleted = plan.isCompleted,
                            titleColor = if (plan.isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            noteColor = if (plan.isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    )
                }
                lessonMemo?.let { memo ->
                    add(
                        LessonDetailItem(
                            dueHour = 99,
                            dueMinute = 99,
                            title = memo,
                            note = null,
                            isCompleted = false,
                            titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            noteColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            titleFontWeight = FontWeight.Normal
                        )
                    )
                }
            }.sortedWith(compareBy<LessonDetailItem> { it.dueHour }.thenBy { it.dueMinute })
            val isCancelled = !isHoliday && !isExamSchedule && slot != null && isLessonCancelled(date, slot.index)

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
                    val overlayTickReserveWidth = if (slot != null && lesson != null && sortedSegTicks.isNotEmpty()) {
                        with(density) {
                            textMeasurer.measure(
                                text = "\u2713 " + "あ".repeat(7),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            ).size.width.toDp()
                        } + 6.dp
                    } else {
                        0.dp
                    }
                    if (slot != null && !(lesson == null && segmentTicks.isNotEmpty())) {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = overlayTickReserveWidth)
                                .offset(y = 8.dp)
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            primaryTask != null -> onOpenTask(primaryTask)
                                            primaryPlan != null -> onOpenPlan(primaryPlan)
                                        }
                                    },
                                    onLongClick = if (!isExamSchedule && lesson != null && lesson.subject.isNotBlank()) {
                                        {
                                            hapticDaySchedule.performHapticFeedback(HapticFeedbackType.LongPress)
                                            lessonActionDialog = LessonActionDialogData(
                                                lesson = lesson,
                                                originalLesson = originalLesson,
                                                isChanged = isChangedLesson,
                                                slotIndex = slot.index,
                                                lessonStartTime = slot.start,
                                                cancelled = isCancelled
                                            )
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
                            val compactTeacherLabel = lesson?.teacher?.let { teacher ->
                                val names = teacher
                                    .replace('，', '、')
                                    .replace(',', '、')
                                    .split('、')
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                when {
                                    names.isEmpty() -> teacher
                                    !hasLessonDetails || names.size <= 2 -> names.joinToString("、")
                                    else -> "${names[0]}、${names[1]} 他${names.size - 2}名"
                                }
                            }.orEmpty()
                            val teacherInfoMaxWidth = if (hasLessonDetails) 136.dp else 176.dp
                            val teacherInfoMaxLines = if (hasLessonDetails) 1 else 2

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
                                        if (isCancelled) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                            ) {
                                                Text(
                                                    text = strCancelled,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
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
                                            modifier = Modifier
                                                .padding(start = 12.dp)
                                                .widthIn(max = teacherInfoMaxWidth)
                                        ) {
                                            Text(
                                                text = compactTeacherLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = teacherInfoMaxLines,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            if (!lesson.location.isNullOrBlank()) {
                                                Text(
                                                    text = lesson.location,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                ) {
                                    val subjectStyle = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = if (lesson != null) FontWeight.Bold else FontWeight.Normal
                                    )
                                    val detailTitleStyle = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                    val detailNoteStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                    val completionMarkWidth = if (hasLessonDetails) {
                                        with(density) {
                                            textMeasurer.measure(
                                                text = "\u2713 ",
                                                style = detailTitleStyle,
                                                maxLines = 1
                                            ).size.width.toDp()
                                        }
                                    } else {
                                        0.dp
                                    }
                                    val subjectMinWidth = if (hasLessonDetails) {
                                        with(density) {
                                            textMeasurer.measure(
                                                text = "あああ",
                                                style = subjectStyle,
                                                maxLines = 1
                                            ).size.width.toDp()
                                        } + 4.dp
                                    } else {
                                        0.dp
                                    }
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

                                    val subjectPreferredWidth = if (hasLessonDetails) {
                                        with(density) {
                                            textMeasurer.measure(
                                                text = lesson?.subject ?: strNoLesson,
                                                style = subjectStyle,
                                                maxLines = 1
                                            ).size.width.toDp()
                                        }.coerceAtLeast(subjectMinWidth)
                                    } else {
                                        0.dp
                                    }

                                    val lessonDetailCandidates = if (hasLessonDetails) {
                                        buildList {
                                            lessonDetailItems.take(2).forEach { item ->
                                                add(measureWidth(item.title) + completionMarkWidth)
                                                if (shouldShowLessonNotes) {
                                                    item.note?.let { add(measureWidth(it, isNote = true)) }
                                                }
                                            }
                                        }
                                    } else {
                                        emptyList()
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
                                    val shouldShowOriginalChangedLesson =
                                        isChangedLesson &&
                                        lesson != null &&
                                        originalLesson.subject.isNotBlank() &&
                                        (
                                            originalLesson.subject.trim() != lesson.subject.trim() ||
                                            originalLesson.teacher.trim() != lesson.teacher.trim()
                                        )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .width(subjectWidth)
                                                .alpha(if (isCancelled) 0.6f else 1f)
                                                .padding(end = contentGap),
                                            verticalArrangement = Arrangement.Bottom
                                        ) {
                                            if (shouldShowOriginalChangedLesson) {
                                                Text(
                                                    text = originalLesson.subject,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                                    modifier = Modifier.alpha(0.7f)
                                                )
                                            }
                                            Text(
                                                text = lesson?.subject ?: strNoLesson,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = when {
                                                    lesson == null -> FontWeight.Normal
                                                    isCancelled -> FontWeight.Light
                                                    else -> FontWeight.Bold
                                                },
                                                color = if (lesson != null) MaterialTheme.colorScheme.onSurface
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textDecoration = if (isCancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                            )
                                        }
                                        if (hasLessonDetails) {
                                            Column(
                                                modifier = Modifier.width(lessonDetailsWidth),
                                                horizontalAlignment = Alignment.End,
                                                verticalArrangement = Arrangement.Bottom
                                            ) {
                                                lessonDetailItems.take(2).forEach { item ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                        Text(
                                                            text = if (item.isCompleted) "\u2713 " else "",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = item.titleColor,
                                                            fontWeight = item.titleFontWeight,
                                                            maxLines = 1,
                                                            modifier = Modifier.width(completionMarkWidth)
                                                        )
                                                        Text(
                                                            text = item.title,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = item.titleColor,
                                                            fontWeight = item.titleFontWeight,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                            modifier = Modifier.widthIn(
                                                                max = (lessonDetailsWidth - completionMarkWidth)
                                                                    .coerceAtLeast(0.dp)
                                                            )
                                                        )
                                                    }
                                                    if (shouldShowLessonNotes && !item.note.isNullOrBlank()) {
                                                        Text(
                                                            text = item.note,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = item.noteColor,
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
                        }
                    } else if (seg.breakLabel != null) {
                        val breakLabelHeightDp = 24f
                        val tickBlockRangesDp = sortedSegTicks.mapIndexed { index, tick ->
                            val top = adjustedDueOffsets.getOrElse(index) {
                                ((tick.minuteOfDay - seg.startMin).coerceAtLeast(0) * dpPerMinute)
                            }
                            val height = if (tick.description != null) 36f else 18f
                            top to (top + height)
                        }
                        val breakContentHeightDp = (heightDp.value - 8f).coerceAtLeast(breakLabelHeightDp)
                        val centerY = ((breakContentHeightDp - breakLabelHeightDp) / 2f).coerceAtLeast(0f)
                        val topY = 8f
                        val bottomY = (breakContentHeightDp - breakLabelHeightDp - 8f).coerceAtLeast(0f)
                        val candidateOffsets = listOf(centerY, bottomY, topY).distinct()
                        val selectedOffsetY = candidateOffsets.firstOrNull { candidate ->
                            val candidateBottom = candidate + breakLabelHeightDp
                            tickBlockRangesDp.none { (top, bottom) ->
                                candidate < bottom && candidateBottom > top
                            }
                        } ?: centerY

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = seg.breakLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 20.dp)
                                    .offset(y = selectedOffsetY.dp)
                            )
                        }
                    }

                    sortedSegTicks.forEachIndexed { index, tick ->
                        val yOffset = adjustedDueOffsets.getOrElse(index) {
                            ((tick.minuteOfDay - seg.startMin).coerceAtLeast(0) * dpPerMinute)
                        }.dp
                        val tickCompleted = tick.task?.isCompleted == true || tick.plan?.isCompleted == true
                        val tickColor = if (tickCompleted) MaterialTheme.colorScheme.onSurfaceVariant else tick.color
                        val tickModifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = yOffset)
                            .clickable {
                                tick.task?.let(onOpenTask)
                                tick.plan?.let(onOpenPlan)
                            }
                        Column(
                            verticalArrangement = Arrangement.spacedBy((-2).dp),
                            modifier = if (overlayTickReserveWidth > 0.dp) {
                                tickModifier.widthIn(max = overlayTickReserveWidth)
                            } else {
                                tickModifier
                            }
                        ) {
                            Text(
                                text = "${if (tickCompleted) "\u2713 " else ""}${tick.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tickColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tick.description != null) {
                                Text(
                                    text = tick.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tickColor,
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

@Composable
private fun WeekendCurrentDayMarker(
    pointsTowardWeek: Boolean,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .width(10.dp)
            .height(16.dp)
    ) {
        val path = Path().apply {
            if (pointsTowardWeek) {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
            } else {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2f)
                lineTo(size.width, size.height)
            }
            close()
        }
        drawPath(path = path, color = color)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekScheduleTable(
    dates: List<LocalDate>,
    dayTypeForDate: (LocalDate) -> DayType,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?,
    resolveLesson: (LocalDate, Int) -> ResolvedLesson?,
    isExamScheduleDate: (LocalDate) -> Boolean = { false },
    resolveExamLesson: (LocalDate, Int) -> ResolvedLesson? = { _, _ -> null },
    examNameForDate: (LocalDate) -> String? = { null },
    examSlotForDate: (LocalDate, Int) -> ClassSlot? = { _, _ -> null },
    examMemoForDate: (LocalDate, Int) -> String? = { _, _ -> null },
    resolveOriginalLesson: (LocalDate, Int) -> ResolvedLesson?,
    changedLessonForDate: (LocalDate, Int) -> ChangedLessonEntity?,
    tasks: List<TaskEntity>,
    plans: List<PlanEntity>,
    lessonNotes: List<LessonNoteEntity>,
    lessonNotesEnabled: Boolean = false,
    classSlots: List<ClassSlot> = CLASS_SLOTS,
    showCurrentTimeMarker: Boolean = false,
    onSaveLessonOverride: (LocalDate, Int, DayType) -> Unit,
    onClearLessonOverride: (LocalDate) -> Unit,
    onAddFromLesson: ((subject: String, teacher: String, isPlan: Boolean, date: LocalDate, time: LocalTime) -> Unit)? = null,
    onSetLessonCancelled: (LocalDate, Int, Boolean) -> Unit,
    onEditChangedLesson: (LocalDate, Int) -> Unit,
    onSaveLessonNote: (LocalDate, Int, String) -> Unit,
    onDeleteLessonNote: (LocalDate, Int) -> Unit,
    isLessonCancelled: (LocalDate, Int) -> Boolean,
    onDayClick: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val currentTime = if (showCurrentTimeMarker) rememberCurrentTime() else null
    val haptic = LocalHapticFeedback.current
    data class WeekLessonActionDialogData(
        val date: LocalDate,
        val slotIndex: Int,
        val lessonStartTime: LocalTime,
        val lesson: ResolvedLesson,
        val originalLesson: ResolvedLesson?,
        val isChanged: Boolean,
        val cancelled: Boolean
    )
    data class WeekLessonNoteEditorData(
        val date: LocalDate,
        val slotIndex: Int,
        val lesson: ResolvedLesson,
        val currentText: String?
    )
    val slotLabelWidth = 44.dp
    val cellHeight = 140.dp
    val currentMinuteOfDay = currentTime?.let { it.hour * 60 + it.minute }
    var overrideEditingDate by remember(dates) { mutableStateOf<LocalDate?>(null) }
    var lessonActionDialog by remember(dates) { mutableStateOf<WeekLessonActionDialogData?>(null) }
    var lessonNoteEditor by remember(dates) { mutableStateOf<WeekLessonNoteEditorData?>(null) }
    val strCancelled = stringResource(R.string.label_cancelled)
    val strNoTest = stringResource(R.string.label_exam_no_test)
    val lessonNotesByDateSlot = remember(lessonNotes, dates, lessonNotesEnabled) {
        if (lessonNotesEnabled) {
            lessonNotes
                .filter { it.date in dates && it.text.isNotBlank() }
                .associateBy { it.date to it.slotIndex }
        } else {
            emptyMap()
        }
    }
    val weekendCurrentMarkerSide = remember(dates, today) {
        val firstVisibleDate = dates.firstOrNull()
        val lastVisibleDate = dates.lastOrNull()
        when {
            today.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> 0
            firstVisibleDate != null &&
                today.isBefore(firstVisibleDate) &&
                !today.isBefore(firstVisibleDate.minusDays(2)) -> -1
            lastVisibleDate != null &&
                today.isAfter(lastVisibleDate) &&
                !today.isAfter(lastVisibleDate.plusDays(2)) -> 1
            else -> 0
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // ヘッダー行
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(slotLabelWidth))
            Box(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    dates.forEach { date ->
                        val dayType = dayTypeForDate(date)
                        val dayTypeEntity = dayTypeEntityForDate(date)
                        val examName = if (isExamScheduleDate(date)) examNameForDate(date) else null
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
                                text = examName ?: dayTypeDisplayText(
                                    dayType,
                                    dayTypeEntity?.overrideLessonDayOfWeek,
                                    dayTypeEntity?.holidaySpecialLabel
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (weekendCurrentMarkerSide != 0) {
                    WeekendCurrentDayMarker(
                        pointsTowardWeek = weekendCurrentMarkerSide > 0,
                        modifier = Modifier
                            .align(if (weekendCurrentMarkerSide < 0) Alignment.TopStart else Alignment.TopEnd)
                            .offset(x = if (weekendCurrentMarkerSide < 0) (-2).dp else (-4).dp)
                            .padding(top = 14.dp)
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
                    val dayType = dayTypeForDate(date)
                    val isExamDate = isExamScheduleDate(date)
                    val examSlot = if (isExamDate) examSlotForDate(date, slot.index) else null
                    val dateSlot = examSlot ?: slot
                    val isExamSlot = isExamDate && examSlot != null
                    val isHoliday = dayType == DayType.HOLIDAY && !isExamDate
                    val lesson = when {
                        isHoliday -> null
                        isExamSlot -> resolveExamLesson(date, slot.index)
                        isExamDate -> null
                        else -> resolveLesson(date, slot.index)
                    }
                    val changedLesson = if (!isHoliday && !isExamDate) changedLessonForDate(date, slot.index) else null
                    val originalLesson = if (!isHoliday && !isExamDate && changedLesson != null) resolveOriginalLesson(date, slot.index) else null
                    val isChangedLesson = changedLesson != null && originalLesson != null
                    val slotStartMin = dateSlot.start.hour * 60 + dateSlot.start.minute
                    val slotEndMin = dateSlot.end.hour * 60 + dateSlot.end.minute
                    val currentTimeOffsetDp = if (
                        (!isExamDate || isExamSlot) &&
                        showCurrentTimeMarker &&
                        date == today &&
                        currentMinuteOfDay != null &&
                        currentMinuteOfDay in slotStartMin until slotEndMin
                    ) {
                        ((currentMinuteOfDay - slotStartMin).toFloat() / (slotEndMin - slotStartMin).toFloat()) * cellHeight.value
                    } else {
                        null
                    }
                    val hasTask = !isHoliday && lesson != null && tasks.any { task ->
                        task.dueDate == date && taskMatchesLesson(task, lesson)
                    }
                    val hasPlan = !isHoliday && lesson != null && plans.any { plan ->
                        plan.dueDate == date && planMatchesLesson(plan, lesson)
                    }
                    val lessonMemo = examMemoForDate(date, slot.index)
                        ?: lessonNotesByDateSlot[date to slot.index]?.text?.trim()?.ifBlank { null }
                    val isCancelled = !isHoliday && !isExamDate && isLessonCancelled(date, slot.index)
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
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = { onDayClick(date) },
                                    onLongClick = if (!isExamDate && lesson != null && lesson.subject.isNotBlank()) {
                                        {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            lessonActionDialog = WeekLessonActionDialogData(
                                                date = date,
                                                slotIndex = slot.index,
                                                lessonStartTime = dateSlot.start,
                                                lesson = lesson,
                                                originalLesson = originalLesson,
                                                isChanged = isChangedLesson,
                                                cancelled = isCancelled
                                            )
                                        }
                                    } else null
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = bgColor)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                if (lesson != null) {
                                    Column(
                                        modifier = Modifier.align(Alignment.TopStart),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isExamSlot) {
                                            Text(
                                                text = "%02d:%02d\n–%02d:%02d".format(
                                                    dateSlot.start.hour,
                                                    dateSlot.start.minute,
                                                    dateSlot.end.hour,
                                                    dateSlot.end.minute
                                                ),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 8.sp,
                                                    lineHeight = 9.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }
                                        Text(
                                            text = lesson.subject,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCancelled) FontWeight.Light else FontWeight.Bold,
                                            color = contentColor,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            textDecoration = if (isCancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                            modifier = Modifier.alpha(if (isCancelled) 0.6f else 1f)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (isCancelled) {
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                                ) {
                                                    Text(
                                                        text = strCancelled,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            if (isChangedLesson && !isCancelled) {
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.label_changed),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
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
                                        if (!lessonMemo.isNullOrBlank()) {
                                            Text(
                                                text = lessonMemo,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
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
                                } else if (isExamSlot) {
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = strNoTest,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "%02d:%02d\n–%02d:%02d".format(
                                                dateSlot.start.hour,
                                                dateSlot.start.minute,
                                                dateSlot.end.hour,
                                                dateSlot.end.minute
                                            ),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 8.sp,
                                                lineHeight = 9.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
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

    lessonActionDialog?.let { target ->
        LessonActionDialog(
            date = target.date,
            slotIndex = target.slotIndex,
            lesson = target.lesson,
            originalLesson = target.originalLesson,
            isChanged = target.isChanged,
            cancelled = target.cancelled,
            onDismiss = { lessonActionDialog = null },
            onAddTask = onAddFromLesson?.let {
                {
                    lessonActionDialog = null
                    it(target.lesson.subject, target.lesson.teacher, false, target.date, target.lessonStartTime)
                }
            },
            onAddPlan = onAddFromLesson?.let {
                {
                    lessonActionDialog = null
                    it(target.lesson.subject, target.lesson.teacher, true, target.date, target.lessonStartTime)
                }
            },
            onToggleCancelled = {
                onSetLessonCancelled(target.date, target.slotIndex, !target.cancelled)
                lessonActionDialog = null
            },
            onChangeLesson = {
                lessonActionDialog = null
                onEditChangedLesson(target.date, target.slotIndex)
            },
            onEditNote = if (lessonNotesEnabled) {
                {
                    lessonActionDialog = null
                    val currentNote = lessonNotesByDateSlot[target.date to target.slotIndex]
                    lessonNoteEditor = WeekLessonNoteEditorData(
                        date = target.date,
                        slotIndex = target.slotIndex,
                        lesson = target.lesson,
                        currentText = currentNote?.text
                    )
                }
            } else {
                null
            }
        )
    }

    if (lessonNotesEnabled) lessonNoteEditor?.let { editor ->
        LessonNoteDialog(
            date = editor.date,
            slotIndex = editor.slotIndex,
            lesson = editor.lesson,
            currentText = editor.currentText,
            onDismiss = { lessonNoteEditor = null },
            onSave = { text ->
                onSaveLessonNote(editor.date, editor.slotIndex, text)
                lessonNoteEditor = null
            },
            onDelete = editor.currentText?.let {
                {
                    onDeleteLessonNote(editor.date, editor.slotIndex)
                    lessonNoteEditor = null
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
            currentHolidaySpecialLabel = dayTypeEntity?.holidaySpecialLabel,
            onDismiss = { overrideEditingDate = null },
            onApply = { dayOfWeek, dayTypeValue, _ ->
                if (dayOfWeek == null) {
                    onClearLessonOverride(date)
                } else {
                    onSaveLessonOverride(date, dayOfWeek, dayTypeValue)
                }
                overrideEditingDate = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeLessonScreen(
    date: LocalDate,
    slotLabel: String,
    originalLesson: ResolvedLesson,
    initialLesson: ResolvedLesson,
    subjectSuggestions: List<String>,
    subjectTeacherCandidates: Map<String, List<String>>,
    subjectTeacherLocationCandidates: Map<Pair<String, String>, List<String>>,
    subjectLocationCandidates: Map<String, List<String>>,
    canClear: Boolean,
    onSave: (String, String, String?) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    var subject by remember(initialLesson.subject) { mutableStateOf(initialLesson.subject) }
    var teacher by remember(initialLesson.teacher) { mutableStateOf(initialLesson.teacher) }
    var location by remember(initialLesson.location) { mutableStateOf(initialLesson.location.orEmpty()) }
    var subjectEditedByUser by remember { mutableStateOf(false) }
    val canSave = subject.trim().isNotBlank()

    val teacherCandidates = remember(subject, subjectTeacherCandidates) {
        val key = subjectTeacherCandidates.keys.firstOrNull { it.equals(subject.trim(), ignoreCase = true) }
        key?.let { subjectTeacherCandidates[it].orEmpty() } ?: emptyList()
    }
    val subjectLocationOptions = remember(subject, subjectLocationCandidates) {
        val key = subjectLocationCandidates.keys.firstOrNull { it.equals(subject.trim(), ignoreCase = true) }
        key?.let { subjectLocationCandidates[it].orEmpty() } ?: emptyList()
    }
    val teacherLocationOptions = remember(subject, teacher, subjectTeacherLocationCandidates) {
        val key = subjectTeacherLocationCandidates.keys.firstOrNull {
            it.first.equals(subject.trim(), ignoreCase = true) && it.second.equals(teacher.trim(), ignoreCase = true)
        }
        key?.let { subjectTeacherLocationCandidates[it].orEmpty() } ?: emptyList()
    }
    val filteredSubjectSuggestions = remember(subject, subjectSuggestions) {
        val query = subject.trim()
        subjectSuggestions.filter { query.isBlank() || it.contains(query, ignoreCase = true) }.take(8)
    }

    LaunchedEffect(subject.trim(), subjectEditedByUser, teacherCandidates) {
        if (!subjectEditedByUser) return@LaunchedEffect
        if (teacherCandidates.size == 1) {
            teacher = teacherCandidates.first()
        }
    }

    LaunchedEffect(subject.trim(), teacher.trim(), subjectEditedByUser, teacherLocationOptions, subjectLocationOptions) {
        if (!subjectEditedByUser) return@LaunchedEffect
        val uniqueLocation = when {
            teacherLocationOptions.size == 1 -> teacherLocationOptions.first()
            teacher.trim().isBlank() && subjectLocationOptions.size == 1 -> subjectLocationOptions.first()
            else -> null
        }
        if (!uniqueLocation.isNullOrBlank()) {
            location = uniqueLocation
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_change_lesson)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { onSave(subject.trim(), teacher.trim(), location.trim().ifBlank { null }) },
                        enabled = canSave,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.btn_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${date.format(dateFormatter)} / $slotLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.label_current_lesson),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = originalLesson.subject,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (originalLesson.teacher.isNotBlank()) {
                        Text(
                            text = originalLesson.teacher,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!originalLesson.location.isNullOrBlank()) {
                        Text(
                            text = originalLesson.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_replacement_lesson),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = {
                            subject = it
                            subjectEditedByUser = true
                        },
                        label = { Text(stringResource(R.string.label_task_subject)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (filteredSubjectSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filteredSubjectSuggestions.forEach { suggestion ->
                                FilterChip(
                                    selected = suggestion.equals(subject.trim(), ignoreCase = true),
                                    onClick = {
                                        subject = suggestion
                                        subjectEditedByUser = true
                                    },
                                    label = { Text(suggestion) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text(stringResource(R.string.label_task_teacher)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (teacherCandidates.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            teacherCandidates.forEach { candidate ->
                                FilterChip(
                                    selected = candidate.equals(teacher.trim(), ignoreCase = true),
                                    onClick = { teacher = candidate },
                                    label = { Text(candidate) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text(stringResource(R.string.placeholder_location)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    val locationSuggestions = if (teacher.trim().isNotBlank()) teacherLocationOptions else subjectLocationOptions
                    if (locationSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            locationSuggestions.forEach { candidate ->
                                FilterChip(
                                    selected = candidate.equals(location.trim(), ignoreCase = true),
                                    onClick = { location = candidate },
                                    label = { Text(candidate) }
                                )
                            }
                        }
                    }
                }
            }

            if (canClear) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_clear_lesson_change))
                }
            }
        }
    }
}

@Composable
private fun LessonActionDialog(
    date: LocalDate,
    slotIndex: Int,
    lesson: ResolvedLesson,
    originalLesson: ResolvedLesson?,
    isChanged: Boolean,
    cancelled: Boolean,
    onDismiss: () -> Unit,
    onAddTask: (() -> Unit)?,
    onAddPlan: (() -> Unit)?,
    onToggleCancelled: () -> Unit,
    onChangeLesson: () -> Unit,
    onEditNote: (() -> Unit)?
) {
    val strDialogTitle = stringResource(R.string.dialog_lesson_action_title)
    val strAddFromLessonTitle = stringResource(R.string.dialog_add_from_lesson_title)
    val strAddTask = stringResource(R.string.dialog_add_task_from_lesson)
    val strAddPlan = stringResource(R.string.dialog_add_plan_from_lesson)
    val strSetCancelled = stringResource(R.string.dialog_mark_lesson_cancelled)
    val strClearCancelled = stringResource(R.string.dialog_unmark_lesson_cancelled)
    val strChangeLesson = stringResource(R.string.dialog_change_lesson)
    val strEditNote = stringResource(R.string.dialog_edit_lesson_note)
    val strCancelDialog = stringResource(R.string.btn_cancel)
    val strActionHint = stringResource(R.string.dialog_lesson_action_hint)
    val strStatusCancelled = stringResource(R.string.label_lesson_status_cancelled)
    val strStatusNormal = stringResource(R.string.label_lesson_status_normal)
    val strSlotInfo = stringResource(R.string.label_lesson_slot_info, date.format(dateFormatter), slotIndex + 1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strDialogTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strSlotInfo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (cancelled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (cancelled) strStatusCancelled else strStatusNormal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (cancelled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = lesson.subject,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (cancelled) FontWeight.Light else FontWeight.Bold,
                            textDecoration = if (cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            modifier = Modifier.alpha(if (cancelled) 0.65f else 1f)
                        )
                        if (isChanged && originalLesson != null && originalLesson.subject.isNotBlank()) {
                            Text(
                                text = originalLesson.subject,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                modifier = Modifier.alpha(0.7f)
                            )
                        }
                        if (lesson.teacher.isNotBlank()) {
                            Text(
                                text = lesson.teacher,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = "$strAddFromLessonTitle / $strActionHint",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onAddTask != null) {
                    Button(
                        onClick = onAddTask,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strAddTask) }
                }
                if (onAddPlan != null) {
                    OutlinedButton(
                        onClick = onAddPlan,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strAddPlan) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onChangeLesson,
                        modifier = Modifier.weight(1f)
                    ) { Text(strChangeLesson) }
                    TextButton(
                        onClick = onToggleCancelled,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = if (cancelled) strClearCancelled else strSetCancelled,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                if (onEditNote != null) {
                    OutlinedButton(
                        onClick = onEditNote,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strEditNote)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(strCancelDialog) }
            }
        }
    )
}

@Composable
private fun LessonNoteDialog(
    date: LocalDate,
    slotIndex: Int,
    lesson: ResolvedLesson,
    currentText: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var text by remember(date, slotIndex, currentText) { mutableStateOf(currentText.orEmpty()) }
    val trimmed = text.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_lesson_note_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.label_lesson_slot_info,
                        date.format(dateFormatter),
                        slotIndex + 1
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = lesson.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_lesson_note_text)) },
                    placeholder = { Text(stringResource(R.string.hint_lesson_note_text)) },
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                enabled = trimmed.isNotBlank(),
                onClick = { onSave(trimmed) }
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete) {
                        Text(stringResource(R.string.btn_delete_lesson_note))
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
internal fun LessonOverrideDialog(
    date: LocalDate,
    currentDayType: DayType,
    currentOverrideDayOfWeek: Int?,
    currentOverrideDayType: DayType?,
    currentHolidaySpecialLabel: HolidaySpecialLabel?,
    showDayTypeSelector: Boolean = true,
    onDismiss: () -> Unit,
    onApply: (Int?, DayType, HolidaySpecialLabel?) -> Unit
) {
    val effectiveShowDayTypeSelector = showDayTypeSelector
    var selectedDayOfWeek by remember(date, currentOverrideDayOfWeek) {
        mutableStateOf(currentOverrideDayOfWeek ?: date.dayOfWeek.value.coerceIn(1, 5))
    }
    var scheduleOverrideEnabled by remember(date, currentOverrideDayOfWeek) {
        mutableStateOf(currentOverrideDayOfWeek != null)
    }
    var selectedDayType by remember(date, currentDayType, currentOverrideDayType) {
        mutableStateOf(
            (currentOverrideDayType ?: currentDayType).takeIf { it != DayType.HOLIDAY } ?: DayType.A
        )
    }
    var dayOfWeekExpanded by remember { mutableStateOf(false) }
    var dayTypeExpanded by remember { mutableStateOf(false) }
    var holidayLabelExpanded by remember { mutableStateOf(false) }
    var selectedHolidayLabel by remember(date, currentHolidaySpecialLabel) {
        mutableStateOf(currentHolidaySpecialLabel)
    }
    val canUseScheduleMode = currentDayType != DayType.HOLIDAY
    val canUseLabelMode = currentDayType == DayType.HOLIDAY
    var holidayDialogMode by remember(date, currentDayType) {
        mutableStateOf(
            if (canUseLabelMode) {
                HolidayDialogMode.LABEL
            } else {
                HolidayDialogMode.SCHEDULE
            }
        )
    }

    val weekdayOptions = remember {
        listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
    val appliedDayType = if (effectiveShowDayTypeSelector) selectedDayType else currentDayType
    val holidayLabelOptions = remember {
        listOf(
            HolidaySpecialLabel.MIDTERM,
            HolidaySpecialLabel.FINAL,
            HolidaySpecialLabel.SCHOOL_CLOSED,
            HolidaySpecialLabel.EXCURSION
        )
    }
    val previewDayType = if (effectiveShowDayTypeSelector) selectedDayType else currentDayType

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

                Text(
                    text = stringResource(
                        if (holidayDialogMode == HolidayDialogMode.SCHEDULE) {
                            R.string.desc_holiday_mode_schedule
                        } else {
                            R.string.desc_holiday_mode_label
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = holidayDialogMode == HolidayDialogMode.SCHEDULE,
                        onClick = { if (canUseScheduleMode) holidayDialogMode = HolidayDialogMode.SCHEDULE },
                        enabled = canUseScheduleMode,
                        label = { Text(stringResource(R.string.label_holiday_mode_schedule)) }
                    )
                    FilterChip(
                        selected = holidayDialogMode == HolidayDialogMode.LABEL,
                        onClick = { if (canUseLabelMode) holidayDialogMode = HolidayDialogMode.LABEL },
                        enabled = canUseLabelMode,
                        label = { Text(stringResource(R.string.label_holiday_mode_label)) }
                    )
                }

                if (holidayDialogMode == HolidayDialogMode.SCHEDULE) {
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
                                    Text(
                                        if (scheduleOverrideEnabled) {
                                            stringResource(dayOfWeekRes(DayOfWeek.of(selectedDayOfWeek)))
                                        } else {
                                            stringResource(R.string.label_none)
                                        }
                                    )
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = dayOfWeekExpanded,
                            onDismissRequest = { dayOfWeekExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.label_none)) },
                                onClick = {
                                    scheduleOverrideEnabled = false
                                    dayOfWeekExpanded = false
                                }
                            )
                            weekdayOptions.forEach { dayOfWeek ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(dayOfWeekRes(dayOfWeek))) },
                                    onClick = {
                                        selectedDayOfWeek = dayOfWeek.value
                                        scheduleOverrideEnabled = true
                                        dayOfWeekExpanded = false
                                    }
                                )
                            }
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

                if (previewDayType == DayType.HOLIDAY && holidayDialogMode == HolidayDialogMode.LABEL) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.label_holiday_special_label), style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(
                                onClick = { holidayLabelExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        selectedHolidayLabel?.let { stringResource(holidaySpecialLabelTitleRes(it)) }
                                            ?: stringResource(R.string.label_holiday_special_label_none)
                                    )
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = holidayLabelExpanded,
                            onDismissRequest = { holidayLabelExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.label_holiday_special_label_none)) },
                                onClick = {
                                    selectedHolidayLabel = null
                                    holidayLabelExpanded = false
                                }
                            )
                            holidayLabelOptions.forEach { label ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(holidaySpecialLabelTitleRes(label))) },
                                    onClick = {
                                        selectedHolidayLabel = label
                                        holidayLabelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        selectedDayOfWeek.takeIf { scheduleOverrideEnabled },
                        previewDayType,
                        selectedHolidayLabel.takeIf { previewDayType == DayType.HOLIDAY }
                    )
                }
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        }
    )
}

@Composable
internal fun dayTypeDisplayText(
    dayType: DayType,
    overrideLessonDayOfWeek: Int?,
    holidaySpecialLabel: HolidaySpecialLabel? = null
): String {
    val baseLabel = if (dayType == DayType.HOLIDAY && holidaySpecialLabel != null) {
        stringResource(holidaySpecialLabelShortRes(holidaySpecialLabel))
    } else {
        stringResource(dayTypeRes(dayType))
    }
    if (overrideLessonDayOfWeek == null || dayType == DayType.HOLIDAY) {
        return baseLabel
    }
    return "$baseLabel(${stringResource(dayOfWeekRes(DayOfWeek.of(overrideLessonDayOfWeek)))})"
}

@Composable
private fun examCalendarLabelText(label: HolidaySpecialLabel?): String? = when (label) {
    HolidaySpecialLabel.MIDTERM,
    HolidaySpecialLabel.FINAL -> stringResource(holidaySpecialLabelShortRes(label))
    else -> null
}

@Composable
private fun calendarDateTextColor(
    date: LocalDate,
    dayTypeEntity: DayTypeEntity?,
    defaultColor: Color
): Color {
    val specialLabel = dayTypeEntity?.holidaySpecialLabel
    val ignoresHolidayColor = specialLabel == HolidaySpecialLabel.MIDTERM ||
        specialLabel == HolidaySpecialLabel.FINAL ||
        specialLabel == HolidaySpecialLabel.EXCURSION
    return when {
        date.dayOfWeek == DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primary
        specialLabel == HolidaySpecialLabel.SCHOOL_CLOSED -> MaterialTheme.colorScheme.error
        date.dayOfWeek == DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
        dayTypeEntity?.dayType == DayType.HOLIDAY && !ignoresHolidayColor ->
            MaterialTheme.colorScheme.error
        else -> defaultColor
    }
}

private fun normalizeTeacherCandidates(value: String): List<String> {
    return value
        .replace('，', '、')
        .replace(',', '、')
        .replace('　', ' ')
        .split('、', ' ')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun teacherMatches(expectedTeacher: String?, actualTeacher: String): Boolean {
    val normalizedExpected = expectedTeacher?.trim().orEmpty()
    if (normalizedExpected.isBlank()) return true
    val normalizedActual = actualTeacher.trim()
    if (normalizedActual.equals(normalizedExpected, ignoreCase = true)) return true
    return normalizeTeacherCandidates(normalizedActual).any { it.equals(normalizedExpected, ignoreCase = true) }
}

private fun taskMatchesLesson(task: TaskEntity, lesson: ResolvedLesson): Boolean {
    val taskSubject = task.subject.trim()
    val lessonSubject = lesson.subject.trim()
    if (taskSubject.isBlank() || lessonSubject.isBlank()) return false
    if (!taskSubject.equals(lessonSubject, ignoreCase = true)) return false
    return if (task.useTeacherMatching) teacherMatches(task.teacher, lesson.teacher) else true
}

private fun planMatchesLesson(plan: PlanEntity, lesson: ResolvedLesson): Boolean {
    val planSubject = plan.subject.trim()
    val lessonSubject = lesson.subject.trim()
    if (planSubject.isBlank() || lessonSubject.isBlank()) return false
    if (!planSubject.equals(lessonSubject, ignoreCase = true)) return false
    return if (plan.useTeacherMatching) teacherMatches(plan.teacher, lesson.teacher) else true
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

@StringRes
internal fun dayTypeRes(dayType: DayType): Int = when (dayType) {
    DayType.A -> R.string.daytype_a
    DayType.B -> R.string.daytype_b
    DayType.HOLIDAY -> R.string.daytype_holiday
}

private fun holidaySpecialLabelTitleRes(label: HolidaySpecialLabel): Int = when (label) {
    HolidaySpecialLabel.MIDTERM -> R.string.holiday_label_midterm
    HolidaySpecialLabel.FINAL -> R.string.holiday_label_final
    HolidaySpecialLabel.SCHOOL_CLOSED -> R.string.holiday_label_school_closed
    HolidaySpecialLabel.EXCURSION -> R.string.holiday_label_excursion
}

private fun holidaySpecialLabelShortRes(label: HolidaySpecialLabel): Int = when (label) {
    HolidaySpecialLabel.MIDTERM -> R.string.holiday_label_midterm_short
    HolidaySpecialLabel.FINAL -> R.string.holiday_label_final_short
    HolidaySpecialLabel.SCHOOL_CLOSED -> R.string.holiday_label_school_closed_short
    HolidaySpecialLabel.EXCURSION -> R.string.holiday_label_excursion_short
}

private enum class HolidayDialogMode {
    SCHEDULE,
    LABEL
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
