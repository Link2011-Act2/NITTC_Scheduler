package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.SktjfExtraLessonBackup
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.data.SktjfSourceStateBackup
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.normalizeSubjectRomanNumerals
import java.time.LocalDate
import java.util.UUID

/** A read-only draft. It must not be committed until every display path resolves its conditions. */
internal data class SktjfTimedStorageDraft(
    val ordinary: SktjfTimetableProjection.Ready,
    val alternatives: List<SktjfExtraLessonEntity>,
    val source: SktjfStoredBaseline,
)

/** Reconstruct local values from persisted rows while retaining the separate official snapshot. */
internal fun restoreSktjfTimedStorage(
    state: SktjfSourceStateEntity, rows: List<LessonEntity>, alternatives: List<SktjfExtraLessonEntity>,
): SktjfTimedStorageDraft? = runCatching {
    require(!state.requiresRelinking)
    SktjfSourceStateBackup.validate(state)
    val source = SktjfBaselineCodec.decode(state.baselineJson)
    require(source.termBounds != null && source.series.isNotEmpty())
    val term = if (state.term == "S") TimetableTerm.FIRST else TimetableTerm.SECOND
    require(rows.all { it.id > 0 && it.academicYear == state.academicYear && it.timetableTerm == term })
    require(rows.map { it.dayOfWeek to it.slotIndex }.distinct().size == rows.size)
    require(alternatives.all { it.academicYear == state.academicYear && it.term == state.term && it.classId == state.classId })
    SktjfExtraLessonBackup.validateList(alternatives)
    val links = SktjfSourceLinks.decode(state.linksJson).associateBy { Triple(it.day, it.period, it.cycle) }
    val metadata = linkedMapOf<Pair<Int, Int>, MutableList<SktjfMergedLesson>>()
    for (row in rows) {
        val cycles = if (row.mode == LessonMode.WEEKLY) listOf(SktjfCycle.WEEKLY) else listOf(SktjfCycle.A, SktjfCycle.B)
        for (cycle in cycles) {
            val (subject, teacher, room) = when (cycle) {
                SktjfCycle.WEEKLY -> Triple(row.weeklySubject, row.weeklyTeacher, row.weeklyLocation)
                SktjfCycle.A -> Triple(row.aSubject, row.aTeacher, row.aLocation)
                SktjfCycle.B -> Triple(row.bSubject, row.bTeacher, row.bLocation)
            }
            if (subject.isBlank()) continue
            val link = links[Triple(row.dayOfWeek, row.slotIndex + 1, cycle)]
            val prior = link?.let { source.lessons.lessons[it.sourceLessonId] }
            val content = SktjfLessonContent(row.dayOfWeek, row.slotIndex + 1, cycle, subject,
                listOfNotNull(teacher.takeIf { it.isNotBlank() }), listOfNotNull(room?.takeIf { it.isNotBlank() }),
                prior?.conditions ?: SktjfConditions(emptySet(), emptySet()),
                if (prior != null && normalizeSubjectRomanNumerals(subject) == normalizeSubjectRomanNumerals(prior.subject)) prior.kind else SktjfLessonKind.NORMAL,
                prior?.remote)
            metadata.getOrPut(row.dayOfWeek to row.slotIndex + 1) { mutableListOf() } +=
                SktjfMergedLesson(sktjfLegacyKey(row.id, cycle), link?.sourceLessonId, content)
        }
    }
    SktjfTimedStorageDraft(SktjfTimetableProjection.Ready(rows, metadata), alternatives, source)
}.getOrNull()

