package jp.linkserver.nittcsc.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.InternalFeatureFlags
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import jp.linkserver.nittcsc.data.LessonNotificationExclusionEntity
import jp.linkserver.nittcsc.data.LessonStartNotificationChipMode
import jp.linkserver.nittcsc.data.LongBreakEntity
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.logic.PeriodLabelStyle
import jp.linkserver.nittcsc.update.clearDismissedUpdateNotification
import jp.linkserver.nittcsc.update.getUpdateCurrentVersionOverrideForTesting
import jp.linkserver.nittcsc.update.isIntDevBuild
import jp.linkserver.nittcsc.update.isShowLatestReleaseForTestingEnabled
import jp.linkserver.nittcsc.update.setShowLatestReleaseForTestingEnabled
import jp.linkserver.nittcsc.update.setUpdateCurrentVersionOverrideForTesting
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import jp.linkserver.nittcsc.ui.components.SettingsSection
import jp.linkserver.nittcsc.ui.components.PreferenceRow
import jp.linkserver.nittcsc.ui.components.ValuePreferenceRow
import jp.linkserver.nittcsc.ui.components.NavigationPreferenceRow
import jp.linkserver.nittcsc.ui.components.AppSettingsCategory
import jp.linkserver.nittcsc.ui.components.AppSettingsExpandableItem
import jp.linkserver.nittcsc.ui.components.AppSettingsGroup
import jp.linkserver.nittcsc.ui.components.AppSettingsScaffold
import jp.linkserver.nittcsc.ui.components.AppDialog
import jp.linkserver.nittcsc.ui.components.AppPrimaryButton
import jp.linkserver.nittcsc.ui.components.SettingsNavigationCard
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SchedulerUiState,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    onOpenLocalSync: () -> Unit = {},
    onToggleLocalAi: (Boolean) -> Unit,
    onToggleNaturalLanguageTaskAdd: (Boolean) -> Unit = {},
    onToggleDrawerNavigation: (Boolean) -> Unit,
    onToggleShowSyncButton: (Boolean) -> Unit = {},
    onOpenSpecialTimetableSettings: () -> Unit = {},
    onUpdateSecondTermStart: (Int, Int) -> Unit = { _, _ -> },
    timetableSettingsPage: TimetableSettingsPage? = null,
    onTimetableSettingsPageChange: (TimetableSettingsPage?) -> Unit = {},
    onToggleSemesterTimetables: (Boolean) -> Unit = {},
    onToggleAbTimetable: (Boolean) -> Unit = {},
    onToggleExamTimetable: (Boolean) -> Unit = {},
    onUpdateUiDesignMode: (UiDesignMode) -> Unit = {},
    onAcknowledgeExpressiveWarning: () -> Unit = {},
    onToggleAddTasksToCalendar: (Boolean) -> Unit,
    onToggleSyncLessonsToCalendar: (Boolean) -> Unit = {},
    onEnableSyncLessonsToCalendar: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    onUpdateLessonCalendarSyncRange: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    onClearAppCalendarEvents: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onToggleCurrentTimeMarker: (Boolean) -> Unit,
    onToggleUnifyTaskPlanView: (Boolean) -> Unit,
    onToggleShowWeekdayOnDates: (Boolean) -> Unit,
    onToggleAdvancedTimeSettingsUi: (Boolean) -> Unit,
    subjectSuggestions: List<String> = emptyList(),
    subjectTeacherCandidates: Map<String, List<String>> = emptyMap(),
    onToggleLessonStartNotifications: (Boolean) -> Unit = {},
    onUpdateLessonStartNotificationMinutesBefore: (Int) -> Unit = {},
    onToggleLessonStartNotificationLiveUpdates: (Boolean) -> Unit = {},
    onToggleLessonStartNotificationProgressCountsDown: (Boolean) -> Unit = {},
    onUpdateLessonStartNotificationLiveUpdateEarlyMinutes: (Int) -> Unit = {},
    onUpdateLessonStartNotificationChipMode: (LessonStartNotificationChipMode) -> Unit = {},
    onAddLessonNotificationExclusion: (String, String?, Boolean) -> Unit = { _, _, _ -> },
    onDeleteLessonNotificationExclusion: (LessonNotificationExclusionEntity) -> Unit = {},
    tutorialFirstTimeCheckDisabledForTesting: Boolean = false,
    onToggleTutorialFirstTimeCheckDisabledForTesting: (Boolean) -> Unit = {},
    onUpdateScheduleSettings: (periodsPerDay: Int, periodDurationMin: Int, breakBetweenPeriodsMin: Int, lunchBreakMin: Int, lunchAfterPeriod: Int, startHour: Int, startMinute: Int, periodLabelStyle: PeriodLabelStyle, arrivalHour: Int, arrivalMinute: Int, departureHour: Int, departureMinute: Int) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onUpdateExamTimetableSettings: (periodsPerDay: Int, periodDurationMin: Int, breakBetweenPeriodsMin: Int, lunchBreakMin: Int, lunchAfterPeriod: Int, startHour: Int, startMinute: Int, arrivalHour: Int, arrivalMinute: Int) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onExportAllAsJson: suspend () -> String = { "{}" },
    onImportAllFromJson: (String) -> Unit = {}
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3) {
        LegacySettingsScreen(
            state = state,
            onBack = onBack,
            onAbout = onAbout,
            onOpenLocalSync = onOpenLocalSync,
            onToggleLocalAi = onToggleLocalAi,
            onToggleNaturalLanguageTaskAdd = onToggleNaturalLanguageTaskAdd,
            onToggleDrawerNavigation = onToggleDrawerNavigation,
            onToggleShowSyncButton = onToggleShowSyncButton,
            onOpenSpecialTimetableSettings = onOpenSpecialTimetableSettings,
            onUpdateSecondTermStart = onUpdateSecondTermStart,
            onUpdateUiDesignMode = onUpdateUiDesignMode,
            onAcknowledgeExpressiveWarning = onAcknowledgeExpressiveWarning,
            onToggleAddTasksToCalendar = onToggleAddTasksToCalendar,
            onToggleSyncLessonsToCalendar = onToggleSyncLessonsToCalendar,
            onEnableSyncLessonsToCalendar = onEnableSyncLessonsToCalendar,
            onUpdateLessonCalendarSyncRange = onUpdateLessonCalendarSyncRange,
            onClearAppCalendarEvents = onClearAppCalendarEvents,
            onToggleCurrentTimeMarker = onToggleCurrentTimeMarker,
            onToggleUnifyTaskPlanView = onToggleUnifyTaskPlanView,
            onToggleShowWeekdayOnDates = onToggleShowWeekdayOnDates,
            onToggleAdvancedTimeSettingsUi = onToggleAdvancedTimeSettingsUi,
            subjectSuggestions = subjectSuggestions,
            subjectTeacherCandidates = subjectTeacherCandidates,
            onToggleLessonStartNotifications = onToggleLessonStartNotifications,
            onUpdateLessonStartNotificationMinutesBefore = onUpdateLessonStartNotificationMinutesBefore,
            onToggleLessonStartNotificationLiveUpdates = onToggleLessonStartNotificationLiveUpdates,
            onToggleLessonStartNotificationProgressCountsDown = onToggleLessonStartNotificationProgressCountsDown,
            onUpdateLessonStartNotificationLiveUpdateEarlyMinutes = onUpdateLessonStartNotificationLiveUpdateEarlyMinutes,
            onUpdateLessonStartNotificationChipMode = onUpdateLessonStartNotificationChipMode,
            onAddLessonNotificationExclusion = onAddLessonNotificationExclusion,
            onDeleteLessonNotificationExclusion = onDeleteLessonNotificationExclusion,
            tutorialFirstTimeCheckDisabledForTesting = tutorialFirstTimeCheckDisabledForTesting,
            onToggleTutorialFirstTimeCheckDisabledForTesting = onToggleTutorialFirstTimeCheckDisabledForTesting,
            onUpdateScheduleSettings = onUpdateScheduleSettings,
            onUpdateExamTimetableSettings = onUpdateExamTimetableSettings,
            onExportAllAsJson = onExportAllAsJson,
            onImportAllFromJson = onImportAllFromJson
        )
        return
    }
    val enabledLocalAi = state.settings?.enableLocalAi ?: false
    val enabledNaturalLanguageTaskAdd =
        InternalFeatureFlags.NATURAL_LANGUAGE_TASK_ADD &&
            (state.settings?.enableNaturalLanguageTaskAdd ?: false)
    val enabledDrawerNavigation = state.settings?.useDrawerNavigation ?: false
    val enabledTaskCalendarSync = state.settings?.addTasksToCalendar ?: false
    val enabledLessonCalendarSync = state.settings?.syncLessonsToCalendar ?: false
    val enabledCurrentTimeMarker = state.settings?.showCurrentTimeMarker ?: false
    val enabledUnifyTaskPlanView = state.settings?.unifyTaskPlanView ?: false
    val enabledShowWeekdayOnDates = state.settings?.showWeekdayOnDates ?: false
    val enabledAdvancedTimeSettingsUi = state.settings?.useAdvancedTimeSettingsUi ?: false
    val enabledLessonStartNotifications = state.settings?.lessonStartNotificationEnabled ?: false
    val supportsLessonStartLiveUpdates = Build.VERSION.SDK_INT >= 36
    val enabledLessonStartLiveUpdates = supportsLessonStartLiveUpdates &&
        (state.settings?.lessonStartNotificationLiveUpdatesEnabled ?: true)
    val enabledLessonStartProgressCountsDown =
        state.settings?.lessonStartNotificationProgressCountsDown ?: false
    val lessonStartLiveUpdateEarlyMinutes =
        state.settings?.lessonStartNotificationLiveUpdateEarlyMinutes ?: 0
    val lessonStartChipMode =
        state.settings?.lessonStartNotificationChipMode ?: LessonStartNotificationChipMode.MINUTE_TEXT
    var showLocalAiWarningDialog by remember { mutableStateOf(false) }
    var showUiDesignModeDialog by rememberSaveable { mutableStateOf(false) }
    var showExpressiveWarningDialog by rememberSaveable { mutableStateOf(false) }
    val s = state.settings
    val lessonCalendarSyncStart = s?.lessonCalendarSyncStart ?: s?.termStart ?: LocalDate.now()
    val lessonCalendarSyncEnd = s?.lessonCalendarSyncEnd ?: s?.termEnd ?: lessonCalendarSyncStart
    var lessonCalendarDatePickerTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var showLessonCalendarSyncWizard by rememberSaveable { mutableStateOf(false) }
    var lessonCalendarWizardStartEpoch by rememberSaveable { mutableStateOf(lessonCalendarSyncStart.toEpochDay()) }
    var lessonCalendarWizardEndEpoch by rememberSaveable { mutableStateOf(lessonCalendarSyncEnd.toEpochDay()) }
    var showClearAppCalendarEventsDialog by rememberSaveable { mutableStateOf(false) }
    var clearLessonCalendarEvents by rememberSaveable { mutableStateOf(true) }
    var clearDeadlineCalendarEvents by rememberSaveable { mutableStateOf(true) }
    var clearReminderCalendarEvents by rememberSaveable { mutableStateOf(true) }
    val lessonCalendarWizardStart = LocalDate.ofEpochDay(lessonCalendarWizardStartEpoch)
    val lessonCalendarWizardEnd = LocalDate.ofEpochDay(lessonCalendarWizardEndEpoch)

    fun openLessonCalendarSyncWizard() {
        val today = LocalDate.now()
        val (defaultStart, defaultEnd) = defaultLessonCalendarSyncRange(
            today = today,
            termStart = s?.termStart ?: today,
            termEnd = s?.termEnd ?: s?.termStart ?: today,
            longBreaks = state.longBreaks
        )
        lessonCalendarWizardStartEpoch = defaultStart.toEpochDay()
        lessonCalendarWizardEndEpoch = defaultEnd.toEpochDay()
        showLessonCalendarSyncWizard = true
    }

    var lessonStartNotificationMinutesBefore by remember(s?.lessonStartNotificationMinutesBefore) {
        mutableStateOf((s?.lessonStartNotificationMinutesBefore ?: 10).toString())
    }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var promotedNotificationsEnabled by remember {
        mutableStateOf(
            supportsLessonStartLiveUpdates &&
                runCatching {
                    NotificationManagerCompat.from(context).canPostPromotedNotifications()
                }.getOrDefault(false)
        )
    }
    fun refreshNotificationStates() {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationsEnabled = notificationManager.areNotificationsEnabled()
        promotedNotificationsEnabled = supportsLessonStartLiveUpdates &&
            runCatching {
                notificationManager.canPostPromotedNotifications()
            }.getOrDefault(false)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsScrollState = rememberScrollState()
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    val currentVersionName = remember {
        runCatching {
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
    }
    val isIntDev = remember(currentVersionName) { isIntDevBuild(currentVersionName) }
    var showLatestReleaseForTesting by remember {
        mutableStateOf(isShowLatestReleaseForTestingEnabled(context, currentVersionName))
    }
    var updateCurrentVersionOverrideForTesting by remember {
        mutableStateOf(getUpdateCurrentVersionOverrideForTesting(context, currentVersionName))
    }
    LaunchedEffect(lessonStartNotificationMinutesBefore, s?.lessonStartNotificationMinutesBefore) {
        delay(500)
        val minutes = lessonStartNotificationMinutesBefore.toIntOrNull()?.coerceIn(0, 360) ?: return@LaunchedEffect
        if (minutes != (s?.lessonStartNotificationMinutesBefore ?: 10)) {
            onUpdateLessonStartNotificationMinutesBefore(minutes)
        }
    }

    LaunchedEffect(enabledLessonStartNotifications) {
        refreshNotificationStates()
    }

    DisposableEffect(lifecycleOwner, supportsLessonStartLiveUpdates) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshNotificationStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.onSuccess { jsonText ->
            if (!jsonText.isNullOrBlank()) {
                pendingImportJson = jsonText
                showImportConfirmDialog = true
            } else {
                Toast.makeText(context, resources.getString(R.string.msg_import_read_failed), Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(context, resources.getString(R.string.msg_import_read_failed), Toast.LENGTH_SHORT).show()
        }
    }

    lessonCalendarDatePickerTarget?.let { target ->
        val initialDate = when (target) {
            "wizardStart" -> lessonCalendarWizardStart
            "wizardEnd" -> lessonCalendarWizardEnd
            "start" -> lessonCalendarSyncStart
            else -> lessonCalendarSyncEnd
        }
        key(target, initialDate) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = initialDate.toEpochDay() * 86_400_000L
            )
            DatePickerDialog(
                onDismissRequest = { lessonCalendarDatePickerTarget = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickerState.selectedDateMillis?.let { selectedMillis ->
                                val selectedDate = LocalDate.ofEpochDay(selectedMillis / 86_400_000L)
                                when (target) {
                                    "start" -> onUpdateLessonCalendarSyncRange(
                                        selectedDate,
                                        maxOf(selectedDate, lessonCalendarSyncEnd)
                                    )
                                    "end" -> onUpdateLessonCalendarSyncRange(
                                        minOf(lessonCalendarSyncStart, selectedDate),
                                        selectedDate
                                    )
                                    "wizardStart" -> {
                                        lessonCalendarWizardStartEpoch = selectedDate.toEpochDay()
                                        if (lessonCalendarWizardEndEpoch < lessonCalendarWizardStartEpoch) {
                                            lessonCalendarWizardEndEpoch = lessonCalendarWizardStartEpoch
                                        }
                                    }
                                    "wizardEnd" -> {
                                        lessonCalendarWizardEndEpoch = selectedDate.toEpochDay()
                                        if (lessonCalendarWizardStartEpoch > lessonCalendarWizardEndEpoch) {
                                            lessonCalendarWizardStartEpoch = lessonCalendarWizardEndEpoch
                                        }
                                    }
                                }
                            }
                            lessonCalendarDatePickerTarget = null
                        }
                    ) {
                        Text(stringResource(R.string.btn_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { lessonCalendarDatePickerTarget = null }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            ) {
                DatePicker(state = pickerState)
            }
        }
    }

    if (showLessonCalendarSyncWizard) {
        AlertDialog(
            onDismissRequest = { showLessonCalendarSyncWizard = false },
            title = { Text(stringResource(R.string.dialog_lesson_calendar_sync_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_lesson_calendar_sync_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LessonCalendarSyncDateRow(
                        label = stringResource(R.string.label_lesson_calendar_sync_start),
                        date = lessonCalendarWizardStart,
                        onClick = { lessonCalendarDatePickerTarget = "wizardStart" }
                    )
                    LessonCalendarSyncDateRow(
                        label = stringResource(R.string.label_lesson_calendar_sync_end),
                        date = lessonCalendarWizardEnd,
                        onClick = { lessonCalendarDatePickerTarget = "wizardEnd" }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEnableSyncLessonsToCalendar(
                            minOf(lessonCalendarWizardStart, lessonCalendarWizardEnd),
                            maxOf(lessonCalendarWizardStart, lessonCalendarWizardEnd)
                        )
                        showLessonCalendarSyncWizard = false
                    }
                ) {
                    Text(stringResource(R.string.btn_enable_sync))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLessonCalendarSyncWizard = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showClearAppCalendarEventsDialog) {
        val hasClearSelection = clearLessonCalendarEvents ||
            clearDeadlineCalendarEvents ||
            clearReminderCalendarEvents
        AlertDialog(
            onDismissRequest = { showClearAppCalendarEventsDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_app_calendar_events_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_clear_app_calendar_events_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    CalendarDeleteCategoryRow(
                        title = stringResource(R.string.label_clear_calendar_lessons),
                        checked = clearLessonCalendarEvents,
                        onCheckedChange = { clearLessonCalendarEvents = it }
                    )
                    CalendarDeleteCategoryRow(
                        title = stringResource(R.string.label_clear_calendar_deadlines),
                        checked = clearDeadlineCalendarEvents,
                        onCheckedChange = { clearDeadlineCalendarEvents = it }
                    )
                    CalendarDeleteCategoryRow(
                        title = stringResource(R.string.label_clear_calendar_reminders),
                        checked = clearReminderCalendarEvents,
                        onCheckedChange = { clearReminderCalendarEvents = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = hasClearSelection,
                    onClick = {
                        showClearAppCalendarEventsDialog = false
                        onClearAppCalendarEvents(
                            clearLessonCalendarEvents,
                            clearDeadlineCalendarEvents,
                            clearReminderCalendarEvents
                        )
                    }
                ) {
                    Text(stringResource(R.string.btn_check_delete_count))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAppCalendarEventsDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (InternalFeatureFlags.MATERIAL_3_EXPRESSIVE && showUiDesignModeDialog) {
        AppDialog(
            onDismissRequest = { showUiDesignModeDialog = false },
            title = { Text(stringResource(R.string.dialog_ui_design_title)) },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    UiDesignModeOptionRow(
                        title = stringResource(R.string.ui_design_material_3),
                        supportingText = null,
                        selected = state.uiDesignMode == UiDesignMode.MATERIAL_3,
                        onSelect = {
                            onUpdateUiDesignMode(UiDesignMode.MATERIAL_3)
                            showUiDesignModeDialog = false
                        }
                    )
                    UiDesignModeOptionRow(
                        title = stringResource(R.string.ui_design_material_3_expressive),
                        supportingText = stringResource(R.string.ui_design_experimental_label),
                        selected = state.uiDesignMode == UiDesignMode.MATERIAL_3_EXPRESSIVE,
                        onSelect = {
                            showUiDesignModeDialog = false
                            if (state.expressiveWarningAcknowledged) {
                                onUpdateUiDesignMode(UiDesignMode.MATERIAL_3_EXPRESSIVE)
                            } else {
                                showExpressiveWarningDialog = true
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showUiDesignModeDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    if (InternalFeatureFlags.MATERIAL_3_EXPRESSIVE && showExpressiveWarningDialog) {
        AppDialog(
            onDismissRequest = { showExpressiveWarningDialog = false },
            title = { Text(stringResource(R.string.dialog_expressive_warning_title)) },
            text = { Text(stringResource(R.string.dialog_expressive_warning_body)) },
            confirmButton = {
                AppPrimaryButton(
                    onClick = {
                        onAcknowledgeExpressiveWarning()
                        onUpdateUiDesignMode(UiDesignMode.MATERIAL_3_EXPRESSIVE)
                        showExpressiveWarningDialog = false
                    }
                ) {
                    Text(stringResource(R.string.btn_use_expressive_design))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpressiveWarningDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    AnimatedContent(
        targetState = timetableSettingsPage,
        transitionSpec = {
            if (targetState == null) {
                slideInHorizontally { -it / 2 } + fadeIn() togetherWith
                    slideOutHorizontally { it } + fadeOut()
            } else {
                slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it / 2 } + fadeOut()
            }
        },
        label = "timetable-settings-navigation"
    ) { page ->
        if (page != null) {
            val onReturnToTimetableSettings = { onTimetableSettingsPageChange(null) }
            when (page) {
                TimetableSettingsPage.LESSON,
                TimetableSettingsPage.EXAM -> Material3SettingsPresentation {
                    LegacySettingsScreen(
                        state = state,
                        onBack = onReturnToTimetableSettings,
                        onAbout = {},
                        onToggleLocalAi = {},
                        onToggleDrawerNavigation = {},
                        onToggleAddTasksToCalendar = {},
                        onToggleCurrentTimeMarker = {},
                        onToggleUnifyTaskPlanView = {},
                        onToggleShowWeekdayOnDates = {},
                        onToggleAdvancedTimeSettingsUi = onToggleAdvancedTimeSettingsUi,
                        onUpdateScheduleSettings = onUpdateScheduleSettings,
                        onUpdateSecondTermStart = onUpdateSecondTermStart,
                        onUpdateExamTimetableSettings = onUpdateExamTimetableSettings,
                        timetableSettingsPage = page
                    )
                }
                TimetableSettingsPage.SPECIAL -> SpecialTimetableSettingsScreen(
                    settings = state.settings,
                    onBack = onReturnToTimetableSettings,
                    onToggleSemesterTimetables = onToggleSemesterTimetables,
                    onToggleAbTimetable = onToggleAbTimetable,
                    onToggleExamTimetable = onToggleExamTimetable
                )
            }
        } else {
            AppSettingsScaffold(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
                scrollState = settingsScrollState
            ) {
        // ── 時間割設定 ──────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettingsCategory(title = stringResource(R.string.section_timetable_settings))
            AppSettingsGroup {
                item("lesson-timetable-settings") {
                    SettingsNavigationCard(
                        title = stringResource(R.string.settings_timetable_time_editor_title),
                        description = stringResource(R.string.settings_time_editor_summary),
                        onClick = { onTimetableSettingsPageChange(TimetableSettingsPage.LESSON) }
                    )
                }
                item("exam-timetable-settings") {
                    SettingsNavigationCard(
                        title = stringResource(R.string.settings_exam_time_editor_title),
                        description = stringResource(R.string.desc_exam_timetable_settings),
                        onClick = { onTimetableSettingsPageChange(TimetableSettingsPage.EXAM) }
                    )
                }
                item("special-timetable-features") {
                    SettingsNavigationCard(
                        title = stringResource(R.string.special_timetable_settings_title),
                        description = stringResource(
                            if (InternalFeatureFlags.SPECIAL_TIMETABLE_TOGGLES)
                                R.string.special_timetable_settings_description
                            else R.string.special_timetable_settings_release_description
                        ),
                        onClick = { onTimetableSettingsPageChange(TimetableSettingsPage.SPECIAL) }
                    )
                }
            }
        }
        // ── 通知設定 ──────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettingsCategory(title = stringResource(R.string.section_notification_settings))

            LessonStartNotificationSettingsContent(
                enabled = enabledLessonStartNotifications,
                notificationsEnabled = notificationsEnabled,
                promotedNotificationsEnabled = promotedNotificationsEnabled,
                liveUpdatesEnabled = enabledLessonStartLiveUpdates,
                liveUpdatesSupported = supportsLessonStartLiveUpdates,
                progressCountsDown = enabledLessonStartProgressCountsDown,
                liveUpdateEarlyMinutes = lessonStartLiveUpdateEarlyMinutes,
                chipMode = lessonStartChipMode,
                minutesBefore = lessonStartNotificationMinutesBefore,
                exclusions = state.lessonNotificationExclusions,
                subjectSuggestions = subjectSuggestions,
                subjectTeacherCandidates = subjectTeacherCandidates,
                onToggleEnabled = onToggleLessonStartNotifications,
                onOpenNotificationSettings = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                },
                onOpenPromotedNotificationSettings = {
                    val intent = if (Build.VERSION.SDK_INT >= 36) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    } else {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
                onToggleLiveUpdates = onToggleLessonStartNotificationLiveUpdates,
                onToggleProgressCountsDown = onToggleLessonStartNotificationProgressCountsDown,
                onUpdateLiveUpdateEarlyMinutes = onUpdateLessonStartNotificationLiveUpdateEarlyMinutes,
                onUpdateChipMode = onUpdateLessonStartNotificationChipMode,
                onMinutesBeforeChange = { lessonStartNotificationMinutesBefore = it },
                onAddExclusion = onAddLessonNotificationExclusion,
                onDeleteExclusion = onDeleteLessonNotificationExclusion
            )
        }

        // ── 表示設定 ──────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettingsCategory(title = stringResource(R.string.section_display_settings))

            AppSettingsGroup {
                if (InternalFeatureFlags.MATERIAL_3_EXPRESSIVE) {
                    item("label_ui_design") {
                        ValuePreferenceRow(
                            title = stringResource(R.string.label_ui_design),
                            value = when (state.uiDesignMode) {
                                UiDesignMode.MATERIAL_3 -> stringResource(R.string.ui_design_material_3)
                                UiDesignMode.MATERIAL_3_EXPRESSIVE -> stringResource(R.string.ui_design_material_3_expressive_current)
                            },
                            onClick = { showUiDesignModeDialog = true }
                        )

                    }
                }
                item("label_show_current_time_marker") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.label_show_current_time_marker),
                        description = stringResource(R.string.desc_show_current_time_marker),
                        checked = enabledCurrentTimeMarker,
                        onCheckedChange = onToggleCurrentTimeMarker
                    )
                }
                item("label_show_weekday_on_dates") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.label_show_weekday_on_dates),
                        description = stringResource(R.string.desc_show_weekday_on_dates),
                        checked = enabledShowWeekdayOnDates,
                        onCheckedChange = onToggleShowWeekdayOnDates
                    )
                }

            }
        }

        // ── カレンダー連携 ──────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettingsCategory(title = stringResource(R.string.section_task_plan_settings))

            AppSettingsGroup {
                item("label_add_tasks_to_calendar") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.label_add_tasks_to_calendar),
                        description = stringResource(R.string.desc_add_tasks_to_calendar),
                        checked = enabledTaskCalendarSync,
                        onCheckedChange = onToggleAddTasksToCalendar
                    )
                }
                item("label_sync_lessons_to_calendar") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.label_sync_lessons_to_calendar),
                        description = stringResource(R.string.desc_sync_lessons_to_calendar),
                        checked = enabledLessonCalendarSync,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                openLessonCalendarSyncWizard()
                            } else {
                                onToggleSyncLessonsToCalendar(false)
                            }
                        }
                    )
                }
            }
            if (enabledLessonCalendarSync) {
                AppSettingsCategory(title = stringResource(R.string.label_lesson_calendar_sync_period))
                AppSettingsGroup {
                    item("label_lesson_calendar_sync_start") {
                        LessonCalendarSyncDateRow(
                            label = stringResource(R.string.label_lesson_calendar_sync_start),
                            date = lessonCalendarSyncStart,
                            onClick = { lessonCalendarDatePickerTarget = "start" }
                        )
                    }
                    item("label_lesson_calendar_sync_end") {
                        LessonCalendarSyncDateRow(
                            label = stringResource(R.string.label_lesson_calendar_sync_end),
                            date = lessonCalendarSyncEnd,
                            onClick = { lessonCalendarDatePickerTarget = "end" }
                        )
                    }
                }
            }
            AppSettingsGroup {
                item("label_clear_app_calendar_events") {
                    NavigationPreferenceRow(
                        title = stringResource(R.string.label_clear_app_calendar_events),
                        summary = null,
                        onClick = {
                            clearLessonCalendarEvents = true
                            clearDeadlineCalendarEvents = true
                            clearReminderCalendarEvents = true
                            showClearAppCalendarEventsDialog = true
                        }
                    )
                }
            }
        }

        // ── 上級者向け機能 ──────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettingsCategory(title = stringResource(R.string.section_navigation_settings))

            AppSettingsGroup {
                item("label_show_sync_button") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.label_show_sync_button),
                        description = stringResource(R.string.desc_show_sync_button),
                        checked = state.settings?.showSyncButton ?: false,
                        onCheckedChange = onToggleShowSyncButton
                    )
                }
                item("label_unify_task_plan_view") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.label_unify_task_plan_view),
                        description = stringResource(R.string.desc_unify_task_plan_view),
                        checked = enabledUnifyTaskPlanView,
                        onCheckedChange = onToggleUnifyTaskPlanView
                    )
                }

            }
        }

        // ── 実験的機能 ──────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettingsCategory(title = stringResource(R.string.section_experimental))

            AppSettingsGroup {
                item("label_use_hamburger_navigation") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.label_use_hamburger_navigation),
                        description = stringResource(R.string.desc_use_hamburger_navigation),
                        checked = enabledDrawerNavigation,
                        onCheckedChange = onToggleDrawerNavigation
                    )
                }
                item("label_advanced_time_settings_ui") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.label_advanced_time_settings_ui),
                        description = stringResource(R.string.desc_advanced_time_settings_ui),
                        checked = enabledAdvancedTimeSettingsUi,
                        onCheckedChange = onToggleAdvancedTimeSettingsUi
                    )
                }
                item("label_local_ai_import") {
                    SettingsSwitchRow(
                        title = stringResource(R.string.label_local_ai_import),
                        description = stringResource(R.string.desc_local_ai_import),
                        checked = enabledLocalAi,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showLocalAiWarningDialog = true
                            } else {
                                onToggleLocalAi(false)
                            }
                        }
                    )
                }
                if (InternalFeatureFlags.NATURAL_LANGUAGE_TASK_ADD) {
                    item("label_natural_language_task_add") {
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_natural_language_task_add),
                            description = stringResource(R.string.desc_natural_language_task_add),
                            checked = enabledNaturalLanguageTaskAdd,
                            onCheckedChange = onToggleNaturalLanguageTaskAdd
                        )
                    }
                }

                if (isIntDev) {
                    item("label_disable_tutorial_first_time_check_for_testing") {
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_disable_tutorial_first_time_check_for_testing),
                            description = stringResource(R.string.desc_disable_tutorial_first_time_check_for_testing),
                            checked = tutorialFirstTimeCheckDisabledForTesting,
                            onCheckedChange = onToggleTutorialFirstTimeCheckDisabledForTesting
                        )
                    }
                    item("label_update_show_latest_for_testing") {
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_update_show_latest_for_testing),
                            description = stringResource(R.string.desc_update_show_latest_for_testing),
                            checked = showLatestReleaseForTesting,
                            onCheckedChange = { enabled ->
                                showLatestReleaseForTesting = enabled
                                setShowLatestReleaseForTestingEnabled(context, currentVersionName, enabled)
                            }
                        )
                    }
                    item("label_update_current_version_override_for_testing") {
                        TextSettingRow(
                            label = stringResource(R.string.label_update_current_version_override_for_testing),
                            value = updateCurrentVersionOverrideForTesting,
                            summary = stringResource(R.string.desc_update_current_version_override_for_testing, currentVersionName),
                            onValueChange = { value ->
                                updateCurrentVersionOverrideForTesting = value
                                setUpdateCurrentVersionOverrideForTesting(context, currentVersionName, value)
                            }
                        )
                    }
                    item("msg_update_dismiss_reset") {
                        PreferenceRow(
                            title = stringResource(R.string.btn_reset_update_dismiss),
                            onClick = {
                                clearDismissedUpdateNotification(context)
                                Toast.makeText(
                                    context,
                                    resources.getString(R.string.msg_update_dismiss_reset),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }

            }
        }

        // ── 設定データの移行 ───────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettingsCategory(title = stringResource(R.string.section_data_transfer))
            AppSettingsGroup {
                item("desc_data_transfer", contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.desc_data_transfer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item("btn_export_json") {
                    PreferenceRow(
                        title = stringResource(R.string.btn_export_json),
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                                        .format(LocalDateTime.now())
                                    val filename = "nittcsc_settings_${stamp}.json"
                                    val json = onExportAllAsJson()
                                    val exportFile = withContext(Dispatchers.IO) {
                                        File(context.cacheDir, filename).apply {
                                            writeText(json)
                                        }
                                    }
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        exportFile
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, filename)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            resources.getString(R.string.btn_export_json)
                                        )
                                    )
                                }.onSuccess {
                                    Toast.makeText(context, resources.getString(R.string.msg_export_success), Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, resources.getString(R.string.msg_export_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
                item("btn_import_json") {
                    NavigationPreferenceRow(
                        title = stringResource(R.string.btn_import_json),
                        summary = null,
                        onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain")) }
                    )
                }

            }
        }

        // ── このアプリについて ──────────────────────────────────────
        SettingsSection(title = stringResource(R.string.about_section_title)) {
            item("about") {
                SettingsNavigationCard(
                    title = stringResource(R.string.about_section_title),
                    description = stringResource(R.string.about_section_help),
                    onClick = onAbout
                )
            }
        }
            }
        }
    }

    if (showLocalAiWarningDialog) {
        AlertDialog(
            onDismissRequest = { showLocalAiWarningDialog = false },
            title = { Text(stringResource(R.string.dialog_local_ai_warning_title)) },
            text = { Text(stringResource(R.string.dialog_local_ai_warning_body)) },
            confirmButton = {
                Button(onClick = {
                    showLocalAiWarningDialog = false
                    onToggleLocalAi(true)
                }) {
                    Text(stringResource(R.string.dialog_local_ai_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocalAiWarningDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportJson = null
            },
            title = { Text(stringResource(R.string.dialog_import_confirm_title)) },
            text = { Text(stringResource(R.string.dialog_import_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    val json = pendingImportJson
                    showImportConfirmDialog = false
                    pendingImportJson = null
                    if (!json.isNullOrBlank()) {
                        onImportAllFromJson(json)
                        Toast.makeText(context, resources.getString(R.string.msg_import_started), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.dialog_import_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    pendingImportJson = null
                }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

private fun defaultLessonCalendarSyncRange(
    today: LocalDate,
    termStart: LocalDate,
    termEnd: LocalDate,
    longBreaks: List<LongBreakEntity>
): Pair<LocalDate, LocalDate> {
    val normalizedTermStart = minOf(termStart, termEnd)
    val normalizedTermEnd = maxOf(termStart, termEnd)
    val referenceDate = today.coerceIn(normalizedTermStart, normalizedTermEnd)
    val mergedBreaks = mutableListOf<Pair<LocalDate, LocalDate>>()

    longBreaks
        .mapNotNull { longBreak ->
            val breakStart = maxOf(minOf(longBreak.startDate, longBreak.endDate), normalizedTermStart)
            val breakEnd = minOf(maxOf(longBreak.startDate, longBreak.endDate), normalizedTermEnd)
            (breakStart to breakEnd).takeIf { breakStart <= breakEnd }
        }
        .sortedBy { it.first }
        .forEach { current ->
            val previous = mergedBreaks.lastOrNull()
            if (previous != null && !current.first.isAfter(previous.second.plusDays(1))) {
                mergedBreaks[mergedBreaks.lastIndex] = previous.first to maxOf(previous.second, current.second)
            } else {
                mergedBreaks += current
            }
        }

    val currentBreak = mergedBreaks.firstOrNull { referenceDate in it.first..it.second }
    val start = currentBreak?.second
        ?: mergedBreaks.lastOrNull { it.second.isBefore(referenceDate) }?.second
        ?: normalizedTermStart
    val end = mergedBreaks.firstOrNull {
        it.first.isAfter(currentBreak?.second ?: referenceDate)
    }?.first ?: normalizedTermEnd

    return minOf(start, end) to maxOf(start, end)
}

@Composable
private fun LessonCalendarSyncDateRow(
    label: String,
    date: LocalDate,
    onClick: () -> Unit
) {
    ValuePreferenceRow(label, date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), onClick)
}

@Composable
private fun CalendarDeleteCategoryRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun UiDesignModeOptionRow(
    title: String,
    supportingText: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
