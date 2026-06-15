package jp.linkserver.nittcsc.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import jp.linkserver.nittcsc.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import jp.linkserver.nittcsc.data.LessonNotificationExclusionEntity
import jp.linkserver.nittcsc.data.LongBreakEntity
import jp.linkserver.nittcsc.logic.generateClassSlots
import jp.linkserver.nittcsc.update.clearDismissedUpdateNotification
import jp.linkserver.nittcsc.update.isIntDevBuild
import jp.linkserver.nittcsc.update.isShowLatestReleaseForTestingEnabled
import jp.linkserver.nittcsc.update.setShowLatestReleaseForTestingEnabled
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
    onToggleDrawerNavigation: (Boolean) -> Unit,
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
    onAddLessonNotificationExclusion: (String, String?, Boolean) -> Unit = { _, _, _ -> },
    onDeleteLessonNotificationExclusion: (LessonNotificationExclusionEntity) -> Unit = {},
    onUpdateScheduleSettings: (periodsPerDay: Int, periodDurationMin: Int, breakBetweenPeriodsMin: Int, lunchBreakMin: Int, lunchAfterPeriod: Int, startHour: Int, startMinute: Int, useKosenMode: Boolean, arrivalHour: Int, arrivalMinute: Int, departureHour: Int, departureMinute: Int) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onExportAllAsJson: suspend () -> String = { "{}" },
    onImportAllFromJson: (String) -> Unit = {}
) {
    val enabledLocalAi = state.settings?.enableLocalAi ?: false
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
    var expandTimetableSettings by rememberSaveable { mutableStateOf(true) }
    var showLocalAiWarningDialog by remember { mutableStateOf(false) }
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

    // 時間割設定ローカル状態
    var periodsPerDay by remember(s) { mutableStateOf(s?.periodsPerDay?.toString() ?: "4") }
    var periodDurationMin by remember(s) { mutableStateOf(s?.periodDurationMin?.toString() ?: "90") }
    var breakBetweenPeriodsMin by remember(s) { mutableStateOf(s?.breakBetweenPeriodsMin?.toString() ?: "10") }
    var lunchBreakMin by remember(s) { mutableStateOf(s?.lunchBreakMin?.toString() ?: "60") }
    var lunchAfterPeriod by remember(s) { mutableStateOf(s?.lunchAfterPeriod?.toString() ?: "2") }
    var startHour by remember(s) { mutableStateOf(s?.firstPeriodStartHour?.toString() ?: "8") }
    var startMinute by remember(s) { mutableStateOf(s?.firstPeriodStartMinute?.toString() ?: "40") }
    var useKosenMode by remember(s) { mutableStateOf(s?.useKosenMode ?: true) }
    // 登下校時刻（空文字 = 未設定）
    var arrivalHour by remember(s) { mutableStateOf(if ((s?.arrivalHour ?: -1) >= 0) s!!.arrivalHour.toString() else "") }
    var arrivalMinute by remember(s) { mutableStateOf(if ((s?.arrivalMinute ?: -1) >= 0) s!!.arrivalMinute.toString().padStart(2,'0') else "") }
    var departureHour by remember(s) { mutableStateOf(if ((s?.departureHour ?: -1) >= 0) s!!.departureHour.toString() else "") }
    var departureMinute by remember(s) { mutableStateOf(if ((s?.departureMinute ?: -1) >= 0) s!!.departureMinute.toString().padStart(2,'0') else "") }
    var lessonStartNotificationMinutesBefore by remember(s?.lessonStartNotificationMinutesBefore) {
        mutableStateOf((s?.lessonStartNotificationMinutesBefore ?: 10).toString())
    }
    var advancedPeriodCount by remember(enabledAdvancedTimeSettingsUi) { mutableStateOf(s?.periodsPerDay?.toString() ?: "4") }
    var advancedLunchAfterPeriod by remember(enabledAdvancedTimeSettingsUi) { mutableStateOf(s?.lunchAfterPeriod ?: 2) }
    var advancedPeriodRanges by remember(enabledAdvancedTimeSettingsUi) { mutableStateOf(emptyList<TimeRangeDraft>()) }
    var advancedLunchRange by remember(enabledAdvancedTimeSettingsUi) { mutableStateOf(TimeRangeDraft("12", "00", "13", "00")) }
    var expandedAdvancedTimeItemKey by rememberSaveable { mutableStateOf<String?>(null) }
    var previewLunchAfterPeriod by remember { mutableStateOf<Int?>(null) }
    var isDraggingLunch by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
    val defaultPeriodDuration = periodDurationMin.toIntOrNull()?.coerceIn(10, 300) ?: 90
    val defaultBreakDuration = breakBetweenPeriodsMin.toIntOrNull()?.coerceIn(0, 120) ?: 10

    LaunchedEffect(
        s?.periodsPerDay,
        s?.periodDurationMin,
        s?.breakBetweenPeriodsMin,
        s?.lunchBreakMin,
        s?.lunchAfterPeriod,
        s?.firstPeriodStartHour,
        s?.firstPeriodStartMinute,
        enabledAdvancedTimeSettingsUi
    ) {
        val settings = s ?: return@LaunchedEffect
        val draft = buildAdvancedTimeEditorDraft(
            periodsPerDay = settings.periodsPerDay,
            periodDurationMin = settings.periodDurationMin,
            breakBetweenPeriodsMin = settings.breakBetweenPeriodsMin,
            lunchBreakMin = settings.lunchBreakMin,
            lunchAfterPeriod = settings.lunchAfterPeriod,
            firstPeriodStartHour = settings.firstPeriodStartHour,
            firstPeriodStartMinute = settings.firstPeriodStartMinute,
            useKosenMode = settings.useKosenMode
        )
        advancedPeriodCount = settings.periodsPerDay.toString()
        advancedLunchAfterPeriod = settings.lunchAfterPeriod.coerceIn(0, settings.periodsPerDay)
        advancedPeriodRanges = draft.periodRanges
        advancedLunchRange = draft.lunchRange
        previewLunchAfterPeriod = null
        isDraggingLunch = false
    }

    LaunchedEffect(advancedPeriodCount, enabledAdvancedTimeSettingsUi) {
        if (!enabledAdvancedTimeSettingsUi) return@LaunchedEffect
        val targetCount = advancedPeriodCount.toIntOrNull()?.coerceIn(1, 12) ?: return@LaunchedEffect
        if (advancedPeriodRanges.size == targetCount) {
            advancedLunchAfterPeriod = advancedLunchAfterPeriod.coerceIn(0, targetCount)
            return@LaunchedEffect
        }
        advancedPeriodRanges = resizeTimeRangeDrafts(
            current = advancedPeriodRanges,
            targetCount = targetCount,
            defaultPeriodDurationMin = defaultPeriodDuration,
            defaultBreakDurationMin = defaultBreakDuration,
            fallbackStartHour = startHour.toIntOrNull()?.coerceIn(0, 23) ?: 8,
            fallbackStartMinute = startMinute.toIntOrNull()?.coerceIn(0, 59) ?: 40
        )
        advancedLunchAfterPeriod = advancedLunchAfterPeriod.coerceIn(0, targetCount)
    }

    val advancedTimeValidation = remember(
        enabledAdvancedTimeSettingsUi,
        advancedPeriodCount,
        advancedLunchAfterPeriod,
        advancedPeriodRanges,
        advancedLunchRange,
        defaultBreakDuration
    ) {
        if (!enabledAdvancedTimeSettingsUi) {
            AdvancedTimeValidation()
        } else {
            validateAdvancedTimeEditor(
                periodCountText = advancedPeriodCount,
                periodRanges = advancedPeriodRanges,
                lunchRange = advancedLunchRange,
                lunchAfterPeriod = advancedLunchAfterPeriod,
                fallbackBreakDurationMin = defaultBreakDuration
            )
        }
    }

    LaunchedEffect(enabledAdvancedTimeSettingsUi, advancedTimeValidation.derivedSettings) {
        if (!enabledAdvancedTimeSettingsUi) return@LaunchedEffect
        val derived = advancedTimeValidation.derivedSettings ?: return@LaunchedEffect
        periodsPerDay = derived.periodsPerDay.toString()
        periodDurationMin = derived.periodDurationMin.toString()
        breakBetweenPeriodsMin = derived.breakBetweenPeriodsMin.toString()
        lunchBreakMin = derived.lunchBreakMin.toString()
        lunchAfterPeriod = derived.lunchAfterPeriod.toString()
        startHour = derived.firstPeriodStartHour.toString()
        startMinute = derived.firstPeriodStartMinute.toString().padStart(2, '0')
    }

    // 時間割設定は入力後に自動保存（デバウンス）
    LaunchedEffect(
        periodsPerDay,
        periodDurationMin,
        breakBetweenPeriodsMin,
        lunchBreakMin,
        lunchAfterPeriod,
        startHour,
        startMinute,
        useKosenMode,
        arrivalHour,
        arrivalMinute,
        departureHour,
        departureMinute,
        s
    ) {
        delay(500)

        val p = periodsPerDay.toIntOrNull()?.coerceIn(1, 12) ?: 4
        val d = periodDurationMin.toIntOrNull()?.coerceIn(10, 300) ?: 90
        val b = breakBetweenPeriodsMin.toIntOrNull()?.coerceIn(0, 120) ?: 10
        val l = lunchBreakMin.toIntOrNull()?.coerceIn(0, 180) ?: 60
        val la = lunchAfterPeriod.toIntOrNull()?.coerceIn(0, p) ?: (p / 2)
        val h = startHour.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val m = startMinute.toIntOrNull()?.coerceIn(0, 59) ?: 40
        val ah = arrivalHour.toIntOrNull()?.coerceIn(0, 23) ?: -1
        val am = if (ah >= 0) arrivalMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0 else -1
        val dh = departureHour.toIntOrNull()?.coerceIn(0, 23) ?: -1
        val dm = if (dh >= 0) departureMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0 else -1

        val changed = s == null ||
            s.periodsPerDay != p ||
            s.periodDurationMin != d ||
            s.breakBetweenPeriodsMin != b ||
            s.lunchBreakMin != l ||
            s.lunchAfterPeriod != la ||
            s.firstPeriodStartHour != h ||
            s.firstPeriodStartMinute != m ||
            s.useKosenMode != useKosenMode ||
            s.arrivalHour != ah ||
            s.arrivalMinute != am ||
            s.departureHour != dh ||
            s.departureMinute != dm

        if (changed) {
            onUpdateScheduleSettings(p, d, b, l, la, h, m, useKosenMode, ah, am, dh, dm)
        }
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
                Toast.makeText(context, context.getString(R.string.msg_import_read_failed), Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.msg_import_read_failed), Toast.LENGTH_SHORT).show()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(settingsScrollState, enabled = !isDraggingLunch),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── 時間割設定 ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandTimetableSettings = !expandTimetableSettings },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.section_timetable_settings),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (expandTimetableSettings) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expandTimetableSettings) {
                            stringResource(R.string.desc_close)
                        } else {
                            stringResource(R.string.desc_expand)
                        },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (expandTimetableSettings) Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                )
                {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 校時表記切り替え
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.label_koshi_notation), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = useKosenMode,
                                    onClick = { useKosenMode = true }
                                )
                                Text(
                                    stringResource(R.string.mode_kosen),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                                RadioButton(
                                    selected = !useKosenMode,
                                    onClick = { useKosenMode = false }
                                )
                                Text(
                                    stringResource(R.string.mode_regular),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        if (enabledAdvancedTimeSettingsUi) {
                            NumberSettingRow(
                                label = stringResource(R.string.label_periods_per_day),
                                value = advancedPeriodCount,
                                unit = stringResource(R.string.unit_period),
                                onValueChange = { advancedPeriodCount = it }
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.label_timetable_blocks),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.desc_advanced_time_settings_reorder),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        val displayedLunchAfterPeriod = previewLunchAfterPeriod ?: advancedLunchAfterPeriod
                                        val advancedItems = buildAdvancedTimeItems(
                                            periodRanges = advancedPeriodRanges,
                                            lunchRange = advancedLunchRange,
                                            lunchAfterPeriod = displayedLunchAfterPeriod,
                                            useKosenMode = useKosenMode,
                                            arrivalHour = arrivalHour,
                                            arrivalMinute = arrivalMinute,
                                            departureHour = departureHour,
                                            departureMinute = departureMinute
                                        )
                                        Column {
                                            advancedItems.forEachIndexed { index, item ->
                                                key(item.key) {
                                                    CompactTimeListRow(
                                                        item = item,
                                                        rowIndex = index,
                                                        lunchAfterPeriod = displayedLunchAfterPeriod,
                                                        expanded = expandedAdvancedTimeItemKey == item.key,
                                                        isDraggingLunch = isDraggingLunch && item.isLunch,
                                                        onToggleExpanded = {
                                                            expandedAdvancedTimeItemKey =
                                                                if (expandedAdvancedTimeItemKey == item.key) null else item.key
                                                        },
                                                        onRangeChange = { updated ->
                                                            if (item.isLunch) {
                                                                advancedLunchRange = updated
                                                            } else {
                                                                val slotIndex = item.periodIndex ?: return@CompactTimeListRow
                                                                advancedPeriodRanges = advancedPeriodRanges.toMutableList().also { it[slotIndex] = updated }
                                                            }
                                                        },
                                                        onPointChange = { updated ->
                                                            when (item.key) {
                                                                "start" -> {
                                                                    arrivalHour = updated.hour
                                                                    arrivalMinute = updated.minute
                                                                }
                                                                "end" -> {
                                                                    departureHour = updated.hour
                                                                    departureMinute = updated.minute
                                                                }
                                                            }
                                                        },
                                                        onLunchDragStart = {
                                                            expandedAdvancedTimeItemKey = null
                                                            isDraggingLunch = true
                                                            previewLunchAfterPeriod = advancedLunchAfterPeriod
                                                        },
                                                        onLunchDragPreview = { targetPosition ->
                                                            val targetCount = advancedPeriodCount.toIntOrNull()?.coerceIn(1, 12)
                                                                ?: advancedPeriodRanges.size
                                                            val nextPosition = targetPosition.coerceIn(0, targetCount)
                                                            previewLunchAfterPeriod = nextPosition
                                                            nextPosition
                                                        },
                                                        onLunchDragEnd = {
                                                            advancedLunchAfterPeriod = previewLunchAfterPeriod ?: advancedLunchAfterPeriod
                                                            previewLunchAfterPeriod = null
                                                            isDraggingLunch = false
                                                        },
                                                        onLunchDragCancel = {
                                                            previewLunchAfterPeriod = null
                                                            isDraggingLunch = false
                                                        }
                                                    )
                                                }
                                                if (index < advancedItems.lastIndex) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp)
                                                            .height(1.dp)
                                                    ) {}
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            advancedTimeValidation.warningMessageRes?.let { warningRes ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = stringResource(warningRes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        } else {
                            NumberSettingRow(label = stringResource(R.string.label_periods_per_day), value = periodsPerDay, unit = stringResource(R.string.unit_period), onValueChange = { periodsPerDay = it })
                            NumberSettingRow(label = stringResource(R.string.label_period_duration), value = periodDurationMin, unit = stringResource(R.string.unit_minute), onValueChange = { periodDurationMin = it })
                            NumberSettingRow(label = stringResource(R.string.label_break_duration), value = breakBetweenPeriodsMin, unit = stringResource(R.string.unit_minute), onValueChange = { breakBetweenPeriodsMin = it })
                            NumberSettingRow(label = stringResource(R.string.label_lunch_duration), value = lunchBreakMin, unit = stringResource(R.string.unit_minute), onValueChange = { lunchBreakMin = it })
                            NumberSettingRow(label = stringResource(R.string.label_lunch_after), value = lunchAfterPeriod, unit = stringResource(R.string.unit_after_period), onValueChange = { lunchAfterPeriod = it })
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.label_first_period_start), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = startHour,
                                        onValueChange = { startHour = it.filter { c -> c.isDigit() }.take(2) },
                                        label = { Text(stringResource(R.string.label_hour)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(72.dp)
                                    )
                                    Text(":", style = MaterialTheme.typography.titleMedium)
                                    OutlinedTextField(
                                        value = startMinute,
                                        onValueChange = { startMinute = it.filter { c -> c.isDigit() }.take(2) },
                                        label = { Text(stringResource(R.string.label_minute)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(72.dp)
                                    )
                                }
                            }
                            TimeSettingRow(
                                label = stringResource(R.string.label_arrival_time),
                                hour = arrivalHour,
                                minute = arrivalMinute,
                                onHourChange = { arrivalHour = it },
                                onMinuteChange = { arrivalMinute = it }
                            )
                            TimeSettingRow(
                                label = stringResource(R.string.label_departure_time),
                                hour = departureHour,
                                minute = departureMinute,
                                onHourChange = { departureHour = it },
                                onMinuteChange = { departureMinute = it }
                            )
                        }
                        Text(
                            text = "変更は自動保存されます",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── 通知設定 ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.section_notification_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LessonStartNotificationSettingsContent(
                        enabled = enabledLessonStartNotifications,
                        notificationsEnabled = notificationsEnabled,
                        promotedNotificationsEnabled = promotedNotificationsEnabled,
                        liveUpdatesEnabled = enabledLessonStartLiveUpdates,
                        liveUpdatesSupported = supportsLessonStartLiveUpdates,
                        progressCountsDown = enabledLessonStartProgressCountsDown,
                        liveUpdateEarlyMinutes = lessonStartLiveUpdateEarlyMinutes,
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
                        onMinutesBeforeChange = { lessonStartNotificationMinutesBefore = it },
                        onAddExclusion = onAddLessonNotificationExclusion,
                        onDeleteExclusion = onDeleteLessonNotificationExclusion
                    )
                }
            }

            // ── 表示設定 ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.section_display_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_show_current_time_marker),
                            description = stringResource(R.string.desc_show_current_time_marker),
                            checked = enabledCurrentTimeMarker,
                            onCheckedChange = onToggleCurrentTimeMarker
                        )
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
                Text(
                    text = stringResource(R.string.section_task_plan_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_add_tasks_to_calendar),
                            description = stringResource(R.string.desc_add_tasks_to_calendar),
                            checked = enabledTaskCalendarSync,
                            onCheckedChange = onToggleAddTasksToCalendar
                        )
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
                        if (enabledLessonCalendarSync) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.label_lesson_calendar_sync_period),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LessonCalendarSyncDateRow(
                                    label = stringResource(R.string.label_lesson_calendar_sync_start),
                                    date = lessonCalendarSyncStart,
                                    onClick = { lessonCalendarDatePickerTarget = "start" }
                                )
                                LessonCalendarSyncDateRow(
                                    label = stringResource(R.string.label_lesson_calendar_sync_end),
                                    date = lessonCalendarSyncEnd,
                                    onClick = { lessonCalendarDatePickerTarget = "end" }
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clearLessonCalendarEvents = true
                                    clearDeadlineCalendarEvents = true
                                    clearReminderCalendarEvents = true
                                    showClearAppCalendarEventsDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.label_clear_app_calendar_events))
                            }
                        }
                    }
                }
            }

            // ── ナビゲーション設定 ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.section_navigation_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
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
                Text(
                    text = stringResource(R.string.section_experimental),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_use_hamburger_navigation),
                            description = stringResource(R.string.desc_use_hamburger_navigation),
                            checked = enabledDrawerNavigation,
                            onCheckedChange = onToggleDrawerNavigation
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_advanced_time_settings_ui),
                            description = stringResource(R.string.desc_advanced_time_settings_ui),
                            checked = enabledAdvancedTimeSettingsUi,
                            onCheckedChange = onToggleAdvancedTimeSettingsUi
                        )
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

                        if (isIntDev) {
                            SettingsSwitchRow(
                                title = stringResource(R.string.label_update_show_latest_for_testing),
                                description = stringResource(R.string.desc_update_show_latest_for_testing),
                                checked = showLatestReleaseForTesting,
                                onCheckedChange = { enabled ->
                                    showLatestReleaseForTesting = enabled
                                    setShowLatestReleaseForTestingEnabled(context, currentVersionName, enabled)
                                }
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        clearDismissedUpdateNotification(context)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.msg_update_dismiss_reset),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ) {
                                    Text(stringResource(R.string.btn_reset_update_dismiss))
                                }
                            }
                        }
                    }
                }
            }

            // ── 設定データの移行 ───────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.section_data_transfer),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.desc_data_transfer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
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
                                                    context.getString(R.string.btn_export_json)
                                                )
                                            )
                                        }.onSuccess {
                                            Toast.makeText(context, context.getString(R.string.msg_export_success), Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, context.getString(R.string.msg_export_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.btn_export_json))
                            }
                            OutlinedButton(
                                onClick = {
                                    importJsonLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.btn_import_json))
                            }
                        }
                    }
                }
            }

            // ── このアプリについて ──────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.about_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAbout() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                stringResource(R.string.about_section_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.about_section_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stringResource(R.string.about_section_open),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
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
                        Toast.makeText(context, context.getString(R.string.msg_import_started), Toast.LENGTH_SHORT).show()
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

