package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LongBreakEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QrDayTypesScopeTest {
    private fun date(value: String) = LocalDate.parse(value)
    private fun vacation(name: String, start: String, end: String, id: Long = 0) =
        LongBreakEntity(id, name, date(start), date(end))

    @Test fun semesterBoundaryUsesConfiguredStartIncludingNextCalendarYear() {
        assertEquals(date("2026-04-01")..date("2027-03-31"), qrDayTypesRange(2026, QrDayTypesScope.YEAR, 9, 23))
        assertEquals(date("2026-04-01")..date("2026-09-22"), qrDayTypesRange(2026, QrDayTypesScope.FIRST, 9, 23))
        assertEquals(date("2026-09-23")..date("2027-03-31"), qrDayTypesRange(2026, QrDayTypesScope.SECOND, 9, 23))
        assertEquals(date("2027-01-15")..date("2027-03-31"), qrDayTypesRange(2026, QrDayTypesScope.SECOND, 1, 15))
    }

    @Test fun springBreakBelongsToBothYearsByOverlapWithInclusiveBoundaries() {
        val spring = vacation("春休み", "2027-03-20", "2027-04-10")
        assertTrue(spring.overlaps(qrDayTypesRange(2026, QrDayTypesScope.SECOND, 10, 1)))
        assertTrue(spring.overlaps(qrDayTypesRange(2027, QrDayTypesScope.FIRST, 10, 1)))
        assertFalse(spring.overlaps(qrDayTypesRange(2027, QrDayTypesScope.SECOND, 10, 1)))
        assertTrue(spring.overlaps(date("2027-04-10")..date("2027-04-30")))
    }

    @Test fun replacesInsideRangeAndPreservesOutsidePartsAcrossBothBoundaries() {
        val crossing = vacation("休み", "2026-03-20", "2027-04-10", 1)
        val outside = vacation("来年度", "2027-08-01", "2027-08-31", 2)
        val inside = vacation("夏休み", "2026-08-01", "2026-08-31", 3)
        val range = qrDayTypesRange(2026, QrDayTypesScope.YEAR, 10, 1)
        assertEquals(listOf(vacation("休み", "2026-03-20", "2026-03-31"),
            vacation("休み", "2027-04-01", "2027-04-10"), outside),
            qrReplaceLongBreaks(listOf(crossing, outside, inside), emptyList(), range))
    }

    @Test fun retainsFullSpringIntervalAndStableIdOnRepeatedImportsFromEitherYear() {
        val spring = vacation("春休み", "2027-03-20", "2027-04-10", 42)
        for (year in listOf(2026, 2027)) {
            assertEquals(listOf(spring), qrReplaceLongBreaks(listOf(spring), listOf(spring.copy(id = 0)),
                qrDayTypesRange(year, QrDayTypesScope.YEAR, 10, 1)))
        }
        assertEquals(listOf(spring.copy(id = 0)), qrReplaceLongBreaks(emptyList(), listOf(spring, spring),
            qrDayTypesRange(2027, QrDayTypesScope.FIRST, 10, 1)))
    }

    @Test fun shorteningSpringBreakDoesNotEraseExistingNextYearPortion() {
        val original = vacation("春休み", "2027-03-20", "2027-04-10", 42)
        val received = vacation("春休み", "2027-03-25", "2027-03-31")
        assertEquals(listOf(vacation("春休み", "2027-03-25", "2027-04-10")),
            qrReplaceLongBreaks(listOf(original), listOf(received), qrDayTypesRange(2026, QrDayTypesScope.YEAR, 10, 1)))
    }

    @Test fun doesNotMergeOrReassignIdsOfUntouchedOutsideRows() {
        val first = vacation("春休み", "2027-04-01", "2027-04-10", 1)
        val duplicate = first.copy(id = 2)
        assertEquals(listOf(first, duplicate), qrReplaceLongBreaks(listOf(first, duplicate), emptyList(),
            qrDayTypesRange(2026, QrDayTypesScope.YEAR, 10, 1)))
    }
}
