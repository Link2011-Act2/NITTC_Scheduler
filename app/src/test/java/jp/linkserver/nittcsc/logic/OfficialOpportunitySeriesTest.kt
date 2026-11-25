package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class OfficialOpportunitySeriesTest {
    private val start = LocalDate.of(2026, 10, 5)
    private val end = start.plusDays(70)
    private val series = OfficialOpportunitySeries("5M-WED-1-2", DayOfWeek.WEDNESDAY, setOf(0, 1))

    private fun calendar(): List<OfficialOpportunityDay> = (0L..70L).map { offset ->
        val date = start.plusDays(offset)
        OfficialOpportunityDay(date, date.dayOfWeek, true, true, mapOf(0 to true, 1 to true, 2 to true))
    }

    @Test fun `subject changes do not restart the shared opportunity count`() {
        val counts = officialOpportunityOrdinalsForTerm(start, end, series, calendar()).mapValues { requireNotNull(it.value) }
        val eighth = start.plusDays(2 + 7 * 7L)
        val ninth = start.plusDays(2 + 8 * 7L)
        assertEquals(8, counts[eighth])
        assertEquals(9, counts[ninth])
        assertTrue(lessonWeekApplies(LessonWeekRange(end = 8), eighth, counts)!!)
        assertFalse(lessonWeekApplies(LessonWeekRange(end = 8), ninth, counts)!!)
        assertFalse(lessonWeekApplies(LessonWeekRange(start = 9), eighth, counts)!!)
        assertTrue(lessonWeekApplies(LessonWeekRange(start = 9), ninth, counts)!!)
    }

    @Test fun `adjacent periods of separate series are counted independently`() {
        val firstWednesday = start.plusDays(2)
        val official = calendar().map { day ->
            if (day.date == firstWednesday) day.copy(periodCounts = day.periodCounts + (2 to false)) else day
        }
        val adjacent = OfficialOpportunitySeries("5M-WED-3", DayOfWeek.WEDNESDAY, setOf(2))
        val pairedCounts = officialOpportunityOrdinalsForTerm(start, end, series, official)
        val adjacentCounts = officialOpportunityOrdinalsForTerm(start, end, adjacent, official)
        assertEquals(1, pairedCounts[firstWednesday])
        assertFalse(adjacentCounts.containsKey(firstWednesday))
        assertEquals(2, pairedCounts[firstWednesday.plusDays(7)])
        assertEquals(1, adjacentCounts[firstWednesday.plusDays(7)])
    }

    @Test fun `personal cancellation or replacement does not alter official numbering`() {
        val official = calendar()
        val cancelledDate = start.plusDays(2)
        val officialCounts = officialOpportunityOrdinalsForTerm(start, end, series, official)
        val personalCancellations = setOf(cancelledDate)
        val personalReplacements = mapOf(cancelledDate.plusDays(7) to "別の授業")
        // Personal overrides affect presentation, but are not part of officialDays.
        assertTrue(cancelledDate in personalCancellations)
        assertEquals("別の授業", personalReplacements[cancelledDate.plusDays(7)])
        assertEquals(2, officialCounts[cancelledDate.plusDays(7)])
        assertEquals(3, officialCounts[cancelledDate.plusDays(14)])
    }

    @Test fun `missing date may be a substitution and makes later counts unknown`() {
        val missing = start.plusDays(1)
        val counts = officialOpportunityOrdinalsForTerm(start, end, series, calendar().filterNot { it.date == missing })
        assertNull(counts[missing])
        assertNull(counts[start.plusDays(2)])
    }
}
