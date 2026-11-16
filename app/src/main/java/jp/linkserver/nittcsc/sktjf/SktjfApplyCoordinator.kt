package jp.linkserver.nittcsc.sktjf

import androidx.room.withTransaction
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.TimetableTerm
import java.math.BigInteger
import java.time.LocalDate

internal data class SktjfApplySnapshot(
    val settings: SettingsEntity?, val mode: UsageModeEntity?, val profiles: List<NittcProfileEntity>,
    val lessons: List<LessonEntity>, val days: List<DayTypeEntity>, val sourceStates: List<SktjfSourceStateEntity>,
    val extraLessons: List<SktjfExtraLessonEntity>, val referencedExtraIds: Set<String> = emptySet()
)
internal data class SktjfApplyPreview(
    val document: SktjfDocument, val snapshot: SktjfApplySnapshot,
    val lessonConflicts: List<SktjfLessonConflict>, val calendarConflicts: List<SktjfCalendarConflict>,
    val localFile: Boolean = false
)
internal sealed interface SktjfPreviewResult {
    data class Ready(val preview: SktjfApplyPreview) : SktjfPreviewResult
    data object Blocked : SktjfPreviewResult
}
internal sealed interface SktjfApplyResult {
    data object Applied : SktjfApplyResult
    data object Stale : SktjfApplyResult
    data object Blocked : SktjfApplyResult
}

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
            dao.getDayTypesOnce().sortedBy { it.date }, dao.getSktjfSourceStates(), extras, references)
    }

    private fun source(document: SktjfDocument, snapshot: SktjfApplySnapshot) = snapshot.sourceStates.singleOrNull {
        it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId
    }

    private fun acceptable(document: SktjfDocument, snapshot: SktjfApplySnapshot): Boolean {
        if (document.term !in setOf("S", "F") || snapshot.settings == null || snapshot.mode?.mode != SchedulerUsageMode.NITTC.name ||
            !settingsSupportSktjf(snapshot.settings, document)) return false
        // Another class may have data in the same shared timetable; do not guess how to replace it.
        if (snapshot.extraLessons.any { it.academicYear == document.academicYear && it.term == document.term && it.classId != document.classId }) return false
        if (snapshot.profiles.singleOrNull { it.academicYear == document.academicYear }?.classId != document.classId) return false
        val format = Regex("^${document.academicYear}\\.${document.term}\\.(0|[1-9][0-9]*)$")
        val incoming = format.matchEntire(document.revision)?.groupValues?.get(1) ?: return false
        val old = source(document, snapshot) ?: return true
        if (old.isLocalSktjfSource()) return true
        val previous = format.matchEntire(old.revision)?.groupValues?.get(1) ?: return false
        return BigInteger(incoming) >= BigInteger(previous)
    }

    suspend fun preview(input: SktjfDocument): SktjfPreviewResult = db.withTransaction {
        val incoming = input.detachedForApply(); val current = snapshot(incoming)
        if (!acceptable(incoming, current)) return@withTransaction SktjfPreviewResult.Blocked
        val profile = current.profiles.singleOrNull { it.academicYear == incoming.academicYear }
            ?: return@withTransaction SktjfPreviewResult.Blocked
        val document = runCatching { selectSktjfLessons(incoming, profile.attributes()) }.getOrNull()
            ?: return@withTransaction SktjfPreviewResult.Blocked
        val state = source(document, current)
        val extras = current.extraLessons.filter { it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId }
        val planningState = if (extras.isEmpty()) state else (prepareLegacyExtraMigration(state, extras, current.referencedExtraIds)
            ?: return@withTransaction SktjfPreviewResult.Blocked)
        val lesson = prepareSktjfLessonMerge(document, current.lessons, planningState) as? SktjfLessonPreparation.Ready
            ?: return@withTransaction SktjfPreviewResult.Blocked
        val calendar = prepareSktjfCalendarMerge(document, current.days, state) as? SktjfCalendarPreparation.Ready
            ?: return@withTransaction SktjfPreviewResult.Blocked
        SktjfPreviewResult.Ready(SktjfApplyPreview(document, current, lesson.plan.conflicts, calendar.plan.conflicts))
    }

    suspend fun apply(preview: SktjfApplyPreview, lessonChoices: Map<String, SktjfConflictChoice>,
        calendarChoices: Map<LocalDate, SktjfConflictChoice>): SktjfApplyResult = db.withTransaction {
        val document = preview.document
        val current = snapshot(document)
        if (current != preview.snapshot || !acceptable(document, current)) return@withTransaction SktjfApplyResult.Stale
        val state = source(document, current)
        val existingExtras = current.extraLessons.filter { it.academicYear == document.academicYear && it.term == document.term && it.classId == document.classId }
        val planningState = if (existingExtras.isEmpty()) state else (prepareLegacyExtraMigration(state, existingExtras, current.referencedExtraIds)
            ?: return@withTransaction SktjfApplyResult.Blocked)
        val lesson = prepareSktjfLessonMerge(document, current.lessons, planningState) as? SktjfLessonPreparation.Ready
            ?: return@withTransaction SktjfApplyResult.Blocked
        val calendar = prepareSktjfCalendarMerge(document, current.days, state) as? SktjfCalendarPreparation.Ready
            ?: return@withTransaction SktjfApplyResult.Blocked
        if (lesson.plan.conflicts != preview.lessonConflicts || calendar.plan.conflicts != preview.calendarConflicts) return@withTransaction SktjfApplyResult.Stale
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
        dao.requireSktjfRelinking()
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
