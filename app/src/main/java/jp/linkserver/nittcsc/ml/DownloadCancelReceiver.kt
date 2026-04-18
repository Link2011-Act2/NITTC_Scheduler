package jp.linkserver.nittcsc.ml

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "jp.linkserver.nittcsc.CANCEL_DOWNLOAD") {
            // すべてのダウンロード作業をキャンセル
            WorkManager.getInstance(context).cancelAllWorkByTag("model_download")
            android.util.Log.d("DownloadCancel", "Download cancelled via notification")
        }
    }
}
