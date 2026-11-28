package jp.linkserver.nittcsc.sktjf

import android.util.Log
import androidx.room.withTransaction
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.sameSubject
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

internal fun blockedSktjfApply(phase: String, cause: Throwable? = null): SktjfApplyResult.Blocked {
    if (cause is CancellationException) throw cause
    val error = cause?.let { " error=${it.javaClass.simpleName} at=${it.stackTrace.firstOrNull()}" }.orEmpty()
    Log.w("SktjfApply", "phase=$phase$error")
    return SktjfApplyResult.Blocked
}

internal data class SktjfApplySnapshot(
    val settings: SettingsEntity?, val mode: UsageModeEntity?, val profiles: List<NittcProfileEntity>,
    val lessons: List<LessonEntity>, val days: List<DayTypeEntity>, val sourceStates: List<SktjfSourceStateEntity>,
    val extraLessons: List<SktjfExtraLessonEntity>, val referencedExtraIds: Set<String> = emptySet(),
    val selectionGroups: List<SktjfSelectionGroupEntity> = emptyList()
)
internal data class SktjfApplyPreview(
    val document: SktjfDocument, val snapshot: SktjfApplySnapshot,
    val lessonConflicts: List<SktjfLessonConflict>, val calendarConflicts: List<SktjfCalendarConflict>,
    val localFile: Boolean = false, val lessonReview: List<SktjfLessonReview> = emptyList(),
    val timed: SktjfV2ApplyPreview? = null,
    val calendarDays: Map<LocalDate, SktjfCalendarContent> = emptyMap(),
)

/** A previous import still needs review even if all its lessons were deleted. */
internal fun SktjfApplyPreview.hasExistingLessons(): Boolean = snapshot.lessons.any { row ->
    val term = if (document.term == "S") TimetableTerm.FIRST else TimetableTerm.SECOND
    if (row.academicYear != document.academicYear || row.timetableTerm != term) return@any false
    when (row.mode) {
        LessonMode.WEEKLY -> row.weeklySubject.isNotBlank() || row.weeklyTeacher.isNotBlank() || !row.weeklyLocation.isNullOrBlank()
        LessonMode.ALTERNATING -> row.aSubject.isNotBlank() || row.aTeacher.isNotBlank() || !row.aLocation.isNullOrBlank() ||
            row.bSubject.isNotBlank() || row.bTeacher.isNotBlank() || !row.bLocation.isNullOrBlank()
    }
} || snapshot.extraLessons.any {
    it.academicYear == document.academicYear && it.term == document.term
} || snapshot.selectionGroups.any {
    it.academicYear == document.academicYear && it.term == document.term
} || snapshot.sourceStates.any {
    it.academicYear == document.academicYear && it.term == document.term
}
internal sealed interface SktjfPreviewResult {
    data class Ready(val preview: SktjfApplyPreview) : SktjfPreviewResult
    data object Blocked : SktjfPreviewResult
}
internal sealed interface SktjfApplyResult {
    data object Applied : SktjfApplyResult
    data object Stale : SktjfApplyResult
    data object Blocked : SktjfApplyResult
}

internal data class SktjfV2ApplyPreview(val snapshot: SktjfApplySnapshot, val preparation: SktjfV2MergePreparation.Ready,
    val localFile: Boolean = false, val selectionGroups: List<SktjfSelectionGroup> = emptyList(), val hasExplicitSelectionGroups: Boolean = false,
    val selectionChoices: Map<String,String?> = emptyMap(), val classSwitch: Boolean = false)

internal fun SktjfV2ApplyPreview.forReview(): SktjfApplyPreview = SktjfApplyPreview(
    document = preparation.candidate.documentForMerge(), snapshot = snapshot,
    lessonConflicts = preparation.lessons.conflicts, calendarConflicts = preparation.calendar.conflicts,
    localFile = localFile, lessonReview = preparation.lessons.reviewItems, timed = this,
    calendarDays = preparation.calendar.plannedDays)

