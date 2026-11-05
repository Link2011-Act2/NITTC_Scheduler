package jp.linkserver.nittcsc.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.pyrite.screen.PyriteNavigationDirection
import jp.linkserver.pyrite.screen.PyriteScreenTransition
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
                var page by rememberSaveable { mutableStateOf(SettingsDestination.OVERVIEW) }
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
        val width = compose.onNodeWithTag("settings-page-OVERVIEW").fetchSemanticsNode().layoutInfo.width
        assertTrue(incoming <= width / 5f + 1)
        compose.runOnIdle { go(SettingsDestination.OVERVIEW) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle {
            assertEquals(SettingsDestination.LESSON_NOTIFICATIONS, destination)
            assertEquals(1, navigations)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("notification-draft").performTextReplacement("13")
        Espresso.closeSoftKeyboard()
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

    @Test fun everyDetailMatchesPublishedPyriteMotionAndKeepsOutgoingScreen() {
        var page by mutableStateOf(SettingsDestination.OVERVIEW)
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE) {
                Row {
                    Box(Modifier.width(140.dp).height(220.dp).testTag("host-pane")) {
                        SettingsNavigationHost(page, { page = it }) { screen, _ ->
                            Text(screen.name, Modifier.fillMaxSize().testTag("host-${screen.name}"))
                        }
                    }
                    Box(Modifier.width(140.dp).height(220.dp).testTag("reference-pane")) {
                        PyriteScreenTransition(
                            targetState = page,
                            direction = { initial, target ->
                                if (initial != SettingsDestination.OVERVIEW && target == SettingsDestination.OVERVIEW)
                                    PyriteNavigationDirection.Backward else PyriteNavigationDirection.Forward
                            },
                            contentKey = { it.name },
                            stateHolder = rememberSaveableStateHolder()
                        ) { screen ->
                            Text(screen.name, Modifier.fillMaxSize().testTag("reference-${screen.name}"))
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        fun relativeLeft(prefix: String, screen: SettingsDestination): Float =
            compose.onNodeWithTag("$prefix-${screen.name}").fetchSemanticsNode().boundsInRoot.left -
                compose.onNodeWithTag("$prefix-pane").fetchSemanticsNode().boundsInRoot.left
        fun compareFrame(screen: SettingsDestination) {
            assertEquals(relativeLeft("reference", screen), relativeLeft("host", screen), 1f)
        }
        for (detail in SettingsDestination.entries.filter { it != SettingsDestination.OVERVIEW }) {
            compose.runOnIdle { page = detail }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("host-OVERVIEW").assertExists()
            compose.onNodeWithTag("host-${detail.name}").assertExists()
            compareFrame(SettingsDestination.OVERVIEW)
            compareFrame(detail)
            repeat(3) {
                compose.mainClock.advanceTimeBy(32)
                compareFrame(detail)
            }
            compose.mainClock.advanceTimeBy(2_000)
            compose.runOnIdle { page = SettingsDestination.OVERVIEW }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(64)
            compareFrame(detail)
            assertTrue(relativeLeft("host", detail) > 0)
            compose.mainClock.advanceTimeBy(2_000)
            compose.onNodeWithTag("host-OVERVIEW").assertIsDisplayed()
        }
    }
}
