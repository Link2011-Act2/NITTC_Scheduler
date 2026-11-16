package jp.linkserver.nittcsc.calendar

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.logic.CalendarDeletionFailure
import jp.linkserver.nittcsc.logic.CalendarDeletionResult
import jp.linkserver.nittcsc.logic.activeCalendarEventSelection
import jp.linkserver.nittcsc.logic.ExportResult
import jp.linkserver.nittcsc.logic.GeneratedLesson
import jp.linkserver.nittcsc.logic.CalendarInsertResult
import jp.linkserver.nittcsc.logic.CalendarReplacementStore
import jp.linkserver.nittcsc.logic.replaceCalendarEvents
import java.time.ZoneId

class CalendarExporter(private val context: Context) {

    fun sync(lessons: List<GeneratedLesson>): ExportResult {
        return synchronized(SYNC_LOCK) {
            try {
                val calendarId = if (lessons.isNotEmpty()) getWritableCalendarId()
                    ?: return ExportResult(0, lessons.size, "書き込み可能なカレンダーが見つかりませんでした。") else null
                val resolver = context.contentResolver
                val zoneId = ZoneId.systemDefault()
                val items = lessons.distinctBy { lesson ->
                    listOf(lesson.date, lesson.slot.index, lesson.slot.start, lesson.slot.end,
                        lesson.extraLessonId, if (lesson.extraLessonId == null) lesson.subject else null)
                }
                val access = CalendarEventAccess(resolver, AUTO_SYNC_SELECTION, autoSyncSelectionArgs(context.packageName))
                val store = object : CalendarReplacementStore<GeneratedLesson> {
                    override fun activeIds(): Set<Long> = access.activeEventIds()
                    override fun delete(ids: Set<Long>) = access.deleteEvents(ids)
                    override fun insertIfMissing(item: GeneratedLesson, ignoredOldIds: Set<Long>): CalendarInsertResult {
                        val startMillis = item.date.atTime(item.slot.start).atZone(zoneId).toInstant().toEpochMilli()
                        val endMillis = item.date.atTime(item.slot.end).atZone(zoneId).toInstant().toEpochMilli()
                        val title = "${item.subject} (${item.slot.label})"
                        val alreadyExists = if (item.extraLessonId == null) eventExists(calendarId!!, title, startMillis, endMillis, ignoredOldIds)
                            else extraEventExists(calendarId!!, item, title, startMillis, endMillis, ignoredOldIds)
                        if (alreadyExists) return CalendarInsertResult.AlreadyExists
                        val values = ContentValues().apply {
                            put(CalendarContract.Events.CALENDAR_ID, calendarId)
                            put(CalendarContract.Events.TITLE, title)
                            item.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                            put(CalendarContract.Events.DESCRIPTION, "$AUTO_SYNC_MARKER\nNITTC Scheduler\n担当: ${item.teacher}")
                            put(CalendarContract.Events.DTSTART, startMillis)
                            put(CalendarContract.Events.DTEND, endMillis)
                            put(CalendarContract.Events.EVENT_TIMEZONE, zoneId.id)
                            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
                            put(CalendarContract.Events.CUSTOM_APP_URI, autoSyncEventUri(item))
                        }
                        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                            ?: throw IllegalStateException("Calendar provider did not insert event")
                        val id = ContentUris.parseId(uri)
                        require(id >= 0)
                        return CalendarInsertResult.Inserted(id)
                    }
                }
                val result = replaceCalendarEvents(store, items)
                if (result.failure != null) return ExportResult(0, lessons.size.coerceAtLeast(1), context.getString(
                    if (result.failure == CalendarDeletionFailure.PERMISSION) R.string.msg_calendar_delete_permission_denied
                    else R.string.msg_calendar_sync_failed))
                ExportResult(
                    addedCount = result.inserted,
                    skippedCount = lessons.size - result.inserted,
                    message = "授業をカレンダーに${result.inserted}件同期しました。"
                )
            } catch (_: SecurityException) {
                ExportResult(0, lessons.size, "カレンダー権限が不足しています。")
            } catch (_: Exception) {
                ExportResult(0, lessons.size.coerceAtLeast(1), context.getString(R.string.msg_calendar_sync_failed))
            }
        }
    }

    fun clearSyncedLessons(): CalendarDeletionResult {
        return synchronized(SYNC_LOCK) {
            CalendarEventAccess(
                context.contentResolver,
                AUTO_SYNC_SELECTION,
                autoSyncSelectionArgs(context.packageName)
            ).deleteAndVerify()
        }
    }

    fun clearAppCreatedLessonEvents(): CalendarDeletionResult {
        return synchronized(SYNC_LOCK) {
            CalendarEventAccess(
                context.contentResolver,
                LESSON_EVENT_SELECTION,
                lessonEventSelectionArgs(context.packageName)
            ).deleteAndVerify()
        }
    }

    fun countAppCreatedLessonEvents(): Int {
        return synchronized(SYNC_LOCK) {
            countEvents(LESSON_EVENT_SELECTION, lessonEventSelectionArgs(context.packageName))
        }
    }

