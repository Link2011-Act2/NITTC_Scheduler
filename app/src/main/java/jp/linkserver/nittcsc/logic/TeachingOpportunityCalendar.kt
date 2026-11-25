package jp.linkserver.nittcsc.logic

import java.time.DayOfWeek
import java.time.LocalDate

/** Calendar facts for one day. A null decision means the school's counting rule is unknown. */
internal data class TeachingOpportunityDay(
    val date: LocalDate,
    val effectiveWeekday: DayOfWeek?,
    val cycleMatches: Boolean?,
    val dayCounts: Boolean?,
    val periodsCount: List<Boolean?>,
)

/** One explicitly identified official opportunity, independent of its current subject. */
internal data class OfficialOpportunitySeries(
    val id: String,
    val weekday: DayOfWeek,
    val periodIndices: Set<Int>,
) {
    init {
        require(id.isNotBlank())
        require(periodIndices.isNotEmpty() && periodIndices.all { it in 0..11 })
    }
}

/** Only official calendar facts belong here; personal cancellations and changes do not. */
internal data class OfficialOpportunityDay(
    val date: LocalDate,
    val effectiveWeekday: DayOfWeek?,
    val cycleMatches: Boolean?,
    val dayCounts: Boolean?,
    val periodCounts: Map<Int, Boolean?>,
)

internal fun officialOpportunityOrdinalsForTerm(
    start: LocalDate,
    end: LocalDate,
    series: OfficialOpportunitySeries,
    officialDays: List<OfficialOpportunityDay>,
    confirmedNonTeachingDates: Set<LocalDate> = emptySet(),
    missingWithOverrides: Set<LocalDate> = emptySet(),
): Map<LocalDate, Int?> = teachingOpportunityOrdinalsForTerm(
    start, end,
    officialDays.map { day ->
        TeachingOpportunityDay(day.date, day.effectiveWeekday, day.cycleMatches, day.dayCounts,
            series.periodIndices.sorted().map { day.periodCounts[it] })
    },
    series.weekday, confirmedNonTeachingDates, missingWithOverrides,
)

/**
 * Convert verified calendar and lesson facts into counting inputs for one lesson series.
 * The caller must supply every possible day of the term, including substitutions;
 * no default A/B day, holiday policy, or cancellation policy is inferred here.
 */
internal fun teachingOpportunityCalendar(
    days: List<TeachingOpportunityDay>,
    seriesWeekday: DayOfWeek,
): List<TeachingOpportunity> = days.map { day ->
    require(day.periodsCount.isNotEmpty())
    val status = when {
        day.effectiveWeekday != null && day.effectiveWeekday != seriesWeekday -> TeachingOpportunityStatus.EXCLUDED
        day.dayCounts == false || day.cycleMatches == false -> TeachingOpportunityStatus.EXCLUDED
        day.effectiveWeekday == null || day.dayCounts == null || day.cycleMatches == null -> TeachingOpportunityStatus.UNKNOWN
        day.periodsCount.all { it == true } -> TeachingOpportunityStatus.INCLUDED
        day.periodsCount.all { it == false } -> TeachingOpportunityStatus.EXCLUDED
        else -> TeachingOpportunityStatus.UNKNOWN
    }
    TeachingOpportunity(day.date, status)
}

/**
 * Count a complete term, never treating a missing calendar day as an ordinary day.
 * An unlisted weekend is non-teaching unless an official period override
 * indicates an unresolved teaching opportunity; unlisted weekdays remain unknown.
 */
internal fun teachingOpportunityOrdinalsForTerm(
    start: LocalDate,
    end: LocalDate,
    days: List<TeachingOpportunityDay>,
    seriesWeekday: DayOfWeek,
    confirmedNonTeachingDates: Set<LocalDate> = emptySet(),
    missingWithOverrides: Set<LocalDate> = emptySet(),
): Map<LocalDate, Int?> {
    require(!end.isBefore(start) && java.time.temporal.ChronoUnit.DAYS.between(start, end) < 370)
    require(days.all { it.date in start..end })
    require(confirmedNonTeachingDates.all { it in start..end })
    val byDate = days.associateBy { it.date }
    require(byDate.size == days.size)
    val events = mutableListOf<TeachingOpportunity>()
    var date = start
    while (!date.isAfter(end)) {
        val day = byDate[date]
        events += when {
            day == null -> TeachingOpportunity(date, if (date !in missingWithOverrides &&
                (date in confirmedNonTeachingDates || date.dayOfWeek.value >= 6))
                TeachingOpportunityStatus.EXCLUDED else TeachingOpportunityStatus.UNKNOWN)
            date in confirmedNonTeachingDates && day.dayCounts != false ->
                TeachingOpportunity(date, TeachingOpportunityStatus.UNKNOWN)
            else -> teachingOpportunityCalendar(listOf(day), seriesWeekday).single()
        }
        date = date.plusDays(1)
    }
    return teachingOpportunityOrdinals(events)
}
