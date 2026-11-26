package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.SktjfSelectionGroupEntity
import jp.linkserver.nittcsc.sktjf.*
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal fun sktjfElectiveNeedsChoice(lessons: List<SktjfLesson>, ranges: Map<String, SktjfV2Range>,
    series: List<SktjfV2WeekSeries> = emptyList()): Boolean =
    lessons.indices.any { i -> (i + 1 until lessons.size).any { j ->
        val a = ranges[lessons[i].id]?.range
        val b = ranges[lessons[j].id]?.range
        val aSeries = series.filter { lessons[i].id in it.lessonIds }.map { it.id }.toSet()
        val bSeries = series.filter { lessons[j].id in it.lessonIds }.map { it.id }.toSet()
        (aSeries != bSeries && (aSeries.isNotEmpty() || bSeries.isNotEmpty())) ||
            ((a?.start ?: 1) <= (b?.end ?: Int.MAX_VALUE) && (b?.start ?: 1) <= (a?.end ?: Int.MAX_VALUE))
    } }

@Composable
internal fun SktjfElectiveImportDialog(candidate: SktjfV3Candidate, attributes: Set<String>, groupIds: Set<String>,
    removedGroups: List<SktjfSelectionGroupEntity> = emptyList(),
    onConfirm: (Map<String, String?>) -> Unit, onCancel: () -> Unit) {
    val choices = remember(candidate, groupIds) { mutableStateMapOf<String, String?>().apply {
        groupIds.forEach { put(it, null) }
    } }
    val document = candidate.timetable.document
    AlertDialog(onDismissRequest = onCancel, title = { Text(stringResource(R.string.sktjf_elective_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                removedGroups.forEach { group ->
                    val term = stringResource(if (document.term == "S") R.string.sktjf_first_term else R.string.sktjf_second_term)
                    val weekday = DayOfWeek.of(group.dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.JAPANESE)
                    Column {
                        Text(stringResource(R.string.sktjf_elective_slot, document.classId, term, weekday, group.period),
                            style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.sktjf_elective_removed))
                    }
                }
                candidate.selectionGroups.filter { it.id in groupIds }.forEach { group ->
                    Column(Modifier.selectableGroup()) {
                        val term = stringResource(if (document.term == "S") R.string.sktjf_first_term else R.string.sktjf_second_term)
                        val weekday = DayOfWeek.of(group.dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.JAPANESE)
                        Text(stringResource(R.string.sktjf_elective_slot, document.classId, term,
                            weekday, group.period), style = MaterialTheme.typography.titleSmall)
                        val lessons = document.lessons.filter { it.id in group.lessonIds && it.conditions.matches(attributes) }
                        lessons.forEach { lesson ->
                            val label = listOf(lesson.subject, lesson.teachers.joinToString("、"), lesson.rooms.joinToString("、"))
                                .filter(String::isNotBlank).joinToString("\n")
                            ElectiveChoice(label, choices[group.id] == lesson.id) { choices[group.id] = lesson.id }
                        }
                        ElectiveChoice(stringResource(R.string.sktjf_elective_unknown), choices[group.id] == null) {
                            choices[group.id] = null
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choices.toMap()) }) {
            Text(stringResource(if (groupIds.isEmpty()) R.string.sktjf_elective_acknowledge else R.string.sktjf_elective_confirm))
        } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.nittc_cancel)) } })
}

@Composable
private fun ElectiveChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
