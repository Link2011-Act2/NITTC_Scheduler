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
): SktjfV2MergePreparation {
    if (!candidate.weekEvidenceComplete) return SktjfV2MergePreparation.Blocked
    val selected = candidate.forProfile(attributes)
    val document = selected.documentForMerge()
    // A time switch is not a personal course choice. Other overlapping courses
    // must be resolved by the caller before entering the timed merge path.
    if (sktjfProfileSlotOptions(document, emptySet()).any { slot ->
            selected.series.none { group -> group.dayOfWeek == slot.day && slot.period in group.periods &&
                slot.lessons.all { it.id in group.lessonIds } && slot.lessons.any { it.id in selected.ranges } }
        }) return SktjfV2MergePreparation.Blocked
    val lessons = prepareSktjfLessonMerge(document, rows, state, extras, allowTimed = true) as? SktjfLessonPreparation.Ready
        ?: return SktjfV2MergePreparation.Blocked
    val calendar = prepareSktjfCalendarMerge(document, days, state) as? SktjfCalendarPreparation.Ready
        ?: return SktjfV2MergePreparation.Blocked
    return SktjfV2MergePreparation.Ready(selected, lessons.plan, calendar.plan)
}