/** Internal integration point; caller must refresh notification/calendar/widget schedules after Applied. */
internal class SktjfApplyCoordinator(private val db: AppDatabase, private val repository: SchedulerRepository) {
    private val dao = db.schedulerDao()

    private suspend fun snapshot(document: SktjfDocument): SktjfApplySnapshot {
        val term = if (document.term == "S") TimetableTerm.FIRST else TimetableTerm.SECOND
        val extras = dao.getSktjfExtraLessons()
        val extraIds = extras.map { it.id }.toSet()
        val references = if (extraIds.isEmpty()) emptySet() else
            (dao.getTasksOnce().mapNotNull { it.extraLessonId } + dao.getPlansOnce().mapNotNull { it.extraLessonId }).filter { it in extraIds }.toSet()
        return SktjfApplySnapshot(dao.getSettings(), dao.getUsageMode(), dao.getNittcProfilesOnce(),
            dao.getLessonsOnce().filter { it.academicYear == document.academicYear && it.timetableTerm == term },
            dao.getDayTypesOnce().sortedBy { it.date }, dao.getSktjfSourceStates(), extras, references,
            dao.getSktjfSelectionGroups().filter { it.academicYear == document.academicYear && it.term == document.term })
    }

    private fun source(document: SktjfDocument, snapshot: SktjfApplySnapshot) = snapshot.sourceStates.singleOrNull {
        it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId
    }

    private fun isClassSwitch(document: SktjfDocument, current: SktjfApplySnapshot): Boolean {
        val installed = installedSource(document, current)
        val differentSource = if (installed != null) installed.classId != document.classId else current.sourceStates.any {
            it.academicYear == document.academicYear && it.term == document.term && it.classId != document.classId
        }
        return differentSource ||
            current.extraLessons.any { it.academicYear == document.academicYear && it.term == document.term && it.classId != document.classId } ||
            current.selectionGroups.any { it.classId != document.classId }
    }

    private fun installedSource(document: SktjfDocument, current: SktjfApplySnapshot) =
        installedSktjfSources(current.sourceStates)[document.academicYear to document.term]

    private fun acceptable(document: SktjfDocument, snapshot: SktjfApplySnapshot): Boolean {
        if (document.term !in setOf("S", "F") || snapshot.settings == null || snapshot.mode?.mode != SchedulerUsageMode.NITTC.name ||
            !settingsSupportSktjf(snapshot.settings, document)) return false
        if (snapshot.profiles.singleOrNull { it.academicYear == document.academicYear }?.classId != document.classId) return false
        if (!isSktjfUndergraduateClass(document.classId)) return false
        val incoming = parseSktjfRevision(document.revision) ?: return false
        if (incoming.year != document.academicYear || incoming.term != document.term) return false
        val old = source(document, snapshot) ?: return true
        if (old.isLocalSktjfSource()) return true
        val previous = parseSktjfRevision(old.revision) ?: return false
        return incoming.beta != previous.beta || incoming.number >= previous.number
    }

    /** Prepares a v2 merge without writing; it is not an authorization to apply it. */
    suspend fun previewV2(candidate: SktjfV2Candidate): SktjfV2ApplyPreview? = db.withTransaction {
        if (!candidate.weekEvidenceComplete) return@withTransaction null
        val current = snapshot(candidate.document)
        if (!acceptable(candidate.document, current)) return@withTransaction null
        if (isClassSwitch(candidate.document, current)) return@withTransaction previewClassSwitch(candidate, current)
        if (current.selectionGroups.isNotEmpty()) return@withTransaction null
        val profile = current.profiles.singleOrNull { it.academicYear == candidate.document.academicYear }
            ?: return@withTransaction null
        val state = source(candidate.document, current)?.takeUnless { it.requiresRelinking }
        val extras = current.extraLessons.filter { it.academicYear == candidate.document.academicYear &&
            it.term == candidate.document.term && it.classId == candidate.document.classId }
        val prepared = prepareSktjfV2Merge(candidate, profile.attributes(), current.lessons, current.days, state, extras)
            as? SktjfV2MergePreparation.Ready ?: return@withTransaction null
        SktjfV2ApplyPreview(current, prepared)
    }

