package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity

/** Read-only preparation; applying the resulting plan requires a separate v2 storage path. */
internal sealed interface SktjfV2MergePreparation {
    data class Ready(
        val candidate: SktjfV2Candidate,
        val lessons: SktjfLessonMergePlan,
        val calendar: SktjfCalendarMergePlan,
    ) : SktjfV2MergePreparation
    data object Blocked : SktjfV2MergePreparation
}

internal fun prepareSktjfV2Merge(
    candidate: SktjfV2Candidate,
    attributes: Set<String>,
    rows: List<LessonEntity>,
    days: List<DayTypeEntity>,
    state: SktjfSourceStateEntity?,
    extras: List<SktjfExtraLessonEntity> = emptyList(),
    selectionGroups: List<SktjfSelectionGroup> = emptyList(),
    previousSelectionIds: Set<String> = emptySet(),
    calendarState: SktjfSourceStateEntity? = state,
    classSwitch: ((SktjfDocument) -> SktjfLessonMergePlan?)? = null,
): SktjfV2MergePreparation {
    if (!candidate.weekEvidenceComplete) return SktjfV2MergePreparation.Blocked
    val selected = candidate.forProfile(attributes)
    val document = selected.documentForMerge()
    // A time switch is not a personal course choice. Other overlapping courses
    // must be resolved by the caller before entering the timed merge path.
    if (sktjfProfileSlotOptions(document, emptySet()).any { slot ->
            slot.lessons.withIndex().any { (index, a) -> slot.lessons.drop(index + 1).any { b ->
                val sameCycle = a.cycle == SktjfCycle.WEEKLY || b.cycle == SktjfCycle.WEEKLY || a.cycle == b.cycle
                val elective = selectionGroups.any { it.dayOfWeek == slot.day && it.period == slot.period && a.id in it.lessonIds && b.id in it.lessonIds }
                val timed = selected.series.any { it.dayOfWeek == slot.day && slot.period in it.periods &&
                    a.id in it.lessonIds && b.id in it.lessonIds && (a.id in selected.ranges || b.id in selected.ranges) }
                sameCycle && !elective && !timed
            } }
        }) return SktjfV2MergePreparation.Blocked
    val independentIds = previousSelectionIds + selectionGroups.flatMap { it.lessonIds }
    val lessons = if (classSwitch != null) classSwitch(document) else
        (prepareSktjfLessonMerge(document, rows, state, extras, allowTimed = true, independentSourceIds = independentIds)
            as? SktjfLessonPreparation.Ready)?.plan
    if (lessons == null) return SktjfV2MergePreparation.Blocked
    val calendar = prepareSktjfCalendarMerge(document, days, calendarState) as? SktjfCalendarPreparation.Ready
        ?: return SktjfV2MergePreparation.Blocked
    return SktjfV2MergePreparation.Ready(selected, lessons, calendar.plan)
}
