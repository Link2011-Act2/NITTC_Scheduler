package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.sktjf.*
import org.junit.Assert.*
import org.junit.Test

class SktjfExtraLessonBackupTest {
    private fun extra(id: String, sourceId: String?, attributes: Set<String>): SktjfExtraLessonEntity {
        val lesson = SktjfLessonContent(1, 2, SktjfCycle.A, "数学", listOf("先生"), listOf("教室"),
            SktjfConditions(attributes, emptySet()), SktjfLessonKind.NORMAL, null)
        val content = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf(id to lesson)),
            SktjfCalendarBaseline(2026, "S", "1-1", emptyMap()))
        return SktjfExtraLessonEntity(id, 2026, "S", "1-1", sourceId, SktjfBaselineCodec.encode(content))
    }

    @Test fun multipleLessonsInSameSlotSurviveBackup() {
        val lessons = listOf(extra("one", "source-1", emptySet()), extra("two", "source-2", setOf("DD")))
        assertEquals(lessons, SktjfExtraLessonBackup.decode(SktjfExtraLessonBackup.encode(lessons)))
    }

    @Test fun rejectsDuplicateSourceAndMismatchedPartition() {
        val first = extra("one", "source-1", emptySet())
        val second = extra("two", "source-1", setOf("DD"))
        assertTrue(runCatching { SktjfExtraLessonBackup.encode(listOf(first, second)) }.isFailure)
        assertTrue(runCatching { SktjfExtraLessonBackup.encode(listOf(first.copy(classId = "1-2"))) }.isFailure)
    }
}
