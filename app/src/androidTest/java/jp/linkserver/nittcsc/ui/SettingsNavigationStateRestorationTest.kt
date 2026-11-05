package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 保存・復元でホスト自体を破棄し、画面キーと入力・スクロールのBundle対応を確認する。 */
class SettingsNavigationStateRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restoredDetailAndRetainedOverviewKeepTheirIndependentState() {
        val restoration = StateRestorationTester(compose)
        lateinit var go: (SettingsDestination) -> Unit
        var observedPage = SettingsDestination.OVERVIEW
        val positions = mutableMapOf<SettingsDestination, Int>()
        restoration.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE) {
                var page by rememberSaveable { mutableStateOf(SettingsDestination.OVERVIEW) }
                SideEffect { observedPage = page }
                Box(Modifier.width(320.dp).height(420.dp)) {
                    SettingsNavigationHost(page, { page = it }) { screen, navigate ->
                        go = navigate
                        var draft by rememberSaveable { mutableStateOf("") }
                        val scroll = rememberScrollState()
                        val position = scroll.value
                        SideEffect { positions[screen] = position }
                        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                            OutlinedTextField(draft, { draft = it }, Modifier.testTag("draft-${screen.name}"))
                            Spacer(Modifier.height(700.dp))
                            Button({ navigate(if (screen == SettingsDestination.OVERVIEW)
                                SettingsDestination.LESSON_NOTIFICATIONS else SettingsDestination.OVERVIEW) },
                                Modifier.testTag("navigate-${screen.name}")) { Text("移動") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("draft-OVERVIEW").performTextReplacement("overview")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("navigate-OVERVIEW").performScrollTo()
        val overviewPosition = positions.getValue(SettingsDestination.OVERVIEW)
        compose.onNodeWithTag("navigate-OVERVIEW").performClick()
        compose.onNodeWithTag("draft-LESSON_NOTIFICATIONS").performTextReplacement("13")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("navigate-LESSON_NOTIFICATIONS").performScrollTo()
        val detailPosition = positions.getValue(SettingsDestination.LESSON_NOTIFICATIONS)
        assertTrue(overviewPosition > 0 && detailPosition > 0)
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(SettingsDestination.LESSON_NOTIFICATIONS, observedPage)
            assertEquals(detailPosition, positions.getValue(observedPage))
            go(SettingsDestination.OVERVIEW)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(overviewPosition, positions.getValue(SettingsDestination.OVERVIEW)) }
        compose.onNodeWithTag("draft-OVERVIEW").performScrollTo().assertTextContains("overview")
        compose.runOnIdle { go(SettingsDestination.LESSON_NOTIFICATIONS) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(detailPosition, positions.getValue(SettingsDestination.LESSON_NOTIFICATIONS)) }
        compose.onNodeWithTag("draft-LESSON_NOTIFICATIONS").performScrollTo().assertTextContains("13")
    }
}
