package jp.linkserver.nittcsc.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

fun Context.canPostAppNotifications(): Boolean {
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
fun NotificationManagerCompat.notifyIfAllowed(
    context: Context,
    notificationId: Int,
    notification: Notification
) {
    if (context.canPostAppNotifications()) {
        notify(notificationId, notification)
    }
}
