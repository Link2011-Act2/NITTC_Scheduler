package jp.linkserver.nittcsc.ml

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import jp.linkserver.nittcsc.MainActivity
import jp.linkserver.nittcsc.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

class VlmInferenceService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "vlm_inference"
        const val ACTION_CANCEL = "jp.linkserver.nittcsc.CANCEL_INFERENCE"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_PROGRESS = "extra_progress"
    }

    private var serviceJob: Job? = null
    private var isCancelled = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_CANCEL -> {
                isCancelled = true
                stopSelf()
                START_STICKY
            }
            else -> {
                // 通知をセットアップしてForeground Serviceを開始
                val notifText = getString(R.string.notif_ai_processing_start)
                val notification = buildNotification(notifText, 0)
                startForeground(NOTIFICATION_ID, notification)
                START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channelName = getString(R.string.notif_ai_processing_channel)
        val channelDesc = getString(R.string.notif_ai_processing_desc)
        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = channelDesc
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun updateNotification(status: String, progress: Int) {
        val notification = buildNotification(status, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(status: String, progress: Int): android.app.Notification {
        val cancelIntent = Intent(this, VlmInferenceService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Android 16+ (API 36): ステータスバーチップに進捗テキストを表示
            android.util.Log.d("VlmInference", "Using promoted-ongoing notification for Android 16+")
            val chipText = "${progress}%"
            val progressBar = "█".repeat((progress / 5).coerceAtMost(20)) +
                              "░".repeat((20 - (progress / 5)).coerceAtLeast(0))
            val detailedText = "$progressBar $chipText\n$status"
            val titleText = getString(R.string.notif_ai_processing_title)

            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(titleText)
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setProgress(100, progress, progress == 0)
                .setContentIntent(contentIntent)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.notif_cancel_action),
                    cancelPendingIntent
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setStyle(Notification.BigTextStyle().bigText(detailedText))
                .setShortCriticalText(chipText)
                // FLAG_PROMOTED_ONGOING によりステータスバーチップとして表示
                .setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
                .build()
        } else {
            // Android 15以下は NotificationCompat で統一
            android.util.Log.d("VlmInference", "Using NotificationCompat notification for Android 15-")
            val progressBar = "▓".repeat((progress / 5).coerceAtMost(20)) +
                              "▒".repeat((20 - (progress / 5)).coerceAtLeast(0))
            val progressText = "$progressBar ${progress}%"
            val detailedText = "$progressText\n$status"
            val titleText = getString(R.string.notif_ai_processing_title)
            
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(titleText)
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setProgress(100, progress, progress == 0)
                .setContentIntent(contentIntent)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.notif_cancel_action),
                    cancelPendingIntent
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detailedText))
                .build()
        }
    }

    fun isCancelRequested(): Boolean = isCancelled

    fun resetCancelFlag() {
        isCancelled = false
    }
}