private data class TimeRangeDraft(
    val startHour: String,
    val startMinute: String,
    val endHour: String,
    val endMinute: String
)

private data class TimePointDraft(
    val hour: String,
    val minute: String
)

private data class TimeRangeValue(
    val startMin: Int,
    val endMin: Int
)

private data class AdvancedTimeEditorDraft(
    val periodRanges: List<TimeRangeDraft>,
    val lunchRange: TimeRangeDraft
)

private data class DerivedScheduleSettings(
    val periodsPerDay: Int,
    val periodDurationMin: Int,
    val breakBetweenPeriodsMin: Int,
    val lunchBreakMin: Int,
    val lunchAfterPeriod: Int,
    val firstPeriodStartHour: Int,
    val firstPeriodStartMinute: Int
)

private data class AdvancedTimeValidation(
    @param:StringRes val warningMessageRes: Int? = null,
    val derivedSettings: DerivedScheduleSettings? = null
)

private fun buildAdvancedTimeEditorDraft(
    periodsPerDay: Int,
    periodDurationMin: Int,
    breakBetweenPeriodsMin: Int,
    lunchBreakMin: Int,
    lunchAfterPeriod: Int,
    firstPeriodStartHour: Int,
    firstPeriodStartMinute: Int,
    useKosenMode: Boolean
): AdvancedTimeEditorDraft {
    val slots = generateClassSlots(
        periodsPerDay = periodsPerDay,
        periodDurationMin = periodDurationMin,
        breakBetweenPeriodsMin = breakBetweenPeriodsMin,
        lunchBreakMin = lunchBreakMin,
        firstPeriodStartHour = firstPeriodStartHour,
        firstPeriodStartMinute = firstPeriodStartMinute,
        useKosenMode = useKosenMode,
        lunchAfterPeriod = lunchAfterPeriod
    )
    val periodRanges = slots.map { slot ->
        TimeRangeDraft(
            startHour = slot.start.hour.toString(),
            startMinute = slot.start.minute.toString().padStart(2, '0'),
            endHour = slot.end.hour.toString(),
            endMinute = slot.end.minute.toString().padStart(2, '0')
        )
    }
    val lunchAfter = lunchAfterPeriod.coerceIn(0, periodsPerDay)
    val lunchStartMin = when {
        lunchAfter <= 0 -> {
            val firstStart = slots.firstOrNull()?.let { it.start.hour * 60 + it.start.minute }
                ?: (firstPeriodStartHour * 60 + firstPeriodStartMinute)
            (firstStart - lunchBreakMin).coerceAtLeast(0)
        }
        lunchAfter >= slots.size -> {
            val lastEnd = slots.lastOrNull()?.let { it.end.hour * 60 + it.end.minute }
                ?: (firstPeriodStartHour * 60 + firstPeriodStartMinute + periodDurationMin)
            lastEnd
        }
        else -> slots[lunchAfter - 1].end.hour * 60 + slots[lunchAfter - 1].end.minute
    }
    val lunchEndMin = when {
        lunchAfter <= 0 -> slots.firstOrNull()?.let { it.start.hour * 60 + it.start.minute } ?: (lunchStartMin + lunchBreakMin)
        lunchAfter >= slots.size -> lunchStartMin + lunchBreakMin
        else -> slots[lunchAfter].start.hour * 60 + slots[lunchAfter].start.minute
    }
    return AdvancedTimeEditorDraft(
        periodRanges = periodRanges,
        lunchRange = timeRangeDraftFromMinutes(lunchStartMin, lunchEndMin)
    )
}

