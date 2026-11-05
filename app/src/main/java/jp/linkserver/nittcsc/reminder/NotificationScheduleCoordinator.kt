package jp.linkserver.nittcsc.reminder

import android.content.Context
import android.util.Log
import jp.linkserver.nittcsc.data.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect

/** UIの生存期間やデザイン切替に依存せず、インポート・同期を含むDB変更を監視する。 */
object NotificationScheduleCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            AppDatabase.getInstance(appContext).invalidationTracker.createFlow(
                "settings", "tasks", "lessons", "day_types", "cancelled_lessons", "changed_lessons",
                "exam_day_schedules", "exam_lessons", "lesson_notification_exclusions"
            ).debounce(300).collect { refresh(appContext) }
        }
    }

    suspend fun refresh(context: Context) {
        try {
            TaskReminderWorker.rescheduleAll(context)
            LessonStartNotificationWorker.rescheduleAll(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("NotificationSchedule", "Cannot refresh notification schedules", error)
        }
    }
}
