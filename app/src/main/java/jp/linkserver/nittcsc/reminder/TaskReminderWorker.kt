package jp.linkserver.nittcsc.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import jp.linkserver.nittcsc.MainActivity
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.data.EffectiveSchoolSchedule
import jp.linkserver.nittcsc.logic.TaskReminderKind
import jp.linkserver.nittcsc.logic.reminderIsDue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class TaskReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, 0L)
        if (taskId <= 0L) return Result.success()

        val kind = runCatching { TaskReminderKind.valueOf(inputData.getString(KEY_KIND).orEmpty()) }
            .getOrDefault(TaskReminderKind.MANUAL)
        val expected = inputData.getString(KEY_TRIGGER)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (expected == null && kind != TaskReminderKind.MANUAL) return Result.success()
        reminderMutex.withLock {
            val dao = AppDatabase.getInstance(applicationContext).schedulerDao()
            val task = dao.getTaskById(taskId) ?: return Result.success()
            if (task.isCompleted) return Result.success()
            val schedule = EffectiveSchoolSchedule.load(dao) ?: return Result.success()
            val current = reminderTimes(task, schedule)[kind] ?: return Result.success()
            if (!reminderIsDue(expected ?: current, current, LocalDateTime.now()) ||
                !applicationContext.canPostAppNotifications()) return Result.success()
            val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val deliveryKey = "delivered:${workName(taskId, kind)}"
            if (preferences.getString(deliveryKey, null) == current.toString()) return Result.success()

            createNotificationChannel()

            val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                applicationContext,
                task.id.toInt(),
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_assignment)
                .setContentTitle(applicationContext.getString(R.string.task_reminder_notification_title))
                .setContentText(
                    applicationContext.getString(
                        bodyResource(kind),
                        task.title
                    )
                )
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        buildReminderBody(task, kind)
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent)
                .build()

            // Alarmとフォールバックが同時に動いても、永続化した配信記録を同じロックで確認する。
            deliveryGate.deliverOnce(current.toString(),
                { preferences.getString(deliveryKey, null) },
                { preferences.edit().putString(deliveryKey, it).commit() }) {
                NotificationManagerCompat.from(applicationContext).notifyIfAllowed(
                    applicationContext, workName(taskId, kind), 0, notification)
            }
        }
        return Result.success()
    }

    private fun bodyResource(kind: TaskReminderKind): Int = when (kind) {
        TaskReminderKind.MANUAL -> R.string.task_reminder_notification_body
        TaskReminderKind.DAY_BEFORE -> R.string.task_auto_reminder_tomorrow
        TaskReminderKind.DUE_MORNING -> R.string.task_auto_reminder_today
    }

    private fun buildReminderBody(task: TaskEntity, kind: TaskReminderKind): String {
        val dueText = applicationContext.getString(
            R.string.task_reminder_due_summary,
            task.dueDate.toString(),
            String.format("%02d:%02d", task.dueHour, task.dueMinute)
        )
        return buildString {
            append(applicationContext.getString(bodyResource(kind), task.title))
            if (task.subject.isNotBlank()) {
                append("\n")
                append(applicationContext.getString(R.string.task_reminder_subject_summary, task.subject))
            }
            append("\n")
            append(dueText)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.task_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = applicationContext.getString(R.string.task_reminder_channel_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "task_reminders"
        private const val KEY_TASK_ID = "task_id"
        internal const val KEY_KIND = "reminder_kind"
        internal const val KEY_TRIGGER = "trigger_at"
        private const val PREFERENCES = "task_reminder_schedule_v2"
        private const val SCHEDULED_IDS = "scheduled_ids"
        private val reminderMutex = Mutex()
        private val deliveryGate = jp.linkserver.nittcsc.logic.ReminderDeliveryGate()

        fun uniqueWorkName(taskId: Long): String = "task_reminder_$taskId"
        internal fun workName(taskId: Long, kind: TaskReminderKind): String =
            if (kind == TaskReminderKind.MANUAL) uniqueWorkName(taskId) else "task_reminder_${kind.name}_$taskId"

        suspend fun schedule(context: Context, task: TaskEntity) = syncTaskReminder(context, task)

        fun cancel(context: Context, taskId: Long) {
            TaskReminderKind.entries.forEach { cancelKind(context.applicationContext, taskId, it) }
            NotificationManagerCompat.from(context).cancel((taskId % Int.MAX_VALUE).toInt())
        }

        private fun cancelKind(context: Context, taskId: Long, kind: TaskReminderKind, clearNotification: Boolean = true) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(taskId, kind))
            WorkManager.getInstance(context).cancelUniqueWork(workName(taskId, kind) + "_delivery")
            ExactReminderAlarmScheduler.cancel(context, alarmPendingIntent(context, taskId, kind, null))
            if (clearNotification) NotificationManagerCompat.from(context).cancel(workName(taskId, kind), 0)
        }

        internal fun reminderTimes(task: TaskEntity, schedule: EffectiveSchoolSchedule): Map<TaskReminderKind, LocalDateTime> =
            jp.linkserver.nittcsc.data.taskReminderTimes(task, schedule)

        suspend fun syncTaskReminder(context: Context, task: TaskEntity) {
            reminderMutex.withLock {
                val appContext = context.applicationContext
                val dao = AppDatabase.getInstance(appContext).schedulerDao()
                val schedule = EffectiveSchoolSchedule.load(dao) ?: return
                // UIが保存前のデータを渡しても、常に確定済みのDBを正とする。
                val saved = dao.getTaskById(task.id)
                if (saved == null) cancel(appContext, task.id) else sync(appContext, saved, schedule)
            }
        }

        private fun sync(context: Context, task: TaskEntity, schedule: EffectiveSchoolSchedule, forceWorkUpdate: Boolean = false) {
            val times = reminderTimes(task, schedule)
            val now = LocalDateTime.now()
            TaskReminderKind.entries.forEach { kind ->
                val at = times[kind]
                if (at == null) {
                    cancelKind(context, task.id, kind)
                    return@forEach
                }
                val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                val key = "scheduled:${workName(task.id, kind)}"
                if (!at.isAfter(now)) {
                    // Alarm受信でプロセスが起動した際、配信中Workerと1分後のフォールバックを消さない。
                    val previouslyScheduled = preferences.getString(key, null)?.substringBefore('|') == at.toString()
                    if (!previouslyScheduled || !now.isBefore(at.plusMinutes(15))) {
                        cancelKind(context, task.id, kind, clearNotification = false)
                    }
                    return@forEach
                }
                val exact = ExactReminderAlarmScheduler.schedule(context, at,
                    alarmPendingIntent(context, task.id, kind, at))
                val fallback = if (exact) at.plusMinutes(1) else at
                val data = reminderData(task.id, kind, at)
                val request = OneTimeWorkRequestBuilder<TaskReminderWorker>().setInputData(data)
                    .setInitialDelay(Duration.between(now, fallback).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .build()
                val signature = "$at|${ZoneId.systemDefault()}|$exact"
                val policy = if (!forceWorkUpdate && preferences.getString(key, null) == signature) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
                WorkManager.getInstance(context).enqueueUniqueWork(workName(task.id, kind), policy, request)
                preferences.edit().putString(key, signature).apply()
            }
        }

        suspend fun rescheduleAll(context: Context, forceWorkUpdate: Boolean = false) {
            reminderMutex.withLock {
                val appContext = context.applicationContext
                val dao = AppDatabase.getInstance(appContext).schedulerDao()
                val schedule = EffectiveSchoolSchedule.load(dao) ?: return
                val tasks = dao.getTasksOnce()
                val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                val ids = tasks.map { it.id.toString() }.toSet()
                (preferences.getStringSet(SCHEDULED_IDS, emptySet()).orEmpty() - ids).forEach {
                    it.toLongOrNull()?.let { id -> cancel(appContext, id) }
                }
                tasks.forEach { sync(appContext, it, schedule, forceWorkUpdate) }
                preferences.edit().putStringSet(SCHEDULED_IDS, ids).apply()
            }
        }

        fun enqueueNow(context: Context, taskId: Long, kind: TaskReminderKind, at: String?) {
            val trigger = at?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            if (trigger == null && kind != TaskReminderKind.MANUAL) return
            val builder = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInputData(if (trigger != null) reminderData(taskId, kind, trigger) else
                    Data.Builder().putLong(KEY_TASK_ID, taskId).putString(KEY_KIND, kind.name).build())
            // Android 11以前はexpedited実行に別途ForegroundInfoが必要。
            if (Build.VERSION.SDK_INT >= 31) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName(taskId, kind) + "_delivery", ExistingWorkPolicy.KEEP, builder.build())
        }

        private fun reminderData(taskId: Long, kind: TaskReminderKind, at: LocalDateTime): Data = Data.Builder()
            .putLong(KEY_TASK_ID, taskId).putString(KEY_KIND, kind.name).putString(KEY_TRIGGER, at.toString()).build()

        private fun alarmPendingIntent(context: Context, taskId: Long, kind: TaskReminderKind, at: LocalDateTime?): PendingIntent {
            val intent = Intent(context, TaskReminderAlarmReceiver::class.java).apply {
                data = Uri.parse(if (kind == TaskReminderKind.MANUAL) "nittcsc://task-reminder/$taskId"
                    else "nittcsc://task-reminder/$taskId/${kind.name}")
                putExtra(TaskReminderAlarmReceiver.EXTRA_TASK_ID, taskId)
                putExtra(KEY_KIND, kind.name)
                putExtra(KEY_TRIGGER, at?.toString())
            }
            return PendingIntent.getBroadcast(context, (taskId % Int.MAX_VALUE).toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
