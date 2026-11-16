package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.NittcYearTransitionEntity
import jp.linkserver.nittcsc.data.NittcYearOutcome
import java.time.LocalDate

/** Missing affiliation is independent of automatic calendar-year changes or distribution availability. */
internal fun missingNittcAffiliation(mode: SchedulerUsageMode?, profiles: List<NittcProfileEntity>, today: LocalDate,
    upcomingAnnounced: Boolean = false, transitions: List<NittcYearTransitionEntity> = emptyList()): Int? {
    if (mode != SchedulerUsageMode.NITTC) return null
    val year = academicYearForDate(today)
    if (profiles.none { it.academicYear == year } && !nittcGraduatedForYear(year, profiles, transitions)) return year
    return (year + 1).takeIf { target ->
        val deferred = transitions.any { it.academicYear == target && it.outcome == NittcYearOutcome.DEFERRED.name }
        target <= 9998 && (upcomingAnnounced || deferred) && profiles.none { profile -> profile.academicYear == target } &&
            !nittcGraduatedForYear(target, profiles, transitions)
    }
}

internal fun nittcGraduatedForYear(year: Int, profiles: List<NittcProfileEntity>, transitions: List<NittcYearTransitionEntity>): Boolean {
    val graduation = transitions.filter { it.academicYear <= year && it.outcome == NittcYearOutcome.GRADUATED.name }
        .maxOfOrNull { it.academicYear } ?: return false
    return profiles.none { it.academicYear in graduation..year }
}
