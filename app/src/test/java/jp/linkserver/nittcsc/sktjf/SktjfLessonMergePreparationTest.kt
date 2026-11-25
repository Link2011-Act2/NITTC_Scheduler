package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.LessonWeekRange
import org.junit.Assert.*
import org.junit.Test

class SktjfLessonMergePreparationTest {
    private fun document(): SktjfDocument {
        fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/sktjf/$name")).use { it.readBytes() }
        val manifest = SktjfJson.manifest(resource("synthetic-manifest-v1.json"), "example/SKTJF")
        val original = SktjfJson.verifiedDocument(resource("synthetic-v1.json"), manifest, manifest.assets.single())
        return original.copy(lessons = original.lessons.map { it.copy(cycle = SktjfCycle.WEEKLY, conditions = SktjfConditions(emptySet(), emptySet())) })
    }

    @Test fun onePreviousRowCannotBeReusedForTwoNewTimedVariants() {
        val doc = document(); val old = doc.lessons.single()
        val baseline = SktjfStoredBaseline(SktjfBaseline(doc.academicYear, doc.term, doc.classId, mapOf(old.id to old.content())),
            SktjfCalendarBaseline(doc.academicYear, doc.term, doc.classId, emptyMap()))
        val links = SktjfSourceLinks.encode(listOf(SktjfSourceLink(old.id, old.dayOfWeek, old.period, old.cycle)))
        val state = SktjfSourceStateEntity(doc.academicYear, doc.term, doc.classId, doc.revision, SktjfBaselineCodec.encode(baseline), false, links)
        val row = LessonEntity(id = 71, academicYear = doc.academicYear, timetableTerm = TimetableTerm.FIRST,
            dayOfWeek = old.dayOfWeek, slotIndex = old.period - 1, mode = LessonMode.WEEKLY,
            weeklySubject = old.subject, weeklyTeacher = old.teachers.singleOrNull().orEmpty(), weeklyLocation = old.rooms.singleOrNull(),
            aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
        val first = old.copy(id = "first", weekRange = SktjfV2Range(LessonWeekRange(end = 8), "8週まで"))
        val second = old.copy(id = "second", subject = "卒研", weekRange = SktjfV2Range(LessonWeekRange(start = 9), "9週から"))
        val plan = (prepareSktjfLessonMerge(doc.copy(lessons = listOf(second, first)), listOf(row), state) as SktjfLessonPreparation.Ready).plan
        val result = plan.resolve(emptyMap())
        assertEquals(setOf("first", "second"), result.lessons.mapNotNull { it.sourceLessonId }.toSet())
        assertEquals(1, result.lessons.count { it.localKey == sktjfLegacyKey(71, SktjfCycle.WEEKLY) })
        assertEquals("first", result.lessons.single { it.localKey == sktjfLegacyKey(71, SktjfCycle.WEEKLY) }.sourceLessonId)
        assertEquals(1, result.lessons.count { it.localKey == null })
    }

    @Test fun comparesRestoredSourceLinksWithCurrentEditedRow() {
        val doc = document(); val lesson = doc.lessons.single()
        val old = SktjfStoredBaseline(SktjfBaseline(doc.academicYear, doc.term, doc.classId, mapOf(lesson.id to lesson.content())),
            SktjfCalendarBaseline(doc.academicYear, doc.term, doc.classId, doc.calendar.associate { it.date to it.content() }))
        val links = SktjfSourceLinks.encode(listOf(SktjfSourceLink(lesson.id, lesson.dayOfWeek, lesson.period, lesson.cycle)))
        val state = SktjfSourceStateEntity(doc.academicYear, doc.term, doc.classId, doc.revision, SktjfBaselineCodec.encode(old), false, links)
        val row = LessonEntity(id = 71, academicYear = doc.academicYear, timetableTerm = TimetableTerm.FIRST,
            dayOfWeek = lesson.dayOfWeek, slotIndex = lesson.period - 1, mode = LessonMode.WEEKLY,
            weeklySubject = "利用者の修正", weeklyTeacher = lesson.teachers.singleOrNull().orEmpty(),
            weeklyLocation = lesson.rooms.singleOrNull(), aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
        val changed = doc.copy(lessons = listOf(lesson.copy(subject = "配信元の修正")))
        val prepared = prepareSktjfLessonMerge(changed, listOf(row), state) as SktjfLessonPreparation.Ready
        assertEquals(SktjfConflictReason.BOTH_CHANGED, prepared.plan.conflicts.single().reason)
        val kept = prepared.plan.resolve(mapOf(lesson.id to SktjfConflictChoice.KEEP_LOCAL))
        assertEquals("利用者の修正", kept.lessons.single().content.subject)
        assertEquals(sktjfLegacyKey(71, SktjfCycle.WEEKLY), kept.lessons.single().localKey)
        assertEquals(SktjfLocalProjectionIssue.RELINK_REQUIRED,
            (prepareSktjfLessonMerge(changed, listOf(row), state.copy(requiresRelinking = true)) as SktjfLessonPreparation.Blocked).issue)
    }

    @Test fun additionalConditionalLessonParticipatesInConflictWithoutDroppingExistingLesson() {
        val doc = document(); val first = doc.lessons.single()
        val baseline = SktjfStoredBaseline(SktjfBaseline(doc.academicYear, doc.term, doc.classId, mapOf(first.id to first.content())),
            SktjfCalendarBaseline(doc.academicYear, doc.term, doc.classId, emptyMap()))
        val link = SktjfSourceLinks.encode(listOf(SktjfSourceLink(first.id, first.dayOfWeek, first.period, first.cycle)))
        val state = SktjfSourceStateEntity(doc.academicYear, doc.term, doc.classId, doc.revision, SktjfBaselineCodec.encode(baseline), false, link)
        val row = LessonEntity(id = 71, academicYear = doc.academicYear, timetableTerm = TimetableTerm.FIRST,
            dayOfWeek = first.dayOfWeek, slotIndex = first.period - 1, mode = LessonMode.WEEKLY,
            weeklySubject = first.subject, weeklyTeacher = first.teachers.singleOrNull().orEmpty(),
            weeklyLocation = first.rooms.singleOrNull(), aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
        val content = first.content().copy(subject = "手動の追加授業", conditions = SktjfConditions(setOf("DD"), emptySet()))
        val extraBaseline = SktjfStoredBaseline(SktjfBaseline(doc.academicYear, doc.term, doc.classId, mapOf("extra-1" to content)),
            SktjfCalendarBaseline(doc.academicYear, doc.term, doc.classId, emptyMap()))
        val extra = SktjfExtraLessonEntity("extra-1", doc.academicYear, doc.term, doc.classId, null, SktjfBaselineCodec.encode(extraBaseline))
        val incoming = doc.copy(lessons = listOf(first, first.copy(id = "new-dd", subject = "配信の追加授業", conditions = SktjfConditions(setOf("DD"), emptySet()))))
        val plan = (prepareSktjfLessonMerge(incoming, listOf(row), state, listOf(extra)) as SktjfLessonPreparation.Ready).plan
        assertEquals(SktjfConflictReason.OVERLAPPING_LOCAL, plan.conflicts.single().reason)
        val kept = plan.resolve(mapOf("new-dd" to SktjfConflictChoice.KEEP_LOCAL))
        assertEquals(2, kept.lessons.size)
        assertTrue(kept.lessons.any { it.localKey == "extra:extra-1" && it.content.subject == "手動の追加授業" })
        assertTrue(kept.lessons.any { it.localKey == sktjfLegacyKey(71, SktjfCycle.WEEKLY) })
    }
}
