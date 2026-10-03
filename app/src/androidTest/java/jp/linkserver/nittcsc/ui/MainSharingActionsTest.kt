package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MainSharingActionsTest {
    @get:Rule val composeRule = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun toolbarVisibilityUpdatesAndActionsStayInSyncQrSettingsOrder() {
        val showSync = mutableStateOf(false)
        val opened = mutableListOf<String>()
        composeRule.setContent {
            AppTheme(UiDesignMode.MATERIAL_3) {
                Row { MainSharingActions(showSync.value, { opened += "sync" }, { opened += "qr" }, { opened += "settings" }) }
            }
        }
        composeRule.onNodeWithContentDescription(label(R.string.cd_open_local_sync)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(label(R.string.qr_title)).assertIsDisplayed().performClick()
        composeRule.runOnIdle { showSync.value = true }
        val nodes = listOf(R.string.cd_open_local_sync, R.string.qr_title, R.string.cd_settings)
            .map { composeRule.onNodeWithContentDescription(label(it)) }
        val positions = nodes.map { it.fetchSemanticsNode().boundsInRoot.left }
        assertTrue(positions.zipWithNext().all { (a, b) -> a < b })
        nodes.forEach { it.performClick() }
        composeRule.runOnIdle {
            assertEquals(listOf("qr", "sync", "qr", "settings"), opened)
            showSync.value = false
        }
        composeRule.onNodeWithContentDescription(label(R.string.cd_open_local_sync)).assertDoesNotExist()
    }

    @Test fun overflowShowsQrWithOptionalSyncAndClosesAfterOpeningQr() {
        val showSync = mutableStateOf(false)
        var opened = 0
        composeRule.setContent {
            AppTheme(UiDesignMode.MATERIAL_3) {
                MainActionsOverflowMenu(false, showSync.value, {}, {}, { opened++ }, {})
            }
        }
        composeRule.onNodeWithContentDescription(label(R.string.cd_more_actions)).performClick()
        composeRule.onNodeWithText(label(R.string.sync_title_local_sync)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.qr_title)).performClick()
        composeRule.onNodeWithText(label(R.string.qr_title)).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, opened); showSync.value = true }
        composeRule.onNodeWithContentDescription(label(R.string.cd_more_actions)).performClick()
        val positions = listOf(R.string.sync_title_local_sync, R.string.qr_title, R.string.settings_title)
            .map { composeRule.onNodeWithText(label(it)).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(positions.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun legacyAdvancedSettingsCanToggleSyncVisibility() = verifySettingsToggle(UiDesignMode.MATERIAL_3)

    @Test fun expressiveAdvancedSettingsCanToggleSyncVisibility() = verifySettingsToggle(UiDesignMode.MATERIAL_3_EXPRESSIVE)

    private fun verifySettingsToggle(mode: UiDesignMode) {
        val today = LocalDate.of(2026, 10, 3)
        val settings = mutableStateOf(SettingsEntity(termStart = today, termEnd = today.plusMonths(6)))
        composeRule.setContent {
            AppTheme(mode) {
                CompositionLocalProvider(LocalUiDesignMode provides mode) {
                    SettingsScreen(
                        state = SchedulerUiState(settings = settings.value, uiDesignMode = mode),
                        onBack = {}, onAbout = {}, onToggleLocalAi = {}, onToggleDrawerNavigation = {},
                        onToggleShowSyncButton = { settings.value = settings.value.copy(showSyncButton = it) },
                        onToggleAddTasksToCalendar = {}, onToggleCurrentTimeMarker = {},
                        onToggleUnifyTaskPlanView = {}, onToggleShowWeekdayOnDates = {},
                        onToggleAdvancedTimeSettingsUi = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText(label(R.string.section_navigation_settings)).performScrollTo().assertIsDisplayed()
        val title = hasText(label(R.string.label_show_sync_button))
        composeRule.onNodeWithText(label(R.string.label_show_sync_button)).performScrollTo()
        // 通常M3はグループ内のスイッチが同じ親を持つため、対象行を先頭から取得する。
        val control = composeRule.onAllNodes(isToggleable() and (title or hasAnySibling(title or hasAnyDescendant(title))))
            .onFirst().performScrollTo()
        control.assertIsOff().performClick().assertIsOn().performClick().assertIsOff()
        composeRule.runOnIdle { assertEquals(false, settings.value.showSyncButton) }
    }
}
