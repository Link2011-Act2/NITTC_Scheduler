package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.logic.LessonWeekRange
import jp.linkserver.nittcsc.logic.OfficialOpportunitySeries
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.DayOfWeek

class SktjfWeekSelectionTest {
    private val day = LocalDate.of(2026, 12, 1)
    private fun course(name: String, from: Int? = null, to: Int? = null,
        cycle: SktjfCycle = SktjfCycle.WEEKLY, all: Set<String> = emptySet(), none: Set<String> = emptySet()) =
        SktjfWeekCandidate(name, cycle, SktjfConditions(all, none), LessonWeekRange(from, to))

    @Test fun `single course respects its inclusive boundaries`() {
        val candidate = listOf(course("実験", to = 10))
        assertEquals(SktjfWeekSelection.Selected("実験"), selectSktjfWeekCandidate(candidate, day, SktjfCycle.A, emptySet(), mapOf(day to 10)))
        assertEquals(SktjfWeekSelection.Empty, selectSktjfWeekCandidate(candidate, day, SktjfCycle.A, emptySet(), mapOf(day to 11)))
        assertEquals(SktjfWeekSelection.UnconfirmedWeek, selectSktjfWeekCandidate(candidate, day, SktjfCycle.A, emptySet(), emptyMap()))
    }

    @Test fun `multiple candidates do not infer an end date for the unrestricted course`() {
        val split = listOf(course("製図", to = 8), course("卒研", from = 9))
        assertEquals(SktjfWeekSelection.Selected("製図"), selectSktjfWeekCandidate(split, day, SktjfCycle.A, emptySet(), mapOf(day to 8)))
        assertEquals(SktjfWeekSelection.Selected("卒研"), selectSktjfWeekCandidate(split, day, SktjfCycle.A, emptySet(), mapOf(day to 9)))
        val ambiguous = listOf(course("製図"), course("卒研", from = 9))
        assertEquals(SktjfWeekSelection.Conflict, selectSktjfWeekCandidate(ambiguous, day, SktjfCycle.A, emptySet(), mapOf(day to 9)))
        assertEquals(SktjfWeekSelection.UnconfirmedWeek, selectSktjfWeekCandidate(ambiguous, day, SktjfCycle.A, emptySet(), emptyMap()))
    }

    @Test fun `cycle and student conditions filter before week resolution`() {
        val choices = listOf(course("通常", from = 9, cycle = SktjfCycle.A, none = setOf("DD")),
            course("DD", from = 9, cycle = SktjfCycle.A, all = setOf("DD")),
            SktjfWeekCandidate("留学生", SktjfCycle.B, SktjfConditions(setOf("INTERNATIONAL"), emptySet())))
        assertEquals(SktjfWeekSelection.Selected("DD"), selectSktjfWeekCandidate(choices, day, SktjfCycle.A, setOf("DD"), mapOf(day to 9)))
        assertEquals(SktjfWeekSelection.Selected("通常"), selectSktjfWeekCandidate(choices, day, SktjfCycle.A, emptySet(), mapOf(day to 9)))
        assertEquals(SktjfWeekSelection.Selected("留学生"), selectSktjfWeekCandidate(choices, day, SktjfCycle.B, setOf("INTERNATIONAL"), emptyMap()))
    }

    @Test fun `official shared series selects changed subject without a supplied date to week map`() {
        val start = LocalDate.of(2026, 10, 5)
        val end = start.plusDays(70)
        val source = SktjfCalendarBaseline(2026, "F", "5M", (0L..70L).associate { offset ->
            start.plusDays(offset) to SktjfCalendarContent(SktjfDayType.A, null, null, null)
        })
        val series = OfficialOpportunitySeries("wed-1-2", DayOfWeek.WEDNESDAY, setOf(0, 1))
        val split = listOf(course("製図", to = 8), course("卒研", from = 9))
        fun resolve(date: LocalDate, calendar: SktjfCalendarBaseline) = selectSktjfOfficialOpportunityCandidate(
            split, date, SktjfCycle.A, emptySet(), calendar, start, end, series, SktjfCycle.WEEKLY
        ) { _, _ -> SktjfOpportunityDecision(true, mapOf(0 to true, 1 to true)) }
        val eighth = start.plusDays(2 + 7 * 7L)
        val ninth = start.plusDays(2 + 8 * 7L)
        assertEquals(SktjfWeekSelection.Selected("製図"), resolve(eighth, source))
        assertEquals(SktjfWeekSelection.Selected("卒研"), resolve(ninth, source))
        val missingSource = source.copy(days = source.days - start.plusDays(1))
        assertEquals(SktjfWeekSelection.UnconfirmedWeek, resolve(ninth, missingSource))
    }

    @Test fun `excluded and uncertain official days never select an unrestricted course`() {
        val start = LocalDate.of(2026, 10, 5)
        val end = start.plusDays(2)
        val baseline = SktjfCalendarBaseline(2026, "F", "5M", (0L..2L).associate { offset ->
            start.plusDays(offset) to SktjfCalendarContent(SktjfDayType.A, null, null, null)
        })
        val series = OfficialOpportunitySeries("monday", DayOfWeek.MONDAY, setOf(0))
        val courses = listOf(SktjfWeekCandidate("授業", SktjfCycle.WEEKLY, SktjfConditions(emptySet(), emptySet())))
        fun resolve(date: LocalDate, decision: Boolean?) = selectSktjfOfficialOpportunityCandidate(
            courses, date, SktjfCycle.A, emptySet(), baseline, start, end, series, SktjfCycle.WEEKLY
        ) { _, _ -> SktjfOpportunityDecision(decision, mapOf(0 to true)) }
        assertEquals(SktjfWeekSelection.Empty, resolve(start, false))
        assertEquals(SktjfWeekSelection.Empty, resolve(start.plusDays(1), true))
        assertEquals(SktjfWeekSelection.UnconfirmedWeek, resolve(start, null))
        assertEquals(SktjfWeekSelection.Selected("授業"), resolve(start, true))
    }
}
