package jp.linkserver.nittcsc.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    private val release = AppUpdateInfo(
        tagName = "v2.0.0", channel = "Stable", releaseNotes = "更新内容",
        releaseUrl = "https://github.com/example/app/releases/tag/v2.0.0",
        apkAssetName = "app.apk", apkDownloadUrl = "https://example.com/app.apk",
        isPrerelease = false
    )

    @Test
    fun restoresCachedUpdateWithoutNetworkDuringCheckInterval() = runBlocking {
        val store = FakeStore(release, due = false)
        val repository = AppUpdateRepository(store) { error("Unexpected network check") }
        repository.check()
        assertEquals(release, repository.state.value.notificationUpdate)
        assertTrue(repository.state.value.showBanner)
        assertEquals(0, store.successfulChecks)
    }

    @Test
    fun failedCheckKeepsCacheAndDoesNotAdvanceSuccessfulCheck() = runBlocking {
        val store = FakeStore(release)
        var calls = 0
        val repository = AppUpdateRepository(store) {
            calls++
            Result.failure(IllegalStateException("offline"))
        }
        assertTrue(repository.check().isFailure)
        assertTrue(repository.check().isFailure)
        assertEquals(2, calls)
        assertEquals(0, store.successfulChecks)
        assertEquals(release, store.cached)
        assertEquals(release, repository.state.value.notificationUpdate)
    }

    @Test
    fun failedManualCheckKeepsSuccessfulCheckTimeAndCache() = runBlocking {
        val store = FakeStore(release, due = false)
        val repository = AppUpdateRepository(store) { Result.failure(IllegalStateException("offline")) }
        assertTrue(repository.check(force = true).isFailure)
        assertEquals(0, store.successfulChecks)
        assertEquals(release, store.cached)
        assertEquals(release, repository.state.value.notificationUpdate)
    }

    @Test
    fun successfulCheckWithoutUpdateClearsCachedNotification() = runBlocking {
        val store = FakeStore(release)
        val repository = AppUpdateRepository(store) { Result.success(null) }
        repository.check()
        assertEquals(1, store.successfulChecks)
        assertNull(store.cached)
        assertEquals(AppUpdateState(), repository.state.value)
    }

    @Test
    fun successfulCheckWithUpdateSavesResultAndThrottlesNextCheck() = runBlocking {
        val store = FakeStore()
        var calls = 0
        val repository = AppUpdateRepository(store) { calls++; Result.success(release) }
        repository.check()
        repository.check()
        assertEquals(1, calls)
        assertEquals(1, store.successfulChecks)
        assertEquals(release, store.cached)
        repository.check(force = true)
        assertEquals(2, calls)
    }

    @Test
    fun closingBannerKeepsUpdateActionAndDoesNotIgnoreVersion() = runBlocking {
        val store = FakeStore(release, due = false)
        val repository = AppUpdateRepository(store) { error("Unexpected check") }
        repository.check()
        repository.hideBanner()
        repository.check()
        assertFalse(repository.state.value.showBanner)
        assertEquals(release, repository.state.value.notificationUpdate)
        assertNull(store.dismissedTag)
        // 次回起動では再び案内できる。
        val restarted = AppUpdateRepository(store) { error("Unexpected check") }
        restarted.check()
        assertTrue(restarted.state.value.showBanner)
    }

    @Test
    fun ignoringVersionSuppressesBannerAndActionAcrossRestartButKeepsManualUpdate() = runBlocking {
        val store = FakeStore(release, due = false)
        val repository = AppUpdateRepository(store) { Result.success(release) }
        repository.check()
        repository.ignoreVersion(release.tagName)
        val restarted = AppUpdateRepository(store) { Result.success(release) }
        restarted.check()
        assertNull(restarted.state.value.notificationUpdate)
        assertFalse(restarted.state.value.showBanner)
        assertEquals(release, restarted.check(force = true).getOrThrow())
        assertEquals(release, restarted.state.value.availableUpdate)
        assertNull(restarted.state.value.notificationUpdate)
    }

    @Test
    fun checkFinishingAfterIgnoreDoesNotRestoreNotification() = runBlocking {
        val store = FakeStore(release)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val repository = AppUpdateRepository(store) {
            started.complete(Unit)
            finish.await()
            Result.success(release)
        }
        val check = async { repository.check() }
        started.await()
        repository.ignoreVersion(release.tagName)
        finish.complete(Unit)
        check.await()
        assertNull(repository.state.value.notificationUpdate)
        assertFalse(repository.state.value.showBanner)
    }

    @Test
    fun newerVersionIsShownAfterPreviousVersionWasIgnored() = runBlocking {
        val store = FakeStore(release, due = false)
        val next = release.copy(tagName = "v2.1.0")
        val repository = AppUpdateRepository(store) { Result.success(next) }
        repository.check()
        repository.hideBanner()
        repository.ignoreVersion(release.tagName)
        repository.check(force = true)
        assertEquals(next, repository.state.value.notificationUpdate)
        assertTrue(repository.state.value.showBanner)
    }

    @Test
    fun clearingDismissalRestoresActionWithoutWaitingForNetwork() = runBlocking {
        val store = FakeStore(release, due = false).apply { dismissedTag = release.tagName }
        val repository = AppUpdateRepository(store) { error("Unexpected check") }
        repository.check()
        store.dismissedTag = null
        repository.check()
        assertEquals(release, repository.state.value.notificationUpdate)
    }

    @Test
    fun cancellationDoesNotSaveOrReplaceCachedResult() = runBlocking {
        val store = FakeStore(release)
        val repository = AppUpdateRepository(store) { Result.failure(CancellationException()) }
        try {
            repository.check()
            throw AssertionError("Cancellation must be propagated")
        } catch (_: CancellationException) {
            assertEquals(0, store.successfulChecks)
            assertEquals(release, store.cached)
        }
    }

    @Test
    fun installedVersionNoLongerCountsAsAnUpdate() {
        assertFalse(isNewerRelease("v2.0.0", "2.0.0", false))
        assertFalse(isNewerRelease("v2.0.0", "2.1.0", false))
        assertTrue(isNewerRelease("v2.1.0", "2.0.0", false))
    }

    private class FakeStore(
        var cached: AppUpdateInfo? = null,
        var due: Boolean = true
    ) : AppUpdateStore {
        var successfulChecks = 0
        var dismissedTag: String? = null
        override fun loadUpdate() = cached
        override fun shouldCheck() = due
        override fun saveSuccessfulCheck(update: AppUpdateInfo?) {
            cached = update
            successfulChecks++
            due = false
        }
        override fun isDismissed(tagName: String) = tagName.equals(dismissedTag, ignoreCase = true)
        override fun dismiss(tagName: String) { dismissedTag = tagName }
    }
}
