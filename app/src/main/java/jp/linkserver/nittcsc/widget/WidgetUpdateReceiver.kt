package jp.linkserver.nittcsc.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 端末起動完了・日付変更・時刻変更を受信してウィジェットを更新するレシーバー。
 * BOOT_COMPLETED はマニフェストに登録。
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // WorkManager の定期スケジュールを（再）登録
                WidgetUpdateWorker.schedule(appContext)

                // すぐにウィジェットも更新
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetUpdater.updateAll(appContext)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
