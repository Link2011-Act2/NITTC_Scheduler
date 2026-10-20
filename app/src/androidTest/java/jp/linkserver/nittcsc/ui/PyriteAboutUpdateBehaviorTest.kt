package jp.linkserver.nittcsc.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.update.AppUpdateInfo
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PyriteAboutUpdateBehaviorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun checkingDisablesDuplicateRequestsAndFailureCanRetryToAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun text(id: Int) = context.getString(id)
        val responses = Channel<Result<AppUpdateInfo?>>(Channel.UNLIMITED)
        var checks = 0
        var available = 0
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = false, dynamicColor = false) {
                AboutScreen({}, onCheckForUpdates = { checks++; responses.receive() },
                    onUpdateAvailable = { available++ })
            }
        }
        val check = compose.onNodeWithText(text(R.string.about_update_check_button))
        check.performScrollTo().performClick().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.about_update_checking)).assertIsDisplayed()
        check.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, checks) }
        responses.trySend(Result.failure(IllegalStateException("offline fixture")))
        val failed = context.getString(R.string.about_update_check_failed, "offline fixture")
        compose.waitUntil(5_000) { compose.onAllNodesWithText(failed).fetchSemanticsNodes().isNotEmpty() }
        check.assertIsEnabled().performClick()
        val info = AppUpdateInfo("v1.3.0", "Stable", "Offline notes", "https://example.com/release", null, null, false)
        responses.trySend(Result.success(info))
        compose.waitUntil(5_000) { available == 1 }
        compose.onNodeWithText(context.getString(R.string.about_update_available, info.tagName)).assertIsDisplayed()
        check.assertIsEnabled()
        compose.runOnIdle { assertEquals(2, checks); assertEquals(1, available) }
    }
}
