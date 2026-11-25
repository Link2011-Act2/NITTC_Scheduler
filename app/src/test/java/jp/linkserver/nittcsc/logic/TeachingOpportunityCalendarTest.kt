package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TeachingOpportunityCalendarTest {
    private val monday = DayOfWeek.MONDAY
    private val start = LocalDate.of(2026, 10, 5)

    private fun day(offset: Long, weekday: DayOfWeek? = monday, cycle: Boolean? = true,
                    counts: Boolean? = true, periods: List<Boolean?> = listOf(true, true)) =
        TeachingOpportunityDay(start.plusDays(offset), weekday, cycle, counts, periods)

    @Test fun `confirmed holiday exam and cancellation do not count and a substituted day does`() {
        val days = listOf(day(0), day(7, counts = false), day(14, counts = false),
            day(21, periods = listOf(false, false)), day(29), day(35))
        val result = teachingOpportunityOrdinals(teachingOpportunityCalendar(days, monday))
        assertEquals(1, result[start])
        assertEquals(2, result[start.plusDays(29)]) // Tuesday follows Monday's timetable.
        assertEquals(3, result[start.plusDays(35)])
        assertEquals(3, result.size)
    }

    @Test fun `alternating week and ordinary different weekday do not count`() {
        val days = listOf(day(0), day(1, weekday = DayOfWeek.TUESDAY), day(7, cycle = false), day(14))
        val result = teachingOpportunityOrdinals(teachingOpportunityCalendar(days, monday))
        assertEquals(2, result.size)
        assertEquals(2, result[start.plusDays(14)])
    }

    @Test fun `unknown day cycle and partial cancellation never become assumed counts`() {
        val cases = listOf(day(7, weekday = null), day(7, cycle = null),
            day(7, counts = null), day(7, periods = listOf(true, false)),
            day(7, periods = listOf(true, null)))
        cases.forEach { uncertain ->
            val result = teachingOpportunityOrdinals(teachingOpportunityCalendar(listOf(day(0), uncertain, day(14)), monday))
            assertEquals(1, result[start])
            assertNull(result[start.plusDays(7)])
            assertNull(result[start.plusDays(14)])
        }
    }

    @Test fun `missing date is excluded only when explicitly confirmed`() {
        val days = listOf(day(0), day(2))
        val unknown = teachingOpportunityOrdinalsForTerm(start, start.plusDays(2), days, monday)
        assertNull(unknown[start.plusDays(2)])
        val confirmed = teachingOpportunityOrdinalsForTerm(start, start.plusDays(2), days, monday, setOf(start.plusDays(1)))
        assertEquals(2, confirmed[start.plusDays(2)])
    }

    @Test fun `unlisted weekends are excluded but official substitutions still count`() {
        val days = (0L..7L).filter { it !in 5L..6L }.map { offset ->
            val date = start.plusDays(offset)
            TeachingOpportunityDay(date, date.dayOfWeek, true, true, listOf(true, true))
        }
        val normal = teachingOpportunityOrdinalsForTerm(start, start.plusDays(7), days, monday)
        assertEquals(2, normal[start.plusDays(7)])
        val saturday = start.plusDays(5)
        val unresolved = teachingOpportunityOrdinalsForTerm(start, start.plusDays(7), days, monday,
            missingWithOverrides = setOf(saturday))
        assertNull(unresolved[start.plusDays(7)])
        val substituted = TeachingOpportunityDay(saturday, monday, true, true, listOf(true, true))
        val withSubstitution = teachingOpportunityOrdinalsForTerm(start, start.plusDays(7), days + substituted, monday)
        assertEquals(2, withSubstitution[saturday])
        assertEquals(3, withSubstitution[start.plusDays(7)])
    }

    @Test fun `non teaching evidence conflicting with official calendar leaves later counts unknown`() {
        val days = listOf(day(0), day(1), day(2))
        val conflicting = teachingOpportunityOrdinalsForTerm(start, start.plusDays(2), days, monday, setOf(start.plusDays(1)))
        assertEquals(1, conflicting[start])
        assertNull(conflicting[start.plusDays(1)])
        assertNull(conflicting[start.plusDays(2)])
        val agreeing = teachingOpportunityOrdinalsForTerm(start, start.plusDays(2), listOf(day(0), day(1, counts = false), day(2)),
            monday, setOf(start.plusDays(1)))
        assertEquals(2, agreeing[start.plusDays(2)])
    }
}
