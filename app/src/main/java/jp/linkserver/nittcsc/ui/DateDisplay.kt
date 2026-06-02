package jp.linkserver.nittcsc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import jp.linkserver.nittcsc.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val plainDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

@Composable
internal fun formatDateForDisplay(
    date: LocalDate,
    showWeekday: Boolean
): String {
    val base = date.format(plainDateFormatter)
    if (!showWeekday) return base
    return "$base(${stringResource(weekdayRes(date.dayOfWeek))})"
}

@Composable
internal fun formatDateTimeForDisplay(
    date: LocalDate,
    hour: Int,
    minute: Int,
    showWeekday: Boolean
): String {
    return formatDateForDisplay(date, showWeekday) + String.format(" %02d:%02d", hour, minute)
}

private fun weekdayRes(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> R.string.weekday_monday
    DayOfWeek.TUESDAY -> R.string.weekday_tuesday
    DayOfWeek.WEDNESDAY -> R.string.weekday_wednesday
    DayOfWeek.THURSDAY -> R.string.weekday_thursday
    DayOfWeek.FRIDAY -> R.string.weekday_friday
    DayOfWeek.SATURDAY -> R.string.weekday_saturday
    DayOfWeek.SUNDAY -> R.string.weekday_sunday
}
