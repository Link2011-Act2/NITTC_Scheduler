package jp.linkserver.nittcsc.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

object JapaneseHolidayCalculator {

    private val cache = mutableMapOf<Int, Set<LocalDate>>()

    fun isHoliday(date: LocalDate): Boolean {
        return holidaysForYear(date.year).contains(date)
    }

    private fun holidaysForYear(year: Int): Set<LocalDate> {
        return cache.getOrPut(year) {
            val holidays = baseHolidays(year)
            addSubstituteHolidays(year, holidays)
            addCitizenHolidays(year, holidays)
            holidays.toSet()
        }
    }

    private fun baseHolidays(year: Int): MutableSet<LocalDate> {
        val holidays = mutableSetOf<LocalDate>()

        addHoliday(holidays, year, Month.JANUARY, 1)

        if (year >= 2000) {
            holidays += nthWeekday(year, Month.JANUARY, DayOfWeek.MONDAY, 2)
        } else if (year >= 1949) {
            addHoliday(holidays, year, Month.JANUARY, 15)
        }

        addHoliday(holidays, year, Month.FEBRUARY, 11)
        if (year >= 2020) addHoliday(holidays, year, Month.FEBRUARY, 23)
        if (year in 1989..2018) addHoliday(holidays, year, Month.DECEMBER, 23)

        addHoliday(holidays, year, Month.MARCH, vernalEquinoxDay(year))

        if (year >= 2007) {
            addHoliday(holidays, year, Month.APRIL, 29) // Showa Day
        } else if (year >= 1989) {
            addHoliday(holidays, year, Month.APRIL, 29) // Greenery Day (legacy)
        }

        addHoliday(holidays, year, Month.MAY, 3)
        if (year >= 2007) addHoliday(holidays, year, Month.MAY, 4)
        addHoliday(holidays, year, Month.MAY, 5)

        when (year) {
            2020 -> addHoliday(holidays, year, Month.JULY, 23)
            2021 -> addHoliday(holidays, year, Month.JULY, 22)
            else -> {
                if (year >= 2003) {
                    holidays += nthWeekday(year, Month.JULY, DayOfWeek.MONDAY, 3)
                } else if (year >= 1996) {
                    addHoliday(holidays, year, Month.JULY, 20)
                }
            }
        }

        when (year) {
            2020 -> addHoliday(holidays, year, Month.AUGUST, 10)
            2021 -> addHoliday(holidays, year, Month.AUGUST, 8)
            else -> if (year >= 2016) addHoliday(holidays, year, Month.AUGUST, 11)
        }

        if (year >= 2003) {
            holidays += nthWeekday(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 3)
        } else if (year >= 1966) {
            addHoliday(holidays, year, Month.SEPTEMBER, 15)
        }

        addHoliday(holidays, year, Month.SEPTEMBER, autumnEquinoxDay(year))

        when (year) {
            2020 -> addHoliday(holidays, year, Month.JULY, 24)
            2021 -> addHoliday(holidays, year, Month.JULY, 23)
            else -> {
                if (year >= 2000) {
                    holidays += nthWeekday(year, Month.OCTOBER, DayOfWeek.MONDAY, 2)
                } else if (year >= 1966) {
                    addHoliday(holidays, year, Month.OCTOBER, 10)
                }
            }
        }

        addHoliday(holidays, year, Month.NOVEMBER, 3)
        addHoliday(holidays, year, Month.NOVEMBER, 23)

        addSpecialOneTimeHolidays(year, holidays)
        return holidays
    }

    private fun addSpecialOneTimeHolidays(year: Int, holidays: MutableSet<LocalDate>) {
        if (year == 2019) {
            addHoliday(holidays, year, Month.APRIL, 30)
            addHoliday(holidays, year, Month.MAY, 1)
            addHoliday(holidays, year, Month.MAY, 2)
            addHoliday(holidays, year, Month.OCTOBER, 22)
        }
    }

    private fun addSubstituteHolidays(year: Int, holidays: MutableSet<LocalDate>) {
        if (year < 1973) return
        val base = holidays.toList().sorted()
        for (holiday in base) {
            if (holiday.dayOfWeek != DayOfWeek.SUNDAY) continue
            var substitute = holiday.plusDays(1)
            while (holidays.contains(substitute)) {
                substitute = substitute.plusDays(1)
            }
            if (substitute.year == year) {
                holidays += substitute
            }
        }
    }

    private fun addCitizenHolidays(year: Int, holidays: MutableSet<LocalDate>) {
        if (year < 1985) return
        var cursor = LocalDate.of(year, 1, 2)
        val end = LocalDate.of(year, 12, 30)
        while (!cursor.isAfter(end)) {
            if (
                cursor.dayOfWeek != DayOfWeek.SATURDAY &&
                cursor.dayOfWeek != DayOfWeek.SUNDAY &&
                !holidays.contains(cursor) &&
                holidays.contains(cursor.minusDays(1)) &&
                holidays.contains(cursor.plusDays(1))
            ) {
                holidays += cursor
            }
            cursor = cursor.plusDays(1)
        }
    }

    private fun nthWeekday(year: Int, month: Month, weekday: DayOfWeek, nth: Int): LocalDate {
        return LocalDate.of(year, month, 1)
            .with(TemporalAdjusters.dayOfWeekInMonth(nth, weekday))
    }

    private fun addHoliday(
        holidays: MutableSet<LocalDate>,
        year: Int,
        month: Month,
        day: Int
    ) {
        holidays += LocalDate.of(year, month, day)
    }

    private fun vernalEquinoxDay(year: Int): Int {
        return floor(20.8431 + 0.242194 * (year - 1980) - floor((year - 1980) / 4.0)).toInt()
    }

    private fun autumnEquinoxDay(year: Int): Int {
        return floor(23.2488 + 0.242194 * (year - 1980) - floor((year - 1980) / 4.0)).toInt()
    }
}
