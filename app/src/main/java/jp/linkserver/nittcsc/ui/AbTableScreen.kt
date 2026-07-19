package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import jp.linkserver.nittcsc.data.LongBreakEntity
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val abDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

private data class DayTypeVisual(
    val container: Color,
    val content: Color
)

@Composable
internal fun AbTableScreen(
    modifier: Modifier,
    state: SchedulerUiState,
    onSaveDayTypes: (List<LocalDate>, DayType) -> Unit,
    onSaveLessonOverride: (LocalDate, Int, DayType) -> Unit,
    onClearLessonOverride: (LocalDate) -> Unit,
    onUpdateHolidaySpecialLabel: (LocalDate, HolidaySpecialLabel?) -> Unit,
    onResetFiscalYear: () -> Unit,
    onUpdateTerm: (LocalDate, LocalDate) -> Unit,
    onSaveBreak: (Long?, String, LocalDate, LocalDate) -> Unit,
    onDeleteBreak: (LongBreakEntity) -> Unit,
    onOpenExamTimetables: () -> Unit,
    dayTypeForDate: (LocalDate) -> DayType,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?
) {
    val settings = state.settings
    if (settings == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.label_loading))
        }
        return
    }

    var termStart by remember(settings.termStart) { mutableStateOf(settings.termStart) }
    var termEnd by remember(settings.termEnd) { mutableStateOf(settings.termEnd) }
    var showBreakDialog by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val weeks = remember(settings.termStart, settings.termEnd) {
        buildWeekRows(settings.termStart, settings.termEnd)
    }
    val currentWeeks = remember(weeks) { weeks.filter { !it.weekEnd.isBefore(today) } }
    val pastWeeks = remember(weeks) { weeks.filter { it.weekEnd.isBefore(today) } }
    val displayedWeeks = remember(currentWeeks, pastWeeks) {
        currentWeeks.map { DisplayWeekRow(it, false) } + pastWeeks.map { DisplayWeekRow(it, true) }
    }

    // ドラッグ状態（AbTableGrid から AbTableScreen に移動）
    val haptic = LocalHapticFeedback.current
    val dateBounds = remember(displayedWeeks) { HashMap<LocalDate, Rect>() }
    var dragStartDate by remember(displayedWeeks) { mutableStateOf<LocalDate?>(null) }
    var dragCurrentDate by remember(displayedWeeks) { mutableStateOf<LocalDate?>(null) }
    var dragTargetDayType by remember(displayedWeeks) { mutableStateOf<DayType?>(null) }
    var overrideEditingDate by remember(displayedWeeks) { mutableStateOf<LocalDate?>(null) }

    val displayDates = remember(displayedWeeks, settings.termStart, settings.termEnd) {
        displayedWeeks.flatMap { it.row.days }.filter { it in settings.termStart..settings.termEnd }
    }
    val previewDates = remember(displayDates, dragStartDate, dragCurrentDate) {
        val s = dragStartDate
        val c = dragCurrentDate ?: s
        if (s == null || c == null) emptySet()
        else {
            val si = displayDates.indexOf(s)
            val ei = displayDates.indexOf(c)
            if (si == -1 || ei == -1) emptySet()
            else displayDates.subList(minOf(si, ei), maxOf(si, ei) + 1).toSet()
        }
    }

    fun dateAtRoot(rootOffset: Offset): LocalDate? =
        dateBounds.entries.firstOrNull { (_, rect) -> rect.contains(rootOffset) }?.key

    fun resetDragState() { dragStartDate = null; dragCurrentDate = null; dragTargetDayType = null }

    fun dragSelectedDates(): List<LocalDate> {
        val s = dragStartDate ?: return emptyList()
        val c = dragCurrentDate ?: s
        val si = displayDates.indexOf(s)
        val ei = displayDates.indexOf(c)
        if (si == -1 || ei == -1) return emptyList()
        return displayDates.subList(minOf(si, ei), maxOf(si, ei) + 1)
    }

    fun commitDragRange() {
        val t = dragTargetDayType; val d = dragSelectedDates()
        if (t != null && d.isNotEmpty()) onSaveDayTypes(d, t)
        resetDragState()
    }

    AdaptiveContentPane(
        modifier = modifier,
        maxWidth = CalendarContentMaxWidth
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.label_long_holidays), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Button(onClick = { showBreakDialog = true }) { Text(stringResource(R.string.btn_add)) }
                    }

                    if (state.longBreaks.isEmpty()) {
                        Text(stringResource(R.string.label_not_registered), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.longBreaks.forEach { longBreak ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${longBreak.name}: ${longBreak.startDate.format(abDateFormatter)} - ${longBreak.endDate.format(abDateFormatter)}",
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(onClick = { onDeleteBreak(longBreak) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.btn_delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            DayTypeLegend(
                onOpenExamTimetables = onOpenExamTimetables
            )
        }
        item { WeekHeader() }
        items(displayedWeeks, key = { it.row.weekStart }) { displayWeek ->
            WeekRow(
                row = displayWeek.row,
                settingsStart = settings.termStart,
                settingsEnd = settings.termEnd,
                dayTypeForDate = dayTypeForDate,
                dayTypeEntityForDate = dayTypeEntityForDate,
                onSaveDayTypes = onSaveDayTypes,
                onOpenLessonOverride = { date ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    overrideEditingDate = date
                },
                isPast = displayWeek.isPast,
                previewDates = previewDates,
                previewDayType = dragTargetDayType,
                onUpdateDayBounds = { date, rect -> dateBounds[date] = rect },
                onDragStartRoot = { rootOffset ->
                    val startDate = dateAtRoot(rootOffset) ?: return@WeekRow
                    dragStartDate = startDate
                    dragCurrentDate = startDate
                    dragTargetDayType = nextDayType(dayTypeForDate(startDate))
                },
                onDragRoot = { rootOffset ->
                    if (dragStartDate == null) return@WeekRow
                    dateAtRoot(rootOffset)?.let { date ->
                        if (date != dragCurrentDate) {
                            dragCurrentDate = date
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
                onDragEnd = { commitDragRange() },
                onDragCancel = { resetDragState() }
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.label_period_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.label_term_range, settings.termStart.format(abDateFormatter), settings.termEnd.format(abDateFormatter)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DatePickRow(
                        label = stringResource(R.string.label_start_date_plain),
                        date = termStart,
                        showWeekdayOnDates = settings.showWeekdayOnDates,
                        onDateChange = { termStart = it }
                    )
                    DatePickRow(
                        label = stringResource(R.string.label_end_date_plain),
                        date = termEnd,
                        showWeekdayOnDates = settings.showWeekdayOnDates,
                        onDateChange = { termEnd = it }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onUpdateTerm(termStart, termEnd) }) {
                            Icon(Icons.Filled.Event, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_apply_to_period))
                        }
                        OutlinedButton(onClick = onResetFiscalYear) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_back_to_original))
                        }
                    }
                }
            }
        }
        }
    }

    if (showBreakDialog) {
        AddBreakDialog(
            showWeekdayOnDates = settings.showWeekdayOnDates,
            onDismiss = { showBreakDialog = false },
            onSave = { name, start, end ->
                onSaveBreak(null, name, start, end)
                if (name.isNotBlank()) {
                    showBreakDialog = false
                }
            }
        )
    }

    overrideEditingDate?.let { date ->
        val dayTypeEntity = dayTypeEntityForDate(date)
        LessonOverrideDialog(
            date = date,
            currentDayType = dayTypeForDate(date),
            currentOverrideDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek,
            currentOverrideDayType = dayTypeEntity?.overrideLessonDayType,
            currentHolidaySpecialLabel = dayTypeEntity?.holidaySpecialLabel,
            showDayTypeSelector = false,
            onDismiss = { overrideEditingDate = null },
            onApply = { dayOfWeek, dayType, holidayLabel ->
                if (dayOfWeek == null) {
                    onClearLessonOverride(date)
                } else {
                    onSaveLessonOverride(date, dayOfWeek, dayType)
                }
                onUpdateHolidaySpecialLabel(date, holidayLabel)
                overrideEditingDate = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickRow(
    label: String,
    date: LocalDate,
    showWeekdayOnDates: Boolean = false,
    onDateChange: (LocalDate) -> Unit
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86400000L)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(LocalDate.ofEpochDay(it / 86400000L)) }
                    showPicker = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        ) { DatePicker(state = state) }
    }
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label)
            Text(formatDateForDisplay(date, showWeekdayOnDates), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DayTypeLegend(
    onOpenExamTimetables: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.legend_ab_toggle),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.legend_ab_override_long_press),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayChip(stringResource(R.string.daytype_a), dayTypeVisual(DayType.A))
                DayChip(stringResource(R.string.daytype_b), dayTypeVisual(DayType.B))
                DayChip(stringResource(R.string.daytype_holiday), dayTypeVisual(DayType.HOLIDAY))
            }
            OutlinedButton(
                onClick = onOpenExamTimetables,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_create_exam_timetable))
            }
        }
    }
}

