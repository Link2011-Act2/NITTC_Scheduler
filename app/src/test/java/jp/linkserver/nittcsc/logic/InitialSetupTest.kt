package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.SettingsEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class InitialSetupTest {
    private val settings = SettingsEntity(
        termStart = LocalDate.of(2026, 4, 1), termEnd = LocalDate.of(2027, 3, 31),
        initialSetupCompleted = false
    )

    @Test
    fun tsuruokaPresetMatchesExistingSchoolDefaults() {
        assertEquals(settings.copy(initialSetupCompleted = true), InitialSetupDraft.tsuruoka().applyTo(settings))
    }

    @Test
    fun semesterAndAbCanBeSelectedIndependently() {
        for (semester in listOf(false, true)) for (ab in listOf(false, true)) {
            val result = InitialSetupDraft(semester = semester, ab = ab).applyTo(settings)
            assertEquals(semester, result.enableSemesterTimetables)
            assertEquals(ab, result.enableAbTimetable)
            assertTrue(result.initialSetupCompleted)
        }
    }

    @Test
    fun sameAndDeferredExamsUseRegularTimesWithoutEnablingSeparateTimetable() {
        for (choice in listOf(SetupExamChoice.SAME, SetupExamChoice.LATER)) {
            val result = InitialSetupDraft(regular = SetupTimeDraft(start = "09:10"),
                exam = SetupTimeDraft(count = ""), examChoice = choice).applyTo(settings)
            assertFalse(result.enableExamTimetable)
            assertEquals(9, result.examFirstPeriodStartHour)
            assertEquals(10, result.examFirstPeriodStartMinute)
            assertEquals(result.periodDurationMin, result.examPeriodDurationMin)
            assertEquals(-1, result.arrivalHour)
        }
    }

    @Test
    fun invalidInputsAndCrossMidnightSchedulesCannotBeSaved() {
        val invalid = listOf(
            SetupTimeDraft(count = ""), SetupTimeDraft(count = "13"), SetupTimeDraft(start = "24:00"),
            SetupTimeDraft(start = "08:60"), SetupTimeDraft(start = "08:"),
            SetupTimeDraft(duration = "0"), SetupTimeDraft(breakMinutes = "121"),
            SetupTimeDraft(lunchMinutes = "181"), SetupTimeDraft(lunchAfter = "5"),
            SetupTimeDraft(count = "12", duration = "180"),
            SetupTimeDraft(count = "1", start = "23:10", duration = "50", lunchAfter = "0")
        )
        invalid.forEach { assertNull(it.toString(), it.values()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSeparateExamBlocksCompletion() {
        InitialSetupDraft(examChoice = SetupExamChoice.SEPARATE, exam = SetupTimeDraft(start = "invalid"))
            .applyTo(settings)
    }

    @Test
    fun noLunchUsesTheNormalBreakAndPreviewMatchesSavedTimes() {
        val draft = SetupTimeDraft(count = "3", start = "08:00", duration = "50",
            breakMinutes = "10", lunchMinutes = "0", lunchAfter = "2")
        val values = requireNotNull(draft.values())
        assertEquals(0, values.lunchAfter)
        assertEquals(LocalTime.of(10, 50), values.slots(PeriodLabelStyle.KOMA).last().end)
        assertEquals(0, InitialSetupDraft(regular = draft).applyTo(settings).lunchAfterPeriod)
    }

    @Test
    fun lunchReplacesOneBreakInsteadOfAddingAnExtraBreak() {
        val values = requireNotNull(SetupTimeDraft(count = "3", start = "08:00", duration = "50",
            breakMinutes = "10", lunchMinutes = "45", lunchAfter = "2").values())
        val slots = values.slots(PeriodLabelStyle.SINGLE_KOSHI)
        assertEquals(LocalTime.of(10, 35), slots[2].start)
        assertEquals(LocalTime.of(11, 25), slots[2].end)
    }

    @Test
    fun disablingAbPreservesTheStoredBSubjectAndUsesAInViews() {
        val lesson = LessonEntity(academicYear = 2026, dayOfWeek = 1, slotIndex = 0,
            mode = LessonMode.ALTERNATING, weeklySubject = "", weeklyTeacher = "",
            aSubject = "数学", aTeacher = "A先生", bSubject = "英語", bTeacher = "B先生")
        val effective = lesson.forTimetable(false)
        assertEquals(LessonMode.WEEKLY, effective.mode)
        assertEquals("数学", effective.weeklySubject)
        assertEquals("英語", effective.bSubject)
        assertEquals(LessonMode.ALTERNATING, lesson.mode)
        assertSame(lesson, lesson.forTimetable(true))
    }

    @Test
    fun disablingAbDoesNotLoseHolidayLabelsOrTransferredWeekdays() {
        val date = settings.termStart
        val b = DayTypeEntity(date, DayType.B, overrideLessonDayOfWeek = 3, overrideLessonDayType = DayType.B)
        val effective = b.forTimetable(false)
        assertEquals(DayType.A, effective.dayType)
        assertEquals(DayType.A, effective.overrideLessonDayType)
        assertEquals(3, effective.overrideLessonDayOfWeek)
        val holiday = DayTypeEntity(date, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.MIDTERM)
        assertEquals(holiday, holiday.forTimetable(false))
        assertEquals(DayType.B, b.dayType)
    }

    @Test
    fun legacyBackupsKeepThePreviouslyAlwaysEnabledExamFeature() {
        assertTrue(importedExamTimetableEnabled(14, false))
        assertTrue(importedExamTimetableEnabled(1, false))
        assertFalse(importedExamTimetableEnabled(15, false))
        assertTrue(importedExamTimetableEnabled(15, true))
    }
}
