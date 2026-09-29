package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.logic.TimetableTerm
import kotlinx.coroutines.launch

@Composable
fun LegacyTimetableMigrationDialog(
    academicYear: Int,
    lessons: List<LessonEntity>,
    onSelect: suspend (TimetableTerm) -> Boolean
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }

    fun select(term: TimetableTerm) {
        if (saving) return
        saving = true
        scope.launch {
            failed = !onSelect(term)
            saving = false
        }
    }

    if (showPreview) {
        LegacyTimetablePreviewDialog(
            academicYear = academicYear,
            lessons = lessons,
            onClose = { showPreview = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.dialog_legacy_timetable_title, academicYear)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_legacy_timetable_message))
                if (failed) Text(stringResource(R.string.msg_legacy_timetable_move_failed))
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showPreview = true },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_legacy_timetable_preview))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { select(TimetableTerm.SECOND) }, enabled = !saving) {
                Text(stringResource(R.string.btn_legacy_timetable_second))
            }
        },
        dismissButton = {
            TextButton(onClick = { select(TimetableTerm.FIRST) }, enabled = !saving) {
                Text(stringResource(R.string.btn_legacy_timetable_first))
            }
        }
    )
}

@Composable
private fun LegacyTimetablePreviewDialog(
    academicYear: Int,
    lessons: List<LessonEntity>,
    onClose: () -> Unit
) {
    val enteredLessons = remember(lessons) {
        lessons.filter { it.hasPreviewContent() }
            .sortedWith(compareBy(LessonEntity::dayOfWeek, LessonEntity::slotIndex))
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.title_legacy_timetable_preview, academicYear)) },
        text = {
            if (enteredLessons.isEmpty()) {
                Text(stringResource(R.string.msg_legacy_timetable_preview_empty))
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    enteredLessons.forEachIndexed { index, lesson ->
                        if (index > 0) HorizontalDivider()
                        PreviewLesson(lesson)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.btn_close)) }
        }
    )
}

@Composable
private fun PreviewLesson(lesson: LessonEntity) {
    val weekday = when (lesson.dayOfWeek) {
        1 -> R.string.weekday_monday
        2 -> R.string.weekday_tuesday
        3 -> R.string.weekday_wednesday
        4 -> R.string.weekday_thursday
        5 -> R.string.weekday_friday
        else -> return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(
                R.string.label_legacy_timetable_slot,
                stringResource(weekday),
                lesson.slotIndex + 1
            ),
            style = MaterialTheme.typography.titleSmall
        )
        when (lesson.mode) {
            LessonMode.WEEKLY -> PreviewLessonVariant(
                label = stringResource(R.string.label_weekly),
                subject = lesson.weeklySubject,
                teacher = lesson.weeklyTeacher,
                location = lesson.weeklyLocation
            )
            LessonMode.ALTERNATING -> {
                PreviewLessonVariant(
                    label = stringResource(R.string.label_day_a),
                    subject = lesson.aSubject,
                    teacher = lesson.aTeacher,
                    location = lesson.aLocation
                )
                Spacer(Modifier.height(4.dp))
                PreviewLessonVariant(
                    label = stringResource(R.string.label_day_b),
                    subject = lesson.bSubject,
                    teacher = lesson.bTeacher,
                    location = lesson.bLocation
                )
            }
        }
    }
}

@Composable
private fun PreviewLessonVariant(
    label: String,
    subject: String,
    teacher: String,
    location: String?
) {
    val visibleSubject = if (subject.isBlank()) {
        stringResource(R.string.placeholder_not_set)
    } else {
        subject
    }
    Text(
        stringResource(
            R.string.label_legacy_timetable_subject,
            label,
            visibleSubject
        ),
        style = MaterialTheme.typography.bodyMedium
    )
    if (teacher.isNotBlank()) {
        Text(
            stringResource(R.string.lesson_start_notification_teacher_summary, teacher),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (!location.isNullOrBlank()) {
        Text(
            stringResource(R.string.lesson_start_notification_location_summary, location),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun LessonEntity.hasPreviewContent(): Boolean =
    mode != LessonMode.WEEKLY ||
        weeklySubject.isNotBlank() || weeklyTeacher.isNotBlank() || !weeklyLocation.isNullOrBlank() ||
        aSubject.isNotBlank() || aTeacher.isNotBlank() || !aLocation.isNullOrBlank() ||
        bSubject.isNotBlank() || bTeacher.isNotBlank() || !bLocation.isNullOrBlank()