@Composable
private fun DayChip(label: String, visual: DayTypeVisual) {
    Box(
        modifier = Modifier
            .background(visual.container, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = visual.content, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeekHeader() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_period),
                modifier = Modifier.width(110.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            listOf(
                R.string.weekday_monday,
                R.string.weekday_tuesday,
                R.string.weekday_wednesday,
                R.string.weekday_thursday,
                R.string.weekday_friday
            ).forEach { dayRes ->
                Text(
                    text = stringResource(dayRes),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekRow(
    row: WeekRow,
    settingsStart: LocalDate,
    settingsEnd: LocalDate,
    dayTypeForDate: (LocalDate) -> DayType,
    dayTypeEntityForDate: (LocalDate) -> DayTypeEntity?,
    onSaveDayTypes: (List<LocalDate>, DayType) -> Unit,
    onOpenLessonOverride: (LocalDate) -> Unit,
    isPast: Boolean = false,
    previewDates: Set<LocalDate> = emptySet(),
    previewDayType: DayType? = null,
    onUpdateDayBounds: (LocalDate, Rect) -> Unit = { _, _ -> },
    onDragStartRoot: (Offset) -> Unit = {},
    onDragRoot: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {}
) {
    val rowBoundsRef = remember { arrayOfNulls<Rect>(1) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast) 0.38f else 1f)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${row.weekStart.monthValue}/${row.weekStart.dayOfMonth}-${row.weekEnd.monthValue}/${row.weekEnd.dayOfMonth}",
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { rowBoundsRef[0] = it.boundsInRoot() }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { localOffset ->
                            val rb = rowBoundsRef[0] ?: return@detectDragGestures
                            onDragStartRoot(Offset(rb.left + localOffset.x, rb.top + localOffset.y))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val rb = rowBoundsRef[0] ?: return@detectDragGestures
                            onDragRoot(Offset(rb.left + change.position.x, rb.top + change.position.y))
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel
                    )
                }
        ) {

        row.days.forEach { date ->
            if (date < settingsStart || date > settingsEnd) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("-") }
            } else {
                val previewActive = previewDayType != null && date in previewDates
                val shownDayType = if (previewActive) previewDayType else dayTypeForDate(date)
                val visual = dayTypeVisual(shownDayType)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .onGloballyPositioned { onUpdateDayBounds(date, it.boundsInRoot()) }
                        .then(
                            if (previewActive) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            } else {
                                Modifier
                            }
                        )
                        .background(visual.container, RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = { onSaveDayTypes(listOf(date), nextDayType(dayTypeForDate(date))) },
                            onLongClick = { onOpenLessonOverride(date) }
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val label = if (previewActive) {
                        stringResource(dayTypeRes(shownDayType))
                    } else {
                        dayTypeDisplayText(
                            shownDayType,
                            dayTypeEntityForDate(date)?.overrideLessonDayOfWeek,
                            dayTypeEntityForDate(date)?.holidaySpecialLabel
                        )
                    }
                    Text(label, color = visual.content, fontWeight = FontWeight.Bold)
                }
            }
        }
        } // inner Row (drag area)
    } // outer Row
}

