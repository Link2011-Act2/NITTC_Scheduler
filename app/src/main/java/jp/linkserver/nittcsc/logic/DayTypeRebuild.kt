package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import java.time.LocalDate

/** Explicit transferred teaching days override the automatic weekend/holiday default. */
internal fun rebuildDayType(date: LocalDate, existing: DayTypeEntity?, autoHoliday: Boolean): DayTypeEntity {
    val transferred = existing != null && existing.dayType != DayType.HOLIDAY && existing.overrideLessonDayOfWeek != null
    val forceHoliday = autoHoliday && !transferred
    val type = if (forceHoliday) DayType.HOLIDAY else existing?.dayType ?: DayType.A
    return DayTypeEntity(date, type,
        overrideLessonDayOfWeek = if (forceHoliday) null else existing?.overrideLessonDayOfWeek,
        overrideLessonDayType = if (forceHoliday) null else existing?.overrideLessonDayType,
        holidaySpecialLabel = if (type == DayType.HOLIDAY) existing?.holidaySpecialLabel else null,
        schoolEventName = existing?.schoolEventName, schoolEventKind = existing?.schoolEventKind)
}
