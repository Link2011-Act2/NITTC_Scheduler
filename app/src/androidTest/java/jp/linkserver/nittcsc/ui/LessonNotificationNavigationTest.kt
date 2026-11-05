package jp.linkserver.nittcsc.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LessonNotificationNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun slidesInAndBackWhileBlockingRepeatedNavigationAndRetainingDraftAndScroll() {
        lateinit var go: (SettingsDestination) -> Unit
        lateinit var dispatcher: OnBackPressedDispatcher
        var destination = SettingsDestination.OVERVIEW
        var navigations = 0
        var detailScroll = 0
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE) {
                dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                var page by remember { mutableStateOf(SettingsDestination.OVERVIEW) }
                Box(Modifier.width(320.dp).height(420.dp)) {
                    SettingsNavigationHost(page, {
                        navigations++; page = it; destination = it
                    }) { shown, navigate ->
                        go = navigate
                        val scroll = rememberScrollState()
                        var draft by rememberSaveable { mutableStateOf("7") }
                        if (shown == SettingsDestination.LESSON_NOTIFICATIONS) {
                            val position = scroll.value
                            SideEffect { detailScroll = position }
                            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                                OutlinedTextField(draft, { draft = it }, Modifier.testTag("notification-draft"))
                                Spacer(Modifier.height(700.dp))
                                Button({ navigate(SettingsDestination.OVERVIEW) }, Modifier.testTag("notification-back")) { Text("戻る") }
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                Button({ navigate(SettingsDestination.LESSON_NOTIFICATIONS) }) { Text("通知設定") }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            go(SettingsDestination.LESSON_NOTIFICATIONS)
            go(SettingsDestination.EXAM)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        val incoming = compose.onNodeWithTag("settings-page-LESSON_NOTIFICATIONS").fetchSemanticsNode().boundsInRoot.left
        assertTrue(incoming > 0)
        compose.runOnIdle { go(SettingsDestination.OVERVIEW) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle {
            assertEquals(SettingsDestination.LESSON_NOTIFICATIONS, destination)
            assertEquals(1, navigations)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("notification-draft").performTextReplacement("13")
        compose.onNodeWithTag("notification-back").performScrollTo()
        val savedScroll = detailScroll
        assertTrue(savedScroll > 0)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        val outgoing = compose.onNodeWithTag("settings-page-LESSON_NOTIFICATIONS").fetchSemanticsNode().boundsInRoot.left
        assertTrue(outgoing > 0)
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { go(SettingsDestination.LESSON_NOTIFICATIONS) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(savedScroll, detailScroll) }
        compose.onNodeWithTag("notification-draft").performScrollTo().assertTextContains("13")
        compose.onNodeWithTag("notification-back").performScrollTo().performClick()
        compose.onNodeWithTag("settings-page-OVERVIEW").assertIsDisplayed()
    }
}
