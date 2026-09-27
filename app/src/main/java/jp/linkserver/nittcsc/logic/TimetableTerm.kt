package jp.linkserver.nittcsc.logic

import java.time.LocalDate
import java.time.Month
import java.time.MonthDay

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
    semesterTimetablesEnabled: Boolean,
    secondTermStartMonth: Int = 10,
    secondTermStartDay: Int = 1
): TimetableTerm {
    if (!semesterTimetablesEnabled) return TimetableTerm.FIRST
    val academicYear = if (date.monthValue >= Month.APRIL.value) date.year else date.year - 1
    return if (!date.isBefore(secondTermStartDate(academicYear, secondTermStartMonth, secondTermStartDay))) {
        TimetableTerm.SECOND
    } else {
        TimetableTerm.FIRST
    }
}

fun validSecondTermStart(month: Int, day: Int): Boolean {
    val value = runCatching { MonthDay.of(month, day) }.getOrNull() ?: return false
    if (value == MonthDay.of(2, 29)) return false
    return value != MonthDay.of(4, 1)
}

fun secondTermStartDate(academicYear: Int, month: Int = 10, day: Int = 1): LocalDate {
    val safeMonth = if (validSecondTermStart(month, day)) month else 10
    val safeDay = if (validSecondTermStart(month, day)) day else 1
    val calendarYear = if (safeMonth >= Month.APRIL.value) academicYear else academicYear + 1
    return LocalDate.of(calendarYear, safeMonth, safeDay)
}
