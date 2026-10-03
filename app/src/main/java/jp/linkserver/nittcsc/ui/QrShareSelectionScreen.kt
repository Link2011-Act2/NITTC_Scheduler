package jp.linkserver.nittcsc.ui

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.QrShareSection
import jp.linkserver.nittcsc.data.QrShareSelection
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.timetableTermForDate
import jp.linkserver.nittcsc.logic.QrDayTypesScope
import jp.linkserver.nittcsc.logic.qrDayTypesRange
import jp.linkserver.nittcsc.logic.qrShareAcademicYears
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import java.time.LocalDate

@StringRes
internal fun QrShareSection.labelRes(): Int = when (this) {
    QrShareSection.FIRST -> R.string.qr_first
    QrShareSection.SECOND -> R.string.qr_second
    QrShareSection.CANCELLATIONS -> R.string.qr_cancellations
    QrShareSection.CHANGES -> R.string.qr_changes
    QrShareSection.DAY_TYPES -> R.string.qr_days
    QrShareSection.NOTES -> R.string.qr_notes
    QrShareSection.PLANS -> R.string.qr_plans
    QrShareSection.TASKS -> R.string.qr_tasks
    QrShareSection.EXAMS -> R.string.qr_exams
}

@StringRes
internal fun QrDayTypesScope.labelRes(): Int = when (this) {
    QrDayTypesScope.YEAR -> R.string.qr_days_year
    QrDayTypesScope.FIRST -> R.string.qr_days_first
    QrDayTypesScope.SECOND -> R.string.qr_days_second
}

private data class QrPickItem(
    val key: String,
    val title: String,
    val date: LocalDate,
    val description: String? = null,
    val subject: String? = null,
    val teacher: String? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val isCompleted: Boolean = false,
    val priority: Int = 0,
    val slot: Int? = null
)

private data class QrShareChoice(val section: QrShareSection, val daysScope: QrDayTypesScope? = null)

private val qrShareChoices = listOf(
    QrShareChoice(QrShareSection.FIRST),
    QrShareChoice(QrShareSection.DAY_TYPES, QrDayTypesScope.FIRST),
    QrShareChoice(QrShareSection.SECOND),
    QrShareChoice(QrShareSection.DAY_TYPES, QrDayTypesScope.SECOND),
    QrShareChoice(QrShareSection.CANCELLATIONS),
    QrShareChoice(QrShareSection.NOTES),
    QrShareChoice(QrShareSection.PLANS),
    QrShareChoice(QrShareSection.TASKS),
    QrShareChoice(QrShareSection.EXAMS)
)

