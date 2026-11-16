package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import jp.linkserver.nittcsc.logic.ClassSlot
import jp.linkserver.nittcsc.sktjf.SktjfConditions
import jp.linkserver.nittcsc.sktjf.SktjfCycle
import jp.linkserver.nittcsc.sktjf.SktjfLessonContent
import jp.linkserver.nittcsc.sktjf.SktjfLessonKind
import jp.linkserver.nittcsc.sktjf.SktjfVisibleExtra
import jp.linkserver.nittcsc.sktjf.SktjfExtraLessonDraft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class SktjfAdditionalLessonsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multipleLessonsInOneSlotRemainVisible() {
        val slot = ClassSlot(0, "1/2校時", LocalTime.of(8, 40), LocalTime.of(10, 10))
        fun lesson(id: String) = SktjfVisibleExtra(id, id, SktjfLessonContent(1, 1, SktjfCycle.WEEKLY,
            id, listOf("先生"), emptyList(), SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null))
        compose.setContent { MaterialTheme { SktjfAdditionalLessons(listOf(Triple(slot, lesson("数学"), false), Triple(slot, lesson("英語"), false)), false) } }
        compose.onNodeWithText("数学").assertIsDisplayed()
        compose.onNodeWithText("英語").assertIsDisplayed()
    }

    @Test fun compactCountHasAccessibleDescription() {
        compose.setContent { MaterialTheme { SktjfAdditionalLessonCount(2) } }
        compose.onNodeWithContentDescription("追加授業 2件").assertIsDisplayed()
    }

    @Test fun editActionSubmitsOnlySelectedLesson() {
        val slot = ClassSlot(0, "1/2校時", LocalTime.of(8, 40), LocalTime.of(10, 10))
        fun lesson(id: String) = SktjfVisibleExtra(id, id, SktjfLessonContent(1, 1, SktjfCycle.WEEKLY,
            id, emptyList(), emptyList(), SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null))
        var savedId: String? = null; var savedDraft: SktjfExtraLessonDraft? = null
        compose.setContent { MaterialTheme { SktjfAdditionalLessons(listOf(Triple(slot, lesson("数学"), false), Triple(slot, lesson("英語"), false)), false) { selected, draft ->
            savedId = selected.id; savedDraft = draft; true
        } } }
        compose.onNodeWithContentDescription("数学の時間割を編集").performClick()
        compose.onNode(hasSetTextAction() and hasText("数学")).performTextReplacement("代数")
        compose.onNodeWithText("保存").performClick()
        compose.waitForIdle()
        assertEquals("数学", savedId); assertEquals("代数", savedDraft?.subject)
        compose.onNodeWithText("英語").assertIsDisplayed()
    }
}
