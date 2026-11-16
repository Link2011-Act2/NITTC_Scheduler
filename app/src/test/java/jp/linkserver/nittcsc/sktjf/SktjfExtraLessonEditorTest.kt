package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import org.junit.Assert.*
import org.junit.Test

class SktjfExtraLessonEditorTest {
    private fun source(): SktjfExtraLessonEntity {
        val content = SktjfLessonContent(1, 2, SktjfCycle.A, "補講", listOf("先生A", "先生B"), listOf("教室"),
            SktjfConditions(setOf("DD"), setOf("INTERNATIONAL")), SktjfLessonKind.MAKEUP, true)
        val stored = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf("extra-1" to content)),
            SktjfCalendarBaseline(2026, "S", "1-1", emptyMap()))
        return SktjfExtraLessonEntity("extra-1", 2026, "S", "1-1", "source-1", SktjfBaselineCodec.encode(stored))
    }

    @Test fun editingOneLessonKeepsItsIdentityConditionsAndMultipleNames() {
        val original = source()
        val edited = editSktjfExtraLesson(original, SktjfExtraLessonDraft(" 数学 ", "先生A\n先生B", "教室1\n教室2"))
        val content = SktjfBaselineCodec.decode(edited.contentJson).lessons.lessons.getValue("extra-1")
        assertEquals(original.id, edited.id); assertEquals(original.sourceLessonId, edited.sourceLessonId)
        assertEquals("数学", content.subject); assertEquals(listOf("先生A", "先生B"), content.teachers)
        assertEquals(listOf("教室1", "教室2"), content.rooms); assertEquals(SktjfCycle.A, content.cycle)
        assertEquals(SktjfConditions(setOf("DD"), setOf("INTERNATIONAL")), content.conditions)
        assertEquals(true, content.remote); assertEquals(SktjfLessonKind.NORMAL, content.kind)
    }

    @Test fun unchangedSpecialNameKeepsKindAndEmptySubjectIsRejected() {
        val original = source()
        val edited = editSktjfExtraLesson(original, SktjfExtraLessonDraft("補講", "先生", ""))
        assertEquals(SktjfLessonKind.MAKEUP, SktjfBaselineCodec.decode(edited.contentJson).lessons.lessons.getValue("extra-1").kind)
        assertTrue(runCatching { editSktjfExtraLesson(original, SktjfExtraLessonDraft(" ", "", "")) }.isFailure)
        assertEquals("補講", SktjfBaselineCodec.decode(original.contentJson).lessons.lessons.getValue("extra-1").subject)
    }
}
