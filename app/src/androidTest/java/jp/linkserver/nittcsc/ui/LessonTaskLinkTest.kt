package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import jp.linkserver.nittcsc.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LessonTaskLinkTest {
    @get:Rule val compose = createComposeRule()

    @Test fun romanNumeralChangeKeepsTheLessonLink() {
        var saved: TaskEntity? = null
        val date = LocalDate.of(2026, 4, 6)
        val draft = TaskEntity(lessonId = 71, subject = "英語II", title = "課題", dueDate = date, createdDate = date)
        compose.setContent { MaterialTheme { AddTaskScreen(task = draft, autoResolveInitialSubject = false,
            onSave = { saved = it }, onBack = {}) } }
        compose.onNodeWithText("英語II").performTextReplacement("英語Ⅱ")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(71L, saved?.lessonId) }
    }

    @Test fun selectingAnotherSubjectRemovesTheLessonLink() {
        var saved: TaskEntity? = null
        val date = LocalDate.of(2026, 4, 6)
        val draft = TaskEntity(lessonId = 71, subject = "英語II", title = "課題", dueDate = date, createdDate = date)
        compose.setContent { MaterialTheme { AddTaskScreen(task = draft, autoResolveInitialSubject = false,
            onSave = { saved = it }, onBack = {}) } }
        compose.onNodeWithText("英語II").performTextReplacement("数学I")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals("数学I", saved?.subject); assertNull(saved?.lessonId) }
    }
}
