package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import jp.linkserver.nittcsc.logic.LessonWeekRange

class SktjfExtraLessonResolverTest {
    private val date = LocalDate.of(2026, 4, 6)
    private fun extra(id: String, required: Set<String> = emptySet(), excluded: Set<String> = emptySet(),
        cycle: SktjfCycle = SktjfCycle.A, day: Int = 1, classId: String = "1-1"): SktjfExtraLessonEntity {
        val content = SktjfLessonContent(day, 1, cycle, id, listOf("先生"), emptyList(), SktjfConditions(required, excluded), SktjfLessonKind.NORMAL, null)
        val baseline = SktjfStoredBaseline(SktjfBaseline(2026, "S", classId, mapOf(id to content)),
            SktjfCalendarBaseline(2026, "S", classId, emptyMap()))
        return SktjfExtraLessonEntity(id, 2026, "S", classId, id, SktjfBaselineCodec.encode(baseline))
    }
    private fun profile(dd: Boolean = false, international: Boolean = false) = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", dd, international)
    private fun select(resolver: SktjfExtraLessonResolver, day: DayTypeEntity = DayTypeEntity(date, DayType.A)) =
        resolver.lessonsOn(date, 1, day, true, 10, 1).map { it.id }

    @Test fun matchingProfileKeepsAllApplicableLessonsInSameSlot() {
        val lessons = listOf(extra("common"), extra("dd", setOf("DD")), extra("not-international", excluded = setOf("INTERNATIONAL")), extra("other", classId = "1-2"))
        val dd = SktjfExtraLessonResolver.create(SchedulerUsageMode.NITTC, listOf(profile(dd = true)), lessons)
        assertEquals(listOf("common", "dd", "not-international"), select(dd))
        val international = SktjfExtraLessonResolver.create(SchedulerUsageMode.NITTC, listOf(profile(international = true)), lessons)
        assertEquals(listOf("common"), select(international))
        assertTrue(select(SktjfExtraLessonResolver.create(SchedulerUsageMode.GENERIC, listOf(profile(dd = true)), lessons)).isEmpty())
    }

    @Test fun holidayAndSubstitutionUseEffectiveDayAndCycle() {
        val lessons = listOf(extra("monday-a"), extra("tuesday-b", cycle = SktjfCycle.B, day = 2), extra("weekly", cycle = SktjfCycle.WEEKLY, day = 2))
        val resolver = SktjfExtraLessonResolver.create(SchedulerUsageMode.NITTC, listOf(profile()), lessons)
        assertEquals(listOf("tuesday-b", "weekly"), select(resolver, DayTypeEntity(date, DayType.A, overrideLessonDayOfWeek = 2, overrideLessonDayType = DayType.B)))
        assertTrue(select(resolver, DayTypeEntity(date, DayType.HOLIDAY)).isEmpty())
    }

    @Test fun invalidOrDuplicateProvenanceIsNotSilentlyDisplayed() {
        val item = extra("same")
        assertTrue(runCatching { SktjfExtraLessonResolver.create(SchedulerUsageMode.NITTC, listOf(profile()), listOf(item, item)) }.isFailure)
        assertTrue(runCatching { SktjfExtraLessonResolver.create(SchedulerUsageMode.NITTC, listOf(profile(), profile()), emptyList()) }.isFailure)
    }

    @Test fun weekRestrictedExtraOnlyAppearsOnConfirmedApplicableDates() {
        val original=extra("conditional")
        val snapshot=SktjfBaselineCodec.decode(original.contentJson)
        val lesson=snapshot.lessons.lessons.getValue("conditional").copy(weekRange=SktjfV2Range(LessonWeekRange(start=9),"（9週〜）"))
        val changed=snapshot.copy(lessons=snapshot.lessons.copy(lessons=mapOf("conditional" to lesson)),
            weeks=mapOf(date to SktjfV2Week(9,listOf(SktjfReference("src",1,null)))))
        fun make(value:SktjfStoredBaseline)=SktjfExtraLessonResolver.create(SchedulerUsageMode.NITTC,listOf(profile()),
            listOf(original.copy(contentJson=SktjfBaselineCodec.encode(value))))
        assertEquals(listOf("conditional"),select(make(changed)))
        assertTrue(select(make(changed.copy(weeks=mapOf(date to SktjfV2Week(8,listOf(SktjfReference("src",1,null))))))).isEmpty())
        assertTrue(select(make(changed.copy(weeks=emptyMap()))).isEmpty())
        assertTrue(select(make(changed),DayTypeEntity(date,DayType.B)).isEmpty())
    }
}
