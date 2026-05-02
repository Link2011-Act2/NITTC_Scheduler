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
import kotlinx.coroutines.CancellationException

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
            val modelId = inputData.getString("model_id")
            val modelName = inputData.getString("model_name")
            val assetIndex = inputData.getInt("asset_index", 0)
            val assetCount = inputData.getInt("asset_count", 1).coerceAtLeast(1)
            val authToken = inputData.getString("auth_token")
            val progressDisplayMode = DownloadNotificationSettings.getProgressDisplayMode(applicationContext)

            createNotificationChannel()
            
            // Foreground Service として明示的に設定
            try {
                setForeground(getForegroundInfo())
            } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
                // バックグラウンド制限によりフォアグラウンド昇格不可 — 通知なしで続行
                android.util.Log.w("ModelDownloadWorker", "Cannot start foreground service, proceeding in background")
            }
            
            var lastProgress = 0
            var failureMessage: String? = null
            modelManager.downloadModel(modelUrl, fileName, authToken).collect { state ->
                when (state) {
                    is DownloadState.Downloading -> {
                        val progress = (state.progress * 100).toInt()
                        if (progress != lastProgress) {
                            val notification = createProgressNotification(
                                fileName,
                                progress,
                                state.speedMbps,
                                modelId,
                                modelName,
                                assetIndex,
                                assetCount,
                                progressDisplayMode
                            )
                            // setForeground 経由で更新することで FLAG_FOREGROUND_SERVICE が付き
                            // Android 16+ の Live Updates チップに反映される
                            try {
                                setForeground(getForegroundInfo(notification))
                            } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
                                // フォアグラウンド起動不可の場合は直接 notify にフォールバック
                                notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
                            } catch (_: Exception) {
                                notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
                            }
                            val progressData = Data.Builder()
                                .putInt("progress", progress)
                                .putFloat("speed_mbps", state.speedMbps)
                                .build()
                            setProgress(progressData)
                            lastProgress = progress
                        }
                    }
                    is DownloadState.Success -> {
                        val successNotification = createSuccessNotification(
                            fileName = fileName,
                            modelName = modelName,
                            assetCount = assetCount,
                            progressDisplayMode = progressDisplayMode
                        )
                        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, successNotification)
                    }
                    is DownloadState.Error -> {
                        failureMessage = state.exception.message ?: "Unknown error"
                        val errorNotification = createErrorNotification(
                            fileName = fileName,
                            errorMessage = failureMessage,
                            modelName = modelName,
                            assetCount = assetCount,
                            progressDisplayMode = progressDisplayMode
                        )
                        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, errorNotification)
                    }
                    else -> {}
                }
            }

            if (failureMessage != null) {
                Result.failure(
                    Data.Builder()
                        .putString("error", failureMessage)
                        .build()
                )
            } else {
                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorNotification = createErrorNotification(
                "Model Download",
                e.message ?: "Unknown error occurred"
            )
            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, errorNotification)
            Result.failure(
                Data.Builder()
                    .putString("error", e.message ?: "Unknown error occurred")
                    .build()
            )
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return getForegroundInfo(
            createProgressNotification(
                fileName = "ダウンロード中...",
                progress = 0,
                speedMbps = 0f,
                modelId = null,
                modelName = null,
                assetIndex = 0,
                assetCount = 1,
                progressDisplayMode = DownloadProgressDisplayMode.PER_FILE
            )
        )
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
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = channelDesc
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createProgressNotification(
        fileName: String,
        progress: Int,
        speedMbps: Float,
        modelId: String?,
        modelName: String?,
        assetIndex: Int,
        assetCount: Int,
        progressDisplayMode: DownloadProgressDisplayMode
    ): android.app.Notification {
        val cancelIntent = Intent(applicationContext, DownloadCancelReceiver::class.java).apply {
            action = "jp.linkserver.nittcsc.CANCEL_DOWNLOAD"
            data = android.net.Uri.parse("download://$fileName")
            putExtra("file_name", fileName)
            putExtra("model_id", modelId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val speedFormat = applicationContext.getString(jp.linkserver.nittcsc.R.string.format_speed_mbps)
        val speedText = String.format(speedFormat, speedMbps)
        val (displayTitle, displayProgress, contentText) = when (progressDisplayMode) {
            DownloadProgressDisplayMode.OVERALL -> {
                val overallProgress = (((assetIndex + progress / 100f) / assetCount) * 100f).toInt()
                    .coerceIn(0, 100)
                val titleText = applicationContext.getString(
                    jp.linkserver.nittcsc.R.string.notif_download_title,
                    modelName ?: fileName
                )
                val contentText = applicationContext.getString(
                    jp.linkserver.nittcsc.R.string.notif_download_overall_text,
                    overallProgress,
                    assetIndex + 1,
                    assetCount,
                    speedText
                )
                Triple(titleText, overallProgress, contentText)
            }
            DownloadProgressDisplayMode.PER_FILE -> {
                val titleText = applicationContext.getString(
                    jp.linkserver.nittcsc.R.string.notif_download_title,
                    fileName
                )
                Triple(titleText, progress, "${progress}% - $speedText")
            }
        }

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
                .setContentTitle(displayTitle)
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(
                    Notification.ProgressStyle()
                        .setProgress(displayProgress)
                        .setStyledByProgress(true)
                )
                .addAction(cancelAction)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShortCriticalText("${displayProgress}%")
                // FLAG_PROMOTED_ONGOING によりステータスバーチップとして表示
                .setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
                .build()
        } else {
            // Android 15以前: NotificationCompat + テキストプログレスバー
            val progressBar = "▓".repeat((displayProgress / 5).coerceAtMost(20)) +
                    "▒".repeat((20 - (displayProgress / 5)).coerceAtLeast(0))

            return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(displayTitle)
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setProgress(100, displayProgress, false)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$progressBar ${displayProgress}%\n$contentText")
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_cancel_action),
                    cancelPendingIntent
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
    }

    private fun createSuccessNotification(
        fileName: String,
        modelName: String?,
        assetCount: Int,
        progressDisplayMode: DownloadProgressDisplayMode
    ): android.app.Notification {
        val titleText = applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_download_complete_title)
        val contentText = applicationContext.getString(
            jp.linkserver.nittcsc.R.string.notif_download_complete_text,
            if (progressDisplayMode == DownloadProgressDisplayMode.OVERALL && assetCount > 1) {
                modelName ?: fileName
            } else {
                fileName
            }
        )
        
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
        errorMessage: String,
        modelName: String? = null,
        assetCount: Int = 1,
        progressDisplayMode: DownloadProgressDisplayMode = DownloadProgressDisplayMode.PER_FILE
    ): android.app.Notification {
        val titleText = applicationContext.getString(jp.linkserver.nittcsc.R.string.notif_download_failed_title)
        val contentText = applicationContext.getString(
            jp.linkserver.nittcsc.R.string.notif_download_failed_text,
            if (progressDisplayMode == DownloadProgressDisplayMode.OVERALL && assetCount > 1) {
                modelName ?: fileName
            } else {
                fileName
            },
            errorMessage
        )
        
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "model_download_channel_default"
        const val DOWNLOAD_NOTIFICATION_ID = 2001
    }
}
