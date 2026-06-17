package jp.linkserver.nittcsc.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId

internal object ExactReminderAlarmScheduler {
    private const val TAG = "ExactReminderAlarm"

    fun schedule(
        context: Context,
        triggerAt: LocalDateTime,
        pendingIntent: PendingIntent
    ): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }
        val triggerAtMillis = triggerAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        if (triggerAtMillis <= System.currentTimeMillis()) return false

        return runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            true
        }.onFailure {
            Log.w(TAG, "Cannot schedule exact reminder alarm", it)
        }.getOrDefault(false)
    }

    fun cancel(context: Context, pendingIntent: PendingIntent) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
    }
}
