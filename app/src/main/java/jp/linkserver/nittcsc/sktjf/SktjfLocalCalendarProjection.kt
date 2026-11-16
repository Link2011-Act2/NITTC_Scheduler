package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.HolidaySpecialLabel

/** Local event names are comparison labels only; retained local rows must not be rewritten from them. */
internal fun projectSktjfLocalCalendar(
    year: Int, term: String, classId: String, rows: List<DayTypeEntity>, baseline: SktjfCalendarBaseline?
): SktjfLocalCalendar {
    require(term in setOf("S", "F"))
    require(baseline == null || (baseline.academicYear == year && baseline.term == term && baseline.classId == classId))
    require(rows.map { it.date }.distinct().size == rows.size)
    fun type(value: DayType): SktjfDayType = when (value) {
        DayType.A -> SktjfDayType.A; DayType.B -> SktjfDayType.B; DayType.HOLIDAY -> SktjfDayType.HOLIDAY
    }
    fun event(value: HolidaySpecialLabel?): SktjfEvent? = when (value) {
        null -> null
        HolidaySpecialLabel.MIDTERM -> SktjfEvent("中間試験", SktjfEventKind.MIDTERM)
        HolidaySpecialLabel.FINAL -> SktjfEvent("期末試験", SktjfEventKind.FINAL)
        HolidaySpecialLabel.EXCURSION -> SktjfEvent("校外行事", SktjfEventKind.EXCURSION)
        HolidaySpecialLabel.EVENT -> SktjfEvent("学校行事", SktjfEventKind.EVENT)
        HolidaySpecialLabel.EXAM_RETURN -> SktjfEvent("試験返却", SktjfEventKind.EXAM_RETURN)
        HolidaySpecialLabel.SCHOOL_CLOSED -> SktjfEvent("休校", SktjfEventKind.OTHER)
    }
    val days = rows.associate { row ->
        val old = baseline?.days?.get(row.date)
        val originalProjection = old?.let { content ->
            projectSktjfCalendarDay(SktjfCalendarDay(row.date, content.dayType, content.overrideDayOfWeek,
                content.overrideDayType, content.event, emptyList()))
        }
        // Before school-event columns existed, known holiday labels were the only
        // representation of the source event. Preserve that legacy comparison.
        val legacyMatch = originalProjection is SktjfCalendarProjection.Supported &&
            row.dayType == DayType.HOLIDAY && row.holidaySpecialLabel != null &&
            row.schoolEventName == null && row.schoolEventKind == null &&
            originalProjection.day.copy(schoolEventName = null, schoolEventKind = null) == row
        val content = if (originalProjection is SktjfCalendarProjection.Supported && (originalProjection.day == row || legacyMatch)) old
        else SktjfCalendarContent(type(row.dayType), row.overrideLessonDayOfWeek,
            row.overrideLessonDayType?.let(::type), row.schoolEventName?.let { name ->
                SktjfEvent(name, row.schoolEventKind?.let(SktjfEventKind::valueOf))
            } ?: event(row.holidaySpecialLabel))
        row.date to content
    }
    return SktjfLocalCalendar(year, term, classId, days)
}
