package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EffectiveSchoolScheduleTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val settings = SettingsEntity(termStart = monday.minusDays(10), termEnd = monday.plusDays(30))
    private fun lesson(slot: Int, day: Int = 1) = LessonEntity(
        academicYear = 2026, timetableTerm = TimetableTerm.SECOND, dayOfWeek = day, slotIndex = slot,
        mode = LessonMode.ALTERNATING, weeklySubject = "", weeklyTeacher = "",
        aSubject = "数学", aTeacher = "A先生", bSubject = "英語", bTeacher = "B先生")

    private fun schedule(
        settings: SettingsEntity = this.settings,
        days: Map<LocalDate, DayTypeEntity> = mapOf(monday to DayTypeEntity(monday, DayType.B)),
        changed: Map<Pair<LocalDate, Int>, ChangedLessonEntity> = emptyMap(),
        cancelled: Set<Pair<LocalDate, Int>> = emptySet(),
        exams: List<ExamLessonEntity> = emptyList()
    ) = EffectiveSchoolSchedule(settings, days, listOf(lesson(0), lesson(1), lesson(0, 2)), changed,
        cancelled, if (exams.isEmpty()) emptyMap() else mapOf(monday to ExamDayScheduleEntity(monday, arrivalHour = -1, arrivalMinute = -1)),
        exams.groupBy { it.date })

    @Test fun cardAndArrivalReflectBTypeCancellationAndChange() {
        val resolved = schedule(cancelled = setOf(monday to 0), changed = mapOf(
            (monday to 1) to ChangedLessonEntity(date = monday, slotIndex = 1, subject = "物理", teacher = "C先生")))
        val candidates = resolved.lessonsOn(monday)
        assertEquals(listOf("物理"), candidates.map { it.content.subject })
        assertEquals(LocalTime.of(10, 10), resolved.arrivalTime(monday))
        assertEquals("物理", findTodaysUpcomingLesson(candidates, monday.atTime(9, 0), monday)?.candidate?.content?.subject)
        assertNull(findTodaysUpcomingLesson(candidates, candidates.last().endsAt, monday))
        assertEquals("英語", schedule().lessonsOn(monday).first().content.subject)
    }

    @Test fun examsReplaceRegularSlotsAndUseExamStart() {
        val exam = ExamLessonEntity(monday, 0, 9, 30, 10, 20, "試験")
        val resolved = schedule(days = mapOf(monday to DayTypeEntity(monday, DayType.HOLIDAY,
            holidaySpecialLabel = HolidaySpecialLabel.MIDTERM)), exams = listOf(exam))
        assertEquals(LocalTime.of(9, 20), resolved.arrivalTime(monday))
        val selection = findTodaysUpcomingLesson(resolved.lessonsOn(monday), monday.atTime(9, 45), monday)
        assertEquals("試験", selection?.candidate?.content?.subject)
        assertEquals(UpcomingLessonPhase.ONGOING, selection?.phase)
        assertNull(findTodaysUpcomingLesson(resolved.lessonsOn(monday), monday.atTime(10, 20), monday))
    }

    @Test fun holidayAndOutsideTermHaveNoLessonsButHaveFallbackTimes() {
        val resolved = schedule(days = mapOf(monday to DayTypeEntity(monday, DayType.HOLIDAY)))
        assertTrue(resolved.lessonsOn(monday).isEmpty())
        assertTrue(resolved.lessonsOn(monday.plusMonths(3)).isEmpty())
        assertEquals(LocalTime.of(8, 30), resolved.arrivalTime(monday))
        assertEquals(LocalTime.of(16, 0), settings.departureTime())
    }

    @Test fun manualEightThirtyIsHonoredEvenWhenFirstClassIsLater() {
        val manual = settings.copy(arrivalTimeAuto = false, arrivalHour = 8, arrivalMinute = 30)
        assertEquals(LocalTime.of(8, 30), schedule(manual, cancelled = setOf(monday to 0)).arrivalTime(monday))
        assertEquals(LocalTime.of(10, 10), schedule(cancelled = setOf(monday to 0)).arrivalTime(monday))
    }

    @Test fun weekendLessonTransferIsSupported() {
        val sunday = monday.minusDays(1)
        val resolved = schedule(days = mapOf(sunday to DayTypeEntity(sunday, DayType.A,
            overrideLessonDayOfWeek = 2, overrideLessonDayType = DayType.B)))
        assertEquals("英語", resolved.lessonsOn(sunday).single().content.subject)
    }
}
