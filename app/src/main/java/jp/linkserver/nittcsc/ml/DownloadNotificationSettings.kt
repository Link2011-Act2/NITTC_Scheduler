package jp.linkserver.nittcsc.ml

import android.content.Context

enum class DownloadProgressDisplayMode {
    PER_FILE,
    OVERALL
}

object DownloadNotificationSettings {
    private const val PREFS_NAME = "download_notification_settings"
    private const val KEY_PROGRESS_MODE = "progress_mode"

    fun getProgressDisplayMode(context: Context): DownloadProgressDisplayMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_PROGRESS_MODE, DownloadProgressDisplayMode.PER_FILE.name)) {
            DownloadProgressDisplayMode.OVERALL.name -> DownloadProgressDisplayMode.OVERALL
            else -> DownloadProgressDisplayMode.PER_FILE
        }
    }

    fun setProgressDisplayMode(context: Context, mode: DownloadProgressDisplayMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROGRESS_MODE, mode.name)
            .apply()
    }
}
