package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.LessonWeekRange
import org.junit.Assert.*
import org.junit.Test

class SktjfExtendedTimetableProjectionTest {
    private fun row() = LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
        dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "通常", weeklyTeacher = "先生",
        aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
    private fun content(subject: String, attrs: Set<String> = emptySet()) = SktjfLessonContent(1, 1, SktjfCycle.WEEKLY,
        subject, listOf("先生"), emptyList(), SktjfConditions(attrs, emptySet()), SktjfLessonKind.NORMAL, null)
    private fun resolved(vararg lessons: SktjfMergedLesson) = SktjfResolvedLessonMerge(lessons.toList(), SktjfBaseline(2026, "S", "1-1", emptyMap()))

    @Test fun weekRestrictedLessonsAreNeverStoredAsUnrestrictedRows() {
        val candidate=SktjfMergedLesson(null,"week-1",content("卒研").copy(weekRange=SktjfV2Range(LessonWeekRange(start=9),"（9週〜）")))
        assertEquals(SktjfExtendedProjection.Blocked,projectSktjfExtendedTimetable(2026,TimetableTerm.FIRST,"1-1",emptyList(),emptyList(),resolved(candidate)))
        val legacy=projectSktjfTimetable(2026,TimetableTerm.FIRST,"1-1",emptyList(),resolved(candidate))
        assertEquals(SktjfProjectionBlock.NEEDS_ADDITIONAL_STORAGE,(legacy as SktjfTimetableProjection.Blocked).reason)
    }

    @Test fun alternatingLessonsUseTheOrdinaryEditableRow() {
        val a = SktjfMergedLesson(null, "a", content("A授業").copy(cycle = SktjfCycle.A))
        val b = SktjfMergedLesson(null, "b", content("B授業").copy(cycle = SktjfCycle.B))
        val result = projectSktjfExtendedTimetable(2026, TimetableTerm.FIRST, "1-1", emptyList(), emptyList(),
            resolved(a, b)) as SktjfExtendedProjection.Ready
        assertTrue(result.extras.isEmpty())
        assertEquals(LessonMode.ALTERNATING, result.legacy.rows.single().mode)
        assertEquals("A授業", result.legacy.rows.single().aSubject)
        assertEquals("B授業", result.legacy.rows.single().bSubject)
    }

    @Test fun preservesManualRowAndStoresConditionalLessonSeparately() {
        val manual = SktjfMergedLesson(sktjfLegacyKey(71, SktjfCycle.WEEKLY), null, content("通常"))
        val conditional = SktjfMergedLesson(null, "dd-source", content("DD授業", setOf("DD")))
        val result = projectSktjfExtendedTimetable(2026, TimetableTerm.FIRST, "1-1", listOf(row()), emptyList(),
            resolved(manual, conditional), newId = { "extra-1" }) as SktjfExtendedProjection.Ready
        assertEquals(71L, result.legacy.rows.single().id)
        assertEquals("通常", result.legacy.rows.single().weeklySubject)
        val extra = result.extras.single(); assertEquals("dd-source", extra.sourceLessonId)
        assertEquals(setOf("DD"), SktjfBaselineCodec.decode(extra.contentJson).lessons.lessons.getValue("extra-1").conditions.all)
    }

    @Test fun keepsExistingExtraIdentityAndRejectsDroppedRecords() {
        val manual = SktjfMergedLesson(sktjfLegacyKey(71, SktjfCycle.WEEKLY), null, content("通常"))
        val first = projectSktjfExtendedTimetable(2026, TimetableTerm.FIRST, "1-1", listOf(row()), emptyList(),
            resolved(manual, SktjfMergedLesson(null, "dd", content("DD", setOf("DD")))), newId = { "extra-1" }) as SktjfExtendedProjection.Ready
        val updated = SktjfMergedLesson("extra:extra-1", "dd", content("編集済み", setOf("DD")))
        val second = projectSktjfExtendedTimetable(2026, TimetableTerm.FIRST, "1-1", listOf(row()), first.extras,
            resolved(manual, updated)) as SktjfExtendedProjection.Ready
        assertEquals("extra-1", second.extras.single().id)
        assertEquals("編集済み", SktjfBaselineCodec.decode(second.extras.single().contentJson).lessons.lessons.getValue("extra-1").subject)
        assertEquals(SktjfExtendedProjection.Blocked,
            projectSktjfExtendedTimetable(2026, TimetableTerm.FIRST, "1-1", listOf(row()), first.extras, resolved(manual)))
    }
}
