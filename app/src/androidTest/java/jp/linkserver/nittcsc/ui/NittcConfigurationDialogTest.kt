package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NittcConfigurationDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun classMustBeSelectedBeforeSavingSchoolMode() {
        var savedMode: SchedulerUsageMode? = null
        var savedProfile: NittcProfileEntity? = null
        compose.setContent { MaterialTheme {
            NittcConfigurationDialog(null, emptyList(), 2026, { _, _, mode, profile ->
                savedMode = mode; savedProfile = profile; true
            }, {})
        } }
        compose.onNodeWithText("選択").performClick()
        compose.onNodeWithText("鶴岡高専").performClick()
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("選択").performClick()
        compose.onNodeWithText("1-2").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitForIdle()
        assertEquals(SchedulerUsageMode.NITTC, savedMode)
        assertEquals("1-2", savedProfile?.classId)
        assertEquals(2026, savedProfile?.academicYear)
    }

    @Test fun requiredChoiceCannotBeSkippedOrImplicitlyDefaulted() {
        var chosen: SchedulerUsageMode? = null
        compose.setContent { MaterialTheme {
            NittcConfigurationDialog(null, emptyList(), 2026, { _, _, mode, _ -> chosen = mode; true }, {}, required = true)
        } }
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("キャンセル").assertDoesNotExist()
        compose.onNodeWithText("選択").performClick()
        compose.onNodeWithText("汎用").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitForIdle()
        assertEquals(SchedulerUsageMode.GENERIC, chosen)
    }
}
