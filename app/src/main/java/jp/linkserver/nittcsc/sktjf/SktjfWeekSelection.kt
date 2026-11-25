package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.logic.LessonWeekRange
import jp.linkserver.nittcsc.logic.OfficialOpportunitySeries
import jp.linkserver.nittcsc.logic.lessonWeekApplies
import java.time.LocalDate

/** A slot can only be resolved when all relevant week conditions are known. */
internal data class SktjfWeekCandidate<T>(
    val value: T,
    val cycle: SktjfCycle,
    val conditions: SktjfConditions,
    val weeks: LessonWeekRange? = null,
)

internal sealed interface SktjfWeekSelection<out T> {
    data object Empty : SktjfWeekSelection<Nothing>
    data object UnconfirmedWeek : SktjfWeekSelection<Nothing>
    data object Conflict : SktjfWeekSelection<Nothing>
    data class Selected<T>(val value: T) : SktjfWeekSelection<T>
}

internal fun <T> selectSktjfWeekCandidate(
    candidates: List<SktjfWeekCandidate<T>>,
    date: LocalDate,
    cycle: SktjfCycle,
    attributes: Set<String>,
    confirmedWeeks: Map<LocalDate, Int>,
): SktjfWeekSelection<T> {
    require(cycle != SktjfCycle.WEEKLY)
    val eligible = candidates.filter {
        (it.cycle == SktjfCycle.WEEKLY || it.cycle == cycle) && it.conditions.matches(attributes)
    }
    val evaluated = eligible.map { candidate ->
        candidate to if (candidate.weeks == null) true else lessonWeekApplies(candidate.weeks, date, confirmedWeeks)
    }
    if (evaluated.any { it.second == null }) return SktjfWeekSelection.UnconfirmedWeek
    val active = evaluated.filter { it.second == true }
    return when (active.size) {
        0 -> SktjfWeekSelection.Empty
        1 -> SktjfWeekSelection.Selected(active.single().first.value)
        else -> SktjfWeekSelection.Conflict
    }
}

/** Resolve one slot using a shared official opportunity series, not subject render counts. */
internal fun <T> selectSktjfOfficialOpportunityCandidate(
    candidates: List<SktjfWeekCandidate<T>>,
    date: LocalDate,
    cycle: SktjfCycle,
    attributes: Set<String>,
    baseline: SktjfCalendarBaseline,
    termStart: LocalDate,
    termEnd: LocalDate,
    series: OfficialOpportunitySeries,
    seriesCycle: SktjfCycle,
    confirmedNonTeachingDates: Set<LocalDate> = emptySet(),
    missingWithOverrides: Set<LocalDate> = emptySet(),
    decide: (LocalDate, SktjfCalendarContent) -> SktjfOpportunityDecision,
): SktjfWeekSelection<T> {
    if (date !in termStart..termEnd) return SktjfWeekSelection.Empty
    val counts = sktjfOfficialOpportunityOrdinals(baseline, termStart, termEnd, series, seriesCycle, confirmedNonTeachingDates, missingWithOverrides, decide)
    if (date !in counts) return SktjfWeekSelection.Empty
    val number = counts[date] ?: return SktjfWeekSelection.UnconfirmedWeek
    return selectSktjfWeekCandidate(candidates, date, cycle, attributes, mapOf(date to number))
}
