package jp.linkserver.nittcsc.sktjf

import java.time.LocalDate

/** Only source-backed school rules belong here; null always means unconfirmed. */
internal data class SktjfCountingPolicy(
    val regularDayCounts: Boolean?,
    val holidayCounts: Boolean?,
    val eventCounts: Map<SktjfEventKind, Boolean?>,
    val ordinaryPeriodCounts: Boolean?,
    val officialPeriodOverrides: Map<LocalDate, Map<Int, Boolean?>> = emptyMap(),
    val confirmedNonTeachingDates: Set<LocalDate> = emptySet(),
    val sources: List<SktjfReference>,
) {
    init {
        val id = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
        require(sources.isNotEmpty() && sources.all { id.matches(it.sourceId) && it.page > 0 })
        require(officialPeriodOverrides.values.all { values -> values.keys.all { it in 0..11 } })
    }

    fun decision(date: LocalDate, day: SktjfCalendarContent, periods: Set<Int>): SktjfOpportunityDecision {
        val ordinaryDay = when (day.dayType) {
            SktjfDayType.A, SktjfDayType.B -> regularDayCounts
            SktjfDayType.HOLIDAY -> holidayCounts
            null -> null
        }
        val eventKind = day.event?.kind
        val dayCounts = when {
            day.event == null -> ordinaryDay
            eventKind != null && eventCounts.containsKey(eventKind) -> eventCounts[eventKind]
            day.dayType == SktjfDayType.HOLIDAY -> holidayCounts
            eventKind == null || eventKind in setOf(SktjfEventKind.MIDTERM, SktjfEventKind.FINAL, SktjfEventKind.EXAM_RETURN) -> null
            else -> ordinaryDay
        }
        val overrides = officialPeriodOverrides[date].orEmpty()
        return SktjfOpportunityDecision(dayCounts, periods.associateWith { period ->
            if (overrides.containsKey(period)) overrides[period] else ordinaryPeriodCounts
        })
    }
}