    fun export(lessons: List<GeneratedLesson>): ExportResult {
        return synchronized(SYNC_LOCK) {
            try {
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

                    val alreadyExists = if (lesson.extraLessonId == null) eventExists(calendarId, title, startMillis, endMillis)
                        else extraEventExists(calendarId, lesson, title, startMillis, endMillis)
                    if (alreadyExists) {
                        skipped++
                        continue
                    }

                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.TITLE, title)
                        lesson.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                        put(CalendarContract.Events.DESCRIPTION, "NITTC Scheduler\\n担当: ${lesson.teacher}")
                        put(CalendarContract.Events.DTSTART, startMillis)
                        put(CalendarContract.Events.DTEND, endMillis)
                        put(CalendarContract.Events.EVENT_TIMEZONE, zoneId.id)
                        put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
                        put(CalendarContract.Events.CUSTOM_APP_URI, manualLessonEventUri(lesson))
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

    private fun eventExists(calendarId: Long, title: String, startMillis: Long, endMillis: Long, ignoredIds: Set<Long> = emptySet()): Boolean {
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = activeCalendarEventSelection(
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ? AND ${CalendarContract.Events.DTEND} = ? AND " +
                "(${CalendarContract.Events.CUSTOM_APP_PACKAGE} IS NULL OR ${CalendarContract.Events.CUSTOM_APP_PACKAGE} != ? OR " +
                "${CalendarContract.Events.CUSTOM_APP_URI} IS NULL OR ${CalendarContract.Events.CUSTOM_APP_URI} NOT LIKE ?)"
        )
        val args = arrayOf(
            calendarId.toString(),
            title,
            startMillis.toString(),
            endMillis.toString(),
            context.packageName,
            "nittcsc://calendar/lesson/%/extra/%"
        )

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            args,
            null
        ) ?: throw IllegalStateException("Calendar provider returned no cursor")
        return cursor.use { while (it.moveToNext()) if (it.getLong(0) !in ignoredIds) return@use true; false }
    }

    private fun extraEventExists(calendarId: Long, lesson: GeneratedLesson, title: String, startMillis: Long, endMillis: Long,
        ignoredIds: Set<Long> = emptySet()): Boolean {
        val selection = activeCalendarEventSelection(
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ? AND " +
                "${CalendarContract.Events.DTSTART} = ? AND ${CalendarContract.Events.DTEND} = ? AND " +
                "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? AND ${CalendarContract.Events.CUSTOM_APP_URI} IN (?, ?)"
        )
        val args = arrayOf(calendarId.toString(), title, startMillis.toString(), endMillis.toString(), context.packageName,
            autoSyncEventUri(lesson), manualLessonEventUri(lesson))
        val cursor = context.contentResolver.query(CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID),
            selection, args, null) ?: throw IllegalStateException("Calendar provider returned no cursor")
        return cursor.use { while (it.moveToNext()) if (it.getLong(0) !in ignoredIds) return@use true; false }
    }

    private fun countEvents(selection: String, selectionArgs: Array<String>): Int {
        return CalendarEventAccess(context.contentResolver, selection, selectionArgs).count()
    }

    companion object {
        private val SYNC_LOCK = CalendarEventAccess.LOCK
        private const val AUTO_SYNC_MARKER = "[NITTC_SCHEDULER_TIMETABLE_AUTO_SYNC]"
        private const val AUTO_SYNC_URI_PREFIX = "nittcsc://calendar/lesson/auto/"
        private const val MANUAL_LESSON_URI_PREFIX = "nittcsc://calendar/lesson/manual/"
        private val AUTO_SYNC_SELECTION =
            "${CalendarContract.Events.DESCRIPTION} LIKE ? OR " +
                "(${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? AND ${CalendarContract.Events.CUSTOM_APP_URI} LIKE ?)"
        private val LESSON_EVENT_SELECTION =
            "${CalendarContract.Events.DESCRIPTION} LIKE ? OR " +
                "${CalendarContract.Events.DESCRIPTION} LIKE ? OR " +
                "${CalendarContract.Events.DESCRIPTION} LIKE ? OR " +
                // 旧形式の授業を拾う条件で、担当付きの課題・予定まで削除しない。
                "(${CalendarContract.Events.DESCRIPTION} LIKE ? AND ${CalendarContract.Events.DESCRIPTION} NOT LIKE ?) OR " +
                "(${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? AND ${CalendarContract.Events.CUSTOM_APP_URI} LIKE ?) OR " +
                "(${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? AND ${CalendarContract.Events.CUSTOM_APP_URI} LIKE ?)"
        private fun autoSyncSelectionArgs(packageName: String) = arrayOf(
            "$AUTO_SYNC_MARKER%",
            packageName,
            "$AUTO_SYNC_URI_PREFIX%"
        )

        private fun lessonEventSelectionArgs(packageName: String) = arrayOf(
            "$AUTO_SYNC_MARKER%",
            "NITTC Scheduler\\n担当:%",
            "NITTC Scheduler\n担当:%",
            "%NITTC Scheduler%担当:%",
            "%NITTC Scheduler - %",
            packageName,
            "$AUTO_SYNC_URI_PREFIX%",
            packageName,
            "$MANUAL_LESSON_URI_PREFIX%"
        )

        private fun autoSyncEventUri(lesson: GeneratedLesson): String {
            return "$AUTO_SYNC_URI_PREFIX${lesson.date}/${lesson.slot.index}" +
                (lesson.extraLessonId?.let { "/extra/${Uri.encode(it)}" } ?: "")
        }

        private fun manualLessonEventUri(lesson: GeneratedLesson): String {
            return "$MANUAL_LESSON_URI_PREFIX${lesson.date}/${lesson.slot.index}" +
                (lesson.extraLessonId?.let { "/extra/${Uri.encode(it)}" } ?: "")
        }
    }
}
