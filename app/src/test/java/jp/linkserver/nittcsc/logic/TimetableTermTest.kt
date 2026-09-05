package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class TimetableTermTest {
    @Test
    fun `disabled semester timetables always use first term`() {
        assertEquals(
            TimetableTerm.FIRST,
            timetableTermForDate(LocalDate.of(2026, 10, 1), semesterTimetablesEnabled = false)
        )
        assertEquals(
            TimetableTerm.FIRST,
            timetableTermForDate(LocalDate.of(2027, 2, 1), semesterTimetablesEnabled = false)
        )
    }

    @Test
    fun `enabled semester timetables switch on October first`() {
        assertEquals(
            TimetableTerm.FIRST,
            timetableTermForDate(LocalDate.of(2026, 9, 30), semesterTimetablesEnabled = true)
        )
        assertEquals(
            TimetableTerm.SECOND,
            timetableTermForDate(LocalDate.of(2026, 10, 1), semesterTimetablesEnabled = true)
        )
    }

    @Test
    fun `second term continues through March and resets in April`() {
        assertEquals(
            TimetableTerm.SECOND,
            timetableTermForDate(LocalDate.of(2027, 3, 31), semesterTimetablesEnabled = true)
        )
        assertEquals(
            TimetableTerm.FIRST,
            timetableTermForDate(LocalDate.of(2027, 4, 1), semesterTimetablesEnabled = true)
        )
    }

    @Test
    fun `same term weekday and slot remain distinct across academic years`() {
        assertNotEquals(
            LessonKey(2026, TimetableTerm.FIRST, 1, 0),
            LessonKey(2027, TimetableTerm.FIRST, 1, 0)
        )
    }
}
