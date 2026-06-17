package jp.linkserver.nittcsc.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlanReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getLongExtra(EXTRA_PLAN_ID, 0L)
        if (planId > 0L) {
            PlanReminderWorker.enqueueNow(context.applicationContext, planId)
        }
    }

    companion object {
        const val EXTRA_PLAN_ID = "plan_id"
    }
}
