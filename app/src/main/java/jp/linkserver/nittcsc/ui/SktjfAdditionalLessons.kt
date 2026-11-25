package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.logic.ClassSlot
import jp.linkserver.nittcsc.logic.isClearlyRelatedSubjectRename
import jp.linkserver.nittcsc.logic.sameSubject
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.sktjf.SktjfVisibleExtra
import jp.linkserver.nittcsc.sktjf.SktjfExtraLessonDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TimedAlternativeEditors(items: List<SktjfVisibleExtra>, tasks: List<TaskEntity>, plans: List<PlanEntity>,
    onSave: suspend (SktjfVisibleExtra, SktjfExtraLessonDraft) -> Boolean) {
    var editing by remember { mutableStateOf<SktjfVisibleExtra?>(null) }
    items.forEach { lesson ->
        val range = lesson.content.weekRange?.range
        val label = when {
            range?.start != null && range.end != null -> stringResource(R.string.sktjf_timed_between, range.start, range.end, lesson.content.subject)
            range?.start != null -> stringResource(R.string.sktjf_timed_from, range.start, lesson.content.subject)
            range?.end != null -> stringResource(R.string.sktjf_timed_until, range.end, lesson.content.subject)
            else -> lesson.content.subject
        }
        OutlinedButton(onClick = { editing = lesson }, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
    editing?.let { selected ->
        val subjects = tasks.filter { it.extraLessonId == selected.id }.map { it.subject } +
            plans.filter { it.extraLessonId == selected.id }.map { it.subject }
        SktjfExtraLessonEditDialog(selected, subjects, onSave) { editing = null }
    }
}

@Composable
internal fun SktjfAdditionalLessons(items: List<Triple<ClassSlot, SktjfVisibleExtra, Boolean>>, invalid: Boolean,
    tasks: List<TaskEntity> = emptyList(), plans: List<PlanEntity> = emptyList(),
    onOpenTask: (TaskEntity) -> Unit = {}, onOpenPlan: (PlanEntity) -> Unit = {},
    onAdd: ((SktjfVisibleExtra, Boolean, ClassSlot) -> Unit)? = null,
    onSave: (suspend (SktjfVisibleExtra, SktjfExtraLessonDraft) -> Boolean)? = null) {
    if (items.isEmpty() && !invalid) return
    var editing by remember { mutableStateOf<SktjfVisibleExtra?>(null) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sktjf_additional_lessons), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (invalid) Text(stringResource(R.string.sktjf_additional_lessons_error), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
            items.forEach { (slot, lesson, cancelled) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(slot.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lesson.content.subject, style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (cancelled) TextDecoration.LineThrough else null)
                    if (cancelled) Text(stringResource(R.string.label_cancelled), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                    val details = (lesson.content.teachers + lesson.content.rooms).joinToString("・")
                    if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    tasks.filter { it.extraLessonId == lesson.id }.forEach { task ->
                        TextButton(onClick = { onOpenTask(task) }) { Text(task.title) }
                    }
                    plans.filter { it.extraLessonId == lesson.id }.forEach { plan ->
                        TextButton(onClick = { onOpenPlan(plan) }) { Text(plan.title) }
                    }
                    if (!cancelled && onAdd != null) {
                        val taskDescription = stringResource(R.string.sktjf_extra_add_task_description, lesson.content.subject)
                        val planDescription = stringResource(R.string.sktjf_extra_add_plan_description, lesson.content.subject)
                        Row {
                            TextButton(onClick = { onAdd(lesson, false, slot) }, modifier = Modifier.semantics { contentDescription = taskDescription }) { Text(stringResource(R.string.title_add_task)) }
                            TextButton(onClick = { onAdd(lesson, true, slot) }, modifier = Modifier.semantics { contentDescription = planDescription }) { Text(stringResource(R.string.title_add_plan)) }
                        }
                    }
                    if (onSave != null) {
                        val description = stringResource(R.string.sktjf_extra_edit_description, lesson.content.subject)
                        TextButton(onClick = { editing = lesson }, modifier = Modifier.semantics { contentDescription = description }) {
                            Text(stringResource(R.string.sktjf_extra_edit))
                        }
                    }
                }
            }
        }
    }
    editing?.let { selected ->
        val subjects = tasks.filter { it.extraLessonId == selected.id }.map { it.subject } +
            plans.filter { it.extraLessonId == selected.id }.map { it.subject }
        if (onSave != null) SktjfExtraLessonEditDialog(selected, subjects, onSave, onDismiss = { editing = null })
    }
}

@Composable
private fun SktjfExtraLessonEditDialog(lesson: SktjfVisibleExtra, linkedSubjects: List<String>,
    onSave: suspend (SktjfVisibleExtra, SktjfExtraLessonDraft) -> Boolean, onDismiss: () -> Unit) {
    var subject by remember(lesson.id, lesson.storedContentJson) { mutableStateOf(lesson.content.subject) }
    var teachers by remember(lesson.id, lesson.storedContentJson) { mutableStateOf(lesson.content.teachers.joinToString("\n")) }
    var rooms by remember(lesson.id, lesson.storedContentJson) { mutableStateOf(lesson.content.rooms.joinToString("\n")) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var confirmingRename by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save(follow: Boolean) {
        saving = true; failed = false
        scope.launch {
            val ok = try { onSave(lesson, SktjfExtraLessonDraft(subject, teachers, rooms, follow)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
            saving = false
            if (ok) onDismiss() else failed = true
        }
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.sktjf_extra_edit)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(subject, { subject = it }, label = { Text(stringResource(R.string.label_subject_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(teachers, { teachers = it }, label = { Text(stringResource(R.string.sktjf_extra_teachers)) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                OutlinedTextField(rooms, { rooms = it }, label = { Text(stringResource(R.string.sktjf_extra_rooms)) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                if (failed) Text(stringResource(R.string.sktjf_extra_save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = !saving && subject.isNotBlank(), onClick = {
            if (!isClearlyRelatedSubjectRename(lesson.content.subject, subject) &&
                linkedSubjects.any { sameSubject(it, lesson.content.subject) }) confirmingRename = true
            else save(false)
        }) { Text(stringResource(R.string.btn_save)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } })
    if (confirmingRename) AlertDialog(onDismissRequest = { confirmingRename = false },
        title = { Text(stringResource(R.string.lesson_rename_related_title)) },
        text = { Text(stringResource(R.string.lesson_rename_related_message)) },
        confirmButton = { TextButton(onClick = { confirmingRename = false; save(true) }) {
            Text(stringResource(R.string.lesson_rename_related_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmingRename = false; save(false) }) {
            Text(stringResource(R.string.lesson_rename_related_keep)) } })
}

@Composable
internal fun SktjfAdditionalLessonCount(count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.sktjf_additional_lessons_count, count)
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.semantics { contentDescription = description }) {
        Text(stringResource(R.string.sktjf_additional_lessons_compact, count),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