    suspend fun previewV3(candidate: SktjfV3Candidate, choices: Map<String,String?> = emptyMap()): SktjfV2ApplyPreview? = db.withTransaction {
        val document = candidate.timetable.document
        if (!candidate.timetable.weekEvidenceComplete) return@withTransaction null
        val current = snapshot(document)
        if (!acceptable(document, current)) return@withTransaction null
        val profile = current.profiles.singleOrNull { it.academicYear == document.academicYear } ?: return@withTransaction null
        if (choices.keys.any { id -> candidate.selectionGroups.none { it.id == id } }) return@withTransaction null
        if (choices.any { (groupId, selected) -> selected != null && candidate.selectionGroups.none { group ->
            group.id == groupId && selected in group.lessonIds && document.lessons.any { it.id == selected && it.conditions.matches(profile.attributes()) }
        } }) return@withTransaction null
        if (isClassSwitch(document, current)) return@withTransaction previewClassSwitch(
            candidate.timetable, current, candidate.selectionGroups, choices)
        val state = source(document, current)?.takeUnless { it.requiresRelinking }
        val incomingIds = candidate.selectionGroups.flatMap { it.lessonIds }.toSet()
        val retiredIds = current.selectionGroups.flatMap { sktjfGroupIds(it.retiredLessonIdsJson) }.toSet() - incomingIds
        val extras = current.extraLessons.filter { it.academicYear == document.academicYear && it.term == document.term &&
            it.classId == document.classId && it.sourceLessonId !in retiredIds }
        val previousIds = current.selectionGroups.flatMap { group ->
            sktjfGroupIds(group.lessonIdsJson) + sktjfGroupIds(group.retiredLessonIdsJson)
        }.toSet()
        val prepared = prepareSktjfV2Merge(candidate.timetable, profile.attributes(), current.lessons, current.days, state, extras,
            candidate.selectionGroups, previousIds)
            as? SktjfV2MergePreparation.Ready ?: return@withTransaction null
        SktjfV2ApplyPreview(current, prepared, selectionGroups = candidate.selectionGroups, hasExplicitSelectionGroups = true, selectionChoices = choices.toMap())
    }

    private fun previewClassSwitch(candidate: SktjfV2Candidate, current: SktjfApplySnapshot,
        groups: List<SktjfSelectionGroup> = emptyList(), choices: Map<String, String?> = emptyMap()): SktjfV2ApplyPreview? {
        val document = candidate.document
        val profile = current.profiles.single { it.academicYear == document.academicYear }
        val installed = installedSource(document, current)
        val prepared = prepareSktjfV2Merge(candidate, profile.attributes(), current.lessons, current.days, state = null,
            selectionGroups = groups, calendarState = installed, classSwitch = { prepareSktjfClassSwitch(it, current, installed) })
            as? SktjfV2MergePreparation.Ready ?: return null
        return SktjfV2ApplyPreview(current, prepared, selectionGroups = groups, hasExplicitSelectionGroups = true,
            selectionChoices = choices.toMap(), classSwitch = true)
    }

