package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.logic.OfficialOpportunitySeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SktjfOfficialOpportunityCalendarTest {
    private val start = LocalDate.of(2026, 10, 5)
    private val end = start.plusDays(21)
    private val series = OfficialOpportunitySeries("shared-slot", DayOfWeek.WEDNESDAY, setOf(0, 1))
    private val regular = SktjfCalendarContent(SktjfDayType.A, null, null, null)

    private fun baseline(): SktjfStoredBaseline {
        val days = (0L..21L).associate { start.plusDays(it) to regular }
        val original = SktjfStoredBaseline(SktjfBaseline(2026, "F", "5M", emptyMap()),
            SktjfCalendarBaseline(2026, "F", "5M", days))
        return SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(original))
    }

    private fun count(baseline: SktjfCalendarBaseline) = sktjfOfficialOpportunityOrdinals(
        baseline, start, end, series, SktjfCycle.WEEKLY
    ) { _, day -> SktjfOpportunityDecision(if (day.event == null) true else null, mapOf(0 to true, 1 to true)) }

    @Test fun `local calendar edits leave persisted official count unchanged`() {
        val source = baseline()
        val first = start.plusDays(2)
        val local = source.calendar.days.toMutableMap()
        local[first] = regular.copy(dayType = SktjfDayType.HOLIDAY)
        local[first.plusDays(7)] = regular.copy(overrideDayOfWeek = DayOfWeek.FRIDAY.value)
        assertEquals(SktjfDayType.HOLIDAY, local.getValue(first).dayType)
        assertEquals(1, count(source.calendar)[first])
        assertEquals(2, count(source.calendar)[first.plusDays(7)])
        assertEquals(3, count(source.calendar)[first.plusDays(14)])
    }

    @Test fun `unconfirmed official event does not get an invented count`() {
        val source = baseline().calendar
        val first = start.plusDays(2)
        val event = regular.copy(event = SktjfEvent("行事", SktjfEventKind.EVENT))
        val changed = source.copy(days = source.days + (first to event))
        assertNull(count(changed)[first])
        assertNull(count(changed)[first.plusDays(7)])
    }

    @Test fun `confirmed official substitution uses its effective weekday and cycle`() {
        val source = baseline().calendar
        val monday = start.plusDays(7)
        val substitute = regular.copy(dayType = SktjfDayType.B,
            overrideDayOfWeek = DayOfWeek.WEDNESDAY.value, overrideDayType = SktjfDayType.A)
        val modified = source.copy(days = source.days + (monday to substitute))
        val counts = sktjfOfficialOpportunityOrdinals(modified, start, end, series, SktjfCycle.A) { _, _ ->
            SktjfOpportunityDecision(true, mapOf(0 to true, 1 to true))
        }
        assertEquals(2, counts[monday])
        assertEquals(3, counts[monday.plusDays(2)])
    }
}
