package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.linkserver.nittcsc.data.NittcYearOutcome
import jp.linkserver.nittcsc.data.UsageModeEntity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NittcYearTransitionDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun graduationIsAnExplicitChoice() {
        var result: NittcYearOutcome? = null
        var dismissed = false
        compose.setContent { MaterialTheme {
            NittcYearTransitionDialog(2027, UsageModeEntity(mode = "NITTC"), emptyList(), emptyList(),
                { _, _, _, _, outcome -> result = outcome; true }, { _, _, _, _, _, _ -> false }, { dismissed = true })
        } }
        compose.onNodeWithText("卒業").performClick()
        compose.waitForIdle()
        assertEquals(NittcYearOutcome.GRADUATED, result)
        assertTrue(dismissed)
    }

    @Test fun deferralIsRecordedSeparately() {
        var result: NittcYearOutcome? = null
        compose.setContent { MaterialTheme {
            NittcYearTransitionDialog(2027, UsageModeEntity(mode = "NITTC"), emptyList(), emptyList(),
                { _, _, _, _, outcome -> result = outcome; true }, { _, _, _, _, _, _ -> false }, {})
        } }
        compose.onNodeWithText("保留").performClick()
        compose.waitForIdle()
        assertEquals(NittcYearOutcome.DEFERRED, result)
    }
}
