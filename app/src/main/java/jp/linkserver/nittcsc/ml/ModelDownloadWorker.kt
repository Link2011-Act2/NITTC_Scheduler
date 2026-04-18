package jp.linkserver.nittcsc.ml

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.work.Data
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = NotificationManagerCompat.from(context)
    private val modelManager = ModelDownloadManager(context)

    override suspend fun doWork(): Result {
        return try {
            val modelUrl = inputData.getString("model_url") ?: return Result.failure()
            val fileName = inputData.getString("file_name") ?: return Result.failure()
            val authToken = inputData.getString("auth_token")

            createNotificationChannel()
            
            // Foreground Service として明示的に設定
            try {
                setForeground(getForegroundInfo())
            } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
                // バックグラウンド制限によりフォアグラウンド昇格不可 — 通知なしで続行
                android.util.Log.w("ModelDownloadWorker", "Cannot start foreground service, proceeding in background")
            }
            
            var lastProgress = 0
            try {
                modelManager.downloadModel(modelUrl, fileName, authToken).collect { state ->
                    when (state) {
                        is DownloadState.Downloading -> {
                            val progress = (state.progress * 100).toInt()
                            if (progress != lastProgress) {
                                val notification = createProgressNotification(
                                    fileName,
                                    progress,
                                    state.speedMbps
                                )
                                notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
                                try {
                                    setForeground(getForegroundInfo(notification))
                                } catch (_: Exception) { /* foreground update failed, notification still posted */ }
                                val progressData = Data.Builder()
                                    .putInt("progress", progress)
                                    .putFloat("speed_mbps", state.speedMbps)
                                    .build()
                                setProgress(progressData)
                                lastProgress = progress
                            }
                        }
                        is DownloadState.Success -> {
                            val successNotification = createSuccessNotification(fileName)
                            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, successNotification)
                        }
                        is DownloadState.Error -> {
                            val errorNotification = createErrorNotification(
                                fileName,
                                state.exception.message ?: "Unknown error"
                            )
                            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, errorNotification)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                return Result.retry()
            }

            Result.success()
        } catch (e: Exception) {
            val errorNotification = createErrorNotification(
                "Model Download",
                e.message ?: "Unknown error occurred"
            )
            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, errorNotification)
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return getForegroundInfo(createProgressNotification("ダウンロード中...", 0, 0f))
    }

    private fun getForegroundInfo(notification: android.app.Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DOWNLOAD_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(DOWNLOAD_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channelName = applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_download_channel)
        val channelDesc = applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_download_channel_desc)
        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = channelDesc
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createProgressNotification(
        fileName: String,
        progress: Int,
        speedMbps: Float
    ): android.app.Notification {
        val cancelIntent = Intent(applicationContext, DownloadCancelReceiver::class.java).apply {
            action = "jp.linkserver.nittcsc.CANCEL_DOWNLOAD"
            data = android.net.Uri.parse("download://$fileName")
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val titleTemplate = applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_download_title)
        val speedFormat = applicationContext.getString(jp.linkserver.nittcsc.R.string.format_speed_mbps)
        val titleText = titleTemplate.replace("%s", fileName)
        val speedText = String.format(speedFormat, speedMbps)
        val contentText = "${progress}% - $speedText"

        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16+: Notification.ProgressStyle で Live Updates として認識
            val cancelAction = Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(
                    applicationContext, android.R.drawable.ic_menu_close_clear_cancel
                ),
                applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_cancel_action),
                cancelPendingIntent
            ).build()

            return Notification.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(titleText)
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(
                    Notification.ProgressStyle()
                        .setProgress(progress)
                        .setStyledByProgress(true)
                )
                .addAction(cancelAction)
                .setOngoing(true)
                .build()
        } else {
            // Android 15以前: NotificationCompat + テキストプログレスバー
            val progressBar = "▓".repeat((progress / 5).coerceAtMost(20)) +
                    "▒".repeat((20 - (progress / 5)).coerceAtLeast(0))

            return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(titleText)
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setProgress(100, progress, false)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$progressBar ${progress}%\n$contentText")
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_cancel_action),
                    cancelPendingIntent
                )
                .setOngoing(true)
                .build()
        }
    }

    private fun createSuccessNotification(fileName: String): android.app.Notification {
        val titleText = applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_download_complete_title)
        val textTemplate = applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_download_complete_text)
        val contentText = textTemplate.replace("%s", fileName)
        
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun createErrorNotification(
        fileName: String,
        errorMessage: String
    ): android.app.Notification {
        val titleText = applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_download_failed_title)
        val textTemplate = applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_download_failed_text)
        val contentText = textTemplate.replace("%s", fileName).replace("%s", errorMessage)
        
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "model_download_channel"
        const val DOWNLOAD_NOTIFICATION_ID = 2001
    }
}