/** Resolve the draft's local values using only the stored official source calendar. */
internal fun resolveSktjfTimedStorageDraft(
    draft: SktjfTimedStorageDraft, seriesId: String, period: Int, date: LocalDate,
    cycle: SktjfCycle, attributes: Set<String>,
): SktjfWeekSelection<SktjfLocalLesson> {
    val group = draft.source.series.singleOrNull { it.id == seriesId } ?: return SktjfWeekSelection.UnconfirmedWeek
    val local = mutableListOf<SktjfLocalLesson>()
    val slot = group.dayOfWeek to period
    val row = draft.ordinary.rows.singleOrNull { it.dayOfWeek == slot.first && it.slotIndex + 1 == slot.second }
    for (anchor in draft.ordinary.metadata[slot].orEmpty()) {
        if (row == null) return SktjfWeekSelection.UnconfirmedWeek
        val (subject, teacher, room) = when (anchor.content.cycle) {
            SktjfCycle.WEEKLY -> Triple(row.weeklySubject, row.weeklyTeacher, row.weeklyLocation)
            SktjfCycle.A -> Triple(row.aSubject, row.aTeacher, row.aLocation)
            SktjfCycle.B -> Triple(row.bSubject, row.bTeacher, row.bLocation)
        }
        if (subject.isBlank()) continue
        val content = anchor.content.copy(subject = subject, teachers = listOfNotNull(teacher.takeIf { it.isNotBlank() }),
            rooms = listOfNotNull(room?.takeIf { it.isNotBlank() }))
        local += SktjfLocalLesson(anchor.localKey ?: "draft:${anchor.sourceLessonId}", anchor.sourceLessonId, content)
    }
    for (extra in draft.alternatives) {
        val content = SktjfBaselineCodec.decode(extra.contentJson).lessons.lessons.getValue(extra.id)
        if (content.dayOfWeek == slot.first && content.period == slot.second)
            local += SktjfLocalLesson("extra:${extra.id}", extra.sourceLessonId, content)
    }
    return resolveLocalSktjfOpportunity(draft.source, local, seriesId, period, date, cycle, attributes)
}

