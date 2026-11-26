package jp.linkserver.nittcsc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.UsageModeEntity
import jp.linkserver.nittcsc.data.attributes
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.sktjf.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal fun initialSktjfLessonChoices(value: SktjfApplyPreview, automaticUpdate: Boolean): Map<String, SktjfConflictChoice> {
    val previousState = value.snapshot.sourceStates.singleOrNull {
        it.academicYear == value.document.academicYear && it.term == value.document.term && it.classId == value.document.classId
    }
    val previousSourceIds = previousState?.let {
        runCatching { SktjfBaselineCodec.decode(it.baselineJson).lessons.lessons.keys }.getOrNull()
    }.orEmpty()
    val previousLinks = previousState?.let { runCatching { SktjfSourceLinks.decode(it.linksJson) }.getOrNull() }.orEmpty()
    val choices = value.lessonConflicts.associate { conflict ->
        val unlinkedManualAtSlot = conflict.reason == SktjfConflictReason.OVERLAPPING_LOCAL &&
            conflict.overlappingLocal.any { it.sourceLessonId == null } &&
            previousLinks.none { it.day == conflict.incoming.dayOfWeek && it.period == conflict.incoming.period }
        val protectManualAddition = previousState != null &&
            ((conflict.previous == null && conflict.sourceLessonId !in previousSourceIds) || unlinkedManualAtSlot)
        conflict.sourceLessonId to if (!automaticUpdate && !protectManualAddition)
            SktjfConflictChoice.APPLY_INCOMING else SktjfConflictChoice.KEEP_LOCAL
    }.toMutableMap()
    val slots = value.lessonReview.filter { it.sourceLessonId != null && it.status != SktjfLessonReviewStatus.UNCHANGED }
        .groupBy { it.day to it.period }
    for (items in slots.values) {
        val ids = items.mapNotNull { it.sourceLessonId }
        val choice = if ((automaticUpdate && previousState?.requiresRelinking == true) ||
            ids.any { choices[it] == SktjfConflictChoice.KEEP_LOCAL })
            SktjfConflictChoice.KEEP_LOCAL else SktjfConflictChoice.APPLY_INCOMING
        ids.forEach { choices[it] = choice }
    }
    return choices
}

