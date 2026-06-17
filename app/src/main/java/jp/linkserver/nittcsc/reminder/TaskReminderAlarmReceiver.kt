package jp.linkserver.nittcsc.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId > 0L) {
            TaskReminderWorker.enqueueNow(context.applicationContext, taskId)
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}