@Composable
internal fun QrShareSelectionScreen(
    state: SchedulerUiState,
    busy: Boolean,
    picker: QrShareSection?,
    onPick: (QrShareSection) -> Unit,
    onPickDone: () -> Unit,
    modifier: Modifier = Modifier,
    onGenerate: (QrShareSelection) -> Unit
) {
    val today = LocalDate.now()
    val settings = state.settings
    val currentYear = settings?.activeAcademicYear?.takeIf { it > 0 } ?: academicYearForDate(today)
    var year by remember(currentYear) { mutableIntStateOf(currentYear) }
    val years = remember(state.lessons, state.examDaySchedules, state.examLessons, state.dayTypeEntities, currentYear) {
        qrShareAcademicYears(currentYear, state.lessons.keys, state.examDaySchedules.keys,
            state.examLessons.keys.map { it.first }, state.dayTypeEntities.values)
    }
    LaunchedEffect(years) { if (year !in years) year = currentYear }
    val currentTerm = timetableTermForDate(today, settings?.enableSemesterTimetables ?: true, settings?.secondTermStartMonth ?: 10, settings?.secondTermStartDay ?: 1)
    var selected by remember { mutableStateOf(setOf(if (currentTerm.name == "FIRST") QrShareSection.FIRST else QrShareSection.SECOND,
        QrShareSection.CANCELLATIONS, QrShareSection.CHANGES)) }
    var selectedDays by remember { mutableStateOf(setOf(if (currentTerm == TimetableTerm.FIRST) QrDayTypesScope.FIRST else QrDayTypesScope.SECOND)) }
    var selectedNotes by remember { mutableStateOf(emptySet<String>()) }
    var selectedPlans by remember { mutableStateOf(emptySet<String>()) }
    var selectedTasks by remember { mutableStateOf(emptySet<String>()) }
    val noteItems = remember(state.lessonNotes) { state.lessonNotes.map { QrPickItem("${it.date}/${it.slotIndex}", it.text, it.date, slot = it.slotIndex) } }
    val planItems = remember(state.plans) { state.plans.map { QrPickItem(it.id.toString(), it.title, it.dueDate, it.description, it.subject, it.teacher, it.dueHour, it.dueMinute, it.isCompleted, it.priority) } }
    val taskItems = remember(state.tasks) { state.tasks.map { QrPickItem(it.id.toString(), it.title, it.dueDate, it.description, it.subject, it.teacher, it.dueHour, it.dueMinute, it.isCompleted, it.priority) } }
    fun keys(section: QrShareSection) = when (section) {
        QrShareSection.NOTES -> selectedNotes
        QrShareSection.PLANS -> selectedPlans
        QrShareSection.TASKS -> selectedTasks
        else -> emptySet()
    }
    if (picker != null) {
        val entries = when (picker) { QrShareSection.NOTES -> noteItems; QrShareSection.PLANS -> planItems; else -> taskItems }
        fun finishPicker() {
            if (keys(picker).isEmpty()) selected -= picker
            onPickDone()
        }
        BackHandler(onBack = ::finishPicker)
        QrShareItemPickerScreen(picker, entries, keys(picker), state.settings?.showWeekdayOnDates ?: false, onChange = { choice ->
            when (picker) { QrShareSection.NOTES -> selectedNotes = choice; QrShareSection.PLANS -> selectedPlans = choice; else -> selectedTasks = choice }
        }, onDone = ::finishPicker, modifier = modifier)
        return
    }
    val effectiveSections = selected.filter { section ->
        section !in setOf(QrShareSection.NOTES, QrShareSection.PLANS, QrShareSection.TASKS) || keys(section).isNotEmpty()
    }.toSet() + if (selectedDays.isNotEmpty()) setOf(QrShareSection.DAY_TYPES) else emptySet()
    val daysScope = if (selectedDays.size == 2) QrDayTypesScope.YEAR else selectedDays.singleOrNull() ?: QrDayTypesScope.YEAR
    val valid = effectiveSections.isNotEmpty()
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.qr_select_description), modifier = Modifier.padding(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                years.forEach { option -> FilterChip(selected = year == option, onClick = { if (!busy) year = option }, label = { Text(stringResource(R.string.qr_image_year, option)) }) }
            }
        }
        items(qrShareChoices, key = { "${it.section.name}/${it.daysScope?.name.orEmpty()}" }) { choice ->
            val section = choice.section
            // 選択画面では一括操作し、転送データでは既存の2項目を維持する。
            val sections = if (section == QrShareSection.CANCELLATIONS)
                setOf(QrShareSection.CANCELLATIONS, QrShareSection.CHANGES) else setOf(section)
            val checked = choice.daysScope?.let { it in selectedDays } ?: effectiveSections.containsAll(sections)
            val personal = section in setOf(QrShareSection.NOTES, QrShareSection.PLANS, QrShareSection.TASKS)
            fun changeSelection(newChecked: Boolean) {
                val scope = choice.daysScope
                if (scope != null) selectedDays = if (newChecked) selectedDays + scope else selectedDays - scope
                else selected = if (newChecked) selected + sections else selected - sections
                if (personal && newChecked) onPick(section)
            }
            val interaction = if (personal) Modifier.clickable(enabled = !busy, role = Role.Button) {
                selected += section; onPick(section)
            } else Modifier.toggleable(value = checked, enabled = !busy, role = Role.Checkbox, onValueChange = ::changeSelection)
            Row(
                Modifier.fillMaxWidth().then(interaction).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = if (personal) ::changeSelection else null, enabled = !busy)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(stringResource(when (choice.daysScope) {
                        QrDayTypesScope.FIRST -> R.string.qr_first_days
                        QrDayTypesScope.SECOND -> R.string.qr_second_days
                        else -> if (section == QrShareSection.CANCELLATIONS) R.string.qr_cancellations_and_changes else section.labelRes()
                    }))
                    Text(stringResource(when (section) {
                        QrShareSection.DAY_TYPES -> R.string.qr_days_semester_scope
                        QrShareSection.FIRST, QrShareSection.SECOND -> R.string.qr_timetable_scope
                        QrShareSection.EXAMS -> R.string.qr_exams_scope
                        QrShareSection.NOTES, QrShareSection.PLANS, QrShareSection.TASKS -> R.string.qr_personal_scope
                        else -> R.string.qr_year_scope
                    }), style = MaterialTheme.typography.bodySmall)
                    choice.daysScope?.let { scope ->
                        val range = qrDayTypesRange(year, scope, settings?.secondTermStartMonth ?: 10, settings?.secondTermStartDay ?: 1)
                        Text(stringResource(R.string.qr_days_range, range.start.toString(), range.endInclusive.toString()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (personal) TextButton(onClick = { selected += section; onPick(section) }, enabled = !busy) {
                    Text(stringResource(R.string.qr_pick_count, keys(section).size))
                }
            }
        }
        item {
            Text(stringResource(R.string.qr_reminder_notice), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            Button(onClick = {
                onGenerate(QrShareSelection(
                    year, effectiveSections,
                    noteKeys = state.lessonNotes.filter { "${it.date}/${it.slotIndex}" in selectedNotes }.map { it.date to it.slotIndex }.toSet(),
                    planIds = state.plans.filter { it.id.toString() in selectedPlans }.map { it.id }.toSet(),
                    taskIds = state.tasks.filter { it.id.toString() in selectedTasks }.map { it.id }.toSet(),
                    dayTypesScope = daysScope
                ))
            }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.qr_generate))
            }
        }
    }
}

