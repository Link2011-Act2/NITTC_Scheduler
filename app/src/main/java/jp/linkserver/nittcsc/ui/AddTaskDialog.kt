package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.TaskEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    task: TaskEntity? = null,
    subjectSuggestions: List<String> = emptyList(),
    subjectTeacherCandidates: Map<String, List<String>> = emptyMap(),
    defaultDueHour: Int = 8,
    defaultDueMinute: Int = 40,
    onResolveNextLessonDateTime: suspend (subject: String, teacher: String?, useTeacherMatching: Boolean) -> Pair<LocalDate, LocalTime>? = { _, _, _ -> null },
    onSave: (TaskEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var subject by remember { mutableStateOf(task?.subject ?: "") }
    var teacher by remember { mutableStateOf(task?.teacher ?: "") }
    var dueDate by remember { mutableStateOf(task?.dueDate ?: LocalDate.now()) }
    var dueHour by remember { mutableStateOf(task?.dueHour ?: defaultDueHour) }
    var dueMinute by remember { mutableStateOf(task?.dueMinute ?: defaultDueMinute) }
    var priority by remember { mutableStateOf(task?.priority ?: 0) }
    var useTeacherMatching by remember { mutableStateOf(task?.useTeacherMatching ?: false) }
    var showSubjectSuggestions by remember { mutableStateOf(false) }
    var isAutoResolvingDate by remember { mutableStateOf(false) }
    val teacherCandidatesForSubject = remember(subject, subjectTeacherCandidates) {
        val key = subject.trim()
        if (key.isBlank()) {
            emptyList()
        } else {
            subjectTeacherCandidates.entries
                .firstOrNull { it.key.equals(key, ignoreCase = true) }
                ?.value
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
    }

    val dueDateString = dueDate.format(dateFormatter)
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

    LaunchedEffect(subject.trim(), teacher.trim(), useTeacherMatching) {
        val subjectValue = subject.trim()
        if (subjectValue.isBlank()) return@LaunchedEffect
        isAutoResolvingDate = true
        try {
            val nextDateTime = onResolveNextLessonDateTime(
                subjectValue,
                teacher.trim().takeIf { it.isNotBlank() },
                useTeacherMatching
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
        if (teacherCandidatesForSubject.size == 1) {
            teacher = teacherCandidatesForSubject.first()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (task != null) {
                    stringResource(R.string.title_edit_task)
                } else {
                    stringResource(R.string.title_add_task)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.label_task_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.label_task_description)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    maxLines = 3
                )

                // Subject Field (with timetable suggestions)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = {
                            subject = it
                            showSubjectSuggestions = false
                        },
                        label = { Text(stringResource(R.string.label_task_subject)) },
                        trailingIcon = {
                            if (filteredSubjectSuggestions.isNotEmpty() && !showSubjectSuggestions) {
                                IconButton(onClick = { showSubjectSuggestions = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.label_task_subject)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
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
                                }
                            )
                        }
                    }
                }

                // Teacher Field
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text(stringResource(R.string.label_task_teacher)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (teacherCandidatesForSubject.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.label_task_teacher_candidates),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                // Due Date Picker
                DueDatePickerField(
                    date = dueDate,
                    dateString = dueDateString,
                    onDateChange = { dueDate = it }
                )
                DueTimePickerField(
                    hour = dueHour,
                    minute = dueMinute,
                    onTimeChange = { h, m ->
                        dueHour = h
                        dueMinute = m
                    }
                )
                if (isAutoResolvingDate) {
                    Text(
                        text = stringResource(R.string.label_task_due_autofill_searching),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.label_task_due_autofill_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Priority Selection
                Column {
                    Text(
                        text = stringResource(R.string.label_task_priority),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        listOf(
                            1 to stringResource(R.string.label_task_priority_high),
                            0 to stringResource(R.string.label_task_priority_normal),
                            -1 to stringResource(R.string.label_task_priority_low)
                        ).forEach { (value, label) ->
                            Row(
                                modifier = Modifier.clickable { priority = value },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = priority == value,
                                    onClick = { priority = value }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Use Teacher Matching Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useTeacherMatching = !useTeacherMatching }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useTeacherMatching,
                        onCheckedChange = { useTeacherMatching = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.label_task_use_teacher_matching),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.desc_teacher_matching),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && subject.isNotBlank()) {
                        val newTask = TaskEntity(
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
                            useTeacherMatching = useTeacherMatching
                        )
                        onSave(newTask)
                    }
                }
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePickerField(
    date: LocalDate,
    dateString: String,
    onDateChange: (LocalDate) -> Unit
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * 86400000L
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateChange(LocalDate.ofEpochDay(millis / 86400000L))
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        ) { DatePicker(state = state) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { showPicker = true }
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.label_due_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = dateString, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueTimePickerField(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val timeText = String.format("%02d:%02d", hour, minute)
    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        var lastHour by remember(state) { mutableStateOf(state.hour) }
        var lastMinute by remember(state) { mutableStateOf(state.minute) }
        LaunchedEffect(state.hour, state.minute) {
            if (state.hour != lastHour || state.minute != lastMinute) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                lastHour = state.hour
                lastMinute = state.minute
            }
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(state.hour, state.minute)
                    showPicker = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
            text = { TimePicker(state = state) }
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showPicker = true
            }
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.label_due_time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = timeText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
