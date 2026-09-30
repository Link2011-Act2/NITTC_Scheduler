package jp.linkserver.nittcsc.logic

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun canBridgeExamDays(
    previousDate: LocalDate,
    nextDate: LocalDate,
    holidays: Set<LocalDate>
): Boolean {
    val gapDays = ChronoUnit.DAYS.between(previousDate, nextDate) - 1
    if (gapDays !in 0..6) return false
    var date = previousDate.plusDays(1)
    while (date.isBefore(nextDate)) {
        if (date.dayOfWeek.value < 6 && date !in holidays) return false
        date = date.plusDays(1)
    }
    return true
}
