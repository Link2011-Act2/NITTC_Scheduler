package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.TimetableTerm
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfExtraNotificationTest {
    private val date = LocalDate.of(2026, 4, 6)
    private val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)
    private fun extra(id: String, subject: String, kind: SktjfLessonKind = SktjfLessonKind.NORMAL,
        source: Boolean = true, required: Set<String> = emptySet(), cycle: SktjfCycle = SktjfCycle.A): SktjfExtraLessonEntity {
        val content = SktjfLessonContent(1, 1, cycle, subject, listOf("先生$id"), listOf("教室$id"),
            SktjfConditions(required, emptySet()), kind, null)
        val baseline = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf(id to content)),
            SktjfCalendarBaseline(2026, "S", "1-1", emptyMap()))
        return SktjfExtraLessonEntity(id, 2026, "S", "1-1", if (source) id else null, SktjfBaselineCodec.encode(baseline))
    }
    private fun schedule(extras: List<SktjfExtraLessonEntity>, day: DayType = DayType.A,
        cancelled: Boolean = false, mode: SchedulerUsageMode = SchedulerUsageMode.NITTC,
        includeBase: Boolean = true, dd: Boolean = true, abEnabled: Boolean = true): EffectiveSchoolSchedule {
        val row = LessonEntity(academicYear = 2026, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1,
            slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "数学", weeklyTeacher = "先生",
            aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
        return EffectiveSchoolSchedule(SettingsEntity(termStart = date, termEnd = date.plusDays(1), enableAbTimetable = abEnabled),
            mapOf(date to DayTypeEntity(date, day)), if (includeBase) listOf(row) else emptyList(), emptyMap(),
            if (cancelled) setOf(date to 0) else emptySet(), emptyMap(), emptyMap(), null, mode,
            SktjfExtraLessonResolver.create(mode, listOf(profile.copy(isDd = dd)), extras))
    }

    @Test fun simultaneousLessonsAndIndividualExclusionsArePreserved() {
        val resolved = schedule(listOf(extra("dd", "英語", required = setOf("DD")), extra("extra", "物理")))
        val combined = resolved.notificationLessonsOn(date).single().content
        assertEquals("数学、英語、物理", combined.subject)
        assertEquals("数学、物理", resolved.notificationLessonsOn(date) { it.subject != "英語" }.single().content.subject)
        assertEquals("英語", resolved.notificationLessonsOn(date) { it.subject == "英語" }.single().content.subject)
    }

    @Test fun specialManualHolidayCycleAndCancellation() {
        val items = listOf(extra("special", "補講", SktjfLessonKind.MAKEUP),
            extra("manual", "補講", SktjfLessonKind.MAKEUP, source = false),
            extra("cycleb", "化学", cycle = SktjfCycle.B))
        assertEquals("数学、補講", schedule(items).notificationLessonsOn(date).single().content.subject)
        assertEquals("数学、化学", schedule(items, day = DayType.B).notificationLessonsOn(date).single().content.subject)
        assertTrue(schedule(items, day = DayType.HOLIDAY).notificationLessonsOn(date).isEmpty())
        assertTrue(schedule(items, cancelled = true).notificationLessonsOn(date).isEmpty())
        assertEquals("数学", schedule(items, mode = SchedulerUsageMode.GENERIC).notificationLessonsOn(date).single().content.subject)
    }

    @Test fun extraOnlyAndStudentConditionsAndAbDisabled() {
        val items = listOf(extra("dd", "英語", required = setOf("DD")), extra("b", "物理", cycle = SktjfCycle.B))
        assertEquals("英語", schedule(items, includeBase = false).notificationLessonsOn(date).single().content.subject)
        assertTrue(schedule(items, includeBase = false, dd = false).notificationLessonsOn(date).isEmpty())
        assertEquals("英語", schedule(items, day = DayType.B, includeBase = false, abEnabled = false)
            .notificationLessonsOn(date).single().content.subject)
    }
}
