package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class LessonReminderTimingTest {
    private val start = LocalDateTime.of(2026, 11, 9, 8, 40)

    @Test fun standardTimeIsIndependentOfLiveAvailability() {
        for (supported in listOf(false, true)) for (enabled in listOf(false, true)) {
            val timing = lessonReminderTiming(start, 10, enabled, supported, 5)
            assertEquals(start.minusMinutes(10), timing.standardAt)
            assertEquals(if (supported && enabled) start.minusMinutes(15) else null, timing.liveAt)
        }
    }

    @Test fun zeroMinutesOnlySchedulesStandardAtStart() {
        assertEquals(LessonReminderTiming(start, null), lessonReminderTiming(start, 0, true, true, 5))
    }
}
