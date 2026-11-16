package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.basicClassSlots
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.timetableTermForDate

/** Do not silently change a user's timetable settings or apply data at different times. */
internal fun settingsSupportSktjf(settings: SettingsEntity, document: SktjfDocument): Boolean {
    val wantedTerm = when (document.term) { "S" -> TimetableTerm.FIRST; "F" -> TimetableTerm.SECOND; else -> return false }
    if (document.start > document.end) return false
    fun termAt(date: java.time.LocalDate) = timetableTermForDate(date, settings.enableSemesterTimetables,
        settings.secondTermStartMonth, settings.secondTermStartDay)
    if (termAt(document.start) != wantedTerm || termAt(document.end) != wantedTerm) return false
    if (!settings.enableAbTimetable && (document.lessons.any { it.cycle != SktjfCycle.WEEKLY } ||
            document.calendar.any { it.dayType == SktjfDayType.B || it.overrideDayType == SktjfDayType.B })) return false
    val slots = settings.basicClassSlots().associateBy { it.index + 1 }
    return document.periods.isNotEmpty() && document.periods.all { period ->
        val slot = slots[period.number]
        slot != null && slot.start == period.start && slot.end == period.end
    }
}
