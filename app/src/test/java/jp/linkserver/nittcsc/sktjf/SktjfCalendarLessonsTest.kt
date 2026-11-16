package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfCalendarLessonsTest {
    private val monday = LocalDate.of(2026, 4, 6)
    private val settings = SettingsEntity(termStart = monday.minusDays(1), termEnd = monday.plusDays(30))
    private val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)
    private fun extra(id: String, cycle: SktjfCycle = SktjfCycle.A, required: Set<String> = emptySet()): SktjfExtraLessonEntity {
        val content = SktjfLessonContent(1, 1, cycle, "数学", listOf("先生$id"), listOf("教室$id"),
            SktjfConditions(required, emptySet()), SktjfLessonKind.NORMAL, null)
        val baseline = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf(id to content)),
            SktjfCalendarBaseline(2026, "S", "1-1", emptyMap()))
        return SktjfExtraLessonEntity(id, 2026, "S", "1-1", id, SktjfBaselineCodec.encode(baseline))
    }
    private fun generate(days: Map<LocalDate, DayTypeEntity> = emptyMap(), cancelled: Set<Pair<LocalDate, Int>> = emptySet(),
        exams: Set<LocalDate> = emptySet(), profile: NittcProfileEntity = this.profile,
        range: ClosedRange<LocalDate> = monday..monday): List<jp.linkserver.nittcsc.logic.GeneratedLesson> {
        val resolver = SktjfExtraLessonResolver.create(SchedulerUsageMode.NITTC, listOf(profile),
            listOf(extra("one"), extra("two", required = setOf("DD")), extra("b", SktjfCycle.B)))
        return additionalCalendarLessons(range, settings, settings.basicClassSlots(), days, cancelled, exams, resolver)
    }

    @Test fun simultaneousSameNameLessonsRetainIdentityAndRoom() {
        val result = generate()
        assertEquals(listOf("one", "two"), result.map { it.extraLessonId })
        assertEquals(listOf("数学", "数学"), result.map { it.subject })
        assertEquals(listOf("教室one", "教室two"), result.map { it.location })
        assertEquals(listOf("one"), generate(profile = profile.copy(isDd = false)).map { it.extraLessonId })
    }

    @Test fun holidayCancellationExamAndSubstitution() {
        assertTrue(generate(days = mapOf(monday to DayTypeEntity(monday, DayType.HOLIDAY))).isEmpty())
        assertTrue(generate(cancelled = setOf(monday to 0)).isEmpty())
        assertTrue(generate(exams = setOf(monday)).isEmpty())
        val sunday = monday.minusDays(1)
        val day = DayTypeEntity(sunday, DayType.A, overrideLessonDayOfWeek = 1, overrideLessonDayType = DayType.B)
        assertEquals(listOf("b"), generate(days = mapOf(sunday to day), range = sunday..sunday).map { it.extraLessonId })
        assertTrue(generate(range = sunday..sunday).isEmpty())
    }
}
