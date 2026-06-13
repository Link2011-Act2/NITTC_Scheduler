package jp.linkserver.nittcsc.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.TaskEntity
import java.time.ZoneId

class TaskCalendarSync(private val context: Context) {

    fun syncTask(task: TaskEntity): TaskEntity {
        val title = "${task.subject}: ${task.title}"
        val description = buildString {
            append("NITTC Scheduler - 課題")
            if (!task.teacher.isNullOrBlank()) {
                append("\n担当: ${task.teacher}")
            }
            if (!task.description.isNullOrBlank()) {
                append("\n")
                append(task.description)
            }
        }
        val dueEventId = upsertEvent(
            date = task.dueDate,
            hour = task.dueHour,
            minute = task.dueMinute,
            title = title,
            description = description,
            existingEventId = task.calendarEventId
        )
        val reminderEventId = if (task.reminderEnabled && task.reminderDate != null) {
            upsertEvent(
                date = task.reminderDate,
                hour = task.reminderHour,
                minute = task.reminderMinute,
                title = context.getString(R.string.task_reminder_calendar_title, task.subject, task.title),
                description = buildReminderDescription(task),
                existingEventId = task.reminderCalendarEventId
            )
        } else {
            task.reminderCalendarEventId?.let(::deleteTaskEvent)
            null
        }
        return task.copy(
            calendarEventId = dueEventId ?: task.calendarEventId,
            reminderCalendarEventId = reminderEventId
        )
    }

    fun upsertPlanEvent(plan: PlanEntity): Long? {
        val title = "${plan.subject}: ${plan.title}"
        val description = buildString {
            append("NITTC Scheduler - 予定")
            if (!plan.teacher.isNullOrBlank()) {
                append("\n担当: ${plan.teacher}")
            }
            if (!plan.description.isNullOrBlank()) {
                append("\n")
                append(plan.description)
            }
        }
        return upsertEvent(
            date = plan.dueDate,
            hour = plan.dueHour,
            minute = plan.dueMinute,
            title = title,
            description = description,
            existingEventId = plan.calendarEventId
        )
    }

    fun syncPlan(plan: PlanEntity): PlanEntity {
        val dueEventId = upsertPlanEvent(plan)
        val reminderEventId = if (plan.reminderEnabled && plan.reminderDate != null) {
            upsertEvent(
                date = plan.reminderDate,
                hour = plan.reminderHour,
                minute = plan.reminderMinute,
                title = context.getString(R.string.plan_reminder_calendar_title, plan.subject, plan.title),
                description = buildPlanReminderDescription(plan),
                existingEventId = plan.reminderCalendarEventId
            )
        } else {
            plan.reminderCalendarEventId?.let(::deletePlanEvent)
            null
        }
        return plan.copy(
            calendarEventId = dueEventId ?: plan.calendarEventId,
            reminderCalendarEventId = reminderEventId
        )
    }

    private fun upsertEvent(
        date: java.time.LocalDate,
        hour: Int,
        minute: Int,
        title: String,
        description: String,
        existingEventId: Long?
    ): Long? {
        return try {
            val calendarId = getWritableCalendarId() ?: return null
            val zoneId = ZoneId.systemDefault()
            val startMillis = date
                .atTime(hour, minute)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
            val endMillis = startMillis + 30L * 60L * 1000L

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, zoneId.id)
            }

            val resolver = context.contentResolver
            if (existingEventId != null) {
                val target = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
                val updated = resolver.update(target, values, null, null)
                if (updated > 0) return existingEventId
            }

            val inserted = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            inserted?.lastPathSegment?.toLongOrNull()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun deleteTaskEvent(calendarEventId: Long): Boolean {
        return try {
            val target = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, calendarEventId)
            context.contentResolver.delete(target, null, null) > 0
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun deletePlanEvent(calendarEventId: Long): Boolean = deleteTaskEvent(calendarEventId)

    fun clearDeadlineEvents(): Int {
        return try {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                DEADLINE_EVENT_SELECTION,
                DEADLINE_EVENT_SELECTION_ARGS
            )
        } catch (_: SecurityException) {
            0
        } catch (_: Exception) {
            0
        }
    }