    /** Commit a validated v2 preview without dropping its opportunity evidence. */
    suspend fun applyV2(preview: SktjfV2ApplyPreview, lessonChoices: Map<String, SktjfConflictChoice>,
        calendarChoices: Map<LocalDate, SktjfConflictChoice>): SktjfApplyResult = db.withTransaction {
        val candidate = preview.preparation.candidate
        val document = candidate.document
        val current = snapshot(document)
        if (current != preview.snapshot || !acceptable(document, current)) {
            Log.w("SktjfApply", "phase=timed_preview_changed snapshotChanged=${current != preview.snapshot}")
            return@withTransaction SktjfApplyResult.Stale
        }
        if (!preview.hasExplicitSelectionGroups && current.selectionGroups.isNotEmpty()) return@withTransaction blockedSktjfApply("selection_groups_missing")
        if (!candidate.weekEvidenceComplete) return@withTransaction blockedSktjfApply("week_evidence_missing")
        val oldState = source(document, current)?.takeUnless { it.requiresRelinking || preview.classSwitch }
        val existingExtras = current.extraLessons.filter { it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId }
        val incomingGroupIds = preview.selectionGroups.flatMap { it.lessonIds }.toSet()
        val retiredIds = current.selectionGroups.flatMap { sktjfGroupIds(it.retiredLessonIdsJson) }.toSet() - incomingGroupIds
        val parked = if (preview.classSwitch) emptyList() else existingExtras.filter { it.sourceLessonId in retiredIds }
        val activeExtras = if (preview.classSwitch) emptyList() else existingExtras - parked.toSet()
        val resolved = runCatching { preview.preparation.lessons.resolve(lessonChoices, preview.selectionGroups) }
            .getOrElse { return@withTransaction blockedSktjfApply("lesson_choices", it) }
        val removedExtraIds = resolved.clearedLocalKeys.filter { it.startsWith("extra:") }.map { it.removePrefix("extra:") }.toSet()
        val preserveRemoved = activeExtras.filter { it.id in removedExtraIds && it.id in current.referencedExtraIds }
        val newlyRetired = mutableMapOf<String, MutableSet<String>>()
        for (extra in preserveRemoved) {
            val sourceId = extra.sourceLessonId ?: return@withTransaction blockedSktjfApply("retired_alternative_unlinked")
            val owner = current.selectionGroups.singleOrNull { sourceId in sktjfGroupIds(it.lessonIdsJson) || sourceId in sktjfGroupIds(it.retiredLessonIdsJson) }
                ?: return@withTransaction blockedSktjfApply("retired_alternative_owner_missing")
            newlyRetired.getOrPut(owner.groupId) { mutableSetOf() }.add(sourceId)
        }
        val calendar = runCatching { preview.preparation.calendar.resolve(calendarChoices) }
            .getOrElse { return@withTransaction blockedSktjfApply("calendar_choices", it) }
        // A kept local slot must not acquire the incoming group's metadata: that would
        // hide the kept lesson or attach a selection to candidates never accepted.
        val eligibleIds = candidate.document.lessons.map { it.id }.toSet()
        val resolvedIds = resolved.lessons.mapNotNull { it.sourceLessonId }.toSet()
        val acceptedGroups = preview.selectionGroups.filter { group ->
            val members = group.lessonIds.filter { it in eligibleIds }
            members.isNotEmpty() && members.all { it in resolvedIds }
        }
        val keptGroupIds = preview.selectionGroups.map { it.id }.toSet() - acceptedGroups.map { it.id }.toSet()
        if (preview.selectionGroups.any { group -> group.id in keptGroupIds &&
                group.lessonIds.any { lessonChoices[it] == SktjfConflictChoice.APPLY_INCOMING } })
            return@withTransaction blockedSktjfApply("partial_selection_group_adoption")
        val keptExtras = if (preview.classSwitch) resolved.lessons.filter { it.localKey?.startsWith("extra:") == true } else emptyList()
        val storageMerge = if (preview.classSwitch) resolved.copy(lessons = resolved.lessons - keptExtras.toSet(),
            clearedLocalKeys = resolved.clearedLocalKeys.filterNot { it.startsWith("extra:") }.toSet()) else resolved
        val draft = draftSktjfTimedStorage(candidate, current.lessons, activeExtras, storageMerge, calendar.baseline, acceptedGroups,
            onBlocked = { reason ->
                blockedSktjfApply("timed_storage reason=$reason classSwitch=${preview.classSwitch}")
            })
            ?: return@withTransaction SktjfApplyResult.Blocked
        val groups = if (preview.hasExplicitSelectionGroups) runCatching {
            val previous = if (preview.classSwitch) emptyList() else current.selectionGroups.filter { it.classId == document.classId }
            val keptPreviousIds = preview.selectionGroups.filter { it.id in keptGroupIds }.mapNotNull {
                matchingSktjfSelectionGroup(it, previous)?.groupId
            }.toSet()
            val merged = mergeSktjfSelectionGroups(SktjfV3Candidate(candidate, acceptedGroups), previous.filterNot { it.groupId in keptPreviousIds }, newlyRetired)
            val kept = previous.filter { it.groupId in keptPreviousIds }.map { group ->
                val retired = sktjfGroupIds(group.retiredLessonIdsJson) + newlyRetired[group.groupId].orEmpty()
                group.copy(retiredLessonIdsJson = org.json.JSONArray((retired - sktjfGroupIds(group.lessonIdsJson)).sorted()).toString())
            }
            (merged + kept)
                .map { if (it.groupId !in keptGroupIds && preview.selectionChoices.containsKey(it.groupId)) it.copy(selectedLessonId = preview.selectionChoices[it.groupId]) else it }
                .also(SktjfSelectionGroupBackup::validateList)
        }.getOrElse { return@withTransaction blockedSktjfApply("selection_groups_storage", it) } else emptyList()
        val calendarState = if (preview.classSwitch) installedSource(document, current) else oldState
        val oldCalendar = calendarState?.let { sktjfCalendarBaseline(document, it) }
        val localCalendar = projectSktjfLocalCalendar(document.academicYear, document.term, document.classId, current.days, oldCalendar)
        val changedDays = mutableListOf<DayTypeEntity>()
        for ((date, content) in calendar.days) {
            if (localCalendar.days[date] == content) continue
            val day = projectSktjfCalendarDay(SktjfCalendarDay(date, content.dayType, content.overrideDayOfWeek,
                content.overrideDayType, content.event, emptyList())) as? SktjfCalendarProjection.Supported
                ?: return@withTransaction blockedSktjfApply("calendar_projection date=$date")
            changedDays += day.day
        }
        val links = runCatching { SktjfSourceLinks.encode(draft.ordinary.metadata.values.flatten().mapNotNull { lesson ->
            lesson.sourceLessonId?.let { id -> SktjfSourceLink(id, lesson.content.dayOfWeek, lesson.content.period, lesson.content.cycle) }
        }) }.getOrElse { return@withTransaction blockedSktjfApply("source_links", it) }
        val retainedReplacements = oldState?.let { state ->
            jp.linkserver.nittcsc.data.decodeReplacedTimedSlots(state.replacedTimedSlotsJson).filter { (day, period) ->
                preview.preparation.lessons.reviewItems.any { item -> item.day == day && item.period == period &&
                    item.sourceLessonId?.let { lessonChoices[it] == SktjfConflictChoice.KEEP_LOCAL } == true }
            }.toSet()
        } ?: preview.preparation.lessons.reviewItems.filter { item ->
            item.sourceLessonId?.let { lessonChoices[it] == SktjfConflictChoice.KEEP_LOCAL } == true &&
                candidate.series.any { series -> series.dayOfWeek == item.day && item.period in series.periods }
        }.map { it.day to it.period }.toSet()
        val source = runCatching { SktjfSourceStateEntity(document.academicYear, document.term, document.classId, document.revision,
            SktjfBaselineCodec.encode(draft.source.copy(localFile = preview.localFile)), false, links,
            jp.linkserver.nittcsc.data.encodeReplacedTimedSlots(retainedReplacements)).also(SktjfSourceStateBackup::validate) }
            .getOrElse { return@withTransaction blockedSktjfApply("source_snapshot", it) }
        val term = if (document.term == "S") TimetableTerm.FIRST else TimetableTerm.SECOND
        val storedAlternatives = runCatching {
            (draft.alternatives + parked + preserveRemoved + keptExtras.map { storeSktjfKeptClassSwitchExtra(document, it) })
                .also(SktjfExtraLessonBackup::validateList)
        }.getOrElse { return@withTransaction blockedSktjfApply("alternatives_storage", it) }
        if (preview.classSwitch) {
            detachReplacedClassReferences(document, current, draft.ordinary, resolved, storedAlternatives)
            dao.deleteOtherSktjfSourceStates(document.academicYear, document.term, document.classId)
        }
        draft.ordinary.rows.forEach { dao.upsertLesson(it) }
        if (preview.classSwitch || existingExtras.isNotEmpty()) dao.deleteSktjfExtraLessonsForPartition(document.academicYear, document.term)
        if (storedAlternatives.isNotEmpty()) dao.upsertSktjfExtraLessons(storedAlternatives)
        if (preview.hasExplicitSelectionGroups) {
            dao.deleteSktjfSelectionGroupsForPartition(document.academicYear, document.term)
            if (groups.isNotEmpty()) dao.upsertSktjfSelectionGroups(groups)
        }
        if (changedDays.isNotEmpty()) dao.upsertDayTypes(changedDays)
        dao.upsertSktjfSourceStates(listOf(source))
        repository.touchSyncDatasetMeta(LessonSyncPartition(document.academicYear, term).datasetKey, SchedulerRepository.DATASET_DAY_TYPES)
        SktjfApplyResult.Applied
    }