@Composable
private fun QrShareItemPickerScreen(
    section: QrShareSection,
    entries: List<QrPickItem>,
    selected: Set<String>,
    showWeekdayOnDates: Boolean,
    onChange: (Set<String>) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sorted = remember(entries, section) {
        if (section == QrShareSection.NOTES) entries.sortedWith(compareByDescending<QrPickItem> { it.date }.thenByDescending { it.slot ?: 0 })
        else entries.sortedWith(compareBy<QrPickItem> { it.date }.thenBy { it.hour ?: 23 }.thenBy { it.minute ?: 59 }.thenBy { it.title }.thenBy { it.key })
    }
    val incomplete = remember(sorted) { sorted.filterNot { it.isCompleted } }
    val completed = remember(sorted) { sorted.filter { it.isCompleted } }
    var completedExpanded by remember(section) { mutableStateOf(false) }
    val incompleteOnly = section != QrShareSection.NOTES && !completedExpanded && completed.isNotEmpty()
    val selectedCompletedCount = remember(completed, selected) { completed.count { it.key in selected } }
    val completedStateDescription = stringResource(if (completedExpanded) R.string.settings_expanded_state else R.string.settings_collapsed_state)
    Column(modifier.fillMaxSize()) {
        Text(stringResource(R.string.qr_selected_count, selected.size), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(modifier = Modifier.weight(1f), onClick = { onChange(selected + (if (incompleteOnly) incomplete else sorted).map { it.key }) }) {
                Text(stringResource(if (incompleteOnly) R.string.qr_select_incomplete_all else R.string.qr_select_all))
            }
            TextButton(modifier = Modifier.weight(1f), onClick = { onChange(emptySet()) }) { Text(stringResource(R.string.qr_select_none)) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entries.isEmpty()) item { Text(stringResource(R.string.qr_no_items)) }
            if (section == QrShareSection.NOTES) {
                items(sorted, key = { it.key }) { item ->
                    QrSharePickCard(item, item.key in selected, showWeekdayOnDates) { checked ->
                        onChange(if (checked) selected + item.key else selected - item.key)
                    }
                }
            } else {
                if (incomplete.isNotEmpty()) item { Text(stringResource(if (section == QrShareSection.TASKS) R.string.label_incomplete_tasks else R.string.label_incomplete_plans),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(incomplete, key = { it.key }) { item ->
                    QrSharePickCard(item, item.key in selected, showWeekdayOnDates) { checked ->
                        onChange(if (checked) selected + item.key else selected - item.key)
                    }
                }
                if (completed.isNotEmpty()) item(key = "completedHeader") {
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { completedExpanded = !completedExpanded }
                        .semantics { stateDescription = completedStateDescription }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(if (section == QrShareSection.TASKS) R.string.section_completed_tasks_count else R.string.section_completed_plans_count, completed.size),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (selectedCompletedCount > 0) Text(stringResource(R.string.qr_selected_count, selectedCompletedCount),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (completedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                }
                if (completedExpanded) items(completed, key = { it.key }) { item ->
                    QrSharePickCard(item, item.key in selected, showWeekdayOnDates) { checked ->
                        onChange(if (checked) selected + item.key else selected - item.key)
                    }
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.qr_done))
        }
    }
}

@Composable
private fun QrSharePickCard(item: QrPickItem, checked: Boolean, showWeekdayOnDates: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (item.isCompleted) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = null)
            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (!item.description.isNullOrBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val date = if (item.hour != null && item.minute != null)
                    formatDateTimeForDisplay(item.date, item.hour, item.minute, showWeekdayOnDates)
                    else formatDateForDisplay(item.date, showWeekdayOnDates)
                Text(if (item.slot != null) "$date · ${stringResource(R.string.qr_note_slot, item.slot + 1)}" else date,
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!item.subject.isNullOrBlank() || !item.teacher.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(listOfNotNull(item.subject?.takeIf { it.isNotBlank() }, item.teacher?.takeIf { it.isNotBlank() }).joinToString(" · "),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (item.priority != 0) Text(stringResource(if (item.priority > 0) R.string.label_task_priority_high else R.string.label_task_priority_low),
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
