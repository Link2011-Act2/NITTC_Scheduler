package jp.linkserver.nittcsc.logic

import java.time.LocalDate

/** Each input is a confirmed classification of one period within a single teaching series. */
internal enum class TeachingOpportunityStatus { INCLUDED, EXCLUDED, UNKNOWN }
internal data class TeachingOpportunity(val date: LocalDate, val status: TeachingOpportunityStatus)

/**
 * Count teaching opportunities, not UI renders or adjacent periods. Inputs must already
 * incorporate the school's calendar and any confirmed cancellation or substitution policy.
 * A day is counted once even if a lesson spans multiple periods. An unknown day makes
 * the later ordinal unknown; this function does not guess how school weeks are defined.
 */
internal fun teachingOpportunityOrdinals(events: List<TeachingOpportunity>): Map<LocalDate, Int?> {
    val grouped = events.groupBy { it.date }.toSortedMap()
    var count = 0
    var certain = true
    val result = linkedMapOf<LocalDate, Int?>()
    for ((date, rows) in grouped) {
        // Conflicting classifications for adjacent periods cannot establish an ordinal.
        val states = rows.map { it.status }.toSet()
        when {
            states == setOf(TeachingOpportunityStatus.EXCLUDED) -> Unit
            states == setOf(TeachingOpportunityStatus.INCLUDED) -> {
                if (certain) count++
                result[date] = count.takeIf { certain }
            }
            else -> {
                certain = false
                result[date] = null
            }
        }
    }
    return result
}
