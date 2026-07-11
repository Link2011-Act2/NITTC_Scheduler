package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.ExamDayScheduleEntity
import jp.linkserver.nittcsc.data.ExamLessonEntity
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.logic.generateClassSlots
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ExamPeriod(
    val label: HolidaySpecialLabel,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dates: List<LocalDate>
)

fun buildExamPeriods(dayTypes: Collection<DayTypeEntity>): List<ExamPeriod> {
    val examDays = dayTypes
        .filter { it.holidaySpecialLabel == HolidaySpecialLabel.MIDTERM || it.holidaySpecialLabel == HolidaySpecialLabel.FINAL }
        .sortedBy { it.date }
    if (examDays.isEmpty()) return emptyList()

    val result = mutableListOf<ExamPeriod>()
    var currentLabel = examDays.first().holidaySpecialLabel!!
    var currentDates = mutableListOf(examDays.first().date)
    examDays.drop(1).forEach { day ->
        val label = day.holidaySpecialLabel!!
        val continuesCurrent = label == currentLabel &&
            gapContainsOnlyWeekends(currentDates.last(), day.date)
        if (continuesCurrent) {
            currentDates += day.date
        } else {
            result += ExamPeriod(currentLabel, currentDates.first(), currentDates.last(), currentDates.toList())
            currentLabel = label
            currentDates = mutableListOf(day.date)
        }
    }
    result += ExamPeriod(currentLabel, currentDates.first(), currentDates.last(), currentDates.toList())
    return result
}

