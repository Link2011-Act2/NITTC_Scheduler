package jp.linkserver.nittcsc.logic

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicYearTest {
    @Test
    fun `academic year changes on April first`() {
        assertEquals(2026, academicYearForDate(LocalDate.of(2027, 3, 31)))
        assertEquals(2027, academicYearForDate(LocalDate.of(2027, 4, 1)))
    }

    @Test
    fun `academic year and first semester ranges use expected boundaries`() {
        assertEquals(LocalDate.of(2027, 4, 1), academicYearStart(2027))
        assertEquals(LocalDate.of(2028, 3, 31), academicYearEnd(2027))
        assertEquals(LocalDate.of(2027, 9, 30), firstSemesterEnd(2027))
        assertEquals(
            LocalDate.of(2027, 4, 1)..LocalDate.of(2027, 9, 30),
            firstSemesterRange(2027)
        )
    }

    @Test
    fun `active academic year advances only when the current academic year is newer`() {
        assertFalse(
            shouldAdvanceAcademicYear(
                activeAcademicYear = 2026,
                today = LocalDate.of(2027, 3, 31)
            )
        )
        assertTrue(
            shouldAdvanceAcademicYear(
                activeAcademicYear = 2026,
                today = LocalDate.of(2027, 4, 1)
            )
        )
        assertTrue(
            shouldAdvanceAcademicYear(
                activeAcademicYear = 2024,
                today = LocalDate.of(2027, 4, 1)
            )
        )
    }

    @Test
    fun `clock rollback never moves the active academic year backwards`() {
        assertFalse(
            shouldAdvanceAcademicYear(
                activeAcademicYear = 2027,
                today = LocalDate.of(2027, 3, 31)
            )
        )
        assertFalse(
            shouldAdvanceAcademicYear(
                activeAcademicYear = 2028,
                today = LocalDate.of(2027, 4, 1)
            )
        )
    }

    @Test
    fun `prepared first semester ends before configured second term start`() {
        assertEquals(LocalDate.of(2027, 11, 14), firstSemesterEnd(2027, 11, 15))
        assertEquals(
            LocalDate.of(2027, 4, 1)..LocalDate.of(2028, 1, 9),
            firstSemesterRange(2027, 1, 10)
        )
        assertFalse(validSecondTermStart(2, 29))
        assertFalse(validSecondTermStart(4, 1))
    }
}
