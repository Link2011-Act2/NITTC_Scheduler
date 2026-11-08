package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GoldyThemeIntegrationTest(private val mode: UiDesignMode,private val dark: Boolean,private val dynamic: Boolean) {
    @get:Rule val compose=createComposeRule()
    private val candidate=UpdateCandidate(GitHubRelease(999,"v99.0.0-Release",notes="Release notes",url="https://github.com/Link2011-Act2/NITTC_Scheduler/releases/tag/v99.0.0-Release"))
    @Test fun eliefUsesHostColorRolesAndDownloadActionsRemainReachableAtLargeFont() {
        compose.setContent {
            AppTheme(mode,darkTheme=dark,dynamicColor=dynamic) {
                val style=LocalUpdateCardStyle.current
                val colors=MaterialTheme.colorScheme
                val shape=MaterialTheme.shapes.large
                SideEffect { assertEquals(if(dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest,style.containerColor); assertEquals(shape,style.shape) }
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                    jp.linkserver.goldy.compose.UpdateOverviewScreen(UpdateUiState(candidate,apkAvailable=true),{}, {}, {},strings=goldyUpdateStrings())
                }
            }
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.goldy_transfer_download)).performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("v99.0.0-Release").assertExists()
    }
    @Test fun dialogOffersDetailsAsPrimaryWithoutAutomaticallyOpeningIt() {
        var opened=0; var checks=0; var ignored=0
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            AppTheme(mode,darkTheme=dark,dynamicColor=dynamic) {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                    AppUpdatePrompt(GoldyState(availableUpdate=candidate,notificationUpdate=candidate),{checks++},{opened++},{ignored++},{})
                }
            }
        }
        compose.runOnIdle { assertEquals(0,opened); assertEquals(0,checks) }
        compose.onNodeWithText(context.getString(R.string.update_prompt_confirm)).performClick()
        compose.runOnIdle { assertEquals(1,opened); assertEquals(0,ignored) }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}-dark={1}-dynamic={2}")
        fun cases()=UiDesignMode.entries.flatMap { mode -> listOf(false,true).flatMap { dark -> listOf(false,true).map { dynamic -> arrayOf<Any>(mode,dark,dynamic) } } }
    }
}
