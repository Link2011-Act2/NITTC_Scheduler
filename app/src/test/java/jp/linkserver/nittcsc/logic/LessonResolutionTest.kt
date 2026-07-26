package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.ChangedLessonEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LessonResolutionTest {
    @Test
    fun changedLessonCanFillAnEmptyBaseSlot() {
        val changedLesson = ChangedLessonEntity(
            date = LocalDate.of(2026, 7, 27),
            slotIndex = 2,
            subject = "数学",
            teacher = "山田",
            location = "3-A"
        )

        assertEquals(
            ResolvedLesson("数学", "山田", "3-A"),
            applyChangedLesson(baseLesson = null, changedLesson = changedLesson)
        )
    }

    @Test
    fun supplementaryLessonUsesNoLessonAppearance() {
        assertTrue(ResolvedLesson(" 補講 ", "").usesNoLessonAppearance())
        assertTrue((null as ResolvedLesson?).usesNoLessonAppearance())
        assertFalse(ResolvedLesson("数学", "山田").usesNoLessonAppearance())
    }
}
