package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.attributes
import jp.linkserver.nittcsc.data.decodeReplacedTimedSlots
import jp.linkserver.nittcsc.logic.TimetableTerm
import java.time.LocalDate

internal sealed interface SktjfTimedSlotResult {
    data object Unmanaged : SktjfTimedSlotResult
    data class Managed(val selection: SktjfWeekSelection<SktjfLocalLesson>) : SktjfTimedSlotResult
}

/** Builds the time-dependent view from persisted local rows and the official source snapshot. */
class SktjfTimedSchedule private constructor(private val entries: List<Entry>) {
    private data class Entry(val year: Int, val term: TimetableTerm, val group: SktjfV2WeekSeries,
        val draft: SktjfTimedStorageDraft?, val attributes: Set<String>, val editable: List<SktjfVisibleExtra>,
        val replacedSlots: Set<Pair<Int, Int>>)

    internal fun alternativesFor(year: Int, term: TimetableTerm, day: Int, period: Int): List<SktjfVisibleExtra> =
        entries.filter { it.year == year && it.term == term && it.group.dayOfWeek == day && period in it.group.periods && (day to period) !in it.replacedSlots }
            .flatMap { it.editable }.filter { it.content.dayOfWeek == day && it.content.period == period }.distinctBy { it.id }

    internal fun manages(year: Int, term: TimetableTerm, day: Int, period: Int, cycle: SktjfCycle): Boolean =
        entries.any { it.year == year && it.term == term && it.group.dayOfWeek == day && period in it.group.periods && (day to period) !in it.replacedSlots &&
            (it.group.cycle == SktjfCycle.WEEKLY || it.group.cycle == cycle) }

    internal fun resolve(year: Int, term: TimetableTerm, day: Int, period: Int, date: LocalDate, cycle: SktjfCycle): SktjfTimedSlotResult {
        val matches = entries.filter { it.year == year && it.term == term && it.group.dayOfWeek == day && period in it.group.periods && (day to period) !in it.replacedSlots &&
            (it.group.cycle == SktjfCycle.WEEKLY || it.group.cycle == cycle) }
        if (matches.isEmpty()) return SktjfTimedSlotResult.Unmanaged
        if (matches.size != 1) return SktjfTimedSlotResult.Managed(SktjfWeekSelection.Conflict)
        val entry = matches.single()
        val draft = entry.draft ?: return SktjfTimedSlotResult.Managed(SktjfWeekSelection.UnconfirmedWeek)
        return SktjfTimedSlotResult.Managed(resolveSktjfTimedStorageDraft(draft, entry.group.id, period, date, cycle, entry.attributes))
    }

    internal fun origin(year: Int, term: TimetableTerm, day: Int, period: Int, cycle: SktjfCycle, sourceId: String): SktjfLessonOrigin? {
        val entry = entries.singleOrNull { it.year == year && it.term == term && it.group.dayOfWeek == day &&
            period in it.group.periods && sourceId in it.group.lessonIds && (it.group.cycle == SktjfCycle.WEEKLY || it.group.cycle == cycle) }
            ?: return null
        val source = entry.draft?.source?.lessons?.lessons?.get(sourceId) ?: return null
        return SktjfLessonOrigin(source.kind, source.subject)
    }

    companion object {
        internal fun create(mode: SchedulerUsageMode?, profiles: List<NittcProfileEntity>, states: List<SktjfSourceStateEntity>,
            rows: List<LessonEntity>, extras: List<SktjfExtraLessonEntity>): SktjfTimedSchedule {
            if (mode != SchedulerUsageMode.NITTC) return SktjfTimedSchedule(emptyList())
            val byYear = profiles.associateBy { it.academicYear }
            val installed = installedSktjfSources(states)
            val entries = mutableListOf<Entry>()
            for (state in states) {
                val profile = byYear[state.academicYear] ?: continue
                val activeClass = installed[state.academicYear to state.term]?.classId ?: profile.classId
                if (activeClass != state.classId) continue
                val source = runCatching { SktjfBaselineCodec.decode(state.baselineJson) }.getOrNull() ?: continue
                if (source.series.isEmpty()) continue
                val term = if (state.term == "S") TimetableTerm.FIRST else if (state.term == "F") TimetableTerm.SECOND else continue
                val partitionRows = rows.filter { it.academicYear == state.academicYear && it.timetableTerm == term }
                val partitionExtras = extras.filter { it.academicYear == state.academicYear && it.term == state.term && it.classId == state.classId }
                val draft = restoreSktjfTimedStorage(state, partitionRows, partitionExtras)
                source.series.forEach { group ->
                    val editable = if (draft == null) emptyList() else draft.alternatives.mapNotNull { extra ->
                        if (extra.sourceLessonId !in group.lessonIds) return@mapNotNull null
                        val content = SktjfBaselineCodec.decode(extra.contentJson).lessons.lessons.getValue(extra.id)
                        if (!content.conditions.matches(profile.attributes())) null else SktjfVisibleExtra(extra.id, extra.sourceLessonId, content, extra.contentJson)
                    }
                    entries += Entry(state.academicYear, term, group, draft, profile.attributes(), editable, decodeReplacedTimedSlots(state.replacedTimedSlotsJson))
                }
            }
            return SktjfTimedSchedule(entries)
        }
    }
}

internal fun SktjfLocalLesson.resolvedLesson(): ResolvedLesson {
    val baseId = if (localKey.startsWith("base:")) localKey.split(':').getOrNull(1)?.toLongOrNull() else null
    val extraId = localKey.takeIf { it.startsWith("extra:") }?.removePrefix("extra:")
    return ResolvedLesson(content.subject, content.teachers.joinToString("、"), content.rooms.joinToString("、").ifBlank { null },
        extraLessonId = extraId, lessonId = baseId)
}
