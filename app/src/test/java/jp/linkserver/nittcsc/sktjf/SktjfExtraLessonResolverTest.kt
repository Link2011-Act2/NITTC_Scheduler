package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

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
}
