package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.TimetableTerm
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfNotificationScheduleTest {
    private val date = LocalDate.of(2026, 11, 9)
    private val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)
    private val content = SktjfLessonContent(2, 1, SktjfCycle.A, "補講", emptyList(), emptyList(),
        SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.MAKEUP, null)
    private val baseline = SktjfStoredBaseline(SktjfBaseline(2026, "F", "1-1", mapOf("source" to content)),
        SktjfCalendarBaseline(2026, "F", "1-1", emptyMap()))
    private val source = SktjfSourceStateEntity(2026, "F", "1-1", "2026.F.0", SktjfBaselineCodec.encode(baseline),
        false, SktjfSourceLinks.encode(listOf(SktjfSourceLink("source", 2, 1, SktjfCycle.A))))
    private val row = LessonEntity(academicYear = 2026, timetableTerm = TimetableTerm.SECOND, dayOfWeek = 2,
        slotIndex = 0, mode = LessonMode.ALTERNATING, weeklySubject = "", weeklyTeacher = "",
        aSubject = "補講", aTeacher = "", bSubject = "数学", bTeacher = "")

    private fun schedule(mode: SchedulerUsageMode = SchedulerUsageMode.NITTC, state: SktjfSourceStateEntity = source,
        lesson: LessonEntity = row, change: ChangedLessonEntity? = null, type: DayType = DayType.A): EffectiveSchoolSchedule {
        val settings = SettingsEntity(termStart = date.minusDays(1), termEnd = date.plusDays(1), enableAbTimetable = true)
        return EffectiveSchoolSchedule(settings, mapOf(date to DayTypeEntity(date, type, overrideLessonDayOfWeek = 2)),
            listOf(lesson), change?.let { mapOf((date to 0) to it) } ?: emptyMap(), emptySet(), emptyMap(), emptyMap(),
            SktjfNotificationOrigins.create(mode, listOf(profile), listOf(state)), mode)
    }

    @Test fun linkedSpecialIsSuppressedButStillDisplayed() {
        val schedule = schedule()
        assertEquals("補講", schedule.lessonsOn(date).single().content.subject)
        assertTrue(schedule.notificationLessonsOn(date).isEmpty())
        assertEquals("数学", schedule(type = DayType.B).notificationLessonsOn(date).single().content.subject)
    }

    @Test fun manualChangesAndUntrustedOriginsAreNotSuppressed() {
        val changed = ChangedLessonEntity(date, 0, "物理", "")
        assertEquals("物理", schedule(change = changed).notificationLessonsOn(date).single().content.subject)
        assertEquals("補講", schedule(mode = SchedulerUsageMode.GENERIC).notificationLessonsOn(date).single().content.subject)
        assertEquals("補講", schedule(state = source.copy(requiresRelinking = true)).notificationLessonsOn(date).single().content.subject)
        assertEquals("補講", schedule(state = source.copy(linksJson = "invalid")).notificationLessonsOn(date).single().content.subject)
        assertEquals("補講", schedule(lesson = row.copy(mode = LessonMode.WEEKLY, weeklySubject = "補講")).notificationLessonsOn(date).single().content.subject)
    }

    @Test fun extensionIsSuppressedButExamWithSameNameIsNot() {
        val extension = content.copy(subject = "延長枠", kind = SktjfLessonKind.EXTENSION)
        val extensionState = source.copy(baselineJson = SktjfBaselineCodec.encode(
            baseline.copy(lessons = baseline.lessons.copy(lessons = mapOf("source" to extension)))))
        assertTrue(schedule(state = extensionState, lesson = row.copy(aSubject = "延長枠")).notificationLessonsOn(date).isEmpty())
        val settings = SettingsEntity(termStart = date.minusDays(1), termEnd = date.plusDays(1), enableExamTimetable = true)
        val exam = ExamLessonEntity(date, 0, 9, 0, 10, 0, "補講")
        val examSchedule = EffectiveSchoolSchedule(settings,
            mapOf(date to DayTypeEntity(date, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.MIDTERM)),
            listOf(row), emptyMap(), emptySet(), mapOf(date to ExamDayScheduleEntity(date)), mapOf(date to listOf(exam)),
            SktjfNotificationOrigins.create(SchedulerUsageMode.NITTC, listOf(profile), listOf(source)), SchedulerUsageMode.NITTC)
        assertEquals("補講", examSchedule.notificationLessonsOn(date).single().content.subject)
    }
}
