package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class QrDayTypeReplacementTest {
    @Test fun preservesEventsOnReplacedAndMissingDaysButNotOrdinaryMissingDays() {
        val date = LocalDate.of(2026, 4, 6)
        val first = DayTypeEntity(date, DayType.A, schoolEventName = "清掃", schoolEventKind = "OTHER")
        val second = DayTypeEntity(date.plusDays(1), DayType.B, schoolEventName = "行事", schoolEventKind = "EVENT")
        val third = DayTypeEntity(date.plusDays(2), DayType.A)
        val incoming = DayTypeEntity(date, DayType.B)
        val result = qrDayTypeReplacement(listOf(first, second, third), listOf(incoming), date..date.plusDays(2))
        assertEquals(listOf(incoming.copy(schoolEventName = "清掃", schoolEventKind = "OTHER"), second), result)
    }
}