    private suspend fun detachReplacedClassReferences(
        document: SktjfDocument, current: SktjfApplySnapshot, ordinary: SktjfTimetableProjection.Ready,
        resolved: SktjfResolvedLessonMerge, storedExtras: List<SktjfExtraLessonEntity>,
    ) {
        val clearedRows = resolved.clearedLocalKeys.mapNotNull { key ->
            if (key.startsWith("base:")) key.substringAfter("base:").substringBefore(':').toLongOrNull() else null
        }.toSet()
        val affected = current.lessons.filter { it.id in clearedRows }.associateBy { it.id }
        val removedExtras = current.extraLessons.filter { it.academicYear == document.academicYear && it.term == document.term }
            .map { it.id }.toSet() - storedExtras.map { it.id }.toSet()
        fun replaced(id: Long?, subject: String, teacher: String?): Boolean {
            val old = affected[id] ?: return false
            return ordinary.metadata[old.dayOfWeek to old.slotIndex + 1].orEmpty().none { lesson ->
                // A reference may survive for a kept A/B lesson on the same room row.
                lesson.localKey != null && sameSubject(lesson.content.subject, subject) &&
                    (teacher.isNullOrBlank() || lesson.content.teachers.any { it.trim() == teacher.trim() })
            }
        }
        if (affected.isEmpty() && removedExtras.isEmpty()) return
        val now = System.currentTimeMillis()
        val tasks = dao.getTasksOnce().mapNotNull { task ->
            val removeExtra = task.extraLessonId in removedExtras
            val removeOrdinary = replaced(task.lessonId, task.subject, task.teacher)
            if (!removeExtra && !removeOrdinary) null else task.copy(
                extraLessonId = task.extraLessonId.takeUnless { removeExtra },
                lessonId = task.lessonId.takeUnless { removeOrdinary }, updatedAt = now)
        }
        val plans = dao.getPlansOnce().mapNotNull { plan ->
            val removeExtra = plan.extraLessonId in removedExtras
            val removeOrdinary = replaced(plan.lessonId, plan.subject, plan.teacher)
            if (!removeExtra && !removeOrdinary) null else plan.copy(
                extraLessonId = plan.extraLessonId.takeUnless { removeExtra },
                lessonId = plan.lessonId.takeUnless { removeOrdinary }, updatedAt = now)
        }
        if (tasks.isNotEmpty()) {
            dao.upsertTasks(tasks)
            repository.touchSyncDatasetMeta(SchedulerRepository.DATASET_TASKS)
        }
        if (plans.isNotEmpty()) {
            dao.upsertPlans(plans)
            repository.touchSyncDatasetMeta(SchedulerRepository.DATASET_PLANS)
        }
    }

