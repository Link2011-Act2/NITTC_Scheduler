package jp.linkserver.nittcsc.sktjf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SktjfCountingPolicyTest {
    private val date = LocalDate.of(2026, 10, 5)
    private val normal = SktjfCalendarContent(SktjfDayType.A, null, null, null)
    private val policy = SktjfCountingPolicy(regularDayCounts = true, holidayCounts = false,
        eventCounts = mapOf(SktjfEventKind.MIDTERM to false), ordinaryPeriodCounts = true,
        officialPeriodOverrides = mapOf(date to mapOf(1 to false)), sources = listOf(SktjfReference("src", 1, null)))

    @Test fun `explicit rules keep event and official period decisions separate`() {
        val result = policy.decision(date, normal, setOf(0, 1))
        assertEquals(true, result.dayCounts)
        assertEquals(true, result.periodCounts[0])
        assertEquals(false, result.periodCounts[1])
        assertEquals(false, policy.decision(date, normal.copy(dayType = SktjfDayType.HOLIDAY), setOf(0)).dayCounts)
        assertEquals(false, policy.decision(date, normal.copy(event = SktjfEvent("試験", SktjfEventKind.MIDTERM)), setOf(0)).dayCounts)
        assertEquals(true, policy.decision(date, normal.copy(event = SktjfEvent("行事", SktjfEventKind.EVENT)), setOf(0)).dayCounts)
        assertNull(policy.copy(eventCounts = mapOf(SktjfEventKind.EVENT to null))
            .decision(date, normal.copy(event = SktjfEvent("行事", SktjfEventKind.EVENT)), setOf(0)).dayCounts)
        assertNull(policy.decision(date, normal.copy(event = SktjfEvent("不明", null)), setOf(0)).dayCounts)
    }

    @Test fun `policy requires source references and valid period indices`() {
        assertTrue(runCatching { policy.copy(sources = emptyList()) }.isFailure)
        assertTrue(runCatching { policy.copy(officialPeriodOverrides = mapOf(date to mapOf(12 to true))) }.isFailure)
    }
}
