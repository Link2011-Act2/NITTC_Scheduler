package jp.linkserver.nittcsc.update

import android.content.Context
import jp.linkserver.goldy.*

/** APK distribution only. AI runtime/model feeds and workers are independent. */
internal object ApkUpdateProfile {
    val repository = GitHubRepository("Link2011-Act2", "NITTC_Scheduler")
    val policy = UpdatePolicies.nittcScheduler()
    val selection = ApkSelectionPolicies.NittcScheduler
    const val ACTION_VIEW_TRANSFER = "jp.linkserver.nittcsc.VIEW_APK_UPDATE"
    const val JOB_ID = 0x4741504b
    const val NOTIFICATION_ID = 0x4741504b
    const val WORK_NAME = "nittc-apk-update-transfer-v1"
    const val CHANNEL = "nittc-apk-update-transfer"
    const val JOB_NAMESPACE = "nittc-apk-updates"
    fun downloadConfig(context: Context) = ApkDownloadConfig(repository, selection, ApkVersionBindings.Published,
        userAgent = "NITTCScheduler/${installedVersionName(context)}")
}

@Suppress("DEPRECATION")
internal fun installedVersionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName?.takeIf { it.isNotBlank() }
        ?: error("Installed package version is missing")
