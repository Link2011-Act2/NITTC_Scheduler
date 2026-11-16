package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NittcAffiliationReminderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingYearOpensAffiliationSettings() {
        var opened = false
        compose.setContent { MaterialTheme { NittcAffiliationReminder(2027) { opened = true } } }
        compose.onNodeWithText("2027年度の所属が未設定です").assertIsDisplayed()
        compose.onNodeWithText("所属を設定").performClick()
        compose.waitForIdle()
        assertTrue(opened)
    }
}