private fun nextDayType(current: DayType): DayType = when (current) {
    DayType.A -> DayType.B
    DayType.B -> DayType.HOLIDAY
    DayType.HOLIDAY -> DayType.A
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBreakDialog(
    showWeekdayOnDates: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, LocalDate) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = start.toEpochDay() * 86400000L)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { start = LocalDate.ofEpochDay(it / 86400000L) }
                    showStartPicker = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.btn_cancel)) } }
        ) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = end.toEpochDay() * 86400000L)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { end = LocalDate.ofEpochDay(it / 86400000L) }
                    showEndPicker = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.btn_cancel)) } }
        ) { DatePicker(state = state) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_add_break)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_break_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { showStartPicker = true }) {
                    Text(stringResource(R.string.label_start_date, formatDateForDisplay(start, showWeekdayOnDates)))
                }
                OutlinedButton(onClick = { showEndPicker = true }) {
                    Text(stringResource(R.string.label_end_date, formatDateForDisplay(end, showWeekdayOnDates)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, start, end) }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

private data class WeekRow(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val days: List<LocalDate>
)

private data class DisplayWeekRow(
    val row: WeekRow,
    val isPast: Boolean
)

private fun buildWeekRows(startDate: LocalDate, endDate: LocalDate): List<WeekRow> {
    val firstMonday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastFriday = endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))

    val rows = mutableListOf<WeekRow>()
    var cursor = firstMonday
    while (!cursor.isAfter(lastFriday)) {
        val days = (0L..4L).map { cursor.plusDays(it) }
        rows += WeekRow(weekStart = days.first(), weekEnd = days.last(), days = days)
        cursor = cursor.plusWeeks(1)
    }
    return rows
}

@Composable
private fun dayTypeVisual(dayType: DayType): DayTypeVisual {
    val colorScheme = MaterialTheme.colorScheme
    return when (dayType) {
        DayType.A -> DayTypeVisual(colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
        DayType.B -> DayTypeVisual(colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
        DayType.HOLIDAY -> DayTypeVisual(colorScheme.surfaceVariant, colorScheme.onSurfaceVariant)
    }
}
