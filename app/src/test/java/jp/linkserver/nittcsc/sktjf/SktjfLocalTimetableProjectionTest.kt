package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.logic.TimetableTerm
import org.junit.Assert.*
import org.junit.Test

class SktjfLocalTimetableProjectionTest {
    private fun row(id: Long = 71, slot: Int = 0) = LessonEntity(id = id, academicYear = 2026,
        timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1, slotIndex = slot, mode = LessonMode.WEEKLY,
        weeklySubject = "数学", weeklyTeacher = "先生", weeklyLocation = "教室", aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
    private fun baseline() = SktjfBaseline(2026, "S", "1-1", mapOf("one" to SktjfLessonContent(
        1, 1, SktjfCycle.WEEKLY, "数学", listOf("先生"), listOf("教室"), SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, true)))
    private fun project(rows: List<LessonEntity>, baseline: SktjfBaseline? = baseline(), links: List<SktjfSourceLink> = listOf(SktjfSourceLink("one", 1, 1, SktjfCycle.WEEKLY)), relink: Boolean = false) =
        projectSktjfLocalTimetable(2026, TimetableTerm.FIRST, "1-1", rows, baseline, links, relink)

    @Test fun distinguishesLinkedAndManualLessonsAndPreservesRowIdentity() {
        val result = project(listOf(row().copy(weeklySubject = "編集済み"), row(72, 1))) as SktjfLocalProjection.Ready
        assertEquals(listOf("one", null), result.timetable.lessons.map { it.sourceLessonId })
        assertEquals(listOf(sktjfLegacyKey(71, SktjfCycle.WEEKLY), sktjfLegacyKey(72, SktjfCycle.WEEKLY)), result.timetable.lessons.map { it.localKey })
        assertEquals("編集済み", result.timetable.lessons[0].content.subject)
        assertEquals(true, result.timetable.lessons[0].content.remote)
    }

    @Test fun blocksAmbiguousLinksPartialDataAndUnrepresentableMetadata() {
        assertEquals(SktjfLocalProjectionIssue.RELINK_REQUIRED, (project(listOf(row()), relink = true) as SktjfLocalProjection.Blocked).issue)
        assertEquals(SktjfLocalProjectionIssue.INVALID_SOURCE_LINKS, (project(listOf(row()), links = listOf(SktjfSourceLink("missing", 1, 1, SktjfCycle.WEEKLY))) as SktjfLocalProjection.Blocked).issue)
        assertEquals(SktjfLocalProjectionIssue.INCOMPLETE_LESSON, (project(listOf(row().copy(weeklySubject = ""))) as SktjfLocalProjection.Blocked).issue)
        val old = baseline(); val conditional = old.copy(lessons = mapOf("one" to old.lessons.getValue("one").copy(conditions = SktjfConditions(setOf("DD"), emptySet()))))
        assertEquals(SktjfLocalProjectionIssue.NEEDS_ADDITIONAL_STORAGE, (project(listOf(row()), baseline = conditional) as SktjfLocalProjection.Blocked).issue)
    }

    @Test fun deletedAndMissingRowsAreNeverInvented() {
        val result = project(listOf(row().copy(weeklySubject = "", weeklyTeacher = "", weeklyLocation = null))) as SktjfLocalProjection.Ready
        assertTrue(result.timetable.lessons.isEmpty())
        val firstImport = project(listOf(row()), baseline = null, links = emptyList()) as SktjfLocalProjection.Ready
        assertNull(firstImport.timetable.lessons.single().sourceLessonId)
    }
}
