package jp.linkserver.nittcsc.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class LessonStartNotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = runCatching {
            LocalDate.parse(intent.getStringExtra(EXTRA_DATE).orEmpty())
        }.getOrNull() ?: return
        val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)
        if (slotIndex < 0) return

        LessonStartNotificationWorker.enqueueNow(context.applicationContext, date, slotIndex)
    }

    companion object {
        const val EXTRA_DATE = "date"
        const val EXTRA_SLOT_INDEX = "slot_index"
    }
}
