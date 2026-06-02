package jp.linkserver.nittcsc.update

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val UPDATE_PREFS = "github_release_updates"
private const val KEY_LAST_CHECK_MS = "last_check_ms"
private const val KEY_DISMISSED_UPDATE_TAG = "dismissed_update_tag"
private const val KEY_SHOW_LATEST_FOR_TESTING = "show_latest_release_for_testing"
private const val CHECK_INTERVAL_MS = 8L * 60L * 60L * 1000L

data class AppUpdateInfo(
    val tagName: String,
    val channel: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkAssetName: String?,
    val apkDownloadUrl: String?,
    val isPrerelease: Boolean
)

suspend fun checkGitHubReleaseUpdate(
    repositoryUrl: String,
    currentVersion: String,
    userAgentName: String = "NITTCScheduler",
    showLatestForTesting: Boolean = false
): Result<AppUpdateInfo?> {
    return runCatching {
        val repository = parseGitHubRepository(repositoryUrl)
            ?: error("Unsupported GitHub repository URL")
        val releases = fetchReleases(repository.owner, repository.name, "$userAgentName/$currentVersion")
        val candidates = releases
            .map { it.toUpdateInfo() }
            .filter { showLatestForTesting || isNewerRelease(it.tagName, currentVersion, it.isPrerelease) }
        candidates.maxWithOrNull(::compareUpdateInfo)
    }
}

fun markUpdateCheckFinished(context: Context) {
    context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis())
        .apply()
}

fun shouldCheckForUpdates(context: Context, currentVersion: String): Boolean {
    if (isShowLatestReleaseForTestingEnabled(context, currentVersion)) {
        return true
    }
    val prefs = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
    val lastCheckMs = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
    return System.currentTimeMillis() - lastCheckMs >= CHECK_INTERVAL_MS
}

fun isUpdateNotificationDismissed(context: Context, tagName: String): Boolean {
    return context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_DISMISSED_UPDATE_TAG, null)
        .equals(tagName, ignoreCase = true)
}

fun dismissUpdateNotificationUntilNextVersion(context: Context, tagName: String) {
    context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_DISMISSED_UPDATE_TAG, tagName)
        .apply()
}

fun clearDismissedUpdateNotification(context: Context) {
    context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_DISMISSED_UPDATE_TAG)
        .apply()
}

fun isShowLatestReleaseForTestingEnabled(context: Context, currentVersion: String): Boolean {
    if (!isIntDevBuild(currentVersion)) {
        return false
    }
    return context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_SHOW_LATEST_FOR_TESTING, false)
}

fun setShowLatestReleaseForTestingEnabled(context: Context, currentVersion: String, enabled: Boolean) {
    val appliedValue = if (isIntDevBuild(currentVersion)) enabled else false
    context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_SHOW_LATEST_FOR_TESTING, appliedValue)
        .apply()
}

private data class GitHubRepository(val owner: String, val name: String)

private data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val isPrerelease: Boolean,
    val assets: List<GitHubReleaseAsset>
)

private data class GitHubReleaseAsset(
    val name: String,
    val browserDownloadUrl: String
)

private fun parseGitHubRepository(repositoryUrl: String): GitHubRepository? {
    val marker = "github.com/"
    val index = repositoryUrl.indexOf(marker, ignoreCase = true)
    if (index < 0) return null
    val path = repositoryUrl.substring(index + marker.length)
        .trim('/')
        .substringBefore("?")
        .substringBefore("#")
    val parts = path.split('/')
    if (parts.size < 2) return null
    return GitHubRepository(
        owner = parts[0].trim(),
        name = parts[1].removeSuffix(".git").trim()
    )
}

private fun fetchReleases(owner: String, repo: String, userAgent: String): List<GitHubRelease> {
    val connection = (URL("https://api.github.com/repos/$owner/$repo/releases").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", userAgent)
    }
    try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            error("GitHub returned HTTP $status")
        }
        return parseReleaseResponse(response)
    } finally {
        connection.disconnect()
    }
}

