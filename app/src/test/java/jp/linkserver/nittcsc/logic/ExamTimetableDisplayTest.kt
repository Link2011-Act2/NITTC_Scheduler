package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.ui.buildExamPeriods
import jp.linkserver.nittcsc.ui.examPeriodContainingDate
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ExamTimetableDisplayTest {
    private val day = LocalDate.of(2026, 11, 12)
    private fun lesson(subject: String = "", slot: Int = 0) = ExamLessonEntity(day, slot, 8, 40, 9, 30, subject)

    @Test fun anExamLabelWithoutSavedRowsIsUnset() {
        for (label in listOf(HolidaySpecialLabel.MIDTERM, HolidaySpecialLabel.FINAL)) {
            assertEquals(ExamTimetableDisplay.UNSET, examTimetableDisplay(true, label, emptyList()))
        }
    }

    @Test fun savingEmptyTimeSlotsDoesNotConfigureAnExamDay() {
        assertEquals(ExamTimetableDisplay.UNSET,
            examTimetableDisplay(true, HolidaySpecialLabel.FINAL, listOf(lesson("  "), lesson(slot = 1))))
    }

    @Test fun oneEnteredSlotConfiguresTheDayWithoutChangingItsEmptySlots() {
        assertEquals(ExamTimetableDisplay.CONFIGURED,
            examTimetableDisplay(true, HolidaySpecialLabel.FINAL, listOf(lesson(), lesson("数学", 1))))
    }

    @Test fun enteredContentUsesTheExistingDefinitionIncludingMemoAndTeacher() {
        for (entered in listOf(lesson().copy(teacher = "教員"), lesson().copy(location = "教室"), lesson().copy(memo = "持ち物"))) {
            assertEquals(ExamTimetableDisplay.CONFIGURED, examTimetableDisplay(true, HolidaySpecialLabel.FINAL, listOf(entered)))
        }
    }

    @Test fun disabledFeatureAndOrdinaryHolidaysAreNotExamSchedules() {
        assertEquals(ExamTimetableDisplay.NOT_EXAM, examTimetableDisplay(false, HolidaySpecialLabel.MIDTERM, listOf(lesson("数学"))))
        for (label in listOf(null, HolidaySpecialLabel.EVENT, HolidaySpecialLabel.EXAM_RETURN)) {
            assertEquals(ExamTimetableDisplay.NOT_EXAM, examTimetableDisplay(true, label, listOf(lesson("数学"))))
        }
    }

    @Test fun eachDateFindsItsOwnPeriodAndNeverSelectsABridgedHoliday() {
        val friday = LocalDate.of(2026, 11, 13)
        val monday = friday.plusDays(3)
        val next = monday.plusWeeks(2)
        val days = listOf(
            DayTypeEntity(friday, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.MIDTERM),
            DayTypeEntity(friday.plusDays(1), DayType.HOLIDAY),
            DayTypeEntity(friday.plusDays(2), DayType.HOLIDAY),
            DayTypeEntity(monday, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.MIDTERM),
            DayTypeEntity(next, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.FINAL)
        )
        val periods = buildExamPeriods(days)
        assertEquals(2, periods.size)
        assertSame(periods[0], examPeriodContainingDate(periods, friday))
        assertSame(periods[0], examPeriodContainingDate(periods, monday))
        assertSame(periods[1], examPeriodContainingDate(periods, next))
        assertNull(examPeriodContainingDate(periods, friday.plusDays(1)))
        assertNull(examPeriodContainingDate(periods, friday.minusDays(1)))
        assertNull(examPeriodContainingDate(periods, monday.plusDays(1)))
        assertNull(examPeriodContainingDate(emptyList(), friday))
    }

    @Test fun periodLookupDoesNotConfuseAcademicYearsOrAdjacentExamLabels() {
        val dates = listOf(day, day.plusDays(1), day.plusYears(1))
        val periods = buildExamPeriods(dates.mapIndexed { index, date ->
            DayTypeEntity(date, DayType.HOLIDAY, holidaySpecialLabel = if (index == 1) HolidaySpecialLabel.FINAL else HolidaySpecialLabel.MIDTERM)
        })
        assertEquals(3, periods.size)
        dates.forEachIndexed { index, date -> assertSame(periods[index], examPeriodContainingDate(periods, date)) }
    }
}
