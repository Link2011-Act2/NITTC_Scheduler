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
            assertEquals(if (supported && enabled) start.minusMinutes(5) else null, timing.liveEndsAt)
            assertEquals(if (supported && enabled) 600 else 0, timing.liveDurationSeconds)
        }
    }

    @Test fun zeroMinutesOnlySchedulesStandardAtStart() {
        assertEquals(LessonReminderTiming(start, null), lessonReminderTiming(start, 0, true, true, 5))
    }

    @Test fun threeMinutesEarlyMovesEndAndKeepsTenMinuteDuration() {
        val timing = lessonReminderTiming(start, 10, true, true, 3)
        assertEquals(start.withHour(8).withMinute(27), timing.liveAt)
        assertEquals(start.withHour(8).withMinute(37), timing.liveEndsAt)
        assertEquals(600, timing.liveDurationSeconds)
        assertEquals(start.minusMinutes(10), timing.standardAt)
    }

    @Test fun zeroEarlyUsesActualStartAsEnd() {
        val timing = lessonReminderTiming(start, 10, true, true, 0)
        assertEquals(start.withMinute(30), timing.liveAt)
        assertEquals(start, timing.liveEndsAt)
        assertEquals(600, timing.liveDurationSeconds)
    }
}
