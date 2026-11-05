package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.LessonNotificationExclusionEntity
import jp.linkserver.nittcsc.data.LessonStartNotificationChipMode
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PyriteNotificationMigrationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun additionKeepsSubjectSearchTeacherCompletionAndTeacherMatching() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var added: Triple<String, String?, Boolean>? = null
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = true) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    LessonStartNotificationSettingsContent(true, true, true, false, true, false, 1,
                        LessonStartNotificationChipMode.MINUTE_TEXT, "10", emptyList(), listOf("数学"), mapOf("数学" to listOf("先生")),
                        {}, {}, {}, {}, {}, {}, {}, {}, { subject, teacher, match -> added = Triple(subject, teacher, match) }, {})
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.lesson_notification_add_exclusion)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(context.getString(R.string.label_task_subject))).performTextInput("数学")
        compose.onNodeWithText(context.getString(R.string.label_lesson_start_notification_match_teacher)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(context.getString(R.string.label_task_teacher))).assertTextContains("先生")
        compose.onNode(hasClickAction() and hasText(context.getString(R.string.btn_add_lesson_start_notification_exclusion))).performClick()
        compose.runOnIdle { assertEquals(Triple("数学", "先生", true), added) }
    }
    @Test fun deleteButtonIsIndependentAndConditionalGroupRecomputesAtLargeFonts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var deletes = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = false, dynamicColor = false) {
                    var exclusions by remember { mutableStateOf(listOf(LessonNotificationExclusionEntity(
                        id = 42, subject = "Long subject name", teacher = "Teacher", matchTeacher = true))) }
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        LessonStartNotificationSettingsContent(true, true, true, true, true, false, 1,
                            LessonStartNotificationChipMode.MINUTE_TEXT, "10", exclusions, emptyList(), emptyMap(),
                            {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {
                                deletes++; exclusions = exclusions.filterNot { row -> row.id == it.id }
                            })
                    }
                }
            }
        }
        compose.onNodeWithText("Long subject name").performScrollTo().assertHasNoClickAction()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText(context.getString(R.string.btn_delete)).performScrollTo().performClick()
        compose.onNodeWithText("Long subject name").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.msg_no_lesson_start_notification_exclusions)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, deletes) }
    }
}
