package jp.linkserver.nittcsc.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.goldy.*
import androidx.compose.runtime.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PyriteAboutUpdateBehaviorTest(private val mode: UiDesignMode) {
    @get:Rule val compose = createComposeRule()
    @Test fun checkingDisablesDuplicateRequestsAndFailureCanRetryToAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun text(id: Int) = context.getString(id)
        val responses = Channel<CheckOutcome>(Channel.UNLIMITED)
        var checks = 0
        var available = 0
        var candidate by mutableStateOf<UpdateCandidate?>(null)
        compose.setContent {
            AppTheme(mode, darkTheme = false, dynamicColor = false) {
                AboutScreen({}, onCheckForUpdates = { checks++; responses.receive().also { if(it is CheckOutcome.Available) candidate=it.update } },
                    onUpdateAvailable = { available++ }, availableUpdate = candidate)
            }
        }
        val check = compose.onNodeWithText(text(R.string.about_update_check_button))
        check.performScrollTo().performClick().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.about_update_checking)).assertIsDisplayed()
        check.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, checks) }
        responses.trySend(CheckOutcome.Failure(GoldyError.Network))
        val failed = text(R.string.goldy_update_failed)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(failed).fetchSemanticsNodes().isNotEmpty() }
        check.assertIsEnabled().performClick()
        val info = UpdateCandidate(GitHubRelease(123, "v1.3.0-Release", notes="Offline notes", url="https://github.com/Link2011-Act2/NITTC_Scheduler/releases/tag/v1.3.0-Release"))
        responses.trySend(CheckOutcome.Available(info, false))
        compose.waitUntil(5_000) { candidate != null }
        compose.runOnIdle { assertEquals(0, available) }
        compose.onNodeWithText(context.getString(R.string.about_update_available, info.release.tag)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.goldy_update_view)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, checks); assertEquals(1, available) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = UiDesignMode.entries.map { arrayOf<Any>(it) }
    }
}
