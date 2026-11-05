package jp.linkserver.nittcsc

import android.app.Application
import jp.linkserver.nittcsc.reminder.NotificationScheduleCoordinator

class SchedulerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationScheduleCoordinator.start(this)
    }
}
