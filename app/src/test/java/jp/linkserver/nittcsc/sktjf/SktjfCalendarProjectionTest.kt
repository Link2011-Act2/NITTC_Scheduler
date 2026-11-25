package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfCalendarProjectionTest {
    private val date = LocalDate.of(2026, 6, 12)
    private val reference = listOf(SktjfReference("source", 1, null))
    private fun day(type: SktjfDayType? = null, event: SktjfEvent? = null) =
        SktjfCalendarDay(date, type, null, null, event, reference)

    @Test fun examDateUsesExistingLabelWithoutCreatingExamSubjects() {
        val original = day(event = SktjfEvent("前期中間試験", SktjfEventKind.MIDTERM))
        val projection = projectSktjfCalendarDay(original) as SktjfCalendarProjection.Supported
        assertEquals(DayType.HOLIDAY, projection.day.dayType)
        assertEquals(HolidaySpecialLabel.MIDTERM, projection.day.holidaySpecialLabel)
        assertEquals(original, projection.original)
        val withCycle = day(SktjfDayType.B, SktjfEvent("中間試験", SktjfEventKind.MIDTERM))
        val projected = projectSktjfCalendarDay(withCycle) as SktjfCalendarProjection.Supported
        assertEquals(DayType.HOLIDAY, projected.day.dayType)
        assertEquals(HolidaySpecialLabel.MIDTERM, projected.day.holidaySpecialLabel)
        assertEquals(SktjfDayType.B, projected.original.dayType)
    }

    @Test fun ordinarySubstitutionKeepsBothWeekdayAndCycle() {
        val original = day(SktjfDayType.B).copy(overrideDayOfWeek = 1, overrideDayType = SktjfDayType.A)
        val projection = projectSktjfCalendarDay(original) as SktjfCalendarProjection.Supported
        assertEquals(1, projection.day.overrideLessonDayOfWeek)
        assertEquals(DayType.A, projection.day.overrideLessonDayType)
    }

    @Test fun unknownEventNameIsPreservedRatherThanMislabelled() {
        val original = day(SktjfDayType.HOLIDAY, SktjfEvent("独自の行事", SktjfEventKind.OTHER))
        val projection = projectSktjfCalendarDay(original) as SktjfCalendarProjection.Supported
        assertNull(projection.day.holidaySpecialLabel); assertEquals("独自の行事", projection.original.event!!.name)
    }

    @Test fun ambiguousEventRequiresHandlingAndRegularDayRetainsEvent() {
        val unknown = projectSktjfCalendarDay(day(event = SktjfEvent("行事", SktjfEventKind.OTHER)))
        assertEquals(SktjfCalendarProjectionIssue.UNDETERMINED_DAY, (unknown as SktjfCalendarProjection.NeedsExtendedHandling).reason)
        val mixed = projectSktjfCalendarDay(day(SktjfDayType.A, SktjfEvent("行事", SktjfEventKind.EVENT)))
        val day = (mixed as SktjfCalendarProjection.Supported).day
        assertEquals(DayType.A, day.dayType)
        assertEquals("行事", day.schoolEventName)
        assertEquals("EVENT", day.schoolEventKind)
        assertNull(day.holidaySpecialLabel)
    }

    @Test fun examWithSubstitutionPreservesOriginalMetadata() {
        val original = day(event = SktjfEvent("試験", SktjfEventKind.FINAL)).copy(overrideDayOfWeek = 1)
        val projection = projectSktjfCalendarDay(original) as SktjfCalendarProjection.Supported
        assertNull(projection.day.overrideLessonDayOfWeek)
        assertEquals(1, projection.original.overrideDayOfWeek)
        assertEquals(HolidaySpecialLabel.FINAL, projection.day.holidaySpecialLabel)
    }
}
