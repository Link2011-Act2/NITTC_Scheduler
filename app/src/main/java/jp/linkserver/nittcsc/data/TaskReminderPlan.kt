package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.TaskReminderKind
import jp.linkserver.nittcsc.logic.automaticTaskReminderTimes
import java.time.LocalDateTime

fun taskReminderTimes(task: TaskEntity?, schedule: EffectiveSchoolSchedule): Map<TaskReminderKind, LocalDateTime> {
    if (task == null || task.isCompleted || task.id <= 0L) return emptyMap()
    return automaticTaskReminderTimes(task.dueDate, schedule.arrivalTime(task.dueDate),
        schedule.settings.departureTime()).toMutableMap().apply {
        if (task.reminderEnabled && task.reminderDate != null &&
            task.reminderHour in 0..23 && task.reminderMinute in 0..59) {
            put(TaskReminderKind.MANUAL, task.reminderDate.atTime(task.reminderHour, task.reminderMinute))
        }
    }
}