private fun resizeTimeRangeDrafts(
    current: List<TimeRangeDraft>,
    targetCount: Int,
    defaultPeriodDurationMin: Int,
    defaultBreakDurationMin: Int,
    fallbackStartHour: Int,
    fallbackStartMinute: Int
): List<TimeRangeDraft> {
    if (targetCount <= 0) return emptyList()
    if (current.size == targetCount) return current
    if (current.size > targetCount) return current.take(targetCount)

    val result = current.toMutableList()
    while (result.size < targetCount) {
        val previous = result.lastOrNull()
        val nextStartMin = previous?.let {
            parseTimeRangeDraft(it)?.endMin?.plus(defaultBreakDurationMin)
        } ?: (fallbackStartHour * 60 + fallbackStartMinute)
        result += timeRangeDraftFromMinutes(nextStartMin, nextStartMin + defaultPeriodDurationMin)
    }
    return result
}

private fun validateAdvancedTimeEditor(
    periodCountText: String,
    periodRanges: List<TimeRangeDraft>,
    lunchRange: TimeRangeDraft,
    lunchAfterPeriod: Int,
    fallbackBreakDurationMin: Int
): AdvancedTimeValidation {
    val periodCount = periodCountText.toIntOrNull()?.coerceIn(1, 12)
        ?: return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_invalid_period_count)
    if (periodRanges.size != periodCount) {
        return AdvancedTimeValidation()
    }
    val parsedPeriods = periodRanges.map {
        parseTimeRangeDraft(it) ?: return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_invalid_period_time)
    }
    val parsedLunch = parseTimeRangeDraft(lunchRange)
        ?: return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_invalid_lunch_time)

    if (parsedPeriods.any { it.endMin <= it.startMin }) {
        return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_period_end_before_start)
    }
    if (parsedLunch.endMin <= parsedLunch.startMin) {
        return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_lunch_end_before_start)
    }

    val lunchAfter = lunchAfterPeriod.coerceIn(0, periodCount)
    val periodDurations = parsedPeriods.map { it.endMin - it.startMin }
    if (periodDurations.distinct().size > 1) {
        return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_period_duration_mismatch)
    }

    val regularBreaks = mutableListOf<Int>()
    for (i in 0 until parsedPeriods.lastIndex) {
        if (lunchAfter == i + 1) continue
        val gap = parsedPeriods[i + 1].startMin - parsedPeriods[i].endMin
        if (gap < 0) {
            return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_periods_overlap)
        }
        regularBreaks += gap
    }
    if (regularBreaks.distinct().size > 1) {
        return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_break_duration_mismatch)
    }

    when {
        lunchAfter == 0 -> {
            if (parsedLunch.endMin != parsedPeriods.first().startMin) {
                return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_lunch_first_not_connected)
            }
        }
        lunchAfter == periodCount -> {
            if (parsedLunch.startMin != parsedPeriods.last().endMin) {
                return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_lunch_last_not_connected)
            }
        }
        else -> {
            val before = parsedPeriods[lunchAfter - 1]
            val after = parsedPeriods[lunchAfter]
            if (before.endMin != parsedLunch.startMin || parsedLunch.endMin != after.startMin) {
                return AdvancedTimeValidation(warningMessageRes = R.string.warning_advanced_time_lunch_middle_not_connected)
            }
        }
    }

    val firstPeriodStart = parsedPeriods.first().startMin
    val breakDuration = regularBreaks.firstOrNull() ?: fallbackBreakDurationMin
    val derived = DerivedScheduleSettings(
        periodsPerDay = periodCount,
        periodDurationMin = periodDurations.first(),
        breakBetweenPeriodsMin = breakDuration,
        lunchBreakMin = parsedLunch.endMin - parsedLunch.startMin,
        lunchAfterPeriod = lunchAfter,
        firstPeriodStartHour = firstPeriodStart / 60,
        firstPeriodStartMinute = firstPeriodStart % 60
    )
    return AdvancedTimeValidation(derivedSettings = derived)
}

