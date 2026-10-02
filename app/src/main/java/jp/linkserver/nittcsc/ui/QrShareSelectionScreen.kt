package jp.linkserver.nittcsc.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.QrShareSection
import jp.linkserver.nittcsc.data.QrShareSelection
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.timetableTermForDate
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
}

private data class QrPickItem(val key: String, val title: String, val subtitle: String)

@Composable
internal fun QrShareSelectionScreen(state: SchedulerUiState, busy: Boolean, onGenerate: (QrShareSelection) -> Unit) {
    val today = LocalDate.now()
    val settings = state.settings
    var year by remember { mutableIntStateOf(settings?.activeAcademicYear?.takeIf { it > 0 } ?: academicYearForDate(today)) }
    val years = remember(state.lessons, year) { (state.lessons.keys.map { it.academicYear } + year).distinct().sortedDescending() }
    val currentTerm = timetableTermForDate(today, settings?.enableSemesterTimetables ?: true, settings?.secondTermStartMonth ?: 10, settings?.secondTermStartDay ?: 1)
    var selected by remember { mutableStateOf(setOf(if (currentTerm.name == "FIRST") QrShareSection.FIRST else QrShareSection.SECOND, QrShareSection.DAY_TYPES)) }
    var selectedNotes by remember { mutableStateOf(emptySet<String>()) }
    var selectedPlans by remember { mutableStateOf(emptySet<String>()) }
    var selectedTasks by remember { mutableStateOf(emptySet<String>()) }
    var picker by remember { mutableStateOf<QrShareSection?>(null) }
    val noteItems = remember(state.lessonNotes) { state.lessonNotes.map { QrPickItem("${it.date}/${it.slotIndex}", it.text, "${it.date} · ${it.slotIndex + 1}") } }
    val planItems = remember(state.plans) { state.plans.map { QrPickItem(it.id.toString(), it.title, "${it.dueDate} · ${it.subject}") } }
    val taskItems = remember(state.tasks) { state.tasks.map { QrPickItem(it.id.toString(), it.title, "${it.dueDate} · ${it.subject}") } }
    fun keys(section: QrShareSection) = when (section) {
        QrShareSection.NOTES -> selectedNotes
        QrShareSection.PLANS -> selectedPlans
        QrShareSection.TASKS -> selectedTasks
        else -> emptySet()
    }
    val valid = selected.isNotEmpty() && selected.all { section ->
        section !in setOf(QrShareSection.NOTES, QrShareSection.PLANS, QrShareSection.TASKS) || keys(section).isNotEmpty()
    }
    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.qr_select_description), modifier = Modifier.padding(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                years.forEach { option -> FilterChip(selected = year == option, onClick = { if (!busy) year = option }, label = { Text(stringResource(R.string.qr_image_year, option)) }) }
            }
        }
        items(QrShareSection.entries, key = { it.name }) { section ->
            val personal = section in setOf(QrShareSection.NOTES, QrShareSection.PLANS, QrShareSection.TASKS)
            Row(
                Modifier.fillMaxWidth().toggleable(value = section in selected, enabled = !busy, role = Role.Checkbox, onValueChange = { checked ->
                    selected = if (checked) selected + section else selected - section
                    if (personal && checked) picker = section
                }).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Checkbox(checked = section in selected, onCheckedChange = null, enabled = !busy)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(stringResource(section.labelRes()))
                    Text(stringResource(when (section) {
                        QrShareSection.DAY_TYPES -> R.string.qr_days_scope
                        QrShareSection.NOTES, QrShareSection.PLANS, QrShareSection.TASKS -> R.string.qr_personal_scope
                        else -> R.string.qr_year_scope
                    }), style = MaterialTheme.typography.bodySmall)
                }
                if (personal) TextButton(onClick = { selected += section; picker = section }, enabled = !busy) {
                    Text(stringResource(R.string.qr_pick_count, keys(section).size))
                }
            }
        }
        item {
            Text(stringResource(R.string.qr_reminder_notice), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            Button(onClick = {
                onGenerate(QrShareSelection(
                    year, selected,
                    noteKeys = state.lessonNotes.filter { "${it.date}/${it.slotIndex}" in selectedNotes }.map { it.date to it.slotIndex }.toSet(),
                    planIds = state.plans.filter { it.id.toString() in selectedPlans }.map { it.id }.toSet(),
                    taskIds = state.tasks.filter { it.id.toString() in selectedTasks }.map { it.id }.toSet()
                ))
            }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.qr_generate))
            }
        }
    }
    picker?.let { section ->
        val entries = when (section) { QrShareSection.NOTES -> noteItems; QrShareSection.PLANS -> planItems; else -> taskItems }
        QrShareItemPicker(section, entries, keys(section), onChange = { choice ->
            when (section) { QrShareSection.NOTES -> selectedNotes = choice; QrShareSection.PLANS -> selectedPlans = choice; else -> selectedTasks = choice }
        }, onDismiss = { picker = null })
    }
}

@Composable
private fun QrShareItemPicker(section: QrShareSection, entries: List<QrPickItem>, selected: Set<String>, onChange: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_pick_title, stringResource(section.labelRes()))) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onChange(entries.map { it.key }.toSet()) }) { Text(stringResource(R.string.qr_select_all)) }
                    TextButton(onClick = { onChange(emptySet()) }) { Text(stringResource(R.string.qr_select_none)) }
                }
                if (entries.isEmpty()) Text(stringResource(R.string.qr_no_items))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(entries, key = { it.key }) { entry ->
                        Row(Modifier.fillMaxWidth().toggleable(value = entry.key in selected, role = Role.Checkbox, onValueChange = { checked -> onChange(if (checked) selected + entry.key else selected - entry.key) }).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = entry.key in selected, onCheckedChange = null)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(entry.title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.qr_done)) } }
    )
}
