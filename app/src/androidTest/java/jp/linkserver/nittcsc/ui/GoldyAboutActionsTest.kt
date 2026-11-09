package jp.linkserver.nittcsc.ui
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import jp.linkserver.nittcsc.R
import jp.linkserver.goldy.*
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GoldyAboutActionsTest(private val mode: UiDesignMode) {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun oneCardActionCoversCheckCandidateAndSavedTransferWithoutAutoNavigation() {
        val candidate=UpdateCandidate(GitHubRelease(701,"v99.0.0-Release",url="https://github.com/example/app/releases/tag/v99.0.0-Release"))
        var available: UpdateCandidate? by mutableStateOf(null)
        var saved by mutableStateOf(false)
        var checks=0;var opens=0;var savedOpens=0
        val check: suspend () -> CheckOutcome={checks++;available=candidate;CheckOutcome.Available(candidate)}
        compose.setContent { AppTheme(mode, darkTheme=false, dynamicColor=false) { AboutScreen(onBack={}, availableUpdate=available, hasSavedUpdate=saved,
            onOpenUpdateCenter={savedOpens++}, onUpdateAvailable={opens++}, onCheckForUpdates=check) } }
        fun label(id:Int)=compose.activity.getString(id)
        compose.onAllNodesWithText(label(R.string.about_update_check_button)).assertCountEquals(1)
        compose.onNodeWithText(label(R.string.about_update_check_button)).performScrollTo().performClick()
        compose.waitForIdle();assertEquals(1,checks);assertEquals(0,opens)
        compose.onAllNodesWithText(label(R.string.goldy_update_view)).assertCountEquals(1)
        compose.onNodeWithText(label(R.string.goldy_update_view)).performScrollTo().performClick()
        assertEquals(1,opens)
        // Pending APK / paused or restoring transfer remains accessible without a catalog candidate.
        compose.runOnIdle { available=null;saved=true }
        compose.onAllNodesWithText(label(R.string.goldy_update_view)).assertCountEquals(1)
        compose.onNodeWithText(label(R.string.goldy_update_view)).performScrollTo().performClick()
        assertEquals(1,savedOpens);assertEquals(1,opens);assertEquals(1,checks)
        compose.onNodeWithText(label(R.string.about_update_saved)).assertExists()
        // A saved download takes priority even when the release catalog also has a candidate.
        compose.runOnIdle { available=candidate }
        compose.onNodeWithText(label(R.string.goldy_update_view)).performScrollTo().performClick()
        assertEquals(2,savedOpens);assertEquals(1,opens)
    }

    @Test fun savedUpdateRemainsReachableWhileChecking() {
        var saved by mutableStateOf(false)
        var opens = 0
        var checks = 0
        val result = CompletableDeferred<CheckOutcome>()
        compose.setContent {
            AppTheme(mode, darkTheme = false, dynamicColor = false) {
                AboutScreen(onBack = {}, onCheckForUpdates = { checks++; result.await() },
                    hasSavedUpdate = saved, onOpenUpdateCenter = { opens++ })
            }
        }
        fun label(id: Int) = compose.activity.getString(id)
        compose.onNodeWithText(label(R.string.about_update_check_button))
            .performScrollTo().performClick().assertIsNotEnabled()
        compose.runOnIdle { saved = true }
        compose.onNodeWithText(label(R.string.goldy_update_view))
            .performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, opens)
            assertEquals(1, checks)
            result.complete(CheckOutcome.UpToDate)
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = UiDesignMode.entries.map { arrayOf<Any>(it) }
    }
}