    fun clearReminderEvents(): Int {
        return try {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                REMINDER_EVENT_SELECTION,
                REMINDER_EVENT_SELECTION_ARGS
            )
        } catch (_: SecurityException) {
            0
        } catch (_: Exception) {
            0
        }
    }

    fun countDeadlineEvents(): Int {
        return countEvents(DEADLINE_EVENT_SELECTION, DEADLINE_EVENT_SELECTION_ARGS)
    }

    fun countReminderEvents(): Int {
        return countEvents(REMINDER_EVENT_SELECTION, REMINDER_EVENT_SELECTION_ARGS)
    }

    fun clearAppCreatedEvents(): Int {
        return clearDeadlineEvents() + clearReminderEvents()
    }

    private fun buildReminderDescription(task: TaskEntity): String {
        return buildString {
            append("NITTC Scheduler - 課題リマインド")
            if (!task.teacher.isNullOrBlank()) {
                append("\n担当: ${task.teacher}")
            }
            append("\n期限: ${task.dueDate} ${String.format("%02d:%02d", task.dueHour, task.dueMinute)}")
            if (!task.description.isNullOrBlank()) {
                append("\n")
                append(task.description)
            }
        }
    }

    private fun buildPlanReminderDescription(plan: PlanEntity): String {
        return buildString {
            append("NITTC Scheduler - 予定リマインド")
            if (!plan.teacher.isNullOrBlank()) {
                append("\n担当: ${plan.teacher}")
            }
            append("\n時刻: ${plan.dueDate} ${String.format("%02d:%02d", plan.dueHour, plan.dueMinute)}")
            if (!plan.description.isNullOrBlank()) {
                append("\n")
                append(plan.description)
            }
        }
    }

    private fun getWritableCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1"

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            var fallback: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val isPrimary = cursor.getInt(1) == 1
                if (fallback == null) fallback = id
                if (isPrimary) return id
            }
            return fallback
        }

        return null
    }

    private fun countEvents(selection: String, selectionArgs: Array<String>): Int {
        return try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                cursor.count
            } ?: 0
        } catch (_: SecurityException) {
            0
        } catch (_: Exception) {
            0
        }
    }

    companion object {
        private const val TASK_DEADLINE_DESCRIPTION = "NITTC Scheduler - 課題"
        private const val PLAN_DEADLINE_DESCRIPTION = "NITTC Scheduler - 予定"
        private const val TASK_REMINDER_DESCRIPTION = "NITTC Scheduler - 課題リマインド"
        private const val PLAN_REMINDER_DESCRIPTION = "NITTC Scheduler - 予定リマインド"
        private val DEADLINE_EVENT_SELECTION =
            "${CalendarContract.Events.DESCRIPTION} = ? OR " +
                "${CalendarContract.Events.DESCRIPTION} LIKE ? OR " +
                "${CalendarContract.Events.DESCRIPTION} = ? OR " +
                "${CalendarContract.Events.DESCRIPTION} LIKE ?"
        private val DEADLINE_EVENT_SELECTION_ARGS = arrayOf(
            TASK_DEADLINE_DESCRIPTION,
            "$TASK_DEADLINE_DESCRIPTION\n%",
            PLAN_DEADLINE_DESCRIPTION,
            "$PLAN_DEADLINE_DESCRIPTION\n%"
        )
        private val REMINDER_EVENT_SELECTION =
            "${CalendarContract.Events.DESCRIPTION} = ? OR " +
                "${CalendarContract.Events.DESCRIPTION} LIKE ? OR " +
                "${CalendarContract.Events.DESCRIPTION} = ? OR " +
                "${CalendarContract.Events.DESCRIPTION} LIKE ?"
        private val REMINDER_EVENT_SELECTION_ARGS = arrayOf(
            TASK_REMINDER_DESCRIPTION,
            "$TASK_REMINDER_DESCRIPTION\n%",
            PLAN_REMINDER_DESCRIPTION,
            "$PLAN_REMINDER_DESCRIPTION\n%"
        )
    }
}
