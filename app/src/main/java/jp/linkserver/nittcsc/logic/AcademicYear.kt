package jp.linkserver.nittcsc.logic

import java.time.LocalDate
import java.time.Month

fun academicYearForDate(date: LocalDate): Int =
    if (date.monthValue >= Month.APRIL.value) date.year else date.year - 1

fun academicYearStart(academicYear: Int): LocalDate =
    LocalDate.of(academicYear, Month.APRIL, 1)

fun academicYearEnd(academicYear: Int): LocalDate =
    LocalDate.of(academicYear + 1, Month.MARCH, 31)

fun firstSemesterEnd(academicYear: Int): LocalDate =
    LocalDate.of(academicYear, Month.SEPTEMBER, 30)

fun firstSemesterRange(academicYear: Int): ClosedRange<LocalDate> =
    academicYearStart(academicYear)..firstSemesterEnd(academicYear)

fun shouldAdvanceAcademicYear(activeAcademicYear: Int, today: LocalDate): Boolean =
    academicYearForDate(today) > activeAcademicYear
