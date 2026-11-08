package jp.linkserver.nittcsc.update

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class UpdatePreferencesMigrationTest {
    @Test(timeout=60_000) fun oldSettingsAndIgnoreMigrateOnceWithoutInventingTrustedAssets() = runBlocking<Unit> {
        assertTrue("Disposable emulator only", android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk", true))
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val legacy=app.getSharedPreferences("github_release_updates",0)
        val host=app.getSharedPreferences("goldy_host_updates",0)
        val oldLegacy=legacy.all.toMap(); val oldHost=host.all.toMap()
        val store=UpdateStateStores.preferences(app,ApkUpdateProfile.repository)
        val original=store.read()
        fun fresh()=AppUpdates::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible=true }.newInstance(app)
        try {
            legacy.edit().clear().putString("dismissed_update_tag","v2.0.0-Beta2")
                .putBoolean("show_latest_release_for_testing",true)
                .putString("current_version_override_for_testing","0.0.1-IntDev")
                .putLong("last_check_ms",1234567L).putString("cached_update","{legacy-metadata-without-asset-ids}").commit()
            val savedLegacy=legacy.all.toMap()
            host.edit().clear().commit()
            store.write(StoredUpdateState(ignoredTags=setOf("v3.0.0-release")))
            fresh().restoreCachedState()
            assertEquals(setOf("v2.0.0-beta2","v3.0.0-release"),store.read().ignoredTags)
            assertNull(store.read().cache)
            assertEquals(savedLegacy,legacy.all)
            assertEquals(1234567L,host.getLong("legacy_last_success_ms",0))
            // A newly ignored SDK tag must survive another process-style initialization.
            store.write(store.read().copy(ignoredTags=store.read().ignoredTags+"v4.0.0-rc6"))
            fresh().restoreCachedState()
            assertEquals(3,store.read().ignoredTags.size)
            assertEquals(savedLegacy,legacy.all)
            assertTrue(host.getBoolean("migration_v1",false))
        } finally { store.write(original); restorePreferences(host,oldHost); restorePreferences(legacy,oldLegacy) }
    }
}
