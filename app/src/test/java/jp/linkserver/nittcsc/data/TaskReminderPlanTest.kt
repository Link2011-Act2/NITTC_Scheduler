package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.TaskReminderKind
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskReminderPlanTest {
    private val date = LocalDate.of(2026, 11, 9)
    private val settings = SettingsEntity(termStart = date, termEnd = date.plusDays(30))
    private val task = TaskEntity(id = 1, subject = "数学", title = "課題", dueDate = date, createdDate = date.minusDays(3),
        reminderEnabled = true, reminderDate = date.minusDays(2), reminderHour = 20, reminderMinute = 15)
    private fun schedule(settings: SettingsEntity = this.settings) = EffectiveSchoolSchedule(settings,
        emptyMap(), emptyList(), emptyMap(), emptySet(), emptyMap(), emptyMap())

    @Test fun manualReminderCoexistsWithTwoAutomaticReminders() {
        val plan = taskReminderTimes(task, schedule())
        assertEquals(3, plan.size)
        assertEquals(date.minusDays(2).atTime(20, 15), plan[TaskReminderKind.MANUAL])
        assertEquals(date.minusDays(1).atTime(16, 0), plan[TaskReminderKind.DAY_BEFORE])
        assertEquals(date.atTime(8, 10), plan[TaskReminderKind.DUE_MORNING])
    }

    @Test fun completedOrDeletedTaskHasNoPlannedNotifications() {
        assertTrue(taskReminderTimes(task.copy(isCompleted = true), schedule()).isEmpty())
        assertTrue(taskReminderTimes(null, schedule()).isEmpty())
    }

    @Test fun dueDateChangeMovesOnlyAutomaticTimes() {
        val before = taskReminderTimes(task, schedule())
        val after = taskReminderTimes(task.copy(dueDate = date.plusDays(1)), schedule())
        assertEquals(before[TaskReminderKind.MANUAL], after[TaskReminderKind.MANUAL])
        assertEquals(before.getValue(TaskReminderKind.DAY_BEFORE).plusDays(1), after[TaskReminderKind.DAY_BEFORE])
        assertEquals(before.getValue(TaskReminderKind.DUE_MORNING).plusDays(1), after[TaskReminderKind.DUE_MORNING])
    }

    @Test fun scheduleSettingChangeRecomputesAutomaticTimesAndRetainsManual() {
        val after = taskReminderTimes(task, schedule(settings.copy(arrivalTimeAuto = false,
            arrivalHour = 9, arrivalMinute = 20, departureHour = 17, departureMinute = 10)))
        assertEquals(date.atTime(9, 0), after[TaskReminderKind.DUE_MORNING])
        assertEquals(date.minusDays(1).atTime(17, 10), after[TaskReminderKind.DAY_BEFORE])
        assertEquals(date.minusDays(2).atTime(20, 15), after[TaskReminderKind.MANUAL])
    }
}