    suspend fun preview(input: SktjfDocument): SktjfPreviewResult = db.withTransaction {
        val incoming = input.detachedForApply(); val current = snapshot(incoming)
        if (!acceptable(incoming, current)) return@withTransaction SktjfPreviewResult.Blocked
        if (isClassSwitch(incoming, current) || current.extraLessons.any {
                it.academicYear == incoming.academicYear && it.term == incoming.term && it.classId == incoming.classId && it.sourceLessonId == null
            } && current.selectionGroups.isEmpty()) {
            val candidate = SktjfV2Candidate(incoming, emptyMap(), emptyMap())
            val review = if (isClassSwitch(incoming, current)) previewClassSwitch(candidate, current) else previewV2(candidate)
            return@withTransaction review?.forReview()?.let { SktjfPreviewResult.Ready(it) } ?: SktjfPreviewResult.Blocked
        }
        if (current.selectionGroups.isNotEmpty()) return@withTransaction SktjfPreviewResult.Blocked
        val profile = current.profiles.singleOrNull { it.academicYear == incoming.academicYear }
            ?: return@withTransaction SktjfPreviewResult.Blocked
        val document = runCatching { selectSktjfLessons(incoming, profile.attributes()) }.getOrNull()
            ?: return@withTransaction SktjfPreviewResult.Blocked
        // A stale origin cannot be trusted after a restore or sync. Treat existing
        // lessons as local data and require explicit choices before replacing them.
        val state = source(document, current)?.takeUnless { it.requiresRelinking }
        val extras = current.extraLessons.filter { it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId }
        val planningState = if (extras.isEmpty()) state else (prepareLegacyExtraMigration(state, extras, current.referencedExtraIds)
            ?: return@withTransaction SktjfPreviewResult.Blocked)
        val lesson = prepareSktjfLessonMerge(document, current.lessons, planningState) as? SktjfLessonPreparation.Ready
            ?: return@withTransaction SktjfPreviewResult.Blocked
        val calendar = prepareSktjfCalendarMerge(document, current.days, state) as? SktjfCalendarPreparation.Ready
            ?: return@withTransaction SktjfPreviewResult.Blocked
        SktjfPreviewResult.Ready(SktjfApplyPreview(document, current, lesson.plan.conflicts, calendar.plan.conflicts,
            lessonReview = lesson.plan.reviewItems, calendarDays = calendar.plan.plannedDays))
    }

