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

        val task = AppDatabase.getInstance(applicationContext)
            .schedulerDao()
            .getTaskById(taskId)
            ?: return Result.success()

        if (!task.reminderEnabled || task.isCompleted || task.reminderDate == null) {
            return Result.success()
        }

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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.task_reminder_notification_title))
            .setContentText(
                applicationContext.getString(
                    R.string.task_reminder_notification_body,
                    task.title
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildReminderBody(task)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(taskReminderNotificationId(task.id), notification)

        return Result.success()
    }

    private fun buildReminderBody(task: TaskEntity): String {
        val dueText = applicationContext.getString(
            R.string.task_reminder_due_summary,
            task.dueDate.toString(),
            String.format("%02d:%02d", task.dueHour, task.dueMinute)
        )
        return buildString {
            append(applicationContext.getString(R.string.task_reminder_notification_body, task.title))
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

        fun uniqueWorkName(taskId: Long): String = "task_reminder_$taskId"

        fun schedule(context: Context, task: TaskEntity) {
            syncTaskReminder(context, task)
        }

        fun cancel(context: Context, taskId: Long) {
            val appContext = context.applicationContext
            WorkManager.getInstance(appContext)
                .cancelUniqueWork(uniqueWorkName(taskId))
            ExactReminderAlarmScheduler.cancel(appContext, alarmPendingIntent(appContext, taskId))
            NotificationManagerCompat.from(appContext)
                .cancel(taskReminderNotificationId(taskId))
        }

        fun syncTaskReminder(context: Context, task: TaskEntity) {
            if (!task.reminderEnabled || task.isCompleted || task.reminderDate == null || task.id <= 0L) {
                cancel(context, task.id)
                return
            }

            val reminderAt = LocalDateTime.of(
                task.reminderDate,
                java.time.LocalTime.of(task.reminderHour, task.reminderMinute)
            )
            val appContext = context.applicationContext
            val exactAlarmScheduled = ExactReminderAlarmScheduler.schedule(
                appContext,
                reminderAt,
                alarmPendingIntent(appContext, task.id)
            )
            val fallbackAt = if (exactAlarmScheduled) reminderAt.plusMinutes(1) else reminderAt
            val delayMillis = Duration.between(
                LocalDateTime.now(ZoneId.systemDefault()),
                fallbackAt
            ).toMillis()

            if (delayMillis <= 0L) {
                cancel(context, task.id)
                return
            }

            val inputData = Data.Builder()
                .putLong(KEY_TASK_ID, task.id)
                .build()

            val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(
                    uniqueWorkName(task.id),
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        suspend fun rescheduleAll(context: Context) {
            val tasks = AppDatabase.getInstance(context.applicationContext)
                .schedulerDao()
                .getTasksOnce()
            tasks.forEach { syncTaskReminder(context, it) }
        }

        fun enqueueNow(context: Context, taskId: Long) {
            val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInputData(Data.Builder().putLong(KEY_TASK_ID, taskId).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(uniqueWorkName(taskId), ExistingWorkPolicy.REPLACE, request)
        }

        private fun alarmPendingIntent(context: Context, taskId: Long): PendingIntent {
            val intent = Intent(context, TaskReminderAlarmReceiver::class.java).apply {
                data = Uri.parse("nittcsc://task-reminder/$taskId")
                putExtra(TaskReminderAlarmReceiver.EXTRA_TASK_ID, taskId)
            }
            return PendingIntent.getBroadcast(
                context,
                taskReminderNotificationId(taskId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun taskReminderNotificationId(taskId: Long): Int {
            return (taskId % Int.MAX_VALUE).toInt()
        }
    }
}
