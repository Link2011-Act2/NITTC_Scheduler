package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.LessonNoteEntity
import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.data.QrShareSection
import jp.linkserver.nittcsc.logic.QrNoteConflict
import jp.linkserver.nittcsc.logic.QrShareMerge
import jp.linkserver.nittcsc.logic.qrDayTypesRange
import jp.linkserver.nittcsc.logic.overlaps
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState

internal fun LazyListScope.qrImportDetails(
    data: QrSharePayload, state: SchedulerUiState, merge: QrShareMerge?, localCounts: Map<QrShareSection, Int>
) {
    items(data.sections.sortedBy { it.ordinal }, key = { it.name }) { section ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (section) {
                QrShareSection.TASKS, QrShareSection.PLANS, QrShareSection.NOTES -> {
                    merge?.summary?.let { summary ->
                        val (added, duplicates) = when (section) {
                            QrShareSection.TASKS -> summary.addedTasks to summary.duplicateTasks
                            QrShareSection.PLANS -> summary.addedPlans to summary.duplicatePlans
                            else -> summary.addedNotes to summary.duplicateNotes
                        }
                        Text(stringResource(R.string.qr_import_add_count, stringResource(section.labelRes()), added, duplicates),
                            style = MaterialTheme.typography.titleSmall)
                    }
                }
                else -> {
                    Text(stringResource(R.string.qr_import_count, stringResource(section.labelRes()), localCounts[section] ?: 0, data.count(section)),
                        style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(when (section) {
                        QrShareSection.FIRST, QrShareSection.SECOND -> R.string.qr_import_timetable_scope
                        QrShareSection.CANCELLATIONS, QrShareSection.CHANGES -> R.string.qr_import_year_scope
                        QrShareSection.EXAMS -> R.string.qr_import_exams_scope
                        else -> if (data.qrDayTypesRange() == null) R.string.qr_import_all_scope else R.string.qr_import_days_scoped
                    }), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (QrShareSection.TASKS in data.sections || QrShareSection.PLANS in data.sections) item {
        Text(stringResource(R.string.qr_import_items_scope), style = MaterialTheme.typography.bodySmall)
    }
    if (QrShareSection.NOTES in data.sections) item {
        Text(stringResource(R.string.qr_import_notes_scope), style = MaterialTheme.typography.bodySmall)
    }
    if (data.scheduleTimes != null) item {
        Text(stringResource(R.string.qr_import_times_notice), style = MaterialTheme.typography.bodySmall)
    }
    if (QrShareSection.DAY_TYPES in data.sections) item {
        val range = data.qrDayTypesRange()
        val count = state.longBreaks.count { range == null || it.overlaps(range) }
        Text(stringResource(if (range == null) R.string.qr_import_breaks else R.string.qr_import_breaks_scoped,
            count, data.longBreaks.size), style = MaterialTheme.typography.bodySmall)
        if (range != null) Text(stringResource(R.string.qr_import_days_start_notice), style = MaterialTheme.typography.bodySmall)
    }
    item { Text(stringResource(R.string.qr_reminder_notice), style = MaterialTheme.typography.bodySmall) }
}

internal fun LazyListScope.qrImportNoteChoices(
    conflicts: List<QrNoteConflict>, approvedNotes: Set<LessonNoteEntity>, busy: Boolean,
    onCheckedChange: (LessonNoteEntity, Boolean) -> Unit
) {
    item { Text(stringResource(R.string.qr_import_notes_scope), style = MaterialTheme.typography.bodyMedium) }
    items(conflicts, key = { "${it.current.date}:${it.current.slotIndex}" }) { conflict ->
        val checked = conflict.current in approvedNotes
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.qr_import_note_date, conflict.current.date.toString(), conflict.current.slotIndex + 1),
                    style = MaterialTheme.typography.titleSmall)
                QrNoteText(stringResource(R.string.qr_import_note_current), conflict.current.text)
                QrNoteText(stringResource(R.string.qr_import_note_incoming), conflict.incoming.text)
                Row(Modifier.fillMaxWidth().toggleable(checked, enabled = !busy, role = Role.Checkbox,
                    onValueChange = { onCheckedChange(conflict.current, it) }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, onCheckedChange = null, enabled = !busy)
                    Text(stringResource(R.string.qr_import_note_replace), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun QrNoteText(label: String, text: String) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Text(text, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()))
}
