package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.logic.TimetableTerm
import org.junit.Assert.*
import org.junit.Test

class SktjfLegacyTimetableProjectionTest {
    private fun row() = LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
        dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "数学", weeklyTeacher = "先生",
        weeklyLocation = "教室", aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
    private fun content(cycle: SktjfCycle = SktjfCycle.WEEKLY) = SktjfLessonContent(
        dayOfWeek = 1, period = 1, cycle = cycle, subject = "数学", teachers = listOf("先生"), rooms = listOf("教室"),
        conditions = SktjfConditions(emptySet(), emptySet()), kind = SktjfLessonKind.NORMAL, remote = null
    )
    private fun resolved(vararg rows: SktjfMergedLesson) = SktjfResolvedLessonMerge(rows.toList(), SktjfBaseline(2026, "S", "1-1", emptyMap()))
    private fun project(existing: List<LessonEntity>, resolved: SktjfResolvedLessonMerge) =
        projectSktjfTimetable(2026, TimetableTerm.FIRST, "1-1", existing, resolved)
    private fun blocked(result: SktjfTimetableProjection) = (result as SktjfTimetableProjection.Blocked).reason

    @Test fun preservesExistingIdAndSourceMetadata() {
        val old = row(); val merged = SktjfMergedLesson(sktjfLegacyKey(71, SktjfCycle.WEEKLY), "source-1", content().copy(subject = "新しい数学"))
        val result = project(listOf(old), resolved(merged)) as SktjfTimetableProjection.Ready
        assertEquals(71L, result.rows.single().id); assertEquals("新しい数学", result.rows.single().weeklySubject)
        assertEquals("source-1", result.metadata.getValue(1 to 1).single().sourceLessonId)
        assertEquals("数学", old.weeklySubject)
    }

    @Test fun weeklyAndAlternatingRepresentationsPreserveTheSameRowId() {
        val old = row(); val weeklyKey = sktjfLegacyKey(71, SktjfCycle.WEEKLY)
        val a = SktjfMergedLesson(weeklyKey, "a", content(SktjfCycle.A))
        val b = SktjfMergedLesson(null, "b", content(SktjfCycle.B).copy(subject = "英語"))
        val result = project(listOf(old), resolved(a, b)) as SktjfTimetableProjection.Ready
        assertEquals(71L, result.rows.single().id); assertEquals(LessonMode.ALTERNATING, result.rows.single().mode)
        assertEquals("数学", result.rows.single().aSubject); assertEquals("英語", result.rows.single().bSubject)
    }

    @Test fun explicitlyReplacingAlternatingWeeksWithWeeklyRetainsRowId() {
        val old = row().copy(mode = LessonMode.ALTERNATING, weeklySubject = "", weeklyTeacher = "",
            weeklyLocation = null, aSubject = "A授業", aTeacher = "先生", bSubject = "B授業", bTeacher = "先生")
        val merged = resolved(SktjfMergedLesson(sktjfLegacyKey(71, SktjfCycle.A), "new", content()))
            .copy(replacedLocalKeys = setOf(sktjfLegacyKey(71, SktjfCycle.B)))
        val result = project(listOf(old), merged) as SktjfTimetableProjection.Ready
        assertEquals(71L, result.rows.single().id)
        assertEquals(LessonMode.WEEKLY, result.rows.single().mode)
        assertEquals("数学", result.rows.single().weeklySubject)
    }

    @Test fun neverDropsExistingManualContentWhenMissingFromMerge() {
        val result = project(listOf(row()), resolved(SktjfMergedLesson(null, "new", content())))
        assertEquals(SktjfProjectionBlock.MISSING_LOCAL_LESSON, blocked(result))
    }

    @Test fun explicitSourceRemovalClearsTheRowWithoutDeletingItsIdentity() {
        val removed = resolved().copy(clearedLocalKeys = setOf(sktjfLegacyKey(71, SktjfCycle.WEEKLY)))
        val result = project(listOf(row()), removed) as SktjfTimetableProjection.Ready
        assertEquals(71L, result.rows.single().id)
        assertEquals("", result.rows.single().weeklySubject)
        assertEquals("", result.rows.single().weeklyTeacher)
        assertNull(result.rows.single().weeklyLocation)
    }

