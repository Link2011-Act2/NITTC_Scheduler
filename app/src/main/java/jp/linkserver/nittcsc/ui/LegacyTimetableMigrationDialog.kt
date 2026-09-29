package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
    periodsPerDay: Int,
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
            periodsPerDay = periodsPerDay,
            onClose = { showPreview = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.dialog_legacy_timetable_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_legacy_timetable_message, academicYear))
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = { select(TimetableTerm.FIRST) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.btn_legacy_timetable_first),
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
                Button(
                    onClick = { select(TimetableTerm.SECOND) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.btn_legacy_timetable_second),
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    )
}

@Composable
private fun LegacyTimetablePreviewDialog(
    academicYear: Int,
    lessons: List<LessonEntity>,
    periodsPerDay: Int,
    onClose: () -> Unit
) {
    val lessonsByCell = remember(lessons) {
        lessons.associateBy { it.dayOfWeek to it.slotIndex }
    }
    val slotCount = periodsPerDay.coerceIn(1, 12)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.title_legacy_timetable_preview, academicYear)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (lessons.none { it.hasPreviewContent() }) {
                    Text(stringResource(R.string.msg_legacy_timetable_preview_empty))
                }
                for (dayOfWeek in 1..5) {
                    PreviewDayTable(dayOfWeek, slotCount, lessonsByCell)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.btn_close)) }
        }
    )
}

@Composable
private fun PreviewDayTable(
    dayOfWeek: Int,
    slotCount: Int,
    lessonsByCell: Map<Pair<Int, Int>, LessonEntity>
) {
    val weekday = when (dayOfWeek) {
        1 -> R.string.weekday_monday
        2 -> R.string.weekday_tuesday
        3 -> R.string.weekday_wednesday
        4 -> R.string.weekday_thursday
        5 -> R.string.weekday_friday
        else -> return
    }
    Column {
        Text(
            stringResource(
                R.string.label_legacy_timetable_weekday,
                stringResource(weekday)
            ),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            PreviewTableCell(Modifier.width(40.dp).fillMaxHeight(), header = true) {
                Text(stringResource(R.string.unit_period), textAlign = TextAlign.Center)
            }
            PreviewTableCell(Modifier.weight(1f).fillMaxHeight(), header = true) {
                Text(stringResource(R.string.label_day_a), textAlign = TextAlign.Center)
            }
            PreviewTableCell(Modifier.weight(1f).fillMaxHeight(), header = true) {
                Text(stringResource(R.string.label_day_b), textAlign = TextAlign.Center)
            }
        }
        for (slotIndex in 0 until slotCount) {
            val lesson = lessonsByCell[dayOfWeek to slotIndex]
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                PreviewTableCell(Modifier.width(40.dp).fillMaxHeight()) {
                    Text((slotIndex + 1).toString(), textAlign = TextAlign.Center)
                }
                if (lesson?.mode == LessonMode.WEEKLY) {
                    PreviewTableCell(Modifier.weight(2f).fillMaxHeight()) {
                        Text(stringResource(R.string.label_weekly), style = MaterialTheme.typography.labelSmall)
                        PreviewLessonContent(
                            lesson.weeklySubject, lesson.weeklyTeacher, lesson.weeklyLocation
                        )
                    }
                } else {
                    PreviewTableCell(Modifier.weight(1f).fillMaxHeight()) {
                        PreviewLessonContent(
                            lesson?.aSubject.orEmpty(), lesson?.aTeacher.orEmpty(), lesson?.aLocation
                        )
                    }
                    PreviewTableCell(Modifier.weight(1f).fillMaxHeight()) {
                        PreviewLessonContent(
                            lesson?.bSubject.orEmpty(), lesson?.bTeacher.orEmpty(), lesson?.bLocation
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTableCell(
    modifier: Modifier,
    header: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .background(if (header) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = if (header) Alignment.CenterHorizontally else Alignment.Start
    ) {
        content()
    }
}

@Composable
private fun PreviewLessonContent(
    subject: String,
    teacher: String,
    location: String?
) {
    val visibleSubject = if (subject.isBlank()) {
        stringResource(R.string.placeholder_not_set)
    } else {
        subject
    }
    Text(visibleSubject, style = MaterialTheme.typography.bodySmall)
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
