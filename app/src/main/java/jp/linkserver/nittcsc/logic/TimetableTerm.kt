package jp.linkserver.nittcsc.logic

import java.time.LocalDate
import java.time.Month

enum class TimetableTerm {
    FIRST,
    SECOND
}

data class LessonKey(
    val academicYear: Int,
    val timetableTerm: TimetableTerm,
    val dayOfWeek: Int,
    val slotIndex: Int
)

fun timetableTermForDate(
    date: LocalDate,
    semesterTimetablesEnabled: Boolean
): TimetableTerm {
    if (!semesterTimetablesEnabled) return TimetableTerm.FIRST
    return if (date.monthValue >= Month.OCTOBER.value || date.monthValue <= Month.MARCH.value) {
        TimetableTerm.SECOND
    } else {
        TimetableTerm.FIRST
    }
}
