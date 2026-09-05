package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.components.AppButtonGroup
import jp.linkserver.nittcsc.ui.components.AppFabMenu
import jp.linkserver.nittcsc.ui.components.AppFabMenuAction
import jp.linkserver.nittcsc.ui.components.AppIconButton
import jp.linkserver.nittcsc.ui.components.AppSplitButton
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test

class UiDesignSwitchTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchingDesignModeKeepsNavigationAndInputState() {
        composeRule.setContent {
            var designMode by remember { mutableStateOf(UiDesignMode.MATERIAL_3) }
            AppTheme(
                uiDesignMode = designMode,
                darkTheme = false,
                dynamicColor = false
            ) {
                var currentScreen by rememberSaveable { mutableStateOf("main") }
                var input by rememberSaveable { mutableStateOf("") }
                Column {
                    Text(currentScreen)
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.testTag("design-input")
                    )
                    Button(onClick = { currentScreen = "details" }) {
                        Text("open-details")
                    }
                    Button(
                        onClick = {
                            designMode = if (designMode == UiDesignMode.MATERIAL_3) {
                                UiDesignMode.MATERIAL_3_EXPRESSIVE
                            } else {
                                UiDesignMode.MATERIAL_3
                            }
                        },
                        modifier = Modifier.testTag("design-switch")
                    ) {
                        Text("switch-design")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("design-input").performTextInput("draft")
        composeRule.onNodeWithText("open-details").performClick()
        composeRule.onNodeWithTag("design-switch").performClick()

        composeRule.onNodeWithText("details").assertIsDisplayed()
        composeRule.onNodeWithTag("design-input").assertTextEquals("draft")
    }

    @Test
    fun expressiveIconButtonKeepsAccessibilityAndMinimumTouchTarget() {
        composeRule.setContent {
            AppTheme(
                uiDesignMode = UiDesignMode.MATERIAL_3_EXPRESSIVE,
                darkTheme = true,
                dynamicColor = false
            ) {
                AppIconButton(onClick = {}) {
                    Icon(Icons.Filled.Settings, contentDescription = "設定")
                }
            }
        }

        composeRule.onNodeWithContentDescription("設定")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun expressiveAdvancedControlsKeepActionsReachable() {
        composeRule.setContent {
            AppTheme(
                uiDesignMode = UiDesignMode.MATERIAL_3_EXPRESSIVE,
                darkTheme = false,
                dynamicColor = false
            ) {
                var selectedIndex by rememberSaveable { mutableStateOf(0) }
                var result by rememberSaveable { mutableStateOf("none") }
                var menuExpanded by rememberSaveable { mutableStateOf(false) }
                Column {
                    AppButtonGroup(
                        options = listOf("課題", "予定"),
                        selectedIndex = selectedIndex,
                        onSelectedIndexChange = { selectedIndex = it }
                    )
                    Text("selected=$selectedIndex")
                    AppSplitButton(
                        primaryLabel = { Text("主操作") },
                        onPrimaryClick = { result = "primary" },
                        secondaryLabel = { Text("副操作") },
                        onSecondaryClick = { result = "secondary" }
                    )
                    AppFabMenu(
                        actions = listOf(
                            AppFabMenuAction(
                                label = "新規課題",
                                icon = Icons.Filled.Add,
                                contentDescription = "新規課題を作成",
                                onClick = { result = "fab" }
                            )
                        ),
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                        expandContentDescription = "追加メニューを開く",
                        collapseContentDescription = "追加メニューを閉じる"
                    )
                    Text("result=$result")
                }
            }
        }

        composeRule.onNodeWithText("予定").performClick()
        composeRule.onNodeWithText("selected=1").assertIsDisplayed()
        composeRule.onNodeWithText("副操作").performClick()
        composeRule.onNodeWithText("result=secondary").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("追加メニューを開く").performClick()
        composeRule.onNodeWithContentDescription("新規課題を作成").performClick()
        composeRule.onNodeWithText("result=fab").assertIsDisplayed()
    }
}