private fun parseTimeRangeDraft(draft: TimeRangeDraft): TimeRangeValue? {
    val start = parseRequiredTime(draft.startHour, draft.startMinute) ?: return null
    val end = parseRequiredTime(draft.endHour, draft.endMinute) ?: return null
    return TimeRangeValue(startMin = start, endMin = end)
}

private fun parseRequiredTime(hour: String, minute: String): Int? {
    val h = hour.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val m = minute.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return h * 60 + m
}

private fun timeRangeDraftFromMinutes(startMin: Int, endMin: Int): TimeRangeDraft {
    val safeStart = startMin.coerceIn(0, 23 * 60 + 59)
    val safeEnd = endMin.coerceIn(0, 23 * 60 + 59)
    val start = LocalTime.of(safeStart / 60, safeStart % 60)
    val end = LocalTime.of(safeEnd / 60, safeEnd % 60)
    return TimeRangeDraft(
        startHour = start.hour.toString(),
        startMinute = start.minute.toString().padStart(2, '0'),
        endHour = end.hour.toString(),
        endMinute = end.minute.toString().padStart(2, '0')
    )
}

private fun periodLabel(index: Int, useKosenMode: Boolean): String {
    return if (useKosenMode) {
        "${index * 2 + 1}/${index * 2 + 2}校時"
    } else {
        "${index + 1}限"
    }
}

