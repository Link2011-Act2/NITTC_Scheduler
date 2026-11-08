package jp.linkserver.nittcsc.update

import android.content.Context
import jp.linkserver.goldy.*

private const val UPDATE_PREFS = "github_release_updates"
private const val KEY_SHOW_LATEST_FOR_TESTING = "show_latest_release_for_testing"
private const val KEY_CURRENT_VERSION_OVERRIDE_FOR_TESTING = "current_version_override_for_testing"
fun isIntDevBuild(version: String) = VersionPolicies.Common.channel(version)==ReleaseChannel.IntDev
fun detectReleaseChannel(version: String): String = VersionPolicies.Common.channel(version).let {
    when(it) { ReleaseChannel.Rc -> "PreRelease"; else -> it.name }
}
fun clearDismissedUpdateNotification(context: Context) = AppUpdates.get(context).resetIgnoredVersions()
internal fun updateOptions(context: Context) = RuntimeOptions(developer=DeveloperOptions(
    ignoreVersionComparison=isShowLatestReleaseForTestingEnabled(context,installedVersionName(context)),
    currentVersionOverride=getUpdateCurrentVersionOverrideForTesting(context,installedVersionName(context)).takeIf { it.isNotBlank() }))

fun isShowLatestReleaseForTestingEnabled(context: Context, currentVersion: String): Boolean {
    if (!isIntDevBuild(installedVersionName(context))) {
        return false
    }
    return context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_SHOW_LATEST_FOR_TESTING, false)
}

fun setShowLatestReleaseForTestingEnabled(context: Context, currentVersion: String, enabled: Boolean) {
    val appliedValue = if (isIntDevBuild(installedVersionName(context))) enabled else false
    context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_SHOW_LATEST_FOR_TESTING, appliedValue)
        .apply()
}

fun getUpdateCurrentVersionOverrideForTesting(context: Context, currentVersion: String): String {
    if (!isIntDevBuild(installedVersionName(context))) {
        return ""
    }
    return context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_CURRENT_VERSION_OVERRIDE_FOR_TESTING, null)
        .orEmpty()
}

fun setUpdateCurrentVersionOverrideForTesting(context: Context, currentVersion: String, override: String) {
    val prefs = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
    val trimmedOverride = override.trim()
    val editor = prefs.edit()
    if (!isIntDevBuild(installedVersionName(context)) || trimmedOverride.isBlank()) {
        editor.remove(KEY_CURRENT_VERSION_OVERRIDE_FOR_TESTING)
    } else {
        editor.putString(KEY_CURRENT_VERSION_OVERRIDE_FOR_TESTING, trimmedOverride)
    }
    editor.apply()
}

