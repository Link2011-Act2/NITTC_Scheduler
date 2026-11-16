package jp.linkserver.nittcsc.widget

import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.sktjf.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfWidgetLessonsTest {
    private val date = LocalDate.of(2026, 4, 6)
    private val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)
    private fun extra(id: String, day: Int = 1, cycle: SktjfCycle = SktjfCycle.A): SktjfExtraLessonEntity {
        val content = SktjfLessonContent(day, 1, cycle, id, listOf("先生"), emptyList(),
            SktjfConditions(setOf("DD"), emptySet()), SktjfLessonKind.NORMAL, null)
        val baseline = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf(id to content)),
            SktjfCalendarBaseline(2026, "S", "1-1", emptyMap()))
        return SktjfExtraLessonEntity(id, 2026, "S", "1-1", id, SktjfBaselineCodec.encode(baseline))
    }
    private fun data(day: DayTypeEntity = DayTypeEntity(date, DayType.A), extras: List<SktjfExtraLessonEntity> = listOf(extra("english")),
        mode: SchedulerUsageMode = SchedulerUsageMode.NITTC, row: LessonEntity? = null): WidgetData {
        val settings = SettingsEntity(termStart = date.minusDays(1), termEnd = date.plusDays(20))
        return WidgetData(date, settings, settings.basicClassSlots(), day.dayType, mapOf(date to day), mapOf(date to day.dayType),
            emptySet(), emptyMap(), row?.let { mapOf(it.lessonKey() to it) } ?: emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyList(),
            SktjfExtraLessonResolver.create(mode, listOf(profile), extras))
    }
    private val base = LessonEntity(academicYear = 2026, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1, slotIndex = 0,
        mode = LessonMode.WEEKLY, weeklySubject = "数学", weeklyTeacher = "先生", aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")

    @Test fun extraOnlyAndSimultaneousLessonsAreAvailableIndividually() {
        assertEquals("english", WidgetDataHelper.resolveLesson(data(), date, 0)?.subject)
        val widget = data(row = base, extras = listOf(extra("english"), extra("physics")))
        val lessons = WidgetDataHelper.resolveLessons(widget, date, 0)
        assertEquals(listOf("数学", "english", "physics"), lessons.map { it.subject })
        assertEquals("数学 +2", WidgetDataHelper.lessonTitle(lessons))
        val task = TaskEntity(subject = "english", title = "課題", dueDate = date, createdDate = date)
        assertTrue(WidgetDataHelper.hasTasksForDate(widget.copy(incompleteTasks = listOf(task)), date, lessons[1]))
    }

    @Test fun linkedExtraTaskUsesIdentityWhenSubjectsCollideOrChange() {
        val lessons = WidgetDataHelper.resolveLessons(data(row = base, extras = listOf(extra("english"), extra("physics"))), date, 0)
        val task = TaskEntity(subject = "数学", title = "課題", dueDate = date, createdDate = date, extraLessonId = "english")
        val widget = data().copy(incompleteTasks = listOf(task))
        assertFalse(WidgetDataHelper.hasTasksForDate(widget, date, lessons[0]))
        assertTrue(WidgetDataHelper.hasTasksForDate(widget, date, lessons[1]))
        assertFalse(WidgetDataHelper.hasTasksForDate(widget, date, lessons[2]))
    }

    @Test fun holidayProfileAndWeekendSubstitution() {
        assertTrue(WidgetDataHelper.resolveLessons(data(day = DayTypeEntity(date, DayType.HOLIDAY)), date, 0).isEmpty())
        assertNull(WidgetDataHelper.resolveLesson(data(mode = SchedulerUsageMode.GENERIC), date, 0))
        val sunday = date.minusDays(1)
        val widget = data(extras = listOf(extra("english", day = 1, cycle = SktjfCycle.B)))
            .copy(dayTypeEntities = mapOf(sunday to DayTypeEntity(sunday, DayType.A, 1, DayType.B)))
        assertEquals("english", WidgetDataHelper.resolveLesson(widget, sunday, 0)?.subject)
    }

    @Test fun examScheduleReplacesRegularLessons() {
        val widget = data(row = base).copy(dayTypeEntities = mapOf(date to DayTypeEntity(date, DayType.HOLIDAY,
            holidaySpecialLabel = HolidaySpecialLabel.MIDTERM)), examDaySchedules = mapOf(date to ExamDayScheduleEntity(date)),
            examLessons = mapOf((date to 0) to ExamLessonEntity(date, 0, 9, 0, 10, 0, "試験")))
        assertEquals(listOf("試験"), WidgetDataHelper.resolveLessons(widget, date, 0).map { it.subject })
    }
}