private data class AdvancedTimeListItem(
    val key: String,
    val label: String,
    val isLunch: Boolean,
    val range: TimeRangeDraft? = null,
    val point: TimePointDraft? = null,
    val isOptionalPoint: Boolean = false,
    val periodIndex: Int? = null
)

private fun buildAdvancedTimeItems(
    periodRanges: List<TimeRangeDraft>,
    lunchRange: TimeRangeDraft,
    lunchAfterPeriod: Int,
    useKosenMode: Boolean,
    arrivalHour: String,
    arrivalMinute: String,
    departureHour: String,
    departureMinute: String
): List<AdvancedTimeListItem> {
    val items = mutableListOf<AdvancedTimeListItem>()
    items += AdvancedTimeListItem(
        key = "start",
        label = "始業時間",
        isLunch = false,
        point = TimePointDraft(arrivalHour, arrivalMinute),
        isOptionalPoint = true
    )
    val insertIndex = lunchAfterPeriod.coerceIn(0, periodRanges.size)
    for (index in 0..periodRanges.size) {
        if (index == insertIndex) {
            items += AdvancedTimeListItem(
                key = "lunch",
                label = "昼休み",
                isLunch = true,
                range = lunchRange
            )
        }
        if (index < periodRanges.size) {
            items += AdvancedTimeListItem(
                key = "period-$index",
                label = periodLabel(index, useKosenMode),
                isLunch = false,
                range = periodRanges[index],
                periodIndex = index
            )
        }
    }
    items += AdvancedTimeListItem(
        key = "end",
        label = "終業時間",
        isLunch = false,
        point = TimePointDraft(departureHour, departureMinute),
        isOptionalPoint = true
    )
    return items
}

