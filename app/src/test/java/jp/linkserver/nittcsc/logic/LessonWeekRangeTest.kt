package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LessonWeekRangeTest {
    @Test fun `boundaries use only explicitly confirmed week numbers`() {
        val days = (8..11).associate { LocalDate.of(2026, 11, it) to it }
        val fromNine = LessonWeekRange(start = 9)
        val toTen = LessonWeekRange(end = 10)
        assertEquals(false, lessonWeekApplies(fromNine, LocalDate.of(2026, 11, 8), days))
        assertEquals(true, lessonWeekApplies(fromNine, LocalDate.of(2026, 11, 9), days))
        assertEquals(true, lessonWeekApplies(toTen, LocalDate.of(2026, 11, 10), days))
        assertEquals(false, lessonWeekApplies(toTen, LocalDate.of(2026, 11, 11), days))
        assertNull(lessonWeekApplies(fromNine, LocalDate.of(2026, 11, 12), days))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reversed range is rejected`() {
        LessonWeekRange(start = 10, end = 5)
    }
}
