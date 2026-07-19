package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleModelsTest {
    @Test
    fun `period labels support all selectable styles`() {
        assertEquals("1/2校時", formatPeriodLabel(0, PeriodLabelStyle.PAIR_KOSHI))
        assertEquals("3/4校時", formatPeriodLabel(1, PeriodLabelStyle.PAIR_KOSHI))
        assertEquals("1校時", formatPeriodLabel(0, PeriodLabelStyle.SINGLE_KOSHI))
        assertEquals("2校時", formatPeriodLabel(1, PeriodLabelStyle.SINGLE_KOSHI))
        assertEquals("1コマ", formatPeriodLabel(0, PeriodLabelStyle.KOMA))
        assertEquals("2コマ", formatPeriodLabel(1, PeriodLabelStyle.KOMA))
    }
}