@Composable
private fun CompactTimeListRow(
    item: AdvancedTimeListItem,
    rowIndex: Int,
    lunchAfterPeriod: Int,
    expanded: Boolean,
    isDraggingLunch: Boolean,
    onToggleExpanded: () -> Unit,
    onRangeChange: (TimeRangeDraft) -> Unit,
    onPointChange: (TimePointDraft) -> Unit,
    onLunchDragStart: () -> Unit,
    onLunchDragPreview: (Int) -> Int,
    onLunchDragEnd: () -> Unit,
    onLunchDragCancel: () -> Unit
) {
    var dragOffsetPx by remember(item.key) { mutableStateOf(0f) }
    var totalDragOffsetPx by remember(item.key) { mutableStateOf(0f) }
    var dragStartLunchAfterPeriod by remember(item.key) { mutableStateOf(lunchAfterPeriod) }
    var measuredRowHeightPx by remember(item.key) { mutableStateOf(0f) }
    var previousRowIndex by remember(item.key) { mutableStateOf(rowIndex) }
    val placementOffsetPx = remember(item.key) { Animatable(0f) }
    val density = LocalDensity.current
    val dragStepPx = remember(measuredRowHeightPx, density) {
        if (measuredRowHeightPx > 0f) {
            measuredRowHeightPx + with(density) { 1.dp.toPx() }
        } else {
            with(density) { 57.dp.toPx() }
        }
    }
    val dragOffsetDp = with(density) {
        if (item.isLunch && isDraggingLunch) dragOffsetPx.toDp() else 0.dp
    }
    val animatedDragOffsetDp by animateDpAsState(
        targetValue = dragOffsetDp,
        animationSpec = if (item.isLunch && isDraggingLunch) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "lunchDragOffset"
    )
    val liftProgress by animateFloatAsState(
        targetValue = if (item.isLunch && isDraggingLunch) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lunchLift"
    )
    val rowColor by animateColorAsState(
        targetValue = if (item.isLunch && isDraggingLunch) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "timeRowColor"
    )
    val surfaceShadowElevation by animateDpAsState(
        targetValue = if (item.isLunch && isDraggingLunch) 12.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "timeRowShadow"
    )
    val surfaceTonalElevation by animateDpAsState(
        targetValue = if (item.isLunch && isDraggingLunch) 6.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "timeRowTonal"
    )
    LaunchedEffect(rowIndex, dragStepPx) {
        val oldIndex = previousRowIndex
        if (oldIndex != rowIndex) {
            previousRowIndex = rowIndex
            if (!item.isLunch && dragStepPx > 0f) {
                placementOffsetPx.snapTo((oldIndex - rowIndex) * dragStepPx)
                placementOffsetPx.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(
                y = animatedDragOffsetDp + with(density) { placementOffsetPx.value.toDp() }
            )
            .zIndex(if (item.isLunch && isDraggingLunch) 2f else 0f)
            .graphicsLayer {
                scaleX = 1f + 0.04f * liftProgress
                scaleY = 1f + 0.04f * liftProgress
                shadowElevation = 36f * liftProgress
            }
            .clickable { onToggleExpanded() }
            .pointerInput(item.key) {
                if (item.isLunch) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragOffsetPx = 0f
                            totalDragOffsetPx = 0f
                            dragStartLunchAfterPeriod = lunchAfterPeriod
                            onLunchDragStart()
                        },
                        onDragCancel = {
                            dragOffsetPx = 0f
                            totalDragOffsetPx = 0f
                            onLunchDragCancel()
                        },
                        onDragEnd = {
                            dragOffsetPx = 0f
                            totalDragOffsetPx = 0f
                            onLunchDragEnd()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragOffsetPx += dragAmount.y
                            val requestedPosition = dragStartLunchAfterPeriod +
                                (totalDragOffsetPx / dragStepPx).roundToInt()
                            val previewPosition = onLunchDragPreview(requestedPosition)
                            dragOffsetPx = (totalDragOffsetPx -
                                (previewPosition - dragStartLunchAfterPeriod) * dragStepPx)
                                .coerceIn(-dragStepPx, dragStepPx)
                        }
                    )
                }
            }
    ) {
        Surface(
            color = rowColor,
            shadowElevation = surfaceShadowElevation,
            tonalElevation = surfaceTonalElevation,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    measuredRowHeightPx = it.height.toFloat()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (item.isLunch) {
                        Text(
                            text = "≡",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isLunch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = item.range?.let(::formatTimeRangeDraft)
                        ?: item.point?.let(::formatTimePointDraft)
                        ?: "--:--",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (expanded) {
            Surface(
                color = if (item.isLunch) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (item.range != null) {
                    TimeRangeFields(
                        range = item.range,
                        onRangeChange = onRangeChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else if (item.point != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.isOptionalPoint) {
                            Text(
                                text = "未入力でもそのまま使えます",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TimePartField(
                                value = item.point.hour,
                                label = stringResource(R.string.label_hour),
                                onValueChange = { onPointChange(item.point.copy(hour = it)) }
                            )
                            Text(":", style = MaterialTheme.typography.titleMedium)
                            TimePartField(
                                value = item.point.minute,
                                label = stringResource(R.string.label_minute),
                                onValueChange = { onPointChange(item.point.copy(minute = it)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimeRangeDraft(range: TimeRangeDraft): String {
    val start = "${range.startHour.ifBlank { "--" }}:${range.startMinute.ifBlank { "--" }}"
    val end = "${range.endHour.ifBlank { "--" }}:${range.endMinute.ifBlank { "--" }}"
    return "$start-$end"
}

private fun formatTimePointDraft(point: TimePointDraft): String {
    if (point.hour.isBlank() && point.minute.isBlank()) return "未設定"
    return "${point.hour.ifBlank { "--" }}:${point.minute.ifBlank { "--" }}"
}

@Composable
private fun LegacyEditableTimeRangeRow(
    label: String,
    range: TimeRangeDraft,
    onRangeChange: (TimeRangeDraft) -> Unit,
    emphasized: Boolean = false
) {
    Surface(
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            TimeRangeFields(
                range = range,
                onRangeChange = onRangeChange
            )
        }
    }
}

@Composable
private fun TimeRangeFields(
    range: TimeRangeDraft,
    onRangeChange: (TimeRangeDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimePairInputRow(
            label = "開始",
            hour = range.startHour,
            minute = range.startMinute,
            onHourChange = { onRangeChange(range.copy(startHour = it)) },
            onMinuteChange = { onRangeChange(range.copy(startMinute = it)) }
        )
        TimePairInputRow(
            label = "終了",
            hour = range.endHour,
            minute = range.endMinute,
            onHourChange = { onRangeChange(range.copy(endHour = it)) },
            onMinuteChange = { onRangeChange(range.copy(endMinute = it)) }
        )
    }
}

@Composable
private fun TimePairInputRow(
    label: String,
    hour: String,
    minute: String,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(38.dp)
        )
        TimePartField(
            value = hour,
            label = stringResource(R.string.label_hour),
            onValueChange = onHourChange
        )
        Text(":", style = MaterialTheme.typography.titleMedium)
        TimePartField(
            value = minute,
            label = stringResource(R.string.label_minute),
            onValueChange = onMinuteChange
        )
    }
}

@Composable
private fun TimePartField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(2)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(72.dp)
    )
}

@Composable
private fun LessonStartNotificationSettingsContent(
    enabled: Boolean,
    notificationsEnabled: Boolean,
    promotedNotificationsEnabled: Boolean,
    liveUpdatesEnabled: Boolean,
    liveUpdatesSupported: Boolean,
    progressCountsDown: Boolean,
    liveUpdateEarlyMinutes: Int,
    minutesBefore: String,
    exclusions: List<LessonNotificationExclusionEntity>,
    subjectSuggestions: List<String>,
    subjectTeacherCandidates: Map<String, List<String>>,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenPromotedNotificationSettings: () -> Unit,
    onToggleLiveUpdates: (Boolean) -> Unit,
    onToggleProgressCountsDown: (Boolean) -> Unit,
    onUpdateLiveUpdateEarlyMinutes: (Int) -> Unit,
    onMinutesBeforeChange: (String) -> Unit,
    onAddExclusion: (String, String?, Boolean) -> Unit,
    onDeleteExclusion: (LessonNotificationExclusionEntity) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var matchTeacher by remember { mutableStateOf(false) }
    var showSubjectSuggestions by remember { mutableStateOf(false) }
    var showLiveUpdateEarlyMinutesMenu by remember { mutableStateOf(false) }

    val filteredSubjectSuggestions = remember(subject, subjectSuggestions) {
        val query = subject.trim()
        subjectSuggestions
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            .distinct()
            .sorted()
            .take(8)
            .toList()
    }
    val teacherCandidates = remember(subject, subjectTeacherCandidates) {
        val key = subject.trim()
        if (key.isBlank()) emptyList()
        else subjectTeacherCandidates.entries
            .firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?.value.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val canAdd = subject.trim().isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.label_lesson_start_notification),
            description = stringResource(R.string.desc_lesson_start_notification),
            checked = enabled,
            onCheckedChange = onToggleEnabled
        )

        if (enabled) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!notificationsEnabled) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = stringResource(R.string.warning_notifications_disabled),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedButton(onClick = onOpenNotificationSettings) {
                                Text(stringResource(R.string.btn_open_notification_settings))
                            }
                        }
                    }
                }

                if (liveUpdatesEnabled && liveUpdatesSupported && !promotedNotificationsEnabled) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = stringResource(R.string.warning_promoted_notifications_disabled),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedButton(onClick = onOpenPromotedNotificationSettings) {
                                Text(stringResource(R.string.btn_open_live_updates_settings))
                            }
                        }
                    }
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.label_lesson_start_live_updates),
                    description = if (liveUpdatesSupported) {
                        stringResource(R.string.desc_lesson_start_live_updates)
                    } else {
                        stringResource(R.string.desc_lesson_start_live_updates_unavailable)
                    },
                    checked = liveUpdatesEnabled,
                    enabled = liveUpdatesSupported,
                    onCheckedChange = onToggleLiveUpdates
                )

                if (liveUpdatesEnabled && liveUpdatesSupported) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.label_lesson_start_live_update_early_minutes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.desc_lesson_start_live_update_early_minutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box {
                            OutlinedButton(onClick = { showLiveUpdateEarlyMinutesMenu = true }) {
                                Text(
                                    if (liveUpdateEarlyMinutes == 0) {
                                        stringResource(R.string.lesson_start_live_update_early_none)
                                    } else {
                                        stringResource(
                                            R.string.lesson_start_live_update_early_value,
                                            liveUpdateEarlyMinutes
                                        )
                                    }
                                )
                            }
                            DropdownMenu(
                                expanded = showLiveUpdateEarlyMinutesMenu,
                                onDismissRequest = { showLiveUpdateEarlyMinutesMenu = false }
                            ) {
                                listOf(0, 1, 2, 3, 5).forEach { minutes ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (minutes == 0) {
                                                    stringResource(R.string.lesson_start_live_update_early_none)
                                                } else {
                                                    stringResource(
                                                        R.string.lesson_start_live_update_early_value,
                                                        minutes
                                                    )
                                                }
                                            )
                                        },
                                        onClick = {
                                            showLiveUpdateEarlyMinutesMenu = false
                                            onUpdateLiveUpdateEarlyMinutes(minutes)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.label_lesson_start_progress_direction),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        LessonStartProgressDirectionRow(
                            selected = !progressCountsDown,
                            title = stringResource(R.string.label_lesson_start_progress_increasing),
                            description = stringResource(R.string.desc_lesson_start_progress_increasing),
                            onClick = { onToggleProgressCountsDown(false) }
                        )
                        LessonStartProgressDirectionRow(
                            selected = progressCountsDown,
                            title = stringResource(R.string.label_lesson_start_progress_decreasing),
                            description = stringResource(R.string.desc_lesson_start_progress_decreasing),
                            onClick = { onToggleProgressCountsDown(true) }
                        )
                    }
                }

                NumberSettingRow(
                    label = stringResource(R.string.label_lesson_start_notification_minutes),
                    value = minutesBefore,
                    unit = stringResource(R.string.unit_minutes_before),
                    onValueChange = { onMinutesBeforeChange(it.filter { c -> c.isDigit() }.take(3)) }
                )

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.label_lesson_start_notification_exclusions),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.desc_lesson_start_notification_exclusions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = {
                            subject = it
                            showSubjectSuggestions = false
                        },
                        label = { Text(stringResource(R.string.label_task_subject)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (filteredSubjectSuggestions.isNotEmpty()) {
                                IconButton(onClick = { showSubjectSuggestions = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.label_task_subject)
                                    )
                                }
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showSubjectSuggestions && filteredSubjectSuggestions.isNotEmpty(),
                        onDismissRequest = { showSubjectSuggestions = false },
                        modifier = Modifier.fillMaxWidth(0.95f)
                    ) {
                        filteredSubjectSuggestions.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate) },
                                onClick = {
                                    subject = candidate
                                    showSubjectSuggestions = false
                                    val candidates = subjectTeacherCandidates[candidate].orEmpty()
                                    if (candidates.size == 1) {
                                        teacher = candidates.first()
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            matchTeacher = !matchTeacher
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = matchTeacher,
                        onCheckedChange = { checked -> matchTeacher = checked }
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.label_lesson_start_notification_match_teacher),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.desc_lesson_start_notification_match_teacher),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (matchTeacher) {
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text(stringResource(R.string.label_task_teacher)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (teacherCandidates.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            teacherCandidates.take(4).forEach { candidate ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.clickable { teacher = candidate }
                                ) {
                                    Text(
                                        text = candidate,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onAddExclusion(
                            subject.trim(),
                            teacher.trim().takeIf { it.isNotBlank() },
                            matchTeacher && teacher.trim().isNotBlank()
                        )
                        subject = ""
                        teacher = ""
                        matchTeacher = false
                    },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_add_lesson_start_notification_exclusion))
                }

                if (exclusions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_no_lesson_start_notification_exclusions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        exclusions.forEach { exclusion ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exclusion.subject,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (exclusion.matchTeacher && !exclusion.teacher.isNullOrBlank()) {
                                        Text(
                                            text = stringResource(
                                                R.string.label_lesson_start_notification_exclusion_teacher,
                                                exclusion.teacher
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(onClick = { onDeleteExclusion(exclusion) }) {
                                    Text(stringResource(R.string.btn_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
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
private fun LessonStartProgressDirectionRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LessonCalendarSyncDateRow(
    label: String,
    date: LocalDate,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(onClick = onClick) {
            Text(date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
        }
    }
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
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun NumberSettingRow(
    label: String,
    value: String,
    unit: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(3)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp)
            )
            Text(unit, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TimeSettingRow(
    label: String,
    hour: String,
    minute: String,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = hour,
                onValueChange = { onHourChange(it.filter { c -> c.isDigit() }.take(2)) },
                label = { Text(stringResource(R.string.label_hour)) },
                placeholder = { Text(stringResource(R.string.placeholder_time)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(72.dp)
            )
            Text(":", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = minute,
                onValueChange = { onMinuteChange(it.filter { c -> c.isDigit() }.take(2)) },
                label = { Text(stringResource(R.string.label_minute)) },
                placeholder = { Text(stringResource(R.string.placeholder_time)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(72.dp)
            )
        }
    }
}
