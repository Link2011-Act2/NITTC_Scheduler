package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class QrShareAcademicYearsTest {
    @Test fun ordinaryAbDaysAndSpringHolidayDoNotOfferNextYear() {
        val days = listOf(
            DayTypeEntity(LocalDate.of(2027, 4, 1), DayType.HOLIDAY),
            DayTypeEntity(LocalDate.of(2027, 4, 12), DayType.A),
            DayTypeEntity(LocalDate.of(2027, 5, 1), DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.EVENT)
        )
        assertEquals(listOf(2026), qrShareAcademicYears(2026, emptyList(), emptyList(), emptyList(), days))
    }

    @Test fun preparedTimetablesAndExamsOfferTheirActualAcademicYearAndDeduplicate() {
        val lessons = listOf(LessonKey(2027, TimetableTerm.FIRST, 1, 0), LessonKey(2026, TimetableTerm.SECOND, 1, 0))
        val exams = listOf(LocalDate.of(2026, 3, 10), LocalDate.of(2027, 2, 10))
        assertEquals(listOf(2027, 2026, 2025), qrShareAcademicYears(2026, lessons, exams, exams, emptyList()))
    }

    @Test fun examLabelAloneOffersItsYearAndCurrentYearRemainsAvailableWithoutData() {
        val exam = DayTypeEntity(LocalDate.of(2028, 2, 10), DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.MIDTERM)
        assertEquals(listOf(2027, 2026), qrShareAcademicYears(2026, emptyList(), emptyList(), emptyList(), listOf(exam)))
        assertEquals(listOf(2026), qrShareAcademicYears(2026, emptyList(), emptyList(), emptyList(), emptyList()))
    }
}
