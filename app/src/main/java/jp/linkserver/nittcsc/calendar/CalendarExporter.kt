package jp.linkserver.nittcsc.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import jp.linkserver.nittcsc.logic.ExportResult
import jp.linkserver.nittcsc.logic.GeneratedLesson
import java.time.ZoneId

class CalendarExporter(private val context: Context) {

    fun export(lessons: List<GeneratedLesson>): ExportResult {
        return try {
            if (lessons.isEmpty()) {
                return ExportResult(0, 0, "出力対象の授業がありません。")
            }

            val calendarId = getWritableCalendarId()
                ?: return ExportResult(0, lessons.size, "書き込み可能なカレンダーが見つかりませんでした。")

            val resolver = context.contentResolver
            val zoneId = ZoneId.systemDefault()

            var added = 0
            var skipped = 0

            for (lesson in lessons) {
                val title = "${lesson.subject} (${lesson.slot.label})"
                val startMillis = lesson.date.atTime(lesson.slot.start).atZone(zoneId).toInstant().toEpochMilli()
                val endMillis = lesson.date.atTime(lesson.slot.end).atZone(zoneId).toInstant().toEpochMilli()

                if (eventExists(calendarId, title, startMillis, endMillis)) {
                    skipped++
                    continue
                }

                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, "NITTC Scheduler\\n担当: ${lesson.teacher}")
                    put(CalendarContract.Events.DTSTART, startMillis)
                    put(CalendarContract.Events.DTEND, endMillis)
                    put(CalendarContract.Events.EVENT_TIMEZONE, zoneId.id)
                }

                val inserted = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                if (inserted != null) added++ else skipped++
            }

            ExportResult(
                addedCount = added,
                skippedCount = skipped,
                message = "カレンダーに${added}件追加しました（スキップ${skipped}件）。"
            )
        } catch (_: SecurityException) {
            ExportResult(0, lessons.size, "カレンダー権限が不足しています。")
        } catch (e: Exception) {
            ExportResult(0, lessons.size, "カレンダー出力に失敗しました: ${e.message ?: "unknown error"}")
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
                val primary = cursor.getInt(1) == 1
                if (fallback == null) fallback = id
                if (primary) return id
            }
            return fallback
        }

        return null
    }

    private fun eventExists(calendarId: Long, title: String, startMillis: Long, endMillis: Long): Boolean {
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ? AND ${CalendarContract.Events.DTEND} = ?"
        val args = arrayOf(
            calendarId.toString(),
            title,
            startMillis.toString(),
            endMillis.toString()
        )

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            return cursor.count > 0
        }

        return false
    }
}
