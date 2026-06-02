package jp.linkserver.nittcsc.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import jp.linkserver.nittcsc.MainActivity
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.PlanEntity
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class PlanReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val planId = inputData.getLong(KEY_PLAN_ID, 0L)
        if (planId <= 0L) return Result.success()

        val plan = AppDatabase.getInstance(applicationContext)
            .schedulerDao()
            .getPlanById(planId)
            ?: return Result.success()

        if (!plan.reminderEnabled || plan.isCompleted || plan.reminderDate == null) {
            return Result.success()
        }

        createNotificationChannel()

        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            applicationContext,
            plan.id.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.plan_reminder_notification_title))
            .setContentText(
                applicationContext.getString(
                    R.string.plan_reminder_notification_body,
                    plan.title
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildReminderBody(plan)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(planReminderNotificationId(plan.id), notification)

        return Result.success()
    }

    private fun buildReminderBody(plan: PlanEntity): String {
        val dueText = applicationContext.getString(
            R.string.task_reminder_due_summary,
            plan.dueDate.toString(),
            String.format("%02d:%02d", plan.dueHour, plan.dueMinute)
        )
        return buildString {
            append(applicationContext.getString(R.string.plan_reminder_notification_body, plan.title))
            if (plan.subject.isNotBlank()) {
                append("\n")
                append(applicationContext.getString(R.string.task_reminder_subject_summary, plan.subject))
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
            applicationContext.getString(R.string.plan_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = applicationContext.getString(R.string.plan_reminder_channel_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "plan_reminders"
        private const val KEY_PLAN_ID = "plan_id"

        private fun uniqueWorkName(planId: Long): String = "plan_reminder_$planId"

        fun cancel(context: Context, planId: Long) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(uniqueWorkName(planId))
            NotificationManagerCompat.from(context.applicationContext)
                .cancel(planReminderNotificationId(planId))
        }

        fun syncPlanReminder(context: Context, plan: PlanEntity) {
            if (!plan.reminderEnabled || plan.isCompleted || plan.reminderDate == null || plan.id <= 0L) {
                cancel(context, plan.id)
                return
            }

            val reminderAt = LocalDateTime.of(
                plan.reminderDate,
                java.time.LocalTime.of(plan.reminderHour, plan.reminderMinute)
            )
            val delayMillis = Duration.between(
                LocalDateTime.now(ZoneId.systemDefault()),
                reminderAt
            ).toMillis()

            if (delayMillis <= 0L) {
                cancel(context, plan.id)
                return
            }

            val request = OneTimeWorkRequestBuilder<PlanReminderWorker>()
                .setInputData(Data.Builder().putLong(KEY_PLAN_ID, plan.id).build())
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(uniqueWorkName(plan.id), ExistingWorkPolicy.REPLACE, request)
        }

        suspend fun rescheduleAll(context: Context) {
            val plans = AppDatabase.getInstance(context.applicationContext)
                .schedulerDao()
                .getPlansOnce()
            plans.forEach { syncPlanReminder(context, it) }
        }

        private fun planReminderNotificationId(planId: Long): Int {
            return (planId % Int.MAX_VALUE).toInt() + 20_000
        }
    }
}
