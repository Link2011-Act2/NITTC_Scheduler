package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.LessonStartNotificationChipMode
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.components.AppSettingsGroup
import jp.linkserver.nittcsc.ui.components.AppSettingsNavigationItem
import jp.linkserver.nittcsc.ui.components.AppSettingsSwitchItem
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate

@RunWith(Parameterized::class)
class SettingsScreenDesignTest(
    private val mode: UiDesignMode,
    private val dark: Boolean,
    private val dynamic: Boolean,
    private val fontScale: Float
) {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun settingsKeepControlsReachableAcrossThemesAndFontSizes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var backCount = 0
        var markerChanges = 0
        composeRule.setContent {
            AppTheme(mode, darkTheme = dark, dynamicColor = dynamic) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    var settings by remember {
                        mutableStateOf(SettingsEntity(
                            termStart = LocalDate.now(),
                            termEnd = LocalDate.now().plusMonths(3)
                        ))
                    }
                    SettingsScreen(
                        state = SchedulerUiState(settings = settings, uiDesignMode = mode),
                        onBack = { backCount++ },
                        onAbout = {},
                        onToggleLocalAi = {},
                        onToggleDrawerNavigation = {},
                        onToggleAddTasksToCalendar = {},
                        onToggleCurrentTimeMarker = {
                            markerChanges++
                            settings = settings.copy(showCurrentTimeMarker = it)
                        },
                        onToggleUnifyTaskPlanView = {},
                        onToggleShowWeekdayOnDates = {},
                        onToggleAdvancedTimeSettingsUi = {}
                    )
                }
            }
        }
        val label: (Int) -> String = context::getString
        composeRule.onNodeWithContentDescription(label(R.string.cd_back))
            .assertHasClickAction().assertTouchHeightIsEqualTo(48.dp)
        capture("top")
        val timeEditorLabel = if (mode == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
            composeRule.onNodeWithText(label(R.string.section_timetable_settings)).assertHasNoClickAction()
            label(R.string.settings_timetable_time_editor_title)
        } else {
            label(R.string.section_timetable_settings)
        }
        composeRule.onNodeWithText(timeEditorLabel).performScrollTo().performClick()
        capture("collapsed")
        composeRule.onNodeWithText(label(R.string.label_show_current_time_marker)).performScrollTo()
        if (mode == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
            composeRule.onNodeWithText(label(R.string.label_show_current_time_marker))
                .assertIsOff().performClick().assertIsOn()
            composeRule.runOnIdle { assertEquals(1, markerChanges) }
        }
        capture("groups")
        composeRule.onNodeWithText(label(R.string.label_ui_design)).performScrollTo().performClick()
        composeRule.onNodeWithText(label(R.string.dialog_ui_design_title)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.btn_close)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.cd_back)).performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    private fun capture(state: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = context.cacheDir.resolve("settings-${mode.name}-$dark-$dynamic-$fontScale-$state.png")
        file.outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}, dark={1}, dynamic={2}, font={3}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf(UiDesignMode.MATERIAL_3_EXPRESSIVE, false, false, 1f),
            arrayOf(UiDesignMode.MATERIAL_3_EXPRESSIVE, true, false, 1f),
            arrayOf(UiDesignMode.MATERIAL_3_EXPRESSIVE, false, true, 1f),
            arrayOf(UiDesignMode.MATERIAL_3_EXPRESSIVE, true, true, 1f),
            arrayOf(UiDesignMode.MATERIAL_3_EXPRESSIVE, true, true, 1.8f),
            arrayOf(UiDesignMode.MATERIAL_3, false, false, 1f),
            arrayOf(UiDesignMode.MATERIAL_3, true, false, 1f)
        )
    }
}

class SettingsItemInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun notificationDetailsStayReachableWhenConditionalRowsChange() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var directionChanges = 0
        composeRule.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    var enabled by remember { mutableStateOf(false) }
                    var countsDown by remember { mutableStateOf(false) }
                    LessonStartNotificationSettingsContent(
                        enabled = enabled,
                        notificationsEnabled = true,
                        promotedNotificationsEnabled = true,
                        liveUpdatesEnabled = true,
                        liveUpdatesSupported = true,
                        progressCountsDown = countsDown,
                        liveUpdateEarlyMinutes = 1,
                        chipMode = LessonStartNotificationChipMode.MINUTE_TEXT,
                        minutesBefore = "10",
                        exclusions = emptyList(),
                        subjectSuggestions = emptyList(),
                        subjectTeacherCandidates = emptyMap(),
                        onToggleEnabled = { enabled = it },
                        onOpenNotificationSettings = {},
                        onOpenPromotedNotificationSettings = {},
                        onToggleLiveUpdates = {},
                        onToggleProgressCountsDown = { countsDown = it; directionChanges++ },
                        onUpdateLiveUpdateEarlyMinutes = {},
                        onUpdateChipMode = {},
                        onMinutesBeforeChange = {},
                        onAddExclusion = { _, _, _ -> },
                        onDeleteExclusion = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.label_lesson_start_notification)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.label_lesson_start_live_update_display_details))
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.label_lesson_start_progress_decreasing))
            .performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, directionChanges) }
        composeRule.onNodeWithText(context.getString(R.string.label_lesson_start_notification))
            .performScrollTo().performClick().assertIsOff()
    }

    @Test
    fun longSummaryAndThumbShareOneToggleActionAndDisabledRowCannotToggle() {
        var changes = 0
        var navigationCount = 0
        val longSummary = "画面キーボードの使用中に英字入力で各文の先頭の文字を大文字にします（日本語キーボードでは適用されません）"
        composeRule.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = true) {
                Surface {
                    Column(Modifier.width(320.dp)) {
                        AppSettingsGroup {
                            item("switch") {
                                var checked by remember { mutableStateOf(false) }
                                AppSettingsSwitchItem("自動修正", longSummary, checked, {
                                    changes++
                                    checked = it
                                })
                            }
                            item("disabled") {
                                AppSettingsSwitchItem("使用不可", "この端末では使用できません", false,
                                    { changes++ }, enabled = false)
                            }
                            item("navigation") {
                                AppSettingsNavigationItem("詳細設定", longSummary, { navigationCount++ })
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText("自動修正").assertHeightIsAtLeast(80.dp).performClick().assertIsOn()
        // Tap where the standard Switch thumb is rendered; the parent must fire only once.
        composeRule.onNodeWithText("自動修正").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width - 40.dp.toPx(), centerY))
        }.assertIsOff()
        composeRule.onNodeWithText("使用不可").assertIsNotEnabled().performTouchInput { click() }
        composeRule.onNodeWithText("詳細設定").performClick()
        composeRule.runOnIdle {
            assertEquals(2, changes)
            assertEquals(1, navigationCount)
        }
    }
}
