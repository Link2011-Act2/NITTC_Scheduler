package jp.linkserver.nittcsc.logic

import java.time.LocalDate

/** Inclusive school-week boundaries; dates must be supplied from a confirmed schedule. */
internal data class LessonWeekRange(val start: Int? = null, val end: Int? = null) {
    init {
        require(start == null || start > 0)
        require(end == null || end > 0)
        require(start == null || end == null || start <= end)
    }
}

/** Null means the calendar has no confirmed week number for this date. */
internal fun lessonWeekApplies(
    range: LessonWeekRange,
    date: LocalDate,
    confirmedWeeks: Map<LocalDate, Int>,
): Boolean? {
    if (range.start == null && range.end == null) return true
    val week = confirmedWeeks[date]?.takeIf { it > 0 } ?: return null
    return (range.start == null || week >= range.start) && (range.end == null || week <= range.end)
}
