package jp.linkserver.nittcsc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.TaskEntity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    task: TaskEntity? = null,
    subjectSuggestions: List<String> = emptyList(),
    subjectTeacherCandidates: Map<String, List<String>> = emptyMap(),
    defaultDueHour: Int = 8,
    defaultDueMinute: Int = 40,
    isPlan: Boolean = false,
    onResolveNextLessonDateTime: suspend (subject: String, teacher: String?, fromDate: LocalDate, fromTime: LocalTime) -> Pair<LocalDate, LocalTime>? = { _, _, _, _ -> null },
    onResolvePreviousLessonDateTime: suspend (subject: String, teacher: String?, fromDate: LocalDate, fromTime: LocalTime) -> Pair<LocalDate, LocalTime>? = { _, _, _, _ -> null },
    onResolveNextLessonDateTimeSkipCurrent: suspend (subject: String, teacher: String?, fromDate: LocalDate, fromTime: LocalTime) -> Pair<LocalDate, LocalTime>? = { _, _, _, _ -> null },
    onSave: (TaskEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var subject by remember { mutableStateOf(task?.subject ?: "") }
    var teacher by remember { mutableStateOf(task?.teacher ?: "") }
    // 新規タスクで教科名が事前設定されている場合は自動検索を即座にトリガーする
    var subjectEditedByUser by remember { mutableStateOf((task?.id ?: 0L) == 0L && task?.subject?.isNotBlank() == true) }
    var dueDate by remember { mutableStateOf(task?.dueDate ?: LocalDate.now()) }
    var dueHour by remember { mutableStateOf(if ((task?.id ?: 0L) == 0L) defaultDueHour else (task?.dueHour ?: defaultDueHour)) }
    var dueMinute by remember { mutableStateOf(if ((task?.id ?: 0L) == 0L) defaultDueMinute else (task?.dueMinute ?: defaultDueMinute)) }
    var priority by remember { mutableStateOf(task?.priority ?: 0) }
    var showSubjectSuggestions by remember { mutableStateOf(false) }
    var isAutoResolvingDate by remember { mutableStateOf(false) }
    var showDatePickerDialog by rememberSaveable { mutableStateOf(false) }
    var showTimePickerDialog by rememberSaveable { mutableStateOf(false) }

    val teacherCandidatesForSubject = remember(subject, subjectTeacherCandidates) {
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

    LaunchedEffect(subject.trim(), teacher.trim()) {
        val subjectValue = subject.trim()
        if (subjectValue.isBlank()) return@LaunchedEffect
        if (!subjectEditedByUser) return@LaunchedEffect
        isAutoResolvingDate = true
        try {
            val nextDateTime = onResolveNextLessonDateTime(
                subjectValue,
                teacher.trim().takeIf { it.isNotBlank() },
                LocalDate.now(),
                LocalTime.now()
            )
            if (nextDateTime != null) {
                dueDate = nextDateTime.first
                dueHour = nextDateTime.second.hour
                dueMinute = nextDateTime.second.minute
            }
        } finally {
            isAutoResolvingDate = false
        }
    }

    LaunchedEffect(subject.trim(), subjectTeacherCandidates) {
        val subjectValue = subject.trim()
        if (subjectValue.isBlank()) return@LaunchedEffect
        if (!subjectEditedByUser) return@LaunchedEffect
        if (teacherCandidatesForSubject.size == 1) {
            teacher = teacherCandidatesForSubject.first()
        }
    }

    val canSave = title.isNotBlank() && (isPlan || subject.isNotBlank())

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

    fun saveTask() {
        if (!canSave) return
        onSave(
            TaskEntity(
                id = task?.id ?: 0,
                lessonId = task?.lessonId,
                subject = subject.trim(),
                teacher = teacher.trim().takeIf { it.isNotEmpty() },
                title = title.trim(),
                description = description.trim().takeIf { it.isNotEmpty() },
                dueDate = dueDate,
                dueHour = dueHour,
                dueMinute = dueMinute,
                isCompleted = task?.isCompleted ?: false,
                completedDate = task?.completedDate,
                createdDate = task?.createdDate ?: LocalDate.now(),
                priority = priority,
                useTeacherMatching = true,
                calendarEventId = task?.calendarEventId
            )
        )
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (task != null) stringResource(if (isPlan) R.string.title_edit_plan else R.string.title_edit_task)
                               else stringResource(if (isPlan) R.string.title_add_plan else R.string.title_add_task)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { saveTask() },
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

            // ── 基本情報 ──────────────────────────────────────────
            SectionLabel(stringResource(R.string.section_task_basic))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(if (isPlan) R.string.label_plan_title else R.string.label_task_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text(stringResource(if (isPlan) R.string.placeholder_plan_title else R.string.placeholder_task_title)) },
                        textStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = fieldColors
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.label_task_description),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text(stringResource(R.string.placeholder_task_description)) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        colors = fieldColors
                    )
                }
            }

            // ── 教科・担当教員 ──────────────────────────────────────────
            SectionLabel(stringResource(R.string.section_task_subject))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(R.string.label_task_subject),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = subject,
                                onValueChange = {
                                    subject = it
                                    subjectEditedByUser = true
                                    showSubjectSuggestions = false
                                },
                                placeholder = { Text(stringResource(R.string.placeholder_not_set)) },
                                textStyle = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = fieldColors
                            )
                            if (filteredSubjectSuggestions.isNotEmpty()) {
                                IconButton(onClick = { showSubjectSuggestions = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.label_task_subject),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
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
                                        subjectEditedByUser = true
                                        showSubjectSuggestions = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.label_task_teacher),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        placeholder = { Text(stringResource(R.string.placeholder_teacher)) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = fieldColors
                    )
                    if (teacherCandidatesForSubject.size > 1) {
                        Text(
                            text = stringResource(R.string.label_task_teacher_candidates),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            teacherCandidatesForSubject.forEach { candidate ->
                                AssistChip(
                                    onClick = { teacher = candidate },
                                    label = { Text(candidate) }
                                )
                            }
                        }
                    }
                }
            }

            // ── 期限 ──────────────────────────────────────────
            SectionLabel(
                if (isAutoResolvingDate) stringResource(R.string.label_task_due_autofill_searching)
                else stringResource(R.string.section_task_due)
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (showDatePickerDialog) {
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = dueDate.toEpochDay() * 86400000L
                        )
                        DatePickerDialog(
                            onDismissRequest = { showDatePickerDialog = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        dueDate = LocalDate.ofEpochDay(millis / 86400000L)
                                    }
                                    showDatePickerDialog = false
                                }) { Text(stringResource(R.string.btn_save)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePickerDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                            }
                        ) { DatePicker(state = datePickerState) }
                    }
                    if (showTimePickerDialog) {
                        val timePickerState = rememberTimePickerState(
                            initialHour = dueHour,
                            initialMinute = dueMinute,
                            is24Hour = true
                        )
                        var lastHour by remember(timePickerState) { mutableStateOf(timePickerState.hour) }
                        var lastMinute by remember(timePickerState) { mutableStateOf(timePickerState.minute) }
                        LaunchedEffect(timePickerState.hour, timePickerState.minute) {
                            if (timePickerState.hour != lastHour || timePickerState.minute != lastMinute) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHour = timePickerState.hour
                                lastMinute = timePickerState.minute
                            }
                        }
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showTimePickerDialog = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    dueHour = timePickerState.hour
                                    dueMinute = timePickerState.minute
                                    showTimePickerDialog = false
                                }) { Text(stringResource(R.string.btn_save)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTimePickerDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                            },
                            text = { TimePicker(state = timePickerState) }
                        )
                    }
                    // Date row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePickerDialog = true }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.label_due_date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            dueDate.format(dateFormatter),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider()
                    // Time row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showTimePickerDialog = true
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.label_due_time),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            String.format("%02d:%02d", dueHour, dueMinute),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    // Navigation buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (subject.isNotBlank()) {
                                    isAutoResolvingDate = true
                                    coroutineScope.launch {
                                        try {
                                            val prevDateTime = onResolvePreviousLessonDateTime(
                                                subject.trim(),
                                                teacher.trim().takeIf { it.isNotBlank() },
                                                dueDate,
                                                LocalTime.of(dueHour, dueMinute)
                                            )
                                            if (prevDateTime != null) {
                                                dueDate = prevDateTime.first
                                                dueHour = prevDateTime.second.hour
                                                dueMinute = prevDateTime.second.minute
                                            }
                                        } finally {
                                            isAutoResolvingDate = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = subject.isNotBlank()
                        ) {
                            Text(stringResource(R.string.btn_prev_lesson))
                        }
                        OutlinedButton(
                            onClick = {
                                if (subject.isNotBlank()) {
                                    isAutoResolvingDate = true
                                    coroutineScope.launch {
                                        try {
                                            val nextDateTime = onResolveNextLessonDateTimeSkipCurrent(
                                                subject.trim(),
                                                teacher.trim().takeIf { it.isNotBlank() },
                                                dueDate,
                                                LocalTime.of(dueHour, dueMinute)
                                            )
                                            if (nextDateTime != null) {
                                                dueDate = nextDateTime.first
                                                dueHour = nextDateTime.second.hour
                                                dueMinute = nextDateTime.second.minute
                                            }
                                        } finally {
                                            isAutoResolvingDate = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = subject.isNotBlank()
                        ) {
                            Text(stringResource(R.string.btn_next_lesson))
                        }
                    }
                }
            }

            // ── 優先度 ──────────────────────────────────────────
            SectionLabel(stringResource(R.string.label_task_priority))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        1 to stringResource(R.string.label_task_priority_high),
                        0 to stringResource(R.string.label_task_priority_normal),
                        -1 to stringResource(R.string.label_task_priority_low)
                    ).forEach { (value, label) ->
                        val selected = priority == value
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { priority = value },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                RadioButton(selected = selected, onClick = { priority = value })
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // bottom spacer for scroll
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp)
    )
}
