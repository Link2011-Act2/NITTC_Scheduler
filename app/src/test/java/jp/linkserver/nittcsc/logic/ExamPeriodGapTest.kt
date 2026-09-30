package jp.linkserver.nittcsc.logic

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamPeriodGapTest {
    @Test
    fun `weekday holiday can bridge exam days`() {
        val friday = LocalDate.of(2026, 10, 9)
        val tuesday = LocalDate.of(2026, 10, 13)
        assertTrue(canBridgeExamDays(friday, tuesday, setOf(LocalDate.of(2026, 10, 12))))
        assertFalse(canBridgeExamDays(friday, tuesday, emptySet()))
    }

    @Test
    fun `six intervening holidays bridge but seven do not`() {
        val first = LocalDate.of(2026, 10, 1)
        val holidays = (1L..7L).mapTo(mutableSetOf()) { first.plusDays(it) }
        assertTrue(canBridgeExamDays(first, first.plusDays(7), holidays))
        assertFalse(canBridgeExamDays(first, first.plusDays(8), holidays))
    }

    @Test
    fun `regular weekday separates periods`() {
        val monday = LocalDate.of(2026, 10, 5)
        assertFalse(canBridgeExamDays(monday, monday.plusDays(2), emptySet()))
    }
}
