package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.sktjf.*
import java.time.LocalDate

@Composable
internal fun SktjfMergeScreen(
    preview: SktjfApplyPreview,
    lessonChoices: MutableMap<String, SktjfConflictChoice>,
    calendarChoices: MutableMap<LocalDate, SktjfConflictChoice>,
    pending: Boolean, progress: Int, error: Int, onApply: () -> Unit, onDismiss: () -> Unit
) {
    val resources = LocalResources.current
    val fallback = preview.lessonConflicts.map { conflict ->
        SktjfLessonReview(conflict.sourceLessonId, conflict.incoming.dayOfWeek, conflict.incoming.period,
            conflict.incoming.cycle, conflict.current?.content ?: conflict.overlappingLocal.firstOrNull()?.content,
            if (conflict.isDeletion) null else conflict.incoming, SktjfLessonReviewStatus.CONFLICT)
    }
    val items = preview.lessonReview.ifEmpty { fallback }
    val bySlot = items.groupBy { it.day to it.period }
    val periods = maxOf(preview.document.periods.maxOfOrNull { it.number } ?: 0, items.maxOfOrNull { it.period } ?: 0)
    val weekdays = resources.getStringArray(R.array.sktjf_weekdays)
    var selected by remember(preview) { mutableStateOf<Pair<Int, Int>?>(null) }
    val countAuto = items.count { (it.status == SktjfLessonReviewStatus.AUTOMATIC &&
        it.sourceLessonId?.let { id -> lessonChoices[id] != SktjfConflictChoice.KEEP_LOCAL } == true) ||
        (it.status == SktjfLessonReviewStatus.PROTECTED && it.sourceLessonId?.let { id -> lessonChoices[id] == SktjfConflictChoice.APPLY_INCOMING } == true) }
    val countConflict = items.count { it.status == SktjfLessonReviewStatus.CONFLICT }
    val countProtected = items.count { (it.status == SktjfLessonReviewStatus.PROTECTED &&
        it.sourceLessonId?.let { id -> lessonChoices[id] != SktjfConflictChoice.APPLY_INCOMING } != false) ||
        (it.status == SktjfLessonReviewStatus.AUTOMATIC && it.sourceLessonId?.let { id -> lessonChoices[id] == SktjfConflictChoice.KEEP_LOCAL } == true) }
    fun statusText(status: SktjfLessonReviewStatus) = when (status) {
        SktjfLessonReviewStatus.AUTOMATIC -> R.string.sktjf_merge_automatic
        SktjfLessonReviewStatus.CONFLICT -> R.string.sktjf_merge_conflict
        SktjfLessonReviewStatus.PROTECTED -> R.string.sktjf_merge_protected
        SktjfLessonReviewStatus.UNCHANGED -> R.string.sktjf_merge_unchanged
    }
    fun selectedIncoming(item: SktjfLessonReview): Boolean = when (item.status) {
        SktjfLessonReviewStatus.CONFLICT -> item.sourceLessonId?.let { lessonChoices[it] == SktjfConflictChoice.APPLY_INCOMING } == true
        SktjfLessonReviewStatus.AUTOMATIC -> item.sourceLessonId?.let { lessonChoices[it] != SktjfConflictChoice.KEEP_LOCAL } == true
        SktjfLessonReviewStatus.PROTECTED -> item.sourceLessonId?.let { lessonChoices[it] == SktjfConflictChoice.APPLY_INCOMING } == true
        else -> false
    }
    fun chosenStatus(item: SktjfLessonReview): Int = when (item.status) {
        SktjfLessonReviewStatus.CONFLICT -> if (selectedIncoming(item)) R.string.sktjf_merge_chosen_incoming else R.string.sktjf_merge_chosen_current
        SktjfLessonReviewStatus.AUTOMATIC -> if (selectedIncoming(item)) R.string.sktjf_merge_automatic else R.string.sktjf_merge_excluded
        SktjfLessonReviewStatus.PROTECTED -> if (selectedIncoming(item)) R.string.sktjf_merge_chosen_incoming else R.string.sktjf_merge_protected
        else -> statusText(item.status)
    }
    Dialog(onDismissRequest = { if (!pending) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.sktjf_merge_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.sktjf_merge_context, preview.document.academicYear,
                    stringResource(if (preview.document.term == "S") R.string.sktjf_first_term else R.string.sktjf_second_term), preview.document.classId))
                Text(stringResource(R.string.sktjf_merge_counts, countAuto, countConflict, countProtected),
                    style = MaterialTheme.typography.bodyMedium)
                if (items.any { it.sourceLessonId != null && it.status != SktjfLessonReviewStatus.UNCHANGED } || preview.calendarConflicts.isNotEmpty()) {
                    Row {
                        TextButton(enabled = !pending, onClick = {
                            items.filter { it.sourceLessonId != null && it.status != SktjfLessonReviewStatus.UNCHANGED }
                                .mapNotNull { it.sourceLessonId }.forEach { lessonChoices[it] = SktjfConflictChoice.KEEP_LOCAL }
                            preview.calendarConflicts.forEach { calendarChoices[it.date] = SktjfConflictChoice.KEEP_LOCAL }
                        }) { Text(stringResource(R.string.sktjf_keep_all)) }
                        TextButton(enabled = !pending, onClick = {
                            items.filter { it.sourceLessonId != null && it.status != SktjfLessonReviewStatus.UNCHANGED }
                                .mapNotNull { it.sourceLessonId }.forEach { lessonChoices[it] = SktjfConflictChoice.APPLY_INCOMING }
                            preview.calendarConflicts.forEach { calendarChoices[it.date] = if (it.projection is SktjfCalendarProjection.Supported)
                                SktjfConflictChoice.APPLY_INCOMING else SktjfConflictChoice.KEEP_LOCAL }
                        }) { Text(stringResource(R.string.sktjf_apply_all)) }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            Row {
                                Spacer(Modifier.width(56.dp))
                                weekdays.take(5).forEach { Text(it, Modifier.width(112.dp).padding(8.dp), style = MaterialTheme.typography.labelLarge) }
                            }
                            for (period in 1..periods) Row {
                                Text(stringResource(R.string.sktjf_merge_period, period), Modifier.width(56.dp).padding(top = 8.dp),
                                    style = MaterialTheme.typography.labelMedium)
                                for (day in 1..5) {
                                    val slot = bySlot[day to period].orEmpty()
                                    val status = when {
                                        slot.any { it.status == SktjfLessonReviewStatus.CONFLICT } -> SktjfLessonReviewStatus.CONFLICT
                                        slot.any { it.status == SktjfLessonReviewStatus.AUTOMATIC } -> SktjfLessonReviewStatus.AUTOMATIC
                                        slot.any { it.status == SktjfLessonReviewStatus.PROTECTED } -> SktjfLessonReviewStatus.PROTECTED
                                        else -> SktjfLessonReviewStatus.UNCHANGED
                                    }
                                    val color = when (status) {
                                        SktjfLessonReviewStatus.CONFLICT -> MaterialTheme.colorScheme.errorContainer
                                        SktjfLessonReviewStatus.AUTOMATIC -> MaterialTheme.colorScheme.primaryContainer
                                        SktjfLessonReviewStatus.PROTECTED -> MaterialTheme.colorScheme.secondaryContainer
                                        SktjfLessonReviewStatus.UNCHANGED -> MaterialTheme.colorScheme.surfaceContainerLow
                                    }
                                    Surface(onClick = { selected = day to period }, enabled = slot.isNotEmpty() && !pending,
                                        color = color, shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.width(112.dp).height(96.dp).padding(2.dp).testTag("sktjf-merge-$day-$period")) {
                                        Column(Modifier.padding(6.dp)) {
                                            val representative = slot.firstOrNull { it.status == status } ?: slot.firstOrNull()
                                            if (representative != null) {
                                                Text(stringResource(chosenStatus(representative)), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                                val chosen = slot.flatMap { item ->
                                                    if (selectedIncoming(item)) listOfNotNull(item.incoming) else {
                                                        val overlap = preview.lessonConflicts.firstOrNull { it.sourceLessonId == item.sourceLessonId }
                                                            ?.overlappingLocal.orEmpty().map { it.content }
                                                        overlap.ifEmpty { listOfNotNull(item.current) }
                                                    }
                                                }.distinct()
                                                val title = chosen.joinToString("\n") { content ->
                                                    val prefix = when (content.cycle) { SktjfCycle.A -> "A "; SktjfCycle.B -> "B "; else -> "" }
                                                    prefix + lessonSubjectWithRange(resources, content)
                                                }.ifEmpty { resources.getString(R.string.sktjf_none) }
                                                Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (preview.calendarConflicts.isNotEmpty()) {
                        Text(stringResource(R.string.sktjf_merge_dates), style = MaterialTheme.typography.titleMedium)
                        preview.calendarConflicts.forEach { conflict ->
                            Text(conflict.date.toString(), style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.sktjf_current_value, calendarDetails(resources, conflict.current)))
                            Text(stringResource(R.string.sktjf_incoming_value, calendarDetails(resources, conflict.incoming)))
                            ChoiceRow(R.string.sktjf_keep_current, calendarChoices[conflict.date] != SktjfConflictChoice.APPLY_INCOMING, !pending) {
                                calendarChoices[conflict.date] = SktjfConflictChoice.KEEP_LOCAL
                            }
                            ChoiceRow(R.string.sktjf_apply_incoming, calendarChoices[conflict.date] == SktjfConflictChoice.APPLY_INCOMING,
                                !pending && conflict.projection is SktjfCalendarProjection.Supported) {
                                calendarChoices[conflict.date] = SktjfConflictChoice.APPLY_INCOMING
                            }
                        }
                    }
                }
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                if (pending) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (progress != 0) Text(stringResource(progress), style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !pending, onClick = onDismiss) { Text(stringResource(R.string.nittc_cancel)) }
                    Button(enabled = !pending, onClick = onApply) { Text(stringResource(R.string.sktjf_merge_selected_apply)) }
                }
            }
        }
    }
    selected?.let { slot ->
        AlertDialog(onDismissRequest = { selected = null },
            title = { Text(stringResource(R.string.sktjf_lesson_slot, weekdays.getOrElse(slot.first - 1) { "?" }, slot.second)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val slotItems = bySlot[slot].orEmpty()
                    val currentValues = slotItems.flatMap { item ->
                        val overlap = preview.lessonConflicts.firstOrNull { it.sourceLessonId == item.sourceLessonId }
                            ?.overlappingLocal.orEmpty().map { it.content }
                        overlap.ifEmpty { listOfNotNull(item.current) }
                    }.distinct()
                    val incomingValues = slotItems.mapNotNull { it.incoming }.distinct()
                    Text(stringResource(R.string.sktjf_current_value, currentValues.joinToString("\n") { lessonDetails(resources, it) }.ifEmpty { stringResource(R.string.sktjf_none) }))
                    Text(stringResource(R.string.sktjf_incoming_value, incomingValues.joinToString("\n") { lessonDetails(resources, it) }.ifEmpty { stringResource(R.string.sktjf_none) }))
                    val ids = slotItems.filter { it.sourceLessonId != null && it.status != SktjfLessonReviewStatus.UNCHANGED }
                        .mapNotNull { it.sourceLessonId }.distinct()
                    if (ids.isNotEmpty()) {
                        val adopt = ids.all { lessonChoices[it] == SktjfConflictChoice.APPLY_INCOMING }
                        ChoiceRow(R.string.sktjf_keep_current, !adopt, !pending) { ids.forEach { lessonChoices[it] = SktjfConflictChoice.KEEP_LOCAL } }
                        ChoiceRow(R.string.sktjf_apply_incoming, adopt, !pending) { ids.forEach { lessonChoices[it] = SktjfConflictChoice.APPLY_INCOMING } }
                    }
                }
            }, confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.sktjf_merge_close)) } })
    }
}
