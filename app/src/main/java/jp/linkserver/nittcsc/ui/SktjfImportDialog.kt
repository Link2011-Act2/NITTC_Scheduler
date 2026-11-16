package jp.linkserver.nittcsc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.UsageModeEntity
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.sktjf.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun SktjfImportDialog(
    mode: UsageModeEntity?, profiles: List<NittcProfileEntity>, defaultYear: Int,
    discover: suspend (Int, String) -> SktjfDiscoveryResult,
    fetch: suspend (String, String) -> SktjfFetchResult,
    preview: suspend (SktjfDocument) -> SktjfPreviewResult,
    apply: suspend (SktjfApplyPreview, Map<String, SktjfConflictChoice>, Map<LocalDate, SktjfConflictChoice>) -> SktjfApplyResult,
    onApplied: () -> Unit, onDismiss: () -> Unit,
    initialPreview: SktjfApplyPreview? = null
) {
    var yearText by remember { mutableStateOf((initialPreview?.document?.academicYear ?: defaultYear.takeIf { it in 1900..9998 }
        ?: academicYearForDate(LocalDate.now())).toString()) }
    var term by remember { mutableStateOf(initialPreview?.document?.term ?: "S") }
    var pending by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    var prepared by remember { mutableStateOf(initialPreview) }
    val lessonChoices = remember { mutableStateMapOf<String, SktjfConflictChoice>() }
    val calendarChoices = remember { mutableStateMapOf<LocalDate, SktjfConflictChoice>() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val context = LocalContext.current
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pending = true; error = 0
            scope.launch {
                try {
                    val document = withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openInputStream(uri)).use(SktjfJson::localDocument)
                    }
                    when (val result = preview(document)) {
                        is SktjfPreviewResult.Ready -> { lessonChoices.clear(); calendarChoices.clear(); prepared = result.preview.copy(localFile = true) }
                        SktjfPreviewResult.Blocked -> error = R.string.sktjf_cannot_apply
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = R.string.sktjf_file_read_failed }
                finally { pending = false }
            }
        }
    }
    val year = yearText.toIntOrNull()?.takeIf { it in 1900..9998 }
    val profile = profiles.singleOrNull { it.academicYear == year }
    val canFetch = !pending && year != null && mode?.mode == SchedulerUsageMode.NITTC.name && profile != null
    val current = prepared
    val canApply = !pending && current != null &&
        lessonChoices.keys.toSet() == current.lessonConflicts.map { it.sourceLessonId }.toSet() &&
        calendarChoices.keys.toSet() == current.calendarConflicts.map { it.date }.toSet()
    AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = { Text(stringResource(R.string.sktjf_import_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current == null) {
                    OutlinedTextField(yearText, { yearText = it.filter(Char::isDigit).take(4); error = 0 },
                        label = { Text(stringResource(R.string.nittc_academic_year)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !pending,
                        modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("S" to R.string.sktjf_first_term, "F" to R.string.sktjf_second_term).forEach { (value, label) ->
                            FilterChip(selected = term == value, onClick = { term = value; error = 0 }, enabled = !pending,
                                label = { Text(stringResource(label)) })
                        }
                    }
                    if (mode?.mode != SchedulerUsageMode.NITTC.name || profile == null)
                        Text(stringResource(R.string.sktjf_profile_required))
                    else Text(stringResource(R.string.sktjf_profile_value, profile.classId))
                    TextButton(enabled = !pending && mode?.mode == SchedulerUsageMode.NITTC.name && profiles.isNotEmpty(),
                        onClick = { fileLauncher.launch(arrayOf("application/json", "application/octet-stream")) }) {
                        Text(stringResource(R.string.sktjf_open_file))
                    }
                } else {
                    Text(stringResource(R.string.sktjf_preview_heading, current.document.revision, current.document.classId))
                    if (current.lessonConflicts.isEmpty() && current.calendarConflicts.isEmpty())
                        Text(stringResource(R.string.sktjf_apply_question))
                    if (current.lessonConflicts.isNotEmpty() || current.calendarConflicts.isNotEmpty()) {
                        Text(stringResource(R.string.sktjf_conflicts_heading), style = MaterialTheme.typography.titleSmall)
                        TextButton(enabled = !pending, onClick = {
                            current.lessonConflicts.forEach { lessonChoices[it.sourceLessonId] = SktjfConflictChoice.KEEP_LOCAL }
                            current.calendarConflicts.forEach { calendarChoices[it.date] = SktjfConflictChoice.KEEP_LOCAL }
                        }) { Text(stringResource(R.string.sktjf_keep_all)) }
                        TextButton(enabled = !pending, onClick = {
                            current.lessonConflicts.forEach { lessonChoices[it.sourceLessonId] = SktjfConflictChoice.APPLY_INCOMING }
                            current.calendarConflicts.forEach {
                                calendarChoices[it.date] = if (it.projection is SktjfCalendarProjection.Supported)
                                    SktjfConflictChoice.APPLY_INCOMING else SktjfConflictChoice.KEEP_LOCAL
                            }
                        }) { Text(stringResource(R.string.sktjf_apply_all)) }
                        if (current.calendarConflicts.any { it.projection !is SktjfCalendarProjection.Supported })
                            Text(stringResource(R.string.sktjf_unsupported_keep))
                    }
                    current.lessonConflicts.forEach { conflict ->
                        HorizontalDivider()
                        Text(stringResource(R.string.sktjf_lesson_slot, weekday(resources, conflict.incoming.dayOfWeek), conflict.incoming.period))
                        val local = listOfNotNull(conflict.current?.content) + conflict.overlappingLocal.map { it.content }
                        Text(stringResource(R.string.sktjf_current_value, local.joinToString("\n") { lessonDetails(resources, it) }.ifEmpty { resources.getString(R.string.sktjf_none) }))
                        Text(stringResource(R.string.sktjf_incoming_value, if (conflict.isDeletion)
                            stringResource(R.string.sktjf_none) else lessonDetails(resources, conflict.incoming)))
                        Column(Modifier.selectableGroup()) {
                            ChoiceRow(R.string.sktjf_keep_current, lessonChoices[conflict.sourceLessonId] == SktjfConflictChoice.KEEP_LOCAL, !pending) {
                                lessonChoices[conflict.sourceLessonId] = SktjfConflictChoice.KEEP_LOCAL
                            }
                            ChoiceRow(R.string.sktjf_apply_incoming, lessonChoices[conflict.sourceLessonId] == SktjfConflictChoice.APPLY_INCOMING, !pending) {
                                lessonChoices[conflict.sourceLessonId] = SktjfConflictChoice.APPLY_INCOMING
                            }
                        }
                    }
                    current.calendarConflicts.forEach { conflict ->
                        HorizontalDivider()
                        Text(conflict.date.toString())
                        Text(stringResource(R.string.sktjf_current_value, calendarDetails(resources, conflict.current)))
                        Text(stringResource(R.string.sktjf_incoming_value, calendarDetails(resources, conflict.incoming)))
                        Column(Modifier.selectableGroup()) {
                            ChoiceRow(R.string.sktjf_keep_current, calendarChoices[conflict.date] == SktjfConflictChoice.KEEP_LOCAL, !pending) {
                                calendarChoices[conflict.date] = SktjfConflictChoice.KEEP_LOCAL
                            }
                            ChoiceRow(R.string.sktjf_apply_incoming, calendarChoices[conflict.date] == SktjfConflictChoice.APPLY_INCOMING,
                                !pending && conflict.projection is SktjfCalendarProjection.Supported) {
                                calendarChoices[conflict.date] = SktjfConflictChoice.APPLY_INCOMING
                            }
                        }
                        if (conflict.projection !is SktjfCalendarProjection.Supported) Text(stringResource(R.string.sktjf_day_unsupported))
                    }
                }
                if (pending) CircularProgressIndicator()
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            if (current == null) TextButton(enabled = canFetch, onClick = {
                pending = true; error = 0
                scope.launch {
                    try {
                        when (val found = discover(year!!, term)) {
                            is SktjfDiscoveryResult.Found -> when (val result = fetch(found.revision, profile!!.classId)) {
                                is SktjfFetchResult.Success -> when (val p = preview(result.document)) {
                                    is SktjfPreviewResult.Ready -> { lessonChoices.clear(); calendarChoices.clear(); prepared = p.preview }
                                    SktjfPreviewResult.Blocked -> error = R.string.sktjf_cannot_apply
                                }
                                SktjfFetchResult.ClassNotFound -> error = R.string.sktjf_not_published
                                SktjfFetchResult.ReleaseNotFound -> error = R.string.sktjf_invalid_data
                                SktjfFetchResult.RateLimited -> error = R.string.sktjf_rate_limited
                                SktjfFetchResult.NetworkFailure -> error = R.string.sktjf_network_error
                                is SktjfFetchResult.HttpFailure -> error = R.string.sktjf_fetch_failed
                                SktjfFetchResult.InvalidData -> error = R.string.sktjf_invalid_data
                            }
                            SktjfDiscoveryResult.NotPublished -> error = R.string.sktjf_not_published
                            SktjfDiscoveryResult.RateLimited -> error = R.string.sktjf_rate_limited
                            SktjfDiscoveryResult.NetworkFailure -> error = R.string.sktjf_network_error
                            is SktjfDiscoveryResult.HttpFailure -> error = R.string.sktjf_fetch_failed
                            SktjfDiscoveryResult.InvalidData -> error = R.string.sktjf_invalid_data
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = R.string.sktjf_invalid_data }
                    finally { pending = false }
                }
            }) { Text(stringResource(R.string.sktjf_check_distribution)) }
            else TextButton(enabled = canApply, onClick = {
                pending = true; applying = true; error = 0
                scope.launch {
                    try {
                        when (apply(current, lessonChoices.toMap(), calendarChoices.toMap())) {
                            SktjfApplyResult.Applied -> { onApplied(); onDismiss() }
                            SktjfApplyResult.Stale -> { prepared = null; error = R.string.sktjf_stale }
                            SktjfApplyResult.Blocked -> error = R.string.sktjf_cannot_apply
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = R.string.sktjf_cannot_apply }
                    finally { pending = false; applying = false }
                }
            }) { Text(stringResource(R.string.sktjf_apply)) }
        },
        dismissButton = { TextButton(enabled = !applying, onClick = onDismiss) { Text(stringResource(R.string.nittc_cancel)) } }
    )
}

@Composable
private fun ChoiceRow(label: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(stringResource(label), modifier = Modifier.padding(top = 12.dp))
    }
}

private fun weekday(resources: android.content.res.Resources, value: Int): String = resources.getStringArray(R.array.sktjf_weekdays).getOrElse(value - 1) { "?" }

private fun lessonDetails(resources: android.content.res.Resources, value: SktjfLessonContent): String = buildList {
    add(value.subject)
    if (value.teachers.isNotEmpty()) add(value.teachers.joinToString("、"))
    if (value.rooms.isNotEmpty()) add(value.rooms.joinToString("、"))
    add(when (value.cycle) { SktjfCycle.WEEKLY -> resources.getString(R.string.sktjf_weekly); SktjfCycle.A -> "A"; SktjfCycle.B -> "B" })
    fun attribute(id: String) = when (id) { "DD" -> resources.getString(R.string.nittc_dd); "INTERNATIONAL" -> resources.getString(R.string.nittc_international); else -> id }
    if (value.conditions.all.isNotEmpty()) add(resources.getString(R.string.sktjf_condition_required, value.conditions.all.sorted().joinToString("、") { attribute(it) }))
    if (value.conditions.none.isNotEmpty()) add(resources.getString(R.string.sktjf_condition_excluded, value.conditions.none.sorted().joinToString("、") { attribute(it) }))
    if (value.remote == true) add(resources.getString(R.string.sktjf_remote))
}.joinToString(" / ")

private fun calendarDetails(resources: android.content.res.Resources, value: SktjfCalendarContent?): String = value?.let {
    fun type(type: SktjfDayType?) = when (type) { SktjfDayType.HOLIDAY -> resources.getString(R.string.sktjf_holiday); else -> type?.name }
    listOfNotNull(type(it.dayType), it.overrideDayOfWeek?.let { day -> resources.getString(R.string.sktjf_override_day, weekday(resources, day)) },
        type(it.overrideDayType), it.event?.name).joinToString(" / ").ifEmpty { resources.getString(R.string.sktjf_none) }
} ?: resources.getString(R.string.sktjf_none)
