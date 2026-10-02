package jp.linkserver.nittcsc.qr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import jp.linkserver.nittcsc.calendar.TaskCalendarSync
import jp.linkserver.nittcsc.data.QrShareReplacedData
import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.reminder.LessonStartNotificationWorker
import jp.linkserver.nittcsc.reminder.PlanReminderWorker
import jp.linkserver.nittcsc.reminder.TaskReminderWorker
import jp.linkserver.nittcsc.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException

/** DB保存後の端末固有の処理。失敗時は「保存済み・連携の一部失敗」として通知する。 */
internal suspend fun updateQrShareIntegrations(context: Context, repository: SchedulerRepository, replaced: QrShareReplacedData): Boolean {
    var failed = false
    suspend fun attempt(action: suspend () -> Unit) {
        try { action() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    val hasCalendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val calendar = TaskCalendarSync(context)
    replaced.tasks.forEach { task ->
        attempt { TaskReminderWorker.cancel(context, task.id) }
        if (hasCalendar) listOfNotNull(task.calendarEventId, task.reminderCalendarEventId).distinct().forEach { id ->
            attempt { if (!calendar.deleteTaskEvent(id)) failed = true }
        }
    }
    replaced.plans.forEach { plan ->
        attempt { PlanReminderWorker.cancel(context, plan.id) }
        if (hasCalendar) listOfNotNull(plan.calendarEventId, plan.reminderCalendarEventId).distinct().forEach { id ->
            attempt { if (!calendar.deletePlanEvent(id)) failed = true }
        }
    }
    if (hasCalendar && replaced.syncCalendar) {
        replaced.importedTasks.forEach { task -> attempt {
            val synced = calendar.syncTask(task)
            if (synced.calendarEventId == null) failed = true
            repository.upsertTask(synced)
        } }
        replaced.importedPlans.forEach { plan -> attempt {
            val synced = calendar.syncPlan(plan)
            if (synced.calendarEventId == null) failed = true
            repository.upsertPlan(synced)
        } }
    }
    attempt { TaskReminderWorker.rescheduleAll(context) }
    attempt { PlanReminderWorker.rescheduleAll(context) }
    attempt { LessonStartNotificationWorker.rescheduleAll(context) }
    attempt { WidgetUpdater.updateAll(context) }
    return failed
}
