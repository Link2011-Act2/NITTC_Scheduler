package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.sktjf.*

internal data class SktjfCourseSelection(
    val document: SktjfDocument,
    val attributes: Set<String>,
    val localFile: Boolean,
    val slots: List<SktjfProfileSlotOptions>,
)

@Composable
internal fun SktjfCourseSelectionDialog(selection: SktjfCourseSelection, onConfirm: (SktjfDocument) -> Unit, onCancel: () -> Unit) {
    val selected = remember(selection) { mutableStateMapOf<String, Boolean>().apply {
        selection.slots.forEach { slot ->
            // Preserve an unambiguous A/B counterpart while leaving alternatives unselected.
            listOf(SktjfCycle.A, SktjfCycle.B).forEach { cycle ->
                val matching = slot.lessons.filter { it.cycle == cycle }
                if (matching.size == 1 && slot.lessons.none { it.cycle == SktjfCycle.WEEKLY }) put(matching.single().id, true)
            }
        }
    } }
    val ids = selected.filterValues { it }.keys.toSet()
    val resolved = selectSktjfLessons(selection.document, selection.attributes, ids)
    val weekdays = LocalContext.current.resources.getStringArray(R.array.sktjf_weekdays)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sktjf_choose_courses)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selection.slots.forEach { slot ->
                    Text(stringResource(R.string.sktjf_lesson_slot, weekdays.getOrElse(slot.day - 1) { "?" }, slot.period),
                        style = MaterialTheme.typography.titleSmall)
                    slot.lessons.forEach { lesson ->
                        val label = listOfNotNull(lesson.subject, lesson.teachers.joinToString("、").ifBlank { null },
                            lesson.rooms.joinToString("、").ifBlank { null },
                            lesson.cycle.name.takeUnless { it == "WEEKLY" }).joinToString(" / ")
                        Row(Modifier.fillMaxWidth().toggleable(value = selected[lesson.id] == true, role = Role.Checkbox,
                            onValueChange = { selected[lesson.id] = it })) {
                            Checkbox(checked = selected[lesson.id] == true, onCheckedChange = null)
                            Text(label, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = resolved != null, onClick = { resolved?.let(onConfirm) }) {
            Text(stringResource(R.string.sktjf_confirm_courses))
        } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.nittc_cancel)) } },
    )
}
