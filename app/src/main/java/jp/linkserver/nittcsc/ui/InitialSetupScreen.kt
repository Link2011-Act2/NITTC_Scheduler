package jp.linkserver.nittcsc.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.logic.InitialSetupDraft
import jp.linkserver.nittcsc.logic.PeriodLabelStyle
import jp.linkserver.nittcsc.logic.SetupExamChoice
import jp.linkserver.nittcsc.logic.SetupTimeDraft
import jp.linkserver.nittcsc.logic.forExamTimetable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val LocalAbTimetableEnabled = staticCompositionLocalOf { true }

private val SetupDraftSaver = listSaver<InitialSetupDraft, String>(
    save = { draft ->
        listOf(draft.semester.toString(), draft.ab.toString(), draft.examChoice.name,
            draft.labelStyle.name, draft.tsuruokaPreset.toString()) +
            listOf(draft.regular, draft.exam).flatMap {
                listOf(it.count, it.start, it.duration, it.breakMinutes, it.lunchMinutes, it.lunchAfter)
            }
    },
    restore = { values ->
        fun time(offset: Int) = SetupTimeDraft(values[offset], values[offset + 1],
            values[offset + 2], values[offset + 3], values[offset + 4], values[offset + 5])
        InitialSetupDraft(values[0].toBoolean(), values[1].toBoolean(), time(5), time(11),
            SetupExamChoice.valueOf(values[2]), PeriodLabelStyle.valueOf(values[3]), values[4].toBoolean())
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialSetupScreen(
    onComplete: suspend (InitialSetupDraft) -> Unit,
    onRestore: suspend (String) -> Unit
) {
    var draft by rememberSaveable(stateSaver = SetupDraftSaver) { mutableStateOf(InitialSetupDraft()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var editingReview by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0); error = null }
    fun back() {
        if (editingReview) { step = 4; editingReview = false } else step = (step - 1).coerceAtLeast(0)
    }
    BackHandler(enabled = busy || step > 0) { if (!busy) back() }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val limit = 32 * 1024 * 1024
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= limit)
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name())
                    } ?: error("Cannot open backup")
                }
                onRestore(json)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = R.string.setup_restore_failed
            } finally {
                busy = false
            }
        }
    }
    val canContinue = when (step) {
        2 -> draft.regular.values() != null
        3 -> draft.examChoice != SetupExamChoice.SEPARATE || draft.exam.values() != null
        4 -> draft.regular.values() != null &&
            (draft.examChoice != SetupExamChoice.SEPARATE || draft.exam.values() != null)
        else -> true
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp)) }
                if (step > 0) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = ::back, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.setup_back))
                    }
                    Button(enabled = !busy && canContinue, modifier = Modifier.weight(2f), onClick = {
                        if (step == 4) scope.launch {
                            busy = true
                            error = null
                            try { onComplete(draft) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = R.string.setup_save_failed }
                            finally { busy = false }
                        } else if (editingReview) {
                            step = 4
                            editingReview = false
                        } else step++
                    }) {
                        Text(stringResource(if (step == 4) R.string.setup_start else R.string.setup_next))
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(scroll).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.setup_progress, step + 1, 5),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(when (step) {
                0 -> R.string.setup_welcome
                1 -> R.string.setup_patterns_title
                2 -> R.string.setup_regular_title
                3 -> R.string.setup_exam_title
                else -> R.string.setup_review_title
            }), style = MaterialTheme.typography.headlineSmall)
            if (busy) Text(stringResource(R.string.setup_working)) else when (step) {
                0 -> {
                    Text(stringResource(R.string.setup_welcome_description))
                    Button(enabled = !busy, onClick = {
                        draft = InitialSetupDraft.tsuruoka(); step = 4
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_tsuruoka)) }
                    Text(stringResource(R.string.setup_tsuruoka_description), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = !busy, onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.setup_custom))
                    }
                    OutlinedButton(enabled = !busy, onClick = {
                        restoreLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_restore)) }
                }
                1 -> {
                    Text(stringResource(R.string.setup_patterns_description))
                    SetupCheckRow(stringResource(R.string.label_semester_timetables),
                        stringResource(R.string.desc_semester_timetables), draft.semester) {
                        draft = draft.copy(semester = it)
                    }
                    SetupCheckRow(stringResource(R.string.setup_ab_label),
                        stringResource(R.string.setup_ab_description), draft.ab) { draft = draft.copy(ab = it) }
                    if (!draft.semester && !draft.ab) Text(stringResource(R.string.setup_same_year))
                }
                2 -> {
                    Text(stringResource(R.string.setup_time_description))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            draft = draft.copy(regular = SetupTimeDraft("6", "08:40", "50", "10", "45", "4"),
                                labelStyle = PeriodLabelStyle.SINGLE_KOSHI, tsuruokaPreset = false)
                        }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.setup_preset_50)) }
                        OutlinedButton(onClick = {
                            draft = draft.copy(regular = SetupTimeDraft(), labelStyle = PeriodLabelStyle.KOMA,
                                tsuruokaPreset = false)
                        }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.setup_preset_90)) }
                    }
                    SetupTimeEditor(draft.regular, draft.labelStyle) { draft = draft.copy(regular = it) }
                    Text(stringResource(R.string.label_koshi_notation), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.selectableGroup()) {
                        PeriodLabelStyle.entries.forEach { style ->
                            SetupRadioRow(stringResource(when (style) {
                                PeriodLabelStyle.PAIR_KOSHI -> R.string.period_label_pair_koshi
                                PeriodLabelStyle.SINGLE_KOSHI -> R.string.period_label_single_koshi
                                PeriodLabelStyle.KOMA -> R.string.period_label_koma
                            }), draft.labelStyle == style) { draft = draft.copy(labelStyle = style) }
                        }
                    }
                    Text(stringResource(R.string.setup_arrival_description), style = MaterialTheme.typography.bodySmall)
                }
                3 -> {
                    Column(Modifier.selectableGroup()) {
                        SetupExamChoice.entries.forEach { choice ->
                            SetupRadioRow(stringResource(when (choice) {
                                SetupExamChoice.SEPARATE -> R.string.setup_exam_separate
                                SetupExamChoice.SAME -> R.string.setup_exam_same
                                SetupExamChoice.LATER -> R.string.setup_exam_later
                            }), draft.examChoice == choice) { draft = draft.copy(examChoice = choice) }
                        }
                    }
                    if (draft.examChoice == SetupExamChoice.SEPARATE) {
                        SetupTimeEditor(draft.exam, draft.labelStyle.forExamTimetable(), exam = true) {
                            draft = draft.copy(exam = it)
                        }
                    } else Text(stringResource(R.string.setup_exam_later_description))
                }
                4 -> {
                    Text(stringResource(R.string.setup_review_description))
                    SetupReviewCard(stringResource(R.string.setup_patterns_heading), onEdit = {
                        editingReview = true; step = 1
                    }) {
                        Text(stringResource(if (draft.semester) R.string.setup_semester_on else R.string.setup_semester_off))
                        Text(stringResource(if (draft.ab) R.string.setup_ab_on else R.string.setup_ab_off))
                    }
                    SetupReviewCard(stringResource(R.string.section_timetable_settings), onEdit = {
                        editingReview = true; step = 2
                    }) { SetupTimePreview(draft.regular, draft.labelStyle) }
                    SetupReviewCard(stringResource(R.string.section_exam_timetable_settings), onEdit = {
                        editingReview = true; step = 3
                    }) {
                        if (draft.examChoice == SetupExamChoice.SEPARATE) {
                            SetupTimePreview(draft.exam, draft.labelStyle.forExamTimetable())
                        } else Text(stringResource(if (draft.examChoice == SetupExamChoice.SAME)
                            R.string.setup_exam_same else R.string.setup_exam_later))
                    }
                    if (draft.ab) Text(stringResource(R.string.setup_ab_next), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.setup_notifications_later), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun SetupCheckRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange)
        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupRadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(title, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

@Composable
private fun SetupTimeEditor(
    draft: SetupTimeDraft, style: PeriodLabelStyle, exam: Boolean = false, onChange: (SetupTimeDraft) -> Unit
) {
    SetupField(stringResource(R.string.setup_max_periods), draft.count) { onChange(draft.copy(count = it)) }
    SetupField(stringResource(if (exam) R.string.setup_exam_start else R.string.label_first_period_start),
        draft.start, time = true) { onChange(draft.copy(start = it)) }
    SetupField(stringResource(if (exam) R.string.setup_exam_duration else R.string.setup_regular_duration),
        draft.duration) { onChange(draft.copy(duration = it)) }
    SetupField(stringResource(R.string.setup_break_minutes), draft.breakMinutes) { onChange(draft.copy(breakMinutes = it)) }
    SetupField(stringResource(R.string.setup_lunch_after), draft.lunchAfter) { onChange(draft.copy(lunchAfter = it)) }
    SetupField(stringResource(R.string.setup_lunch_minutes), draft.lunchMinutes) { onChange(draft.copy(lunchMinutes = it)) }
    SetupTimePreview(draft, style)
}

@Composable
private fun SetupField(label: String, value: String, time: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange = { input ->
        onChange(input.filter { it in '0'..'9' || (time && it == ':') }.take(if (time) 5 else 3))
    }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        supportingText = if (time) { { Text(stringResource(R.string.setup_time_format)) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (time) KeyboardType.Ascii else KeyboardType.Number))
}

@Composable
private fun SetupTimePreview(draft: SetupTimeDraft, style: PeriodLabelStyle) {
    val values = remember(draft) { draft.values() }
    val slots = remember(values, style) { values?.slots(style).orEmpty() }
    if (values == null) {
        Text(stringResource(R.string.setup_invalid_time), color = MaterialTheme.colorScheme.error)
    } else {
        Text(stringResource(R.string.label_timetable_blocks), style = MaterialTheme.typography.titleSmall)
        slots.forEach { slot ->
            Text(stringResource(R.string.setup_preview_slot, slot.label, slot.start.toString(), slot.end.toString()))
            if (values.lunchAfter == slot.index + 1) {
                Text(stringResource(R.string.setup_preview_lunch, values.lunchMinutes),
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SetupReviewCard(title: String, onEdit: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onEdit) { Text(stringResource(R.string.setup_edit)) }
            }
            content()
        }
    }
}
