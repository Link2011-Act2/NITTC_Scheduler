package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TeachingOpportunityCountTest {
    private fun day(number: Int) = LocalDate.of(2026, 10, number)
    private fun row(number: Int, status: TeachingOpportunityStatus) = TeachingOpportunity(day(number), status)

    @Test fun `two adjacent periods count once and excluded days do not increment`() {
        val result=teachingOpportunityOrdinals(listOf(row(1,TeachingOpportunityStatus.INCLUDED),
            row(1,TeachingOpportunityStatus.INCLUDED),row(8,TeachingOpportunityStatus.EXCLUDED),
            row(15,TeachingOpportunityStatus.INCLUDED)))
        assertEquals(1,result[day(1)])
        assertFalse(result.containsKey(day(8)))
        assertEquals(2,result[day(15)])
    }

    @Test fun `unknown or conflicting opportunity makes later ordinals uncertain`() {
        val result=teachingOpportunityOrdinals(listOf(row(1,TeachingOpportunityStatus.INCLUDED),
            row(8,TeachingOpportunityStatus.UNKNOWN),row(15,TeachingOpportunityStatus.INCLUDED)))
        assertEquals(1,result[day(1)]);assertNull(result[day(8)]);assertNull(result[day(15)])
        val split=teachingOpportunityOrdinals(listOf(row(1,TeachingOpportunityStatus.INCLUDED),
            row(1,TeachingOpportunityStatus.EXCLUDED),row(8,TeachingOpportunityStatus.INCLUDED)))
        assertNull(split[day(1)]);assertNull(split[day(8)])
    }

    @Test fun `new calculation starts from first opportunity of that series`() {
        val first=teachingOpportunityOrdinals(listOf(row(1,TeachingOpportunityStatus.INCLUDED),row(8,TeachingOpportunityStatus.INCLUDED)))
        val newTerm=teachingOpportunityOrdinals(listOf(row(15,TeachingOpportunityStatus.INCLUDED)))
        assertEquals(2,first[day(8)]);assertEquals(1,newTerm[day(15)])
    }

    @Test fun `ordinal based ranges switch after eight and stop after ten`() {
        val start=LocalDate.of(2026,10,1)
        val rows=(0L..10L).flatMap { offset ->
            val date=start.plusDays(offset*7)
            listOf(TeachingOpportunity(date,TeachingOpportunityStatus.INCLUDED),
                TeachingOpportunity(date,TeachingOpportunityStatus.INCLUDED))
        }
        val ordinals=teachingOpportunityOrdinals(rows).mapValues { requireNotNull(it.value) }
        val eighth=start.plusDays(49);val ninth=start.plusDays(56)
        val tenth=start.plusDays(63);val eleventh=start.plusDays(70)
        assertEquals(true,lessonWeekApplies(LessonWeekRange(end=8),eighth,ordinals))
        assertEquals(false,lessonWeekApplies(LessonWeekRange(end=8),ninth,ordinals))
        assertEquals(false,lessonWeekApplies(LessonWeekRange(start=9),eighth,ordinals))
        assertEquals(true,lessonWeekApplies(LessonWeekRange(start=9),ninth,ordinals))
        assertEquals(true,lessonWeekApplies(LessonWeekRange(end=10),tenth,ordinals))
        assertEquals(false,lessonWeekApplies(LessonWeekRange(end=10),eleventh,ordinals))
    }
}