@Composable
internal fun SktjfImportDialog(
    mode: UsageModeEntity?, profiles: List<NittcProfileEntity>, defaultYear: Int,
    discover: suspend (Int, String) -> SktjfDiscoveryResult,
    fetch: suspend (String, String) -> SktjfFetchResult,
    preview: suspend (SktjfDocument) -> SktjfPreviewResult,
    apply: suspend (SktjfApplyPreview, Map<String, SktjfConflictChoice>, Map<LocalDate, SktjfConflictChoice>) -> SktjfApplyResult,
    onApplied: () -> Unit, onDismiss: () -> Unit,
    initialPreview: SktjfApplyPreview? = null,
    automaticUpdate: Boolean = false,
    initialDocument: SktjfDocument? = null,
    previewTimed: suspend (SktjfV2Candidate) -> SktjfV2ApplyPreview? = { null },
    initialTimed: SktjfV2Candidate? = null,
    previewSelections: suspend (SktjfV3Candidate, Map<String, String?>) -> SktjfV2ApplyPreview? = { _, _ -> null },
    initialSelections: SktjfV3Candidate? = null,
) {
    var yearText by remember { mutableStateOf((initialPreview?.document?.academicYear ?: defaultYear.takeIf { it in 1900..9998 }
        ?: academicYearForDate(LocalDate.now())).toString()) }
    var term by remember { mutableStateOf(initialPreview?.document?.term ?: "S") }
    var pending by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableIntStateOf(0) }
    var prepared by remember { mutableStateOf(initialPreview) }
    var courseSelection by remember { mutableStateOf<SktjfCourseSelection?>(null) }
    var timedCourseSelection by remember { mutableStateOf<SktjfV2Candidate?>(null) }
    var electiveCandidate by remember { mutableStateOf<SktjfV3Candidate?>(null) }
    var electiveLocalFile by remember { mutableStateOf(false) }
    var electiveGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var removedElectiveGroups by remember { mutableStateOf<List<jp.linkserver.nittcsc.data.SktjfSelectionGroupEntity>>(emptyList()) }
    val lessonChoices = remember { mutableStateMapOf<String, SktjfConflictChoice>().apply {
        initialPreview?.let { putAll(initialSktjfLessonChoices(it, automaticUpdate)) }
    } }
    val calendarChoices = remember { mutableStateMapOf<LocalDate, SktjfConflictChoice>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun acceptPreview(value: SktjfApplyPreview) {
        lessonChoices.clear(); lessonChoices.putAll(initialSktjfLessonChoices(value, automaticUpdate)); calendarChoices.clear()
        if (value.hasExistingLessons()) {
            prepared = value
            return
        }
        applying = true
        progress = R.string.sktjf_progress_apply
        try {
            val lessons = value.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }
            val dates = value.calendarConflicts.associate { it.date to if (it.projection is SktjfCalendarProjection.Supported)
                SktjfConflictChoice.APPLY_INCOMING else SktjfConflictChoice.KEEP_LOCAL }
            when (apply(value, lessons, dates)) {
                SktjfApplyResult.Applied -> { onApplied(); onDismiss() }
                SktjfApplyResult.Stale -> error = R.string.sktjf_stale
                SktjfApplyResult.Blocked -> error = R.string.sktjf_cannot_apply
            }
        } finally { applying = false }
    }

    suspend fun prepareDocument(document: SktjfDocument, localFile: Boolean) {
        val target = profiles.singleOrNull { it.academicYear == document.academicYear && it.classId == document.classId }
        if (target == null) { error = R.string.sktjf_cannot_apply; return }
        val attributes = target.attributes()
        val slots = sktjfProfileSlotOptions(document, attributes)
        if (slots.isNotEmpty()) {
            // A collision in an older document does not establish an elective.
            error = R.string.sktjf_cannot_apply
            return
        }
        when (val result = preview(document)) {
            is SktjfPreviewResult.Ready -> acceptPreview(result.preview.copy(localFile = localFile))
            SktjfPreviewResult.Blocked -> error = R.string.sktjf_cannot_apply
        }
    }

    suspend fun prepareTimed(candidate: SktjfV2Candidate, localFile: Boolean = false) {
        val document = candidate.document
        val target = profiles.singleOrNull { it.academicYear == document.academicYear && it.classId == document.classId }
        if (target == null) { error = R.string.sktjf_cannot_apply; return }
        val attributes = target.attributes()
        val options = sktjfProfileSlotOptions(candidate.documentForMerge(), attributes)
        val timedSlots = options.filter { slot -> candidate.series.any { group ->
            group.dayOfWeek == slot.day && slot.period in group.periods &&
                slot.lessons.all { it.id in group.lessonIds } && slot.lessons.any { it.id in candidate.ranges }
        } }.map { it.day to it.period }.toSet()
        val personal = options.filterNot { (it.day to it.period) in timedSlots }
        if (personal.any { slot -> slot.lessons.any { it.id in candidate.ranges } }) {
            error = R.string.sktjf_cannot_apply; return
        }
        if (personal.isNotEmpty()) {
            // Only an explicit selection group can identify an elective.
            error = R.string.sktjf_cannot_apply
            return
        }
        val timed = previewTimed(candidate)
        if (timed == null) error = R.string.sktjf_cannot_apply else acceptPreview(timed.copy(localFile = localFile).forReview())
    }

    suspend fun prepareSelections(candidate: SktjfV3Candidate, localFile: Boolean = false, choices: Map<String, String?>? = null) {
        val document = candidate.timetable.document
        val profile = profiles.singleOrNull { it.academicYear == document.academicYear && it.classId == document.classId }
        if (profile == null) { error = R.string.sktjf_cannot_apply; return }
        val checked = previewSelections(candidate, choices.orEmpty())
        if (checked == null) { error = R.string.sktjf_cannot_apply; return }
        val existing = checked.snapshot.selectionGroups
        val needsChoice = candidate.selectionGroups.filter { group ->
            val eligible = document.lessons.filter { it.id in group.lessonIds && it.conditions.matches(profile.attributes()) }
            val previous = matchingSktjfSelectionGroup(group, existing)
            (previous == null && sktjfElectiveNeedsChoice(eligible, candidate.timetable.ranges, candidate.timetable.series)) ||
                (previous?.selectedLessonId != null && eligible.none { it.id == previous.selectedLessonId })
        }.map { it.id }.toSet()
        val removedGroups = checked.snapshot.selectionGroups.filter { old ->
            old.selectedLessonId != null && candidate.selectionGroups.none { matchingSktjfSelectionGroup(it, existing)?.groupId == old.groupId }
        }
        if (choices == null && (needsChoice.isNotEmpty() || removedGroups.isNotEmpty())) {
            electiveLocalFile = localFile
            electiveGroupIds = needsChoice
            removedElectiveGroups = removedGroups
            electiveCandidate = candidate
        } else acceptPreview(checked.copy(localFile = localFile).forReview())
    }

    LaunchedEffect(initialDocument, initialTimed, initialSelections) {
        if ((initialDocument != null || initialTimed != null || initialSelections != null) && initialPreview == null) {
            pending = true; error = 0; progress = R.string.sktjf_progress_compare
            try {
                if (initialSelections != null) {
                    prepareSelections(initialSelections)
                } else if (initialTimed != null) {
                    prepareTimed(initialTimed)
                } else if (initialDocument != null) prepareDocument(initialDocument, false)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = R.string.sktjf_cannot_apply }
            finally { pending = false; progress = 0 }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pending = true; error = 0; progress = R.string.sktjf_progress_read
            scope.launch {
                try {
                    val asset = withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openInputStream(uri)).use(SktjfJson::localAsset)
                    }
                    progress = R.string.sktjf_progress_compare
                    when (asset) {
                        is SktjfCandidateAsset.V1 -> prepareDocument(asset.document, true)
                        is SktjfCandidateAsset.V2 -> prepareTimed(asset.candidate, true)
                        is SktjfCandidateAsset.V3 -> prepareSelections(asset.candidate, true)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = R.string.sktjf_file_read_failed }
                finally { pending = false; progress = 0 }
            }
        }
    }
    val year = yearText.toIntOrNull()?.takeIf { it in 1900..9998 }
    val profile = profiles.singleOrNull { it.academicYear == year && jp.linkserver.nittcsc.sktjf.isSktjfUndergraduateClass(it.classId) }
    val canFetch = !pending && year != null && mode?.mode == SchedulerUsageMode.NITTC.name && profile != null
    val choosing = courseSelection
    electiveCandidate?.let { candidate ->
        val target = profiles.single { it.academicYear == candidate.timetable.document.academicYear && it.classId == candidate.timetable.document.classId }
        SktjfElectiveImportDialog(candidate, target.attributes(), electiveGroupIds, removedElectiveGroups, onConfirm = { selected ->
            electiveCandidate = null
            pending = true; error = 0; progress = R.string.sktjf_progress_compare
            scope.launch {
                try { prepareSelections(candidate, electiveLocalFile, selected) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = R.string.sktjf_cannot_apply }
                finally { pending = false; progress = 0 }
            }
        }, onCancel = { electiveCandidate = null })
        return
    }
    if (choosing != null) {
        SktjfCourseSelectionDialog(choosing, onConfirm = { selected ->
            courseSelection = null
            val timedCandidate = timedCourseSelection
            timedCourseSelection = null
            pending = true; error = 0; progress = R.string.sktjf_progress_compare
            scope.launch {
                try {
                    if (timedCandidate == null) prepareDocument(selected, choosing.localFile)
                    else {
                        val removedSlots = timedCandidate.document.lessons.map { it.dayOfWeek to it.period }.toSet() -
                            choosing.document.lessons.map { it.dayOfWeek to it.period }.toSet()
                        val lessons = selected.lessons + timedCandidate.document.lessons.filter { (it.dayOfWeek to it.period) in removedSlots }
                        val ids = lessons.map { it.id }.toSet()
                        prepareTimed(timedCandidate.copy(document = timedCandidate.document.copy(lessons = lessons),
                            ranges = timedCandidate.ranges.filterKeys { it in ids },
                            series = timedCandidate.series.mapNotNull { group ->
                                val members = group.lessonIds.intersect(ids)
                                if (members.isEmpty()) null else group.copy(lessonIds = members)
                            }), choosing.localFile)
                    }
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = R.string.sktjf_cannot_apply }
                finally { pending = false; progress = 0 }
            }
        }, onCancel = { courseSelection = null; timedCourseSelection = null })
        return
    }
    val current = prepared
    if (current != null) {
        SktjfMergeScreen(current, lessonChoices, calendarChoices, pending, progress, error, onApply = {
            pending = true; applying = true; error = 0; progress = R.string.sktjf_progress_apply
            scope.launch {
                try {
                    val lessons = current.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL } + lessonChoices.toMap()
                    val dates = current.calendarConflicts.associate { it.date to SktjfConflictChoice.KEEP_LOCAL } + calendarChoices.toMap()
                    when (apply(current, lessons, dates)) {
                        SktjfApplyResult.Applied -> { onApplied(); onDismiss() }
                        SktjfApplyResult.Stale -> { prepared = null; error = R.string.sktjf_stale }
                        SktjfApplyResult.Blocked -> error = R.string.sktjf_cannot_apply
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = R.string.sktjf_cannot_apply }
                finally { pending = false; applying = false; progress = 0 }
            }
        }, onDismiss = { if (!applying) onDismiss() })
        return
    }
    AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = { Text(stringResource(R.string.sktjf_import_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                if (pending) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (progress != 0) Text(stringResource(progress), style = MaterialTheme.typography.bodyMedium)
                }
                if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = canFetch, onClick = {
                pending = true; error = 0; progress = R.string.sktjf_progress_search
                scope.launch {
                    try {
                        when (val found = discover(year!!, term)) {
                            is SktjfDiscoveryResult.Found -> {
                                progress = R.string.sktjf_progress_download
                                when (val result = fetch(found.revision, profile!!.classId)) {
                                is SktjfFetchResult.Success -> {
                                    progress = R.string.sktjf_progress_compare
                                    if (result.selections != null) {
                                        prepareSelections(result.selections)
                                    } else if (result.timed != null) {
                                        prepareTimed(result.timed)
                                    } else prepareDocument(result.document, false)
                                }
                                SktjfFetchResult.ClassNotFound -> error = R.string.sktjf_not_published
                                SktjfFetchResult.ReleaseNotFound -> error = R.string.sktjf_invalid_data
                                SktjfFetchResult.RateLimited -> error = R.string.sktjf_rate_limited
                                SktjfFetchResult.NetworkFailure -> error = R.string.sktjf_network_error
                                is SktjfFetchResult.HttpFailure -> error = R.string.sktjf_fetch_failed
                                SktjfFetchResult.InvalidData -> error = R.string.sktjf_invalid_data
                                }
                            }
                            SktjfDiscoveryResult.NotPublished -> error = R.string.sktjf_not_published
                            SktjfDiscoveryResult.RateLimited -> error = R.string.sktjf_rate_limited
                            SktjfDiscoveryResult.NetworkFailure -> error = R.string.sktjf_network_error
                            is SktjfDiscoveryResult.HttpFailure -> error = R.string.sktjf_fetch_failed
                            SktjfDiscoveryResult.InvalidData -> error = R.string.sktjf_invalid_data
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = R.string.sktjf_invalid_data }
                    finally { pending = false; progress = 0 }
                }
            }) { Text(stringResource(R.string.sktjf_check_distribution)) }
        },
        dismissButton = { TextButton(enabled = !applying, onClick = onDismiss) { Text(stringResource(R.string.nittc_cancel)) } }
    )
}

