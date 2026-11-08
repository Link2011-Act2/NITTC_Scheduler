package jp.linkserver.nittcsc.ui

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.MainActivity
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.update.ApkUpdateProfile
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GoldyNavigationRecoveryTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun notificationOpensCenterAndRecreationPreservesBackNavigation() {
        assertTrue("Disposable emulator only",android.os.Build.MODEL.contains("sdk",true)||android.os.Build.FINGERPRINT.contains("generic"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val title=context.getString(R.string.goldy_update_title)
        val back=context.getString(R.string.goldy_update_back)
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).setAction(ApkUpdateProfile.ACTION_VIEW_TRANSFER)).use { scenario ->
            fun awaitCenter()=compose.waitUntil(20_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            awaitCenter()
            scenario.recreate(); awaitCenter()
            compose.onNodeWithContentDescription(back).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
            scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText(title).assertDoesNotExist()
        }
    }
}
