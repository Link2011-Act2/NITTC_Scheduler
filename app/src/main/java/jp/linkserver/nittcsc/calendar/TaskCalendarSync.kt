package jp.linkserver.nittcsc.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.TaskEntity
import java.time.ZoneId

class TaskCalendarSync(private val context: Context) {

    fun upsertTaskEvent(task: TaskEntity): Long? {
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
        return upsertEvent(
            date = task.dueDate,
            hour = task.dueHour,
            minute = task.dueMinute,
            title = title,
            description = description,
            existingEventId = task.calendarEventId
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
}
