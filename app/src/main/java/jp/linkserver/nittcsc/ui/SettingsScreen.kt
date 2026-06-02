package jp.linkserver.nittcsc.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
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
    onToggleCurrentTimeMarker: (Boolean) -> Unit,
    onToggleUnifyTaskPlanView: (Boolean) -> Unit,
    onToggleShowWeekdayOnDates: (Boolean) -> Unit,
    onToggleAdvancedTimeSettingsUi: (Boolean) -> Unit,
    onUpdateScheduleSettings: (periodsPerDay: Int, periodDurationMin: Int, breakBetweenPeriodsMin: Int, lunchBreakMin: Int, lunchAfterPeriod: Int, startHour: Int, startMinute: Int, useKosenMode: Boolean, arrivalHour: Int, arrivalMinute: Int, departureHour: Int, departureMinute: Int) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onExportAllAsJson: suspend () -> String = { "{}" },
    onImportAllFromJson: (String) -> Unit = {}
) {
    val enabledLocalAi = state.settings?.enableLocalAi ?: false
    val enabledDrawerNavigation = state.settings?.useDrawerNavigation ?: false
    val enabledTaskCalendarSync = state.settings?.addTasksToCalendar ?: false
    val enabledCurrentTimeMarker = state.settings?.showCurrentTimeMarker ?: false
    val enabledUnifyTaskPlanView = state.settings?.unifyTaskPlanView ?: false
    val enabledShowWeekdayOnDates = state.settings?.showWeekdayOnDates ?: false
    val enabledAdvancedTimeSettingsUi = state.settings?.useAdvancedTimeSettingsUi ?: false
    var expandTimetableSettings by rememberSaveable { mutableStateOf(true) }
    var showLocalAiWarningDialog by remember { mutableStateOf(false) }
    val s = state.settings

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
    var advancedPeriodCount by remember(enabledAdvancedTimeSettingsUi) { mutableStateOf(s?.periodsPerDay?.toString() ?: "4") }
    var advancedLunchAfterPeriod by remember(enabledAdvancedTimeSettingsUi) { mutableStateOf(s?.lunchAfterPeriod ?: 2) }
    var advancedPeriodRanges by remember(enabledAdvancedTimeSettingsUi) { mutableStateOf(emptyList<TimeRangeDraft>()) }
    var advancedLunchRange by remember(enabledAdvancedTimeSettingsUi) { mutableStateOf(TimeRangeDraft("12", "00", "13", "00")) }
    var expandedAdvancedTimeItemKey by rememberSaveable { mutableStateOf<String?>(null) }
    var previewLunchAfterPeriod by remember { mutableStateOf<Int?>(null) }
    var isDraggingLunch by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

            // ── 課題・予定設定 ──────────────────────────────────────────
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
                            title = stringResource(R.string.label_unify_task_plan_view),
                            description = stringResource(R.string.desc_unify_task_plan_view),
                            checked = enabledUnifyTaskPlanView,
                            onCheckedChange = onToggleUnifyTaskPlanView
                        )
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
                            title = stringResource(R.string.label_use_hamburger_navigation),
                            description = stringResource(R.string.desc_use_hamburger_navigation),
                            checked = enabledDrawerNavigation,
                            onCheckedChange = onToggleDrawerNavigation
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
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_advanced_time_settings_ui),
                            description = stringResource(R.string.desc_advanced_time_settings_ui),
                            checked = enabledAdvancedTimeSettingsUi,
                            onCheckedChange = onToggleAdvancedTimeSettingsUi
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
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
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
