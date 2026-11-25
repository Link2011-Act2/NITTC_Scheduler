package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.logic.OfficialOpportunityDay
import jp.linkserver.nittcsc.logic.OfficialOpportunitySeries
import jp.linkserver.nittcsc.logic.officialOpportunityOrdinalsForTerm
import java.time.DayOfWeek
import java.time.LocalDate

/** Decisions must be based on an explicitly confirmed school counting rule. */
internal data class SktjfOpportunityDecision(
    val dayCounts: Boolean?,
    val periodCounts: Map<Int, Boolean?>,
)

/**
 * Uses the stored distribution snapshot, not the merged local calendar or personal
 * lesson changes. Missing weekdays and unresolved school rules remain unknown;
 * unlisted weekends follow the school's non-teaching convention.
 */
internal fun sktjfOfficialOpportunityOrdinals(
    baseline: SktjfCalendarBaseline,
    start: LocalDate,
    end: LocalDate,
    series: OfficialOpportunitySeries,
    cycle: SktjfCycle,
    confirmedNonTeachingDates: Set<LocalDate> = emptySet(),
    missingWithOverrides: Set<LocalDate> = emptySet(),
    decide: (LocalDate, SktjfCalendarContent) -> SktjfOpportunityDecision,
): Map<LocalDate, Int?> {
    val officialDays = baseline.days.filterKeys { it in start..end }.map { (date, source) ->
        val decision = decide(date, source)
        val effectiveType = source.overrideDayType ?: source.dayType
        val cycleMatches = when {
            cycle == SktjfCycle.WEEKLY -> true
            effectiveType == SktjfDayType.A || effectiveType == SktjfDayType.B -> cycle.name == effectiveType.name
            else -> null
        }
        OfficialOpportunityDay(date, DayOfWeek.of(source.overrideDayOfWeek ?: date.dayOfWeek.value),
            cycleMatches, decision.dayCounts, decision.periodCounts)
    }
    return officialOpportunityOrdinalsForTerm(start, end, series, officialDays, confirmedNonTeachingDates, missingWithOverrides)
}
