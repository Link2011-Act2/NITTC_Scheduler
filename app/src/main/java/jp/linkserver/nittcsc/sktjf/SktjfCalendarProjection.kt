package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.HolidaySpecialLabel

internal enum class SktjfCalendarProjectionIssue { UNDETERMINED_DAY }
internal sealed interface SktjfCalendarProjection {
    /** The original must be persisted alongside the legacy row; it can carry an unmapped name or override. */
    data class Supported(val day: DayTypeEntity, val original: SktjfCalendarDay) : SktjfCalendarProjection
    data class NeedsExtendedHandling(val original: SktjfCalendarDay, val reason: SktjfCalendarProjectionIssue) : SktjfCalendarProjection
}

/** Does not create exam subjects or exam timetable rows. */
internal fun projectSktjfCalendarDay(source: SktjfCalendarDay): SktjfCalendarProjection {
    val kind = source.event?.kind
    val exam = kind == SktjfEventKind.MIDTERM || kind == SktjfEventKind.FINAL
    val type = if (exam) DayType.HOLIDAY else when (source.dayType) {
        SktjfDayType.A -> DayType.A
        SktjfDayType.B -> DayType.B
        SktjfDayType.HOLIDAY -> DayType.HOLIDAY
        null -> return SktjfCalendarProjection.NeedsExtendedHandling(source, SktjfCalendarProjectionIssue.UNDETERMINED_DAY)
    }
    val label = when (kind) {
        SktjfEventKind.MIDTERM -> HolidaySpecialLabel.MIDTERM
        SktjfEventKind.FINAL -> HolidaySpecialLabel.FINAL
        SktjfEventKind.EXCURSION -> HolidaySpecialLabel.EXCURSION
        SktjfEventKind.EVENT -> HolidaySpecialLabel.EVENT
        SktjfEventKind.EXAM_RETURN -> HolidaySpecialLabel.EXAM_RETURN
        SktjfEventKind.OTHER, null -> null
    }
    val day = DayTypeEntity(
        date = source.date, dayType = type,
        overrideLessonDayOfWeek = if (type == DayType.HOLIDAY) null else source.overrideDayOfWeek,
        overrideLessonDayType = if (type == DayType.HOLIDAY) null else when (source.overrideDayType) {
            SktjfDayType.A -> DayType.A
            SktjfDayType.B -> DayType.B
            SktjfDayType.HOLIDAY -> throw IllegalArgumentException("Invalid lesson day type")
            null -> null
        },
        holidaySpecialLabel = if (type == DayType.HOLIDAY) label else null,
        schoolEventName = source.event?.name,
        schoolEventKind = source.event?.kind?.name
    )
    return SktjfCalendarProjection.Supported(day, source)
}