internal fun draftSktjfTimedStorage(
    candidate: SktjfV2Candidate,
    rows: List<LessonEntity>,
    existingExtras: List<SktjfExtraLessonEntity>,
    resolved: SktjfResolvedLessonMerge,
    calendar: SktjfCalendarBaseline,
    selectionGroups: List<SktjfSelectionGroup> = emptyList(),
    onBlocked: (String) -> Unit = {},
    newId: () -> String = { UUID.randomUUID().toString() },
): SktjfTimedStorageDraft? {
    fun blocked(reason: String, slot: Pair<Int, Int>? = null): Nothing? {
        onBlocked(if (slot == null) reason else "$reason day=${slot.first} period=${slot.second}")
        return null
    }
    val document = candidate.document
    val term = when (document.term) { "S" -> TimetableTerm.FIRST; "F" -> TimetableTerm.SECOND; else -> return blocked("invalid_term") }
    if (!candidate.weekEvidenceComplete || existingExtras.any {
            it.academicYear != document.academicYear || it.term != document.term || it.classId != document.classId
        } || runCatching { SktjfExtraLessonBackup.validateList(existingExtras) }.isFailure) return blocked("invalid_existing_alternatives")
    val existing = existingExtras.associateBy { it.id }
    val keys = resolved.lessons.mapNotNull { it.localKey }
    if (keys.size != keys.distinct().size || keys.any { !it.startsWith("base:") && !it.startsWith("extra:") }) return blocked("invalid_local_keys")
    val extraKeys = keys.filter { it.startsWith("extra:") }.map { it.removePrefix("extra:") }.toSet()
    val removedExtraKeys = resolved.clearedLocalKeys.filter { it.startsWith("extra:") }.map { it.removePrefix("extra:") }.toSet()
    if (removedExtraKeys.any { it !in existing } || extraKeys != existing.keys - removedExtraKeys) return blocked("inconsistent_alternative_keys")
    val ordinary = mutableListOf<SktjfMergedLesson>()
    val alternatives = mutableListOf<SktjfExtraLessonEntity>()
    for ((slot, lessons) in resolved.lessons.groupBy { it.content.dayOfWeek to it.content.period }) {
        val timed = lessons.any { it.content.weekRange != null }
        if (timed) {
            val ids = lessons.map { it.sourceLessonId ?: return blocked("unlinked_timed_lesson", slot) }.toSet()
            val group = candidate.series.singleOrNull { it.dayOfWeek == slot.first && slot.second in it.periods && ids.all(it.lessonIds::contains) }
            val datedSelection = candidate.weeks.isNotEmpty() && selectionGroups.any {
                it.dayOfWeek == slot.first && it.period == slot.second && ids.all(it.lessonIds::contains)
            }
            if (group == null && !datedSelection) return blocked("missing_timed_group", slot)
            if (group != null && group.countingPolicy == null && candidate.weeks.isEmpty()) return blocked("missing_week_evidence", slot)
        }
        if (lessons.any {
                it.content.conditions.all.isNotEmpty() || it.content.conditions.none.isNotEmpty() ||
                    (it.localKey?.startsWith("extra:") != true && (it.content.teachers.size > 1 || it.content.rooms.size > 1))
            }) return blocked("unsupported_lesson_fields", slot)
        if (lessons.any { it.content.cycle == SktjfCycle.WEEKLY } && lessons.any { it.content.cycle != SktjfCycle.WEEKLY }) return blocked("incompatible_cycles", slot)
        val anchors = mutableSetOf<SktjfMergedLesson>()
        for (cycleItems in lessons.groupBy { it.content.cycle }.values) {
            val pinned = cycleItems.filter { it.localKey?.startsWith("base:") == true }
            if (pinned.size > 1) return blocked("multiple_ordinary_anchors", slot)
            val selectable = cycleItems.any { lesson -> selectionGroups.any { lesson.sourceLessonId in it.lessonIds } }
            if (selectable && pinned.isNotEmpty()) return blocked("selection_overlaps_ordinary_anchor", slot)
            val anchor = if (selectable) null else pinned.singleOrNull() ?: cycleItems.filter { it.localKey == null }
                .minWithOrNull(compareBy({ it.content.weekRange?.range?.start ?: 1 }, { it.sourceLessonId ?: "" }))
            if (anchor != null) anchors += anchor
        }
        for (lesson in lessons) {
            if (lesson in anchors) {
                ordinary += lesson.copy(content = lesson.content.copy(weekRange = null))
            } else {
                if (lesson.localKey?.startsWith("base:") == true) return blocked("ordinary_anchor_used_as_alternative", slot)
                val id = lesson.localKey?.removePrefix("extra:") ?: newId()
                val payload = runCatching { SktjfBaselineCodec.encode(SktjfStoredBaseline(
                    SktjfBaseline(document.academicYear, document.term, document.classId, mapOf(id to lesson.content)),
                    SktjfCalendarBaseline(document.academicYear, document.term, document.classId, emptyMap()))) }.getOrNull()
                    ?: return blocked("invalid_alternative_content", slot)
                alternatives += SktjfExtraLessonEntity(id, document.academicYear, document.term, document.classId, lesson.sourceLessonId, payload)
            }
        }
    }
    if (runCatching { SktjfExtraLessonBackup.validateList(alternatives) }.isFailure) return blocked("invalid_merged_alternatives")
    val projected = when (val result = projectSktjfTimetable(document.academicYear, term, document.classId, rows,
        resolved.copy(lessons = ordinary, clearedLocalKeys = resolved.clearedLocalKeys.filterNot { it.startsWith("extra:") }.toSet()))) {
        is SktjfTimetableProjection.Ready -> result
        is SktjfTimetableProjection.Blocked ->
            return blocked("ordinary_projection reason=${result.reason} day=${result.dayOfWeek} period=${result.period}")
    }
    val source = candidate.sourceBaseline().copy(lessons = resolved.baseline, calendar = calendar)
    if (runCatching { SktjfBaselineCodec.encode(source) }.isFailure) return blocked("invalid_timed_baseline")
    return SktjfTimedStorageDraft(projected, alternatives, source)
}