    @Test fun blocksConditionalMultipleOrOverlappingLessonsUntilStorageCanKeepThem() {
        val key = sktjfLegacyKey(71, SktjfCycle.WEEKLY)
        val normal = SktjfMergedLesson(key, "one", content())
        val conditional = SktjfMergedLesson(null, "two", content().copy(conditions = SktjfConditions(setOf("DD"), emptySet())))
        assertEquals(SktjfProjectionBlock.NEEDS_ADDITIONAL_STORAGE, blocked(project(listOf(row()), resolved(normal, conditional))))
        assertEquals(SktjfProjectionBlock.NEEDS_ADDITIONAL_STORAGE, blocked(project(listOf(row()), resolved(normal, conditional.copy(content = content())))))
    }

    @Test fun blocksMovingAnExistingIdentity() {
        val key = sktjfLegacyKey(71, SktjfCycle.WEEKLY)
        assertEquals(SktjfProjectionBlock.IDENTITY_MOVED, blocked(project(listOf(row()), resolved(SktjfMergedLesson(key, "a", content().copy(period = 2))))))
    }

    @Test fun archivesDisplacedInactiveFieldsAndRestoresThemOnlyToTheInactiveMode() {
        val old = row().copy(aSubject = "保存済み", aTeacher = "別の先生", aLocation = "別の教室", bTeacher = "担当者のみ")
        val weeklyKey = sktjfLegacyKey(71, SktjfCycle.WEEKLY)
        val aKey = sktjfLegacyKey(71, SktjfCycle.A)
        val adopted = resolved(SktjfMergedLesson(null, "new-a", content(SktjfCycle.A)))
            .copy(clearedLocalKeys = setOf(weeklyKey))
        val applied = (project(listOf(old), adopted) as SktjfTimetableProjection.Ready).rows.single()
        assertEquals(71L, applied.id)
        assertEquals("数学", applied.aSubject)
        assertEquals("", applied.bTeacher)
        val weekly = resolved(SktjfMergedLesson(null, "later-weekly", content().copy(subject = "次の授業")))
            .copy(clearedLocalKeys = setOf(aKey))
        val restored = (project(listOf(applied), weekly) as SktjfTimetableProjection.Ready).rows.single()
        assertEquals(LessonMode.WEEKLY, restored.mode)
        assertEquals("次の授業", restored.weeklySubject)
        assertEquals("保存済み", restored.aSubject)
        assertEquals("別の先生", restored.aTeacher)
        assertEquals("別の教室", restored.aLocation)
        assertEquals("担当者のみ", restored.bTeacher)
        assertEquals(applied.inactiveModeDraftsJson, restored.inactiveModeDraftsJson)
        assertEquals(SktjfProjectionBlock.INVALID_INACTIVE_DRAFTS,
            blocked(project(listOf(old.copy(inactiveModeDraftsJson = "{}")), adopted)))
    }

    @Test fun rejectsDifferentPartitionAndUnknownLocalIdentity() {
        val key = sktjfLegacyKey(71, SktjfCycle.WEEKLY); val data = resolved(SktjfMergedLesson(key, "a", content()))
        assertEquals(SktjfProjectionBlock.INVALID_PARTITION, blocked(projectSktjfTimetable(2027, TimetableTerm.FIRST, "1-1", listOf(row()), data)))
        assertEquals(SktjfProjectionBlock.INVALID_PARTITION, blocked(projectSktjfTimetable(2026, TimetableTerm.FIRST, "1-2", listOf(row()), data)))
        assertEquals(SktjfProjectionBlock.NEEDS_ADDITIONAL_STORAGE, blocked(project(emptyList(), resolved(SktjfMergedLesson("extra:7", "a", content())))))
    }
}