private fun parseReleaseResponse(response: String): List<GitHubRelease> {
    val json = JSONArray(response)
    return buildList {
        for (index in 0 until json.length()) {
            val release = json.optJSONObject(index) ?: continue
            if (release.optBoolean("draft", false)) continue
            val tagName = release.optString("tag_name").ifBlank { release.optString("name") }
            if (tagName.isBlank()) continue
            val assetsJson = release.optJSONArray("assets")
            val assets = buildList {
                if (assetsJson != null) {
                    for (assetIndex in 0 until assetsJson.length()) {
                        val asset = assetsJson.optJSONObject(assetIndex) ?: continue
                        val name = asset.optString("name").trim()
                        val downloadUrl = asset.optString("browser_download_url").trim()
                        if (name.isNotBlank() && downloadUrl.isNotBlank()) {
                            add(GitHubReleaseAsset(name, downloadUrl))
                        }
                    }
                }
            }
            add(
                GitHubRelease(
                    tagName = tagName,
                    name = release.optString("name"),
                    body = release.optString("body"),
                    htmlUrl = release.optString("html_url"),
                    isPrerelease = release.optBoolean("prerelease", false),
                    assets = assets
                )
            )
        }
    }
}

private fun GitHubRelease.toUpdateInfo(): AppUpdateInfo {
    val apkAsset = assets
        .filter { it.name.endsWith(".apk", ignoreCase = true) }
        .maxWithOrNull(
            compareBy<GitHubReleaseAsset> { it.name.contains("nittc", ignoreCase = true) }
                .thenBy { it.name.contains("scheduler", ignoreCase = true) }
                .thenBy { it.name.contains("universal", ignoreCase = true) }
        )
    val channelSource = listOf(tagName, name, apkAsset?.name.orEmpty()).firstOrNull { it.isNotBlank() }.orEmpty()
    return AppUpdateInfo(
        tagName = tagName.ifBlank { name.ifBlank { "unknown" } },
        channel = detectChannel(channelSource, isPrerelease),
        releaseNotes = body.ifBlank { "" },
        releaseUrl = htmlUrl,
        apkAssetName = apkAsset?.name,
        apkDownloadUrl = apkAsset?.browserDownloadUrl,
        isPrerelease = isPrerelease
    )
}

private fun compareUpdateInfo(left: AppUpdateInfo, right: AppUpdateInfo): Int {
    return compareReleaseVersions(left.tagName, left.isPrerelease, right.tagName, right.isPrerelease)
}

private fun isNewerRelease(remoteTag: String, currentVersion: String, remoteIsPrerelease: Boolean): Boolean {
    return compareReleaseVersions(remoteTag, remoteIsPrerelease, currentVersion, false) > 0
}

private fun compareReleaseVersions(
    leftTag: String,
    leftIsPrerelease: Boolean,
    rightTag: String,
    rightIsPrerelease: Boolean
): Int {
    val left = parseVersionNumbers(leftTag)
    val right = parseVersionNumbers(rightTag)
    val maxSize = maxOf(left.size, right.size)
    for (index in 0 until maxSize) {
        val leftPart = left.getOrElse(index) { 0 }
        val rightPart = right.getOrElse(index) { 0 }
        if (leftPart != rightPart) return leftPart.compareTo(rightPart)
    }
    val leftPriority = channelPriority(detectChannel(leftTag, leftIsPrerelease))
    val rightPriority = channelPriority(detectChannel(rightTag, rightIsPrerelease))
    return leftPriority.compareTo(rightPriority)
}

private fun parseVersionNumbers(value: String): List<Int> {
    val normalizedLower = value.lowercase(Locale.US)
    val normalized = normalizedLower
        .substringAfter("scheduler_", normalizedLower)
        .substringAfter("nittc_", normalizedLower)
        .removePrefix("v")
        .substringBefore("-")
    val dotted = Regex("""\d+(?:\.\d+)+""").find(normalized)?.value
    if (dotted != null) {
        return dotted.split('.').mapNotNull { it.toIntOrNull() }
    }
    val compact = Regex("""\d{3,}""").find(normalized)?.value
    if (compact != null) {
        return compact.map { it.digitToInt() }
    }
    return Regex("""\d+""").findAll(normalized).mapNotNull { it.value.toIntOrNull() }.toList()
}

private fun detectChannel(value: String, isPrerelease: Boolean = false): String {
    val lower = value.lowercase(Locale.US)
    return when {
        "intdev" in lower || "internal" in lower -> "IntDev"
        "beta" in lower -> "Beta"
        "alpha" in lower || "rc" in lower || isPrerelease -> "PreRelease"
        "stable" in lower || "release" in lower -> "Release"
        else -> "Unknown"
    }
}

fun detectReleaseChannel(value: String): String = detectChannel(value)

fun isIntDevBuild(currentVersion: String): Boolean {
    return detectReleaseChannel(currentVersion).equals("IntDev", ignoreCase = true)
}

private fun channelPriority(channel: String): Int {
    return when (channel.lowercase(Locale.US)) {
        "intdev" -> 0
        "prerelease" -> 1
        "beta" -> 2
        "release", "stable" -> 3
        else -> -1
    }
}
