package jp.linkserver.nittcsc.update

import jp.linkserver.goldy.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GoldyUpdatePolicyTest {
    private fun release(id: Long, tag: String) = GitHubRelease(id, tag, url="https://github.com/Link2011-Act2/NITTC_Scheduler/releases/tag/$tag")
    private fun select(tags: List<String>, installed: String="1.0.0-Release", options: RuntimeOptions=RuntimeOptions()) =
        ReleaseSelector.select(ReleaseCatalog(tags.mapIndexed { i,tag -> release(i+1L,tag) }),InstalledVersion(installed,1),ApkUpdateProfile.policy,options)

    @Test fun officialFeedExcludesAiRuntimeAndSelectsCommonVersionOrder() {
        val outcome=select(listOf("ai-runtime-999.0", "v1.0.1-Beta2", "v1.0.1-RC6", "v1.0.1-Release")) as CheckOutcome.Available
        assertEquals("v1.0.1-Release",outcome.update.release.tag)
        assertEquals(ReleaseFeed.ListReleases(30,1),ApkUpdateProfile.policy.feed)
        assertSame(VersionPolicies.Common,ApkUpdateProfile.policy.versions)
        assertEquals(CheckOutcome.UpToDate,select(listOf("v1.0.0-Release")))
    }
    @Test fun developerComparisonOverrideDoesNotApplyToInstalledReleaseBuild() {
        val options=RuntimeOptions(developer=DeveloperOptions(ignoreVersionComparison=true,currentVersionOverride="0.0.0-IntDev"))
        assertEquals(CheckOutcome.UpToDate,select(listOf("v1.0.0-Release"),options=options))
        assertTrue(select(listOf("v1.0.0-Release"),"9.0.0-IntDev",options) is CheckOutcome.Available)
    }
    @Test fun singleLegacyIgnoreMergesIdempotentlyAndDoesNotOverwriteCacheOrRetry() = runBlocking {
        val initial=StoredUpdateState(ignoredTags=setOf("v2.0.0-beta2"),retryAtMillis=1234)
        val store=InMemoryUpdateStateStore(initial)
        repeat(3) { migrateIgnoredTag(store,"v1.2.0-Release") }
        assertEquals(setOf("v2.0.0-beta2","v1.2.0-release"),store.read().ignoredTags)
        assertEquals(initial.retryAtMillis,store.read().retryAtMillis)
        assertNull(store.read().cache)
        migrateIgnoredTag(store, " ")
        assertEquals(2,store.read().ignoredTags.size)
    }
    @Test fun executionIdentifiersCannotCollideWithAiModelTransfers() {
        assertNotEquals(1001,ApkUpdateProfile.NOTIFICATION_ID)
        assertNotEquals(2001,ApkUpdateProfile.NOTIFICATION_ID)
        assertFalse(ApkUpdateProfile.WORK_NAME.contains("model"))
        assertNotEquals("model_download_channel_default",ApkUpdateProfile.CHANNEL)
    }
}
