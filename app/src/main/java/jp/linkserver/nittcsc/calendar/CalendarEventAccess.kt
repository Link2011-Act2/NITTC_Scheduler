package jp.linkserver.nittcsc.calendar

import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import jp.linkserver.nittcsc.logic.CalendarDeletionResult
import jp.linkserver.nittcsc.logic.CalendarDeletionStore
import jp.linkserver.nittcsc.logic.activeCalendarEventSelection
import jp.linkserver.nittcsc.logic.deleteCalendarEvents

internal class CalendarEventAccess(
    private val resolver: ContentResolver,
    selection: String,
    private val selectionArgs: Array<String>
) : CalendarDeletionStore {
    private val activeSelection = activeCalendarEventSelection(selection)

    override fun activeEventIds(): Set<Long> {
        val cursor = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            activeSelection,
            selectionArgs,
            null
        ) ?: throw IllegalStateException("Calendar provider returned no cursor")
        return cursor.use {
            buildSet {
                while (it.moveToNext()) add(it.getLong(0))
            }
        }
    }

    override fun deleteEvents(ids: Set<Long>) {
        // 対象IDを固定し、大量の授業でもSQLiteのパラメータ上限を超えないよう分割する。
        ids.toList().chunked(100).forEach { batch ->
            val placeholders = batch.joinToString(",") { "?" }
            resolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "$activeSelection AND ${CalendarContract.Events._ID} IN ($placeholders)",
                selectionArgs + batch.map(Long::toString)
            )
        }
    }

    fun deleteAndVerify(): CalendarDeletionResult = synchronized(LOCK) { deleteCalendarEvents(this) }
    fun count(): Int = synchronized(LOCK) { activeEventIds().size }

    companion object {
        val LOCK = Any()
    }
}

enum class CalendarEventCategory { LESSONS, DEADLINES, REMINDERS }

class AppCalendarEventCleaner(context: Context) {
    private val lessons = CalendarExporter(context)
    private val tasks = TaskCalendarSync(context)

    fun count(categories: List<CalendarEventCategory>): Map<CalendarEventCategory, Int> =
        synchronized(CalendarEventAccess.LOCK) {
            categories.associateWith { category ->
                when (category) {
                    CalendarEventCategory.LESSONS -> lessons.countAppCreatedLessonEvents()
                    CalendarEventCategory.DEADLINES -> tasks.countDeadlineEvents()
                    CalendarEventCategory.REMINDERS -> tasks.countReminderEvents()
                }
            }
        }

    fun delete(categories: List<CalendarEventCategory>): Map<CalendarEventCategory, CalendarDeletionResult> =
        synchronized(CalendarEventAccess.LOCK) {
            categories.associateWith { category ->
                when (category) {
                    CalendarEventCategory.LESSONS -> lessons.clearAppCreatedLessonEvents()
                    CalendarEventCategory.DEADLINES -> tasks.clearDeadlineEvents()
                    CalendarEventCategory.REMINDERS -> tasks.clearReminderEvents()
                }
            }
        }
}
