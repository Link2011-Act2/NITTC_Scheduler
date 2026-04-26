package jp.linkserver.nittcsc.ml

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "jp.linkserver.nittcsc.CANCEL_DOWNLOAD") {
            val fileName = intent.getStringExtra("file_name")
                ?: intent.data?.schemeSpecificPart
            val modelId = intent.getStringExtra("model_id")
            val workManager = WorkManager.getInstance(context)
            val manager = ModelDownloadManager(context)

            if (!modelId.isNullOrBlank()) {
                workManager.cancelUniqueWork("model_dl_$modelId")
            } else if (!fileName.isNullOrBlank()) {
                workManager.cancelUniqueWork("model_download_$fileName")
            } else {
                workManager.cancelAllWorkByTag("model_download")
            }

            if (!fileName.isNullOrBlank()) {
                manager.deleteModel(fileName)
            }
            android.util.Log.d("DownloadCancel", "Download cancelled via notification")
        }
    }
}
