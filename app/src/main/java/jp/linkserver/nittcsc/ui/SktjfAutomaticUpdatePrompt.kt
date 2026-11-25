package jp.linkserver.nittcsc.ui

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.UsageModeEntity
import jp.linkserver.nittcsc.data.NittcYearTransitionEntity
import jp.linkserver.nittcsc.data.attributes
import jp.linkserver.nittcsc.logic.nittcGraduatedForYear
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.timetableTermForDate
import jp.linkserver.nittcsc.sktjf.*
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun SktjfAutomaticUpdatePrompt(
    viewModel: SchedulerViewModel, mode: UsageModeEntity?, profiles: List<NittcProfileEntity>, transitions: List<NittcYearTransitionEntity>,
    settings: SettingsEntity?, resumeRequest: Long, onApplied: () -> Unit,
    noticePreferences: SharedPreferences? = null,
    checkUpdates: suspend (Int, String, (SktjfUpdateKey) -> String?) -> SktjfAutomaticCheckResult = viewModel::checkSktjfUpdates,
    previewUpdate: suspend (SktjfDocument) -> SktjfPreviewResult = viewModel::previewSktjf,
    applyUpdate: suspend (SktjfApplyPreview, Map<String, SktjfConflictChoice>, Map<LocalDate, SktjfConflictChoice>) -> SktjfApplyResult = viewModel::applySktjf,
    previewTimedUpdate: suspend (SktjfV2Candidate) -> SktjfV2ApplyPreview? = viewModel::previewTimedSktjf
) {
    val context = LocalContext.current
    val prefs = remember(context, noticePreferences) { noticePreferences ?: context.getSharedPreferences(SKTJF_UPDATE_NOTICE_PREFERENCES, Context.MODE_PRIVATE) }
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var offer by remember { mutableStateOf<SktjfAutomaticOffer?>(null) }
    var review by remember { mutableStateOf<SktjfApplyPreview?>(null) }
    var selectionOffer by remember { mutableStateOf<SktjfAutomaticOffer?>(null) }
    var yearOffer by remember { mutableStateOf<Int?>(null) }
    var applying by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currentYear = academicYearForDate(rememberCurrentDate())
    val scopeKey = profiles.filter { it.academicYear in currentYear..(currentYear + 1) }
        .sortedBy { it.academicYear }.joinToString("|") { "${it.academicYear}:${it.classId}" }
    fun noticeKey(key: SktjfUpdateKey) = "notice:${key.academicYear}:${key.term}:${key.classId}"
    fun acknowledge(current: SktjfAutomaticOffer) {
        prefs.edit().putString(noticeKey(current.key), current.document.revision)
            .putLong("next:$scopeKey", System.currentTimeMillis() + 6 * 60 * 60 * 1000L).apply()
    }
    fun retryLater() {
        prefs.edit().putLong("next:$scopeKey", System.currentTimeMillis() + 10 * 60 * 1000L).apply()
    }
    fun needsCourseChoice(candidate: SktjfAutomaticOffer): Boolean {
        val profile = profiles.singleOrNull { it.academicYear == candidate.key.academicYear && it.classId == candidate.key.classId }
            ?: return false
        val timed = candidate.timed
        val slots = sktjfProfileSlotOptions(timed?.documentForMerge() ?: candidate.document, profile.attributes())
        if (timed != null) return slots.any { slot -> timed.series.none { group ->
            group.dayOfWeek == slot.day && slot.period in group.periods &&
                slot.lessons.all { it.id in group.lessonIds } && slot.lessons.any { it.id in timed.ranges }
        } }
        return slots.isNotEmpty() && slots.none { slot -> slot.lessons.any { it.weekRange != null } }
    }

    LaunchedEffect(mode, profiles, transitions, settings, currentYear, resumeRequest, resumed, offer != null, review != null, selectionOffer != null, yearOffer) {
        if (!resumed || mode?.mode != SchedulerUsageMode.NITTC.name || offer != null || review != null ||
            selectionOffer != null || yearOffer != null || settings == null) return@LaunchedEffect
        val relevant = profiles.any { it.academicYear in currentYear..(currentYear + 1) }
        if (!relevant) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val deadline = prefs.getLong("next:$scopeKey", 0L)
        if (deadline - now in 1..(6 * 60 * 60 * 1000L)) return@LaunchedEffect
        val term = if (timetableTermForDate(LocalDate.now(), settings.enableSemesterTimetables,
                settings.secondTermStartMonth, settings.secondTermStartDay) == TimetableTerm.FIRST) "S" else "F"
        val result = try {
            checkUpdates(currentYear, term) { key -> prefs.getString(noticeKey(key), null) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { SktjfAutomaticCheckResult.Failed }
        when (result) {
            is SktjfAutomaticCheckResult.Available -> {
                val available = result.offer
                if (available.timed != null) {
                    if (needsCourseChoice(available)) { offer = available; return@LaunchedEffect }
                    val prepared = try { previewTimedUpdate(available.timed)?.forReview() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                    if (prepared == null) retryLater()
                    else if (prepared.hasExistingLessons()) offer = available
                    else {
                        val applied = try {
                            applyUpdate(prepared,
                                prepared.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL },
                                prepared.calendarConflicts.associate { it.date to if (it.projection is SktjfCalendarProjection.Supported)
                                    SktjfConflictChoice.APPLY_INCOMING else SktjfConflictChoice.KEEP_LOCAL })
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { SktjfApplyResult.Blocked }
                        if (applied == SktjfApplyResult.Applied) {
                            acknowledge(available); onApplied()
                            Toast.makeText(context, R.string.sktjf_applied, Toast.LENGTH_SHORT).show()
                        } else retryLater()
                    }
                    return@LaunchedEffect
                }
                if (needsCourseChoice(available)) {
                    offer = available
                    return@LaunchedEffect
                }
                val prepared = try { previewUpdate(available.document) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { SktjfPreviewResult.Blocked }
                if (prepared is SktjfPreviewResult.Ready && !prepared.preview.hasExistingLessons()) {
                    val candidate = prepared.preview
                    val applied = try {
                        applyUpdate(candidate,
                            candidate.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL },
                            candidate.calendarConflicts.associate { it.date to if (it.projection is SktjfCalendarProjection.Supported)
                                SktjfConflictChoice.APPLY_INCOMING else SktjfConflictChoice.KEEP_LOCAL })
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { SktjfApplyResult.Blocked }
                    if (applied == SktjfApplyResult.Applied) {
                        acknowledge(available); onApplied()
                        Toast.makeText(context, R.string.sktjf_applied, Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.edit().putLong("next:$scopeKey", now + 10 * 60 * 1000L).apply()
                    }
                } else if (prepared is SktjfPreviewResult.Ready) offer = available
                else prefs.edit().putLong("next:$scopeKey", now + 10 * 60 * 1000L).apply()
            }
            is SktjfAutomaticCheckResult.NeedsAffiliation -> {
                val promptedKey = "affiliation-prompted:${result.year}"
                val shouldPrompt = !prefs.getBoolean(promptedKey, false)
                prefs.edit().putBoolean(affiliationReminderKey(result.year), true).apply()
                if (shouldPrompt) yearOffer = result.year
                else prefs.edit().putLong("next:$scopeKey", now + 6 * 60 * 60 * 1000L).apply()
            }
            SktjfAutomaticCheckResult.NoUpdate -> prefs.edit().putLong("next:$scopeKey", now + 6 * 60 * 60 * 1000L).apply()
            SktjfAutomaticCheckResult.Failed -> prefs.edit().putLong("next:$scopeKey", now + 10 * 60 * 1000L).apply()
            SktjfAutomaticCheckResult.Stale -> Unit
        }
    }
    LaunchedEffect(mode, profiles, transitions) {
        val key = offer?.key ?: selectionOffer?.key ?: review?.document?.let { SktjfUpdateKey(it.academicYear, it.term, it.classId) }
        if (mode?.mode != SchedulerUsageMode.NITTC.name || (key != null && profiles.none { it.academicYear == key.academicYear && it.classId == key.classId })) {
            offer = null; review = null; selectionOffer = null
        }
        if (mode?.mode != SchedulerUsageMode.NITTC.name || profiles.any { it.academicYear == yearOffer } ||
            yearOffer?.let { nittcGraduatedForYear(it, profiles, transitions) } == true) yearOffer = null
    }
    val current = offer
    if (current != null && mode?.mode == SchedulerUsageMode.NITTC.name && profiles.any {
            it.academicYear == current.key.academicYear && it.classId == current.key.classId
        }) {
        val yearLabel = when (current.key.academicYear) {
            currentYear -> stringResource(R.string.sktjf_this_year)
            currentYear + 1 -> stringResource(R.string.sktjf_next_year)
            else -> stringResource(R.string.sktjf_named_year, current.key.academicYear)
        }
        val termLabel = stringResource(if (current.key.term == "S") R.string.sktjf_first_term else R.string.sktjf_second_term)
        val message = if (current.kind == SktjfUpdateKind.INITIAL) R.string.sktjf_new_notice else R.string.sktjf_revision_notice
        AlertDialog(
            onDismissRequest = { if (!applying) { acknowledge(current); offer = null } },
            title = { Text(stringResource(R.string.sktjf_notice_title)) },
            text = {
                Column {
                    Text(stringResource(message, yearLabel, termLabel))
                    if (applying) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (progress != 0) Text(stringResource(progress))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !applying, onClick = {
                    if (current.timed != null || needsCourseChoice(current)) {
                        selectionOffer = current; offer = null
                        return@TextButton
                    }
                    applying = true; progress = R.string.sktjf_progress_compare
                    scope.launch {
                        try {
                            when (val result = previewUpdate(current.document)) {
                                is SktjfPreviewResult.Ready -> {
                                    val source = result.preview.snapshot.sourceStates.singleOrNull {
                                        it.academicYear == current.key.academicYear && it.term == current.key.term && it.classId == current.key.classId
                                    }
                                    when (sktjfNoticeDecision(current.key, current.document.revision, source.officialSktjfRevision(), null)) {
                                        SktjfNoticeDecision.AlreadyHandled -> {
                                            offer = null; Toast.makeText(context, R.string.sktjf_already_applied, Toast.LENGTH_SHORT).show(); return@launch
                                        }
                                        SktjfNoticeDecision.Invalid -> {
                                            offer = null; Toast.makeText(context, R.string.sktjf_cannot_apply, Toast.LENGTH_SHORT).show(); return@launch
                                        }
                                        is SktjfNoticeDecision.Offer -> Unit
                                    }
                                    if (result.preview.hasExistingLessons()) {
                                        review = result.preview; offer = null
                                    } else {
                                        progress = R.string.sktjf_progress_apply
                                        when (applyUpdate(result.preview,
                                        result.preview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL },
                                        result.preview.calendarConflicts.associate { it.date to if (it.projection is SktjfCalendarProjection.Supported)
                                            SktjfConflictChoice.APPLY_INCOMING else SktjfConflictChoice.KEEP_LOCAL })) {
                                        SktjfApplyResult.Applied -> { acknowledge(current); offer = null; onApplied(); Toast.makeText(context, R.string.sktjf_applied, Toast.LENGTH_SHORT).show() }
                                        SktjfApplyResult.Stale -> { retryLater(); offer = null; Toast.makeText(context, R.string.sktjf_stale, Toast.LENGTH_SHORT).show() }
                                        SktjfApplyResult.Blocked -> { retryLater(); offer = null; Toast.makeText(context, R.string.sktjf_cannot_apply, Toast.LENGTH_SHORT).show() }
                                        }
                                    }
                                }
                                SktjfPreviewResult.Blocked -> { retryLater(); offer = null; Toast.makeText(context, R.string.sktjf_cannot_apply, Toast.LENGTH_SHORT).show() }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { retryLater(); offer = null; Toast.makeText(context, R.string.sktjf_cannot_apply, Toast.LENGTH_SHORT).show() }
                        finally { applying = false; progress = 0 }
                    }
                }) { Text(stringResource(R.string.sktjf_notice_apply)) }
            },
            dismissButton = { TextButton(enabled = !applying, onClick = { acknowledge(current); offer = null }) { Text(stringResource(R.string.nittc_cancel)) } }
        )
    }
    selectionOffer?.let { selectedOffer ->
        SktjfImportDialog(mode, profiles, currentYear, viewModel::discoverSktjf, viewModel::fetchSktjf,
            previewUpdate, applyUpdate,
            { acknowledge(selectedOffer); onApplied(); Toast.makeText(context, R.string.sktjf_applied, Toast.LENGTH_SHORT).show() },
            { if (prefs.getString(noticeKey(selectedOffer.key), null) != selectedOffer.document.revision) retryLater(); selectionOffer = null },
            automaticUpdate = true, initialDocument = selectedOffer.document,
            initialTimed = selectedOffer.timed, previewTimed = previewTimedUpdate)
    }
    review?.let { preview ->
        SktjfImportDialog(mode, profiles, currentYear, viewModel::discoverSktjf, viewModel::fetchSktjf,
            previewUpdate, applyUpdate,
            {
                acknowledge(SktjfAutomaticOffer(SktjfUpdateKey(preview.document.academicYear, preview.document.term,
                    preview.document.classId), SktjfUpdateKind.REVISION, preview.document))
                onApplied(); Toast.makeText(context, R.string.sktjf_applied, Toast.LENGTH_SHORT).show()
            },
            {
                val key = SktjfUpdateKey(preview.document.academicYear, preview.document.term, preview.document.classId)
                if (prefs.getString(noticeKey(key), null) != preview.document.revision) retryLater()
                review = null
            }, initialPreview = preview, automaticUpdate = true)
    }
    yearOffer?.takeIf { mode?.mode == SchedulerUsageMode.NITTC.name && profiles.none { profile -> profile.academicYear == it } &&
        !nittcGraduatedForYear(it, profiles, transitions) }?.let { year ->
        NittcYearTransitionDialog(year, mode, profiles, transitions, viewModel::recordNittcYearOutcome,
            viewModel::completeNittcYearOutcome, {
                prefs.edit().putBoolean("affiliation-prompted:$year", true)
                    .putLong("next:$scopeKey", System.currentTimeMillis() + 6 * 60 * 60 * 1000L).apply()
                yearOffer = null
            }, announced = true)
    }
}
