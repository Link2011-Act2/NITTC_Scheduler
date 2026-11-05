package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SchoolDayTimesTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val slots = generateClassSlots(4, 90, 10, 60, 8, 40, PeriodLabelStyle.PAIR_KOSHI)

    @Test fun mondayDeadlineNotifiesOnSundayAndAtEightTen() {
        val times = automaticTaskReminderTimes(monday,
            schoolArrivalTime(null, slots, slots), schoolDepartureTime(null, slots))
        assertEquals(monday.minusDays(1).atTime(16, 0), times[TaskReminderKind.DAY_BEFORE])
        assertEquals(monday.atTime(8, 10), times[TaskReminderKind.DUE_MORNING])
    }

    @Test fun holidayUsesBasicSlotsAndDoesNotShiftToSchoolDay() {
        val times = automaticTaskReminderTimes(monday,
            schoolArrivalTime(null, emptyList(), slots), schoolDepartureTime(null, slots))
        assertEquals(monday.minusDays(1), times.getValue(TaskReminderKind.DAY_BEFORE).toLocalDate())
        assertEquals(LocalTime.of(8, 10), times.getValue(TaskReminderKind.DUE_MORNING).toLocalTime())
    }

    @Test fun actualFirstLessonAndManualSettingsTakePriority() {
        assertEquals(slots[1].start.minusMinutes(10), schoolArrivalTime(null, slots.drop(1), slots))
        assertEquals(LocalTime.of(9, 0), schoolArrivalTime(LocalTime.of(9, 0), slots, slots))
        assertEquals(LocalTime.of(17, 20), schoolDepartureTime(LocalTime.of(17, 20), slots))
    }

    @Test fun exactHourIsNotRoundedUpAgain() {
        assertEquals(LocalTime.of(16, 0), schoolDepartureTime(null, listOf(slots.last().copy(end = LocalTime.of(16, 0)))))
    }

    @Test fun midnightSubtractionKeepsCorrectCalendarDate() {
        val times = automaticTaskReminderTimes(monday, LocalTime.of(0, 10), LocalTime.of(16, 0))
        assertEquals(monday.minusDays(1).atTime(23, 50), times[TaskReminderKind.DUE_MORNING])
    }

    @Test fun oldDefaultDoesNotBecomeManual() {
        assertTrue(legacyArrivalTimeIsAutomatic(8, 30))
        assertTrue(legacyArrivalTimeIsAutomatic(-1, -1))
        assertFalse(legacyArrivalTimeIsAutomatic(9, 10))
    }

    @Test fun staleEarlyAndLateDeliveriesAreRejected() {
        val at = monday.atTime(8, 10)
        assertTrue(reminderIsDue(at, at, at))
        assertTrue(reminderIsDue(at, at, at.plusMinutes(1)))
        assertFalse(reminderIsDue(at, at.plusDays(1), at))
        assertFalse(reminderIsDue(at, at, at.minusSeconds(1)))
        assertFalse(reminderIsDue(at, at, at.plusMinutes(15)))
    }
}