    suspend fun apply(preview: SktjfApplyPreview, lessonChoices: Map<String, SktjfConflictChoice>,
        calendarChoices: Map<LocalDate, SktjfConflictChoice>): SktjfApplyResult = db.withTransaction {
        val document = preview.document
        val current = snapshot(document)
        if (current != preview.snapshot || !acceptable(document, current)) {
            Log.w("SktjfApply", "phase=legacy_preview_changed snapshotChanged=${current != preview.snapshot}")
            return@withTransaction SktjfApplyResult.Stale
        }
        val state = source(document, current)?.takeUnless { it.requiresRelinking }
        val existingExtras = current.extraLessons.filter { it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId }
        val planningState = if (existingExtras.isEmpty()) state else (prepareLegacyExtraMigration(state, existingExtras, current.referencedExtraIds)
            ?: return@withTransaction blockedSktjfApply("legacy_alternative_migration"))
        val lesson = prepareSktjfLessonMerge(document, current.lessons, planningState) as? SktjfLessonPreparation.Ready
            ?: return@withTransaction blockedSktjfApply("legacy_lesson_preparation")
        val calendar = prepareSktjfCalendarMerge(document, current.days, state) as? SktjfCalendarPreparation.Ready
            ?: return@withTransaction blockedSktjfApply("legacy_calendar_preparation")
        if (lesson.plan.conflicts != preview.lessonConflicts || calendar.plan.conflicts != preview.calendarConflicts ||
            lesson.plan.reviewItems != preview.lessonReview) return@withTransaction SktjfApplyResult.Stale
        val resolvedLessons = runCatching { lesson.plan.resolve(lessonChoices) }
            .getOrElse { return@withTransaction blockedSktjfApply("legacy_lesson_choices", it) }
        val resolvedCalendar = runCatching { calendar.plan.resolve(calendarChoices) }
            .getOrElse { return@withTransaction blockedSktjfApply("legacy_calendar_choices", it) }
        val term = if (document.term == "S") TimetableTerm.FIRST else TimetableTerm.SECOND
        val projected = projectSktjfExtendedTimetable(document.academicYear, term, document.classId, current.lessons, emptyList(), resolvedLessons) as? SktjfExtendedProjection.Ready
            ?: return@withTransaction blockedSktjfApply("legacy_timetable_projection")
        if (projected.extras.isNotEmpty()) return@withTransaction blockedSktjfApply("legacy_unexpected_alternatives")
        val otherExtras = current.extraLessons.filterNot { it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId }
        runCatching { SktjfExtraLessonBackup.encode(otherExtras + projected.extras) }
            .getOrElse { return@withTransaction blockedSktjfApply("legacy_alternatives_storage", it) }
        val oldCalendar = state?.let { SktjfBaselineCodec.decode(it.baselineJson).calendar }
        val currentCalendar = projectSktjfLocalCalendar(document.academicYear, document.term, document.classId, current.days, oldCalendar)
        val changedDays = mutableListOf<DayTypeEntity>()
        for ((date, content) in resolvedCalendar.days) {
            if (currentCalendar.days[date] == content) continue
            val day = projectSktjfCalendarDay(SktjfCalendarDay(date, content.dayType, content.overrideDayOfWeek,
                content.overrideDayType, content.event, emptyList())) as? SktjfCalendarProjection.Supported
                ?: return@withTransaction blockedSktjfApply("legacy_calendar_projection date=$date")
            changedDays += day.day
        }
        val oldLinks = state?.let { SktjfSourceLinks.decode(it.linksJson) }.orEmpty().associateBy { it.sourceLessonId }
        val updatedLinks = projected.legacy.metadata.values.flatten().mapNotNull { lesson -> lesson.sourceLessonId?.let { id ->
            SktjfSourceLink(id, lesson.content.dayOfWeek, lesson.content.period, lesson.content.cycle)
        } }.associateBy { it.sourceLessonId }
        val extraSourceIds = projected.extras.mapNotNull { it.sourceLessonId }.toSet()
        val links = runCatching { SktjfSourceLinks.encode((oldLinks.filter { (id, link) ->
            id in resolvedLessons.baseline.lessons && id !in extraSourceIds && (id in updatedLinks || updatedLinks.values.none { updated ->
                link.day == updated.day && link.period == updated.period &&
                    (link.cycle == updated.cycle || link.cycle == SktjfCycle.WEEKLY || updated.cycle == SktjfCycle.WEEKLY)
            })
        } + updatedLinks).values.toList()) }.getOrElse { return@withTransaction blockedSktjfApply("legacy_source_links", it) }
        val baseline = runCatching { SktjfBaselineCodec.encode(SktjfStoredBaseline(resolvedLessons.baseline, resolvedCalendar.baseline, preview.localFile)) }
            .getOrElse { return@withTransaction blockedSktjfApply("legacy_baseline", it) }
        val next = SktjfSourceStateEntity(document.academicYear, document.term, document.classId, document.revision, baseline, false, links)
        runCatching { SktjfSourceStateBackup.validate(next) }
            .getOrElse { return@withTransaction blockedSktjfApply("legacy_source_snapshot", it) }

        // From this point, any failure must escape so Room rolls back every write.
        projected.legacy.rows.forEach { dao.upsertLesson(it) }
        if (existingExtras.isNotEmpty()) dao.deleteSktjfExtraLessonsForPartition(document.academicYear, document.term)
        if (changedDays.isNotEmpty()) dao.upsertDayTypes(changedDays)
        dao.upsertSktjfSourceStates(listOf(next))
        repository.touchSyncDatasetMeta(LessonSyncPartition(document.academicYear, term).datasetKey, SchedulerRepository.DATASET_DAY_TYPES)
        SktjfApplyResult.Applied
    }
}

private fun SktjfDocument.detachedForApply() = copy(
    sources = sources.toList(), periods = periods.toList(),
    slotCoverage = slotCoverage.map { it.copy(sources = it.sources.toList()) },
    lessons = lessons.map { it.copy(teachers = it.teachers.toList(), rooms = it.rooms.toList(),
        conditions = SktjfConditions(it.conditions.all.toSet(), it.conditions.none.toSet()), sources = it.sources.toList()) },
    calendar = calendar.map { it.copy(sources = it.sources.toList()) }
)