private fun gapContainsOnlyWeekends(previousDate: LocalDate, nextDate: LocalDate): Boolean {
    if (!nextDate.isAfter(previousDate)) return false
    var date = previousDate.plusDays(1)
    while (date.isBefore(nextDate)) {
        if (date.dayOfWeek.value < 6) return false
        date = date.plusDays(1)
    }
    return true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamTimetablePeriodListScreen(
    periods: List<ExamPeriod>,
    configuredDates: Set<LocalDate>,
    examNames: Map<LocalDate, String> = emptyMap(),
    onBack: () -> Unit,
    onOpenPeriod: (ExamPeriod) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_exam_timetable_periods)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                Text(
                    text = stringResource(R.string.desc_exam_timetable_periods),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (periods.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.msg_exam_timetable_no_periods),
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(periods, key = { "${it.label}-${it.startDate}" }) { period ->
                    val configuredCount = period.dates.count(configuredDates::contains)
                    val examName = period.dates
                        .firstNotNullOfOrNull { date -> examNames[date]?.trim()?.takeIf { it.isNotBlank() } }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPeriod(period) },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = examName ?: stringResource(examPeriodTitleRes(period.label)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(
                                        R.string.label_exam_period_range,
                                        period.startDate.format(examDateFormatter),
                                        period.endDate.format(examDateFormatter)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(
                                        R.string.label_exam_period_progress,
                                        configuredCount,
                                        period.dates.size
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (configuredCount == period.dates.size) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

private data class ExamSlotDraft(
    val slotIndex: Int,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val teacher: String,
    val location: String,
    val memo: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamTimetableEditorScreen(
    period: ExamPeriod,
    settings: SettingsEntity,
    existingSchedules: Map<LocalDate, ExamDayScheduleEntity>,
    existingLessons: Map<Pair<LocalDate, Int>, ExamLessonEntity>,
    onBack: () -> Unit,
    onSave: (List<ExamDayScheduleEntity>, List<ExamLessonEntity>) -> Unit
) {
    val defaultExamName = stringResource(examPeriodTitleRes(period.label))
    val initialExamName = remember(period, existingSchedules) {
        period.dates.firstNotNullOfOrNull { date ->
            existingSchedules[date]?.examName?.trim()?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }
    var examName by rememberSaveable(period.startDate, initialExamName) {
        mutableStateOf(initialExamName)
    }
    var showExamNameDialog by rememberSaveable { mutableStateOf(false) }
    var examNameDraft by rememberSaveable { mutableStateOf("") }
    val initialDrafts = remember(period, settings, existingSchedules, existingLessons) {
        period.dates.associateWith { date ->
            val saved = existingLessons.values
                .filter { it.date == date }
                .sortedBy { it.slotIndex }
            if (saved.isNotEmpty()) {
                saved.map { lesson ->
                    ExamSlotDraft(
                        slotIndex = lesson.slotIndex,
                        start = LocalTime.of(lesson.startHour, lesson.startMinute),
                        end = LocalTime.of(lesson.endHour, lesson.endMinute),
                        subject = lesson.subject,
                        teacher = lesson.teacher,
                        location = lesson.location,
                        memo = lesson.memo
                    )
                }
            } else {
                generateClassSlots(
                    periodsPerDay = settings.examPeriodsPerDay,
                    periodDurationMin = settings.examPeriodDurationMin,
                    breakBetweenPeriodsMin = settings.examBreakBetweenPeriodsMin,
                    lunchBreakMin = settings.examLunchBreakMin,
                    firstPeriodStartHour = settings.examFirstPeriodStartHour,
                    firstPeriodStartMinute = settings.examFirstPeriodStartMinute,
                    useKosenMode = false,
                    lunchAfterPeriod = settings.examLunchAfterPeriod
                ).map { slot ->
                    ExamSlotDraft(slot.index, slot.start, slot.end, "", "", "", "")
                }
            }
        }
    }
    var draftsByDate by remember(period, initialDrafts) { mutableStateOf(initialDrafts) }
    var selectedDateEpochDay by rememberSaveable(period.startDate) {
        mutableStateOf(period.startDate.toEpochDay())
    }
    val selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay).takeIf(period.dates::contains)
        ?: period.startDate
    val selectedDrafts = draftsByDate[selectedDate].orEmpty()
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    )

    fun updateDraft(slotIndex: Int, transform: (ExamSlotDraft) -> ExamSlotDraft) {
        draftsByDate = draftsByDate.toMutableMap().also { map ->
            map[selectedDate] = selectedDrafts.map { draft ->
                if (draft.slotIndex == slotIndex) transform(draft) else draft
            }
        }
    }

    fun saveAll() {
        val now = System.currentTimeMillis()
        val schedules = period.dates.map { date ->
            val existing = existingSchedules[date]
            ExamDayScheduleEntity(
                date = date,
                arrivalHour = existing?.arrivalHour ?: settings.examArrivalHour,
                arrivalMinute = existing?.arrivalMinute ?: settings.examArrivalMinute,
                examName = examName.trim(),
                updatedAt = now
            )
        }
        val lessons = draftsByDate.flatMap { (date, drafts) ->
            drafts.map { draft ->
                ExamLessonEntity(
                    date = date,
                    slotIndex = draft.slotIndex,
                    startHour = draft.start.hour,
                    startMinute = draft.start.minute,
                    endHour = draft.end.hour,
                    endMinute = draft.end.minute,
                    subject = draft.subject.trim(),
                    teacher = draft.teacher.trim(),
                    location = draft.location.trim(),
                    memo = draft.memo.trim(),
                    updatedAt = now
                )
            }
        }
        onSave(schedules, lessons)
    }

    if (showExamNameDialog) {
        AlertDialog(
            onDismissRequest = { showExamNameDialog = false },
            title = { Text(stringResource(R.string.dialog_edit_exam_name_title)) },
            text = {
                OutlinedTextField(
                    value = examNameDraft,
                    onValueChange = { examNameDraft = it },
                    label = { Text(stringResource(R.string.label_exam_name)) },
                    placeholder = { Text(defaultExamName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        examName = examNameDraft.trim()
                        showExamNameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExamNameDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = examName.trim().ifBlank { defaultExamName },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(
                            onClick = {
                                examNameDraft = examName
                                showExamNameDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.dialog_edit_exam_name_title)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = ::saveAll) {
                        Text(stringResource(R.string.btn_save))
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    period.dates.forEach { date ->
                        val selected = date == selectedDate
                        Surface(
                            modifier = Modifier.clickable {
                                selectedDateEpochDay = date.toEpochDay()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Text(
                                text = date.format(examTabDateFormatter),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            item {
                val schedule = existingSchedules[selectedDate]
                Text(
                    text = stringResource(
                        R.string.label_exam_editor_arrival,
                        schedule?.arrivalHour ?: settings.examArrivalHour,
                        schedule?.arrivalMinute ?: settings.examArrivalMinute
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(selectedDrafts, key = { it.slotIndex }) { draft ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.label_exam_period_number, draft.slotIndex + 1),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "%02d:%02d–%02d:%02d".format(
                                    draft.start.hour,
                                    draft.start.minute,
                                    draft.end.hour,
                                    draft.end.minute
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = stringResource(R.string.label_exam_subject),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextField(
                            value = draft.subject,
                            onValueChange = { value -> updateDraft(draft.slotIndex) { it.copy(subject = value) } },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.label_exam_no_test),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            textStyle = MaterialTheme.typography.titleMedium,
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextField(
                                value = draft.teacher,
                                onValueChange = { value -> updateDraft(draft.slotIndex) { it.copy(teacher = value) } },
                                placeholder = { Text(stringResource(R.string.label_exam_teacher)) },
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                            TextField(
                                value = draft.location,
                                onValueChange = { value -> updateDraft(draft.slotIndex) { it.copy(location = value) } },
                                placeholder = { Text(stringResource(R.string.label_exam_location)) },
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.label_exam_memo),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextField(
                            value = draft.memo,
                            onValueChange = { value -> updateDraft(draft.slotIndex) { it.copy(memo = value) } },
                            placeholder = { Text(stringResource(R.string.placeholder_not_set)) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            minLines = 2,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun examPeriodTitleRes(label: HolidaySpecialLabel): Int = when (label) {
    HolidaySpecialLabel.MIDTERM -> R.string.holiday_label_midterm
    HolidaySpecialLabel.FINAL -> R.string.holiday_label_final
    else -> R.string.title_exam_timetable_periods
}

private val examDateFormatter = DateTimeFormatter.ofPattern("M/d")
private val examTabDateFormatter = DateTimeFormatter.ofPattern("M/d(E)")
