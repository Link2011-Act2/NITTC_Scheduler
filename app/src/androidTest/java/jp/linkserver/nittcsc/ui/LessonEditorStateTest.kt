package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LessonEditorStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingLessonsInitializesFieldsWithoutSavingAndCancelsOldDraft() {
        val weekly = lesson(1, LessonMode.WEEKLY, "数学")
        val alternate = lesson(2, LessonMode.ALTERNATING, "理科")
        var current by mutableStateOf(weekly)
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3)
        val saved = mutableListOf<LessonDraft>()
        compose.setContent {
            AppTheme(mode, dynamicColor = false) {
                Surface {
                    Box(Modifier.width(360.dp)) {
                        LessonEditorCard(title = "1/2校時", lesson = current,
                            onAutoSave = { saved += it }, onSave = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        for (design in UiDesignMode.entries) {
            compose.runOnIdle { mode = design; current = weekly }
            compose.mainClock.advanceTimeBy(1000)
            compose.onNodeWithText("数学").assertExists().performTextReplacement("編集途中")
            compose.mainClock.advanceTimeBy(250)
            compose.runOnIdle { current = alternate }
            compose.mainClock.advanceTimeBy(1000)
            compose.onNodeWithText("A理科").assertExists()
            compose.onNodeWithText("B理科").assertExists()
            compose.onNodeWithText("編集途中").assertDoesNotExist()
            compose.runOnIdle { assertEquals("Switching lessons must not save the previous draft", 0, saved.size) }
            compose.runOnIdle { current = weekly }
            compose.mainClock.advanceTimeBy(1000)
            compose.onNodeWithText("数学").performTextReplacement("変更後")
            compose.mainClock.advanceTimeBy(1000)
            compose.runOnIdle {
                assertEquals("Actual input must still auto-save", "変更後", saved.single().weeklySubject)
                saved.clear()
            }
            compose.runOnIdle { current = alternate }
            compose.mainClock.advanceTimeBy(1000)
        }
    }

    private fun lesson(id: Long, mode: LessonMode, subject: String) = LessonEntity(
        id = id, academicYear = 2026, dayOfWeek = id.toInt(), slotIndex = 0,
        mode = mode, weeklySubject = subject, weeklyTeacher = "担当",
        aSubject = "A$subject", aTeacher = "A担当", bSubject = "B$subject", bTeacher = "B担当"
    )
}