@Composable
internal fun ChoiceRow(label: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(stringResource(label), modifier = Modifier.padding(top = 12.dp))
    }
}

internal fun lessonSubjectWithRange(resources: android.content.res.Resources, value: SktjfLessonContent): String {
    val subject = value.subject.ifBlank { resources.getString(R.string.sktjf_none) }
    val range = value.weekRange?.range
    return when {
        range?.start != null && range.end != null -> resources.getString(R.string.sktjf_timed_between, range.start, range.end, subject)
        range?.start != null -> resources.getString(R.string.sktjf_timed_from, range.start, subject)
        range?.end != null -> resources.getString(R.string.sktjf_timed_until, range.end, subject)
        else -> subject
    }
}

internal fun lessonDetails(resources: android.content.res.Resources, value: SktjfLessonContent): String = buildList {
    add(lessonSubjectWithRange(resources, value))
    if (value.teachers.isNotEmpty()) add(value.teachers.joinToString("、"))
    if (value.rooms.isNotEmpty()) add(value.rooms.joinToString("、"))
    add(when (value.cycle) { SktjfCycle.WEEKLY -> resources.getString(R.string.sktjf_weekly); SktjfCycle.A -> "A"; SktjfCycle.B -> "B" })
}.joinToString(" / ")

internal fun calendarDetails(resources: android.content.res.Resources, value: SktjfCalendarContent?): String = value?.let {
    fun type(type: SktjfDayType?) = when (type) { SktjfDayType.HOLIDAY -> resources.getString(R.string.sktjf_holiday); else -> type?.name }
    val weekdays = resources.getStringArray(R.array.sktjf_weekdays)
    listOfNotNull(type(it.dayType), it.overrideDayOfWeek?.let { day ->
        resources.getString(R.string.sktjf_override_day, weekdays.getOrElse(day - 1) { "?" })
    }, type(it.overrideDayType), it.event?.name).joinToString(" / ").ifEmpty { resources.getString(R.string.sktjf_none) }
} ?: resources.getString(R.string.sktjf_none)
