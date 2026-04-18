package jp.linkserver.nittcsc.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import jp.linkserver.nittcsc.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SchedulerUiState,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    onToggleLocalAi: (Boolean) -> Unit,
    onToggleDrawerNavigation: (Boolean) -> Unit,
    onToggleAddTasksToCalendar: (Boolean) -> Unit,
    onUpdateScheduleSettings: (periodsPerDay: Int, periodDurationMin: Int, breakBetweenPeriodsMin: Int, lunchBreakMin: Int, lunchAfterPeriod: Int, startHour: Int, startMinute: Int, useKosenMode: Boolean, arrivalHour: Int, arrivalMinute: Int, departureHour: Int, departureMinute: Int) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onExportAllAsJson: suspend () -> String = { "{}" },
    onImportAllFromJson: (String) -> Unit = {}
) {
    val enabledLocalAi = state.settings?.enableLocalAi ?: false
    val enabledDrawerNavigation = state.settings?.useDrawerNavigation ?: false
    val enabledTaskCalendarSync = state.settings?.addTasksToCalendar ?: false
    var expandTimetableSettings by rememberSaveable { mutableStateOf(true) }
    var showLocalAiWarningDialog by remember { mutableStateOf(false) }
    val s = state.settings

    // 時間割設定ローカル状態
    var periodsPerDay by remember(s) { mutableStateOf(s?.periodsPerDay?.toString() ?: "4") }
    var periodDurationMin by remember(s) { mutableStateOf(s?.periodDurationMin?.toString() ?: "90") }
    var breakBetweenPeriodsMin by remember(s) { mutableStateOf(s?.breakBetweenPeriodsMin?.toString() ?: "10") }
    var lunchBreakMin by remember(s) { mutableStateOf(s?.lunchBreakMin?.toString() ?: "50") }
    var lunchAfterPeriod by remember(s) { mutableStateOf(s?.lunchAfterPeriod?.toString() ?: "2") }
    var startHour by remember(s) { mutableStateOf(s?.firstPeriodStartHour?.toString() ?: "8") }
    var startMinute by remember(s) { mutableStateOf(s?.firstPeriodStartMinute?.toString() ?: "40") }
    var useKosenMode by remember(s) { mutableStateOf(s?.useKosenMode ?: true) }
    // 登下校時刻（空文字 = 未設定）
    var arrivalHour by remember(s) { mutableStateOf(if ((s?.arrivalHour ?: -1) >= 0) s!!.arrivalHour.toString() else "") }
    var arrivalMinute by remember(s) { mutableStateOf(if ((s?.arrivalMinute ?: -1) >= 0) s!!.arrivalMinute.toString().padStart(2,'0') else "") }
    var departureHour by remember(s) { mutableStateOf(if ((s?.departureHour ?: -1) >= 0) s!!.departureHour.toString() else "") }
    var departureMinute by remember(s) { mutableStateOf(if ((s?.departureMinute ?: -1) >= 0) s!!.departureMinute.toString().padStart(2,'0') else "") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

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
        val l = lunchBreakMin.toIntOrNull()?.coerceIn(0, 180) ?: 50
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

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = onExportAllAsJson()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(json)
                } ?: error("failed to open output stream")
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.msg_export_success), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.msg_export_failed), Toast.LENGTH_SHORT).show()
            }
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
                .verticalScroll(rememberScrollState()),
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

                        // 数値入力フィールド群
                        NumberSettingRow(label = stringResource(R.string.label_periods_per_day), value = periodsPerDay, unit = stringResource(R.string.unit_period), onValueChange = { periodsPerDay = it })
                        NumberSettingRow(label = stringResource(R.string.label_period_duration), value = periodDurationMin, unit = stringResource(R.string.unit_minute), onValueChange = { periodDurationMin = it })
                        NumberSettingRow(label = stringResource(R.string.label_break_duration), value = breakBetweenPeriodsMin, unit = stringResource(R.string.unit_minute), onValueChange = { breakBetweenPeriodsMin = it })
                        NumberSettingRow(label = stringResource(R.string.label_lunch_duration), value = lunchBreakMin, unit = stringResource(R.string.unit_minute), onValueChange = { lunchBreakMin = it })
                        NumberSettingRow(label = stringResource(R.string.label_lunch_after), value = lunchAfterPeriod, unit = stringResource(R.string.unit_after_period), onValueChange = { lunchAfterPeriod = it })
                        // 開始時刻
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

                        // 登下校時刻
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
                        Text(
                            text = "変更は自動保存されます",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    stringResource(R.string.label_use_hamburger_navigation),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.desc_use_hamburger_navigation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = enabledDrawerNavigation,
                                onCheckedChange = onToggleDrawerNavigation
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    stringResource(R.string.label_add_tasks_to_calendar),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.desc_add_tasks_to_calendar),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = enabledTaskCalendarSync,
                                onCheckedChange = onToggleAddTasksToCalendar
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    stringResource(R.string.label_local_ai_import),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.desc_local_ai_import),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
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
                                    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                                        .format(LocalDateTime.now())
                                    exportJsonLauncher.launch("nittcsc_settings_${stamp}.json")
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
