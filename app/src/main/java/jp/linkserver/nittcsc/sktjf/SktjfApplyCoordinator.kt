package jp.linkserver.nittcsc.sktjf

import androidx.room.withTransaction
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.TimetableTerm
import java.time.LocalDate

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
    val timed: SktjfV2ApplyPreview? = null
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
    it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId
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
    val selectionChoices: Map<String,String?> = emptyMap())

internal fun SktjfV2ApplyPreview.forReview(): SktjfApplyPreview = SktjfApplyPreview(
    document = preparation.candidate.documentForMerge(), snapshot = snapshot,
    lessonConflicts = preparation.lessons.conflicts, calendarConflicts = preparation.calendar.conflicts,
    localFile = localFile, lessonReview = preparation.lessons.reviewItems, timed = this)

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

    private fun acceptable(document: SktjfDocument, snapshot: SktjfApplySnapshot): Boolean {
        if (document.term !in setOf("S", "F") || snapshot.settings == null || snapshot.mode?.mode != SchedulerUsageMode.NITTC.name ||
            !settingsSupportSktjf(snapshot.settings, document)) return false
        // Another class may have data in the same shared timetable; do not guess how to replace it.
        if (snapshot.extraLessons.any { it.academicYear == document.academicYear && it.term == document.term && it.classId != document.classId }) return false
        if (snapshot.selectionGroups.any { it.classId != document.classId }) return false
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
        if (current.selectionGroups.isNotEmpty()) return@withTransaction null
        if (!acceptable(candidate.document, current)) return@withTransaction null
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

    /** Commit a validated v2 preview without dropping its opportunity evidence. */
    suspend fun applyV2(preview: SktjfV2ApplyPreview, lessonChoices: Map<String, SktjfConflictChoice>,
        calendarChoices: Map<LocalDate, SktjfConflictChoice>): SktjfApplyResult = db.withTransaction {
        val candidate = preview.preparation.candidate
        val document = candidate.document
        val current = snapshot(document)
        if (current != preview.snapshot || !acceptable(document, current)) return@withTransaction SktjfApplyResult.Stale
        if (!preview.hasExplicitSelectionGroups && current.selectionGroups.isNotEmpty()) return@withTransaction SktjfApplyResult.Blocked
        if (!candidate.weekEvidenceComplete) return@withTransaction SktjfApplyResult.Blocked
        val oldState = source(document, current)?.takeUnless { it.requiresRelinking }
        val existingExtras = current.extraLessons.filter { it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId }
        val incomingGroupIds = preview.selectionGroups.flatMap { it.lessonIds }.toSet()
        val retiredIds = current.selectionGroups.flatMap { sktjfGroupIds(it.retiredLessonIdsJson) }.toSet() - incomingGroupIds
        val parked = existingExtras.filter { it.sourceLessonId in retiredIds }
        val activeExtras = existingExtras - parked.toSet()
        val resolved = runCatching { preview.preparation.lessons.resolve(lessonChoices, preview.selectionGroups) }.getOrNull() ?: return@withTransaction SktjfApplyResult.Blocked
        val removedExtraIds = resolved.clearedLocalKeys.filter { it.startsWith("extra:") }.map { it.removePrefix("extra:") }.toSet()
        val preserveRemoved = activeExtras.filter { it.id in removedExtraIds && it.id in current.referencedExtraIds }
        val newlyRetired = mutableMapOf<String, MutableSet<String>>()
        for (extra in preserveRemoved) {
            val sourceId = extra.sourceLessonId ?: return@withTransaction SktjfApplyResult.Blocked
            val owner = current.selectionGroups.singleOrNull { sourceId in sktjfGroupIds(it.lessonIdsJson) || sourceId in sktjfGroupIds(it.retiredLessonIdsJson) }
                ?: return@withTransaction SktjfApplyResult.Blocked
            newlyRetired.getOrPut(owner.groupId) { mutableSetOf() }.add(sourceId)
        }
        val calendar = runCatching { preview.preparation.calendar.resolve(calendarChoices) }.getOrNull() ?: return@withTransaction SktjfApplyResult.Blocked
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
            return@withTransaction SktjfApplyResult.Blocked
        val draft = draftSktjfTimedStorage(candidate, current.lessons, activeExtras, resolved, calendar.baseline, acceptedGroups)
            ?: return@withTransaction SktjfApplyResult.Blocked
        val groups = if (preview.hasExplicitSelectionGroups) runCatching {
            val previous = current.selectionGroups.filter { it.classId == document.classId }
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
        }.getOrNull() ?: return@withTransaction SktjfApplyResult.Blocked else emptyList()
        val oldCalendar = oldState?.let { SktjfBaselineCodec.decode(it.baselineJson).calendar }
        val localCalendar = projectSktjfLocalCalendar(document.academicYear, document.term, document.classId, current.days, oldCalendar)
        val changedDays = mutableListOf<DayTypeEntity>()
        for ((date, content) in calendar.days) {
            if (localCalendar.days[date] == content) continue
            val day = projectSktjfCalendarDay(SktjfCalendarDay(date, content.dayType, content.overrideDayOfWeek,
                content.overrideDayType, content.event, emptyList())) as? SktjfCalendarProjection.Supported ?: return@withTransaction SktjfApplyResult.Blocked
            changedDays += day.day
        }
        val links = runCatching { SktjfSourceLinks.encode(draft.ordinary.metadata.values.flatten().mapNotNull { lesson ->
            lesson.sourceLessonId?.let { id -> SktjfSourceLink(id, lesson.content.dayOfWeek, lesson.content.period, lesson.content.cycle) }
        }) }.getOrNull() ?: return@withTransaction SktjfApplyResult.Blocked
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
            jp.linkserver.nittcsc.data.encodeReplacedTimedSlots(retainedReplacements)).also(SktjfSourceStateBackup::validate) }.getOrNull()
            ?: return@withTransaction SktjfApplyResult.Blocked
        val term = if (document.term == "S") TimetableTerm.FIRST else TimetableTerm.SECOND
        draft.ordinary.rows.forEach { dao.upsertLesson(it) }
        if (existingExtras.isNotEmpty()) dao.deleteSktjfExtraLessonsForPartition(document.academicYear, document.term)
        val storedAlternatives = draft.alternatives + parked + preserveRemoved
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

    suspend fun preview(input: SktjfDocument): SktjfPreviewResult = db.withTransaction {
        val incoming = input.detachedForApply(); val current = snapshot(incoming)
        if (current.selectionGroups.isNotEmpty()) return@withTransaction SktjfPreviewResult.Blocked
        if (!acceptable(incoming, current)) return@withTransaction SktjfPreviewResult.Blocked
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
            lessonReview = lesson.plan.reviewItems))
    }

    suspend fun apply(preview: SktjfApplyPreview, lessonChoices: Map<String, SktjfConflictChoice>,
        calendarChoices: Map<LocalDate, SktjfConflictChoice>): SktjfApplyResult = db.withTransaction {
        val document = preview.document
        val current = snapshot(document)
        if (current != preview.snapshot || !acceptable(document, current)) return@withTransaction SktjfApplyResult.Stale
        val state = source(document, current)?.takeUnless { it.requiresRelinking }
        val existingExtras = current.extraLessons.filter { it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId }
        val planningState = if (existingExtras.isEmpty()) state else (prepareLegacyExtraMigration(state, existingExtras, current.referencedExtraIds)
            ?: return@withTransaction SktjfApplyResult.Blocked)
        val lesson = prepareSktjfLessonMerge(document, current.lessons, planningState) as? SktjfLessonPreparation.Ready
            ?: return@withTransaction SktjfApplyResult.Blocked
        val calendar = prepareSktjfCalendarMerge(document, current.days, state) as? SktjfCalendarPreparation.Ready
            ?: return@withTransaction SktjfApplyResult.Blocked
        if (lesson.plan.conflicts != preview.lessonConflicts || calendar.plan.conflicts != preview.calendarConflicts ||
            lesson.plan.reviewItems != preview.lessonReview) return@withTransaction SktjfApplyResult.Stale
        val resolvedLessons = runCatching { lesson.plan.resolve(lessonChoices) }.getOrNull() ?: return@withTransaction SktjfApplyResult.Blocked
        val resolvedCalendar = runCatching { calendar.plan.resolve(calendarChoices) }.getOrNull() ?: return@withTransaction SktjfApplyResult.Blocked
        val term = if (document.term == "S") TimetableTerm.FIRST else TimetableTerm.SECOND
        val projected = projectSktjfExtendedTimetable(document.academicYear, term, document.classId, current.lessons, emptyList(), resolvedLessons) as? SktjfExtendedProjection.Ready
            ?: return@withTransaction SktjfApplyResult.Blocked
        if (projected.extras.isNotEmpty()) return@withTransaction SktjfApplyResult.Blocked
        val otherExtras = current.extraLessons.filterNot { it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId }
        if (runCatching { SktjfExtraLessonBackup.encode(otherExtras + projected.extras) }.isFailure) return@withTransaction SktjfApplyResult.Blocked
        val oldCalendar = state?.let { SktjfBaselineCodec.decode(it.baselineJson).calendar }
        val currentCalendar = projectSktjfLocalCalendar(document.academicYear, document.term, document.classId, current.days, oldCalendar)
        val changedDays = mutableListOf<DayTypeEntity>()
        for ((date, content) in resolvedCalendar.days) {
            if (currentCalendar.days[date] == content) continue
            val day = projectSktjfCalendarDay(SktjfCalendarDay(date, content.dayType, content.overrideDayOfWeek,
                content.overrideDayType, content.event, emptyList())) as? SktjfCalendarProjection.Supported
                ?: return@withTransaction SktjfApplyResult.Blocked
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
        } + updatedLinks).values.toList()) }.getOrNull()
            ?: return@withTransaction SktjfApplyResult.Blocked
        val baseline = runCatching { SktjfBaselineCodec.encode(SktjfStoredBaseline(resolvedLessons.baseline, resolvedCalendar.baseline, preview.localFile)) }.getOrNull()
            ?: return@withTransaction SktjfApplyResult.Blocked
        val next = SktjfSourceStateEntity(document.academicYear, document.term, document.classId, document.revision, baseline, false, links)
        if (runCatching { SktjfSourceStateBackup.validate(next) }.isFailure) return@withTransaction SktjfApplyResult.Blocked

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
