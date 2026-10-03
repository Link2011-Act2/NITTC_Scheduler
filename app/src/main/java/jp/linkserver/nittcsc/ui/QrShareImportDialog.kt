package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.LessonNoteEntity
import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.data.QrShareSection
import jp.linkserver.nittcsc.logic.QrShareMerge
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.qrShareMerge
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun QrShareImportDialog(
    data: QrSharePayload, state: SchedulerUiState, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onConfirm: (Set<LessonNoteEntity>) -> Unit
) {
    var approvedNotes by remember(data) { mutableStateOf(emptySet<LessonNoteEntity>()) }
    // 一覧の重複判定はUIスレッドから外す。保存時にもDBの最新状態で再判定する。
    val preview by produceState<QrShareMerge?>(null, data, state.tasks, state.plans, state.lessonNotes) {
        value = null
        value = withContext(Dispatchers.Default) {
            qrShareMerge(state.tasks, data.tasks, state.plans, data.plans, state.lessonNotes, data.notes)
        }
    }
    val merge = preview
    val selectedReplacements = merge?.noteConflicts?.count { it.current in approvedNotes } ?: 0
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.qr_import_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    if (data.classLabel.isNotBlank()) Text(data.classLabel, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.qr_image_year, data.academicYear))
                    Text(stringResource(R.string.qr_import_warning))
                }
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
                                    Text(stringResource(R.string.qr_import_add_count, stringResource(section.labelRes()), added, duplicates))
                                    if (section == QrShareSection.NOTES && merge.noteConflicts.isNotEmpty()) {
                                        Text(stringResource(R.string.qr_import_note_choices, selectedReplacements,
                                            merge.noteConflicts.size - selectedReplacements))
                                    }
                                }
                                Text(stringResource(if (section == QrShareSection.NOTES) R.string.qr_import_notes_scope else R.string.qr_import_items_scope),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            else -> {
                                val localCount = when (section) {
                                    QrShareSection.FIRST, QrShareSection.SECOND -> state.lessons.keys.count {
                                        it.academicYear == data.academicYear && it.timetableTerm.name == section.name
                                    }
                                    QrShareSection.CANCELLATIONS -> state.cancelledLessons.count { academicYearForDate(it.first) == data.academicYear }
                                    QrShareSection.CHANGES -> state.changedLessons.keys.count { academicYearForDate(it.first) == data.academicYear }
                                    QrShareSection.EXAMS -> (state.examDaySchedules.keys + state.dayTypeEntities.values.filter {
                                        it.holidaySpecialLabel?.usesExamTimetable == true
                                    }.map { it.date }).distinct().count { academicYearForDate(it) == data.academicYear }
                                    else -> state.dayTypeEntities.size
                                }
                                Text(stringResource(R.string.qr_import_count, stringResource(section.labelRes()), localCount, data.count(section)))
                                Text(stringResource(when (section) {
                                    QrShareSection.FIRST, QrShareSection.SECOND -> R.string.qr_import_timetable_scope
                                    QrShareSection.CANCELLATIONS, QrShareSection.CHANGES -> R.string.qr_import_year_scope
                                    QrShareSection.EXAMS -> R.string.qr_import_exams_scope
                                    else -> R.string.qr_import_all_scope
                                }), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (data.scheduleTimes != null) item {
                    Text(stringResource(R.string.qr_import_times_notice), style = MaterialTheme.typography.bodySmall)
                }
                if (QrShareSection.DAY_TYPES in data.sections) item {
                    Text(stringResource(R.string.qr_import_breaks, state.longBreaks.size, data.longBreaks.size))
                }
                items(merge?.noteConflicts.orEmpty(), key = { "${it.current.date}:${it.current.slotIndex}" }) { conflict ->
                    val checked = conflict.current in approvedNotes
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.qr_import_note_date, conflict.current.date.toString(), conflict.current.slotIndex + 1),
                                style = MaterialTheme.typography.titleSmall)
                            QrNoteText(stringResource(R.string.qr_import_note_current), conflict.current.text)
                            QrNoteText(stringResource(R.string.qr_import_note_incoming), conflict.incoming.text)
                            Row(Modifier.fillMaxWidth().toggleable(checked, enabled = !busy, role = Role.Checkbox, onValueChange = {
                                approvedNotes = if (it) approvedNotes + conflict.current else approvedNotes - conflict.current
                            }), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, onCheckedChange = null, enabled = !busy)
                                Text(stringResource(R.string.qr_import_note_replace), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item { Text(stringResource(R.string.qr_reminder_notice), style = MaterialTheme.typography.bodySmall) }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && merge != null && state.initialized, onClick = { onConfirm(approvedNotes) }) {
                Text(stringResource(R.string.qr_import_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.qr_cancel)) } }
    )
}

@Composable
private fun QrNoteText(label: String, text: String) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Text(text, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState()))
}
