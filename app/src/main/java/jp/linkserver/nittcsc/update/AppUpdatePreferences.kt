package jp.linkserver.nittcsc.update

import android.content.Context
import android.content.SharedPreferences
import jp.linkserver.nittcsc.BuildConfig
import jp.linkserver.nittcsc.R
import org.json.JSONArray
import org.json.JSONObject

private const val KEY_CACHED_UPDATE = "cached_update"

fun createAppUpdateRepository(context: Context): AppUpdateRepository {
    val appContext = context.applicationContext
    return AppUpdateRepository(AppUpdatePreferences(appContext)) {
        checkGitHubReleaseUpdate(
            repositoryUrl = appContext.getString(R.string.about_support_site_url),
            currentVersion = resolveUpdateCurrentVersionForTesting(appContext, BuildConfig.VERSION_NAME),
            showLatestForTesting = isShowLatestReleaseForTestingEnabled(appContext, BuildConfig.VERSION_NAME)
        )
    }
}

private class AppUpdatePreferences(private val context: Context) : AppUpdateStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)

    override fun loadUpdate(): AppUpdateInfo? {
        val update = runCatching {
            prefs.getString(KEY_CACHED_UPDATE, null)?.let { decodeUpdate(JSONObject(it)) }
        }.getOrNull() ?: return null
        val currentVersion = resolveUpdateCurrentVersionForTesting(context, BuildConfig.VERSION_NAME)
        return update.takeIf {
            isShowLatestReleaseForTestingEnabled(context, BuildConfig.VERSION_NAME) ||
                isNewerRelease(it.tagName, currentVersion, it.isPrerelease)
        }
    }

    override fun shouldCheck() = shouldCheckForUpdates(context, BuildConfig.VERSION_NAME)

    override fun saveSuccessfulCheck(update: AppUpdateInfo?) {
        prefs.edit().apply {
            putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis())
            putString(
                KEY_LAST_CHECK_VERSION,
                resolveUpdateCurrentVersionForTesting(context, BuildConfig.VERSION_NAME)
            )
            if (update == null) remove(KEY_CACHED_UPDATE)
            else putString(KEY_CACHED_UPDATE, encodeUpdate(update).toString())
        }.apply()
    }

    override fun isDismissed(tagName: String) = isUpdateNotificationDismissed(context, tagName)

    override fun dismiss(tagName: String) = dismissUpdateNotificationUntilNextVersion(context, tagName)
}

internal fun encodeUpdate(update: AppUpdateInfo): JSONObject = JSONObject().apply {
    put("tagName", update.tagName)
    put("channel", update.channel)
    put("releaseNotes", update.releaseNotes)
    put("releaseUrl", update.releaseUrl)
    put("apkAssetName", update.apkAssetName)
    put("apkDownloadUrl", update.apkDownloadUrl)
    put("isPrerelease", update.isPrerelease)
    put("intermediateReleaseNotes", JSONArray().apply {
        update.intermediateReleaseNotes.forEach { note ->
            put(JSONObject().apply {
                put("tagName", note.tagName)
                put("channel", note.channel)
                put("releaseNotes", note.releaseNotes)
                put("releaseUrl", note.releaseUrl)
                put("isPrerelease", note.isPrerelease)
            })
        }
    })
}

internal fun decodeUpdate(json: JSONObject): AppUpdateInfo {
    val tag = json.getString("tagName")
    require(tag.isNotBlank())
    val notes = json.optJSONArray("intermediateReleaseNotes") ?: JSONArray()
    return AppUpdateInfo(
        tagName = tag,
        channel = json.optString("channel"),
        releaseNotes = json.optString("releaseNotes"),
        releaseUrl = json.optString("releaseUrl"),
        apkAssetName = json.optString("apkAssetName").takeIf { it.isNotBlank() },
        apkDownloadUrl = json.optString("apkDownloadUrl").takeIf { it.isNotBlank() },
        isPrerelease = json.optBoolean("isPrerelease"),
        intermediateReleaseNotes = buildList {
            for (index in 0 until notes.length()) {
                val note = notes.optJSONObject(index) ?: continue
                val noteTag = note.optString("tagName").takeIf { it.isNotBlank() } ?: continue
                add(AppReleaseNoteInfo(
                    tagName = noteTag,
                    channel = note.optString("channel"),
                    releaseNotes = note.optString("releaseNotes"),
                    releaseUrl = note.optString("releaseUrl"),
                    isPrerelease = note.optBoolean("isPrerelease")
                ))
            }
        }
    )
}
