package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.logic.ClassSlot
import jp.linkserver.nittcsc.logic.GeneratedLesson
import jp.linkserver.nittcsc.logic.JapaneseHolidayCalculator
import jp.linkserver.nittcsc.logic.forTimetable
import java.time.LocalDate

/** Keep independently identified simultaneous lessons, even when their names coincide. */
internal fun additionalCalendarLessons(
    range: ClosedRange<LocalDate>, settings: SettingsEntity, slots: List<ClassSlot>,
    days: Map<LocalDate, DayTypeEntity>, cancelled: Set<Pair<LocalDate, Int>>,
    examDates: Set<LocalDate>, resolver: SktjfExtraLessonResolver
): List<GeneratedLesson> = buildList {
    var date = range.start
    while (date <= range.endInclusive) {
        if (date !in examDates) {
            val default = if (date.dayOfWeek.value >= 6 || JapaneseHolidayCalculator.isHoliday(date)) DayType.HOLIDAY else DayType.A
            val day = (days[date] ?: DayTypeEntity(date, default)).forTimetable(settings.enableAbTimetable)
            for (slot in slots) {
                if (slot.index !in 0..11 || date to slot.index in cancelled) continue
                resolver.lessonsOn(date, slot.index + 1, day, settings.enableSemesterTimetables,
                    settings.secondTermStartMonth, settings.secondTermStartDay).forEach { item ->
                    add(GeneratedLesson(date, slot, item.content.subject, item.content.teachers.joinToString("、"),
                        item.content.rooms.joinToString("、").ifBlank { null }, item.id))
                }
            }
        }
        if (date == range.endInclusive) break
        date = date.plusDays(1)
    }
}
