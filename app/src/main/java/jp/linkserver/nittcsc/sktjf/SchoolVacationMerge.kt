package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.SchoolVacationEntity
import jp.linkserver.nittcsc.data.SchoolVacationPeriod
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal data class SchoolVacationChange(
    val previous: SchoolVacationEntity?,
    val candidate: SchoolVacationEntity,
    val teachingDates: List<LocalDate>
) {
    fun defaultSelected(manual: Boolean) =
        teachingDates.isEmpty() && (manual || previous == null || previous.local == previous.base)
}

internal data class SchoolVacationPreview(
    val revision: String,
    val periods: List<SktjfVacation>,
    val changes: List<SchoolVacationChange>
)

internal sealed interface SchoolVacationCheck {
    data class Ready(val preview: SchoolVacationPreview) : SchoolVacationCheck
    data object Failed : SchoolVacationCheck
    data object NotPublished : SchoolVacationCheck
}

internal fun pendingSchoolVacationIds(
    vacations: List<SchoolVacationEntity>, currentYear: Int
): Map<Int, Set<String>> = vacations.asSequence()
    .filter { it.id.endsWith("-year_end") && it.base.end == null && it.local != null }
    .mapNotNull { event -> event.id.take(4).toIntOrNull()?.let { it + 1 to event.id } }
    .filter { (year, _) -> year in currentYear..minOf(currentYear + 1, 9998) }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, ids) -> ids.toSet() }

internal fun SchoolVacationPreview.hasNewlyConfirmedDates(): Boolean = changes.any { change ->
    val before = change.previous ?: return@any false
    before.base.end == null && (change.candidate.base.end != null ||
        change.candidate.base.confirmedThrough > before.base.confirmedThrough)
}

internal fun planSchoolVacations(
    revision: String,
    periods: List<SktjfVacation>,
    existing: List<SchoolVacationEntity>,
    days: List<DayTypeEntity>,
    distributedTeachingDays: Set<LocalDate> = emptySet()
): SchoolVacationPreview {
    val byId = existing.associateBy { it.id }
    val changes = periods.mapNotNull { period ->
        val previous = byId[period.id]
        if (previous != null && compareVacationRevisions(revision, previous.revision) < 0) return@mapNotNull null
        val dates = SchoolVacationPeriod(period.name, period.start, period.end, period.confirmedThrough, period.estimatedEndMonth)
        if (previous != null && previous.local == dates && previous.revision == revision) return@mapNotNull null
        val sources = JSONArray(period.sources.map { source ->
            JSONObject().put("name", source.name).put("url", source.url ?: JSONObject.NULL)
                .put("sha256", source.sha256).put("page", source.page)
        }).toString()
        SchoolVacationChange(previous, SchoolVacationEntity(period.id, revision, sources, dates, dates),
            days.filter {
                period.covers(it.date) && (it.dayType == DayType.B || it.overrideLessonDayOfWeek != null ||
                    it.overrideLessonDayType == DayType.A || it.overrideLessonDayType == DayType.B ||
                    (it.dayType == DayType.A && it.date in distributedTeachingDays)) &&
                    (previous?.local?.let { applied -> it.date in applied.start..applied.confirmedThrough } != true)
            }.map { it.date })
    }
    return SchoolVacationPreview(revision, periods, changes)
}

private fun compareVacationRevisions(left: String, right: String): Int {
    val a = requireNotNull(parseSktjfRevision(left))
    val b = requireNotNull(parseSktjfRevision(right))
    return compareValuesBy(a, b, { it.year }, { it.term == "F" }, { !it.beta }, { it.number })
}
