package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QrScheduleCompatibilityTest {
    private val date = LocalDate.of(2026, 10, 3)
    private val settings = SettingsEntity(termStart = date, termEnd = date.plusMonths(5))
    private fun payload() = QrSharePayload(2026, setOf(QrShareSection.SECOND), 10, 1, scheduleTimes = settings.qrScheduleTimes())
    private fun lesson(id: Long = 42) = LessonEntity(id, 2026, TimetableTerm.SECOND, 1, 0, LessonMode.WEEKLY,
        "数学", "担当A", "教室", "", "", null, "", "", null)

    @Test fun rejectsEveryClockSettingMismatchWithoutModifyingReceiverSettings() {
        val original = settings.qrScheduleTimes()
        val variants = listOf(original.copy(periodsPerDay = 5), original.copy(periodDurationMin = 50),
            original.copy(breakBetweenPeriodsMin = 20), original.copy(lunchBreakMin = 50), original.copy(lunchAfterPeriod = 3),
            original.copy(firstPeriodStartHour = 9), original.copy(firstPeriodStartMinute = 0),
            original.copy(arrivalHour = 9), original.copy(arrivalMinute = 0),
            original.copy(departureHour = 17, departureMinute = 0))
        qrCheckScheduleCompatibility(payload(), settings)
        variants.forEach { times -> failure(QrShareFailure.SETTINGS) { qrCheckScheduleCompatibility(payload().copy(scheduleTimes = times), settings) } }
        failure(QrShareFailure.SETTINGS) { qrCheckScheduleCompatibility(payload().copy(secondTermStartDay = 2), settings) }
        assertEquals(original, settings.qrScheduleTimes())
    }

    @Test fun requiresClockInformationForLegacyTimetableButNotForPersonalDataOrExams() {
        failure(QrShareFailure.SETTINGS) { qrCheckScheduleCompatibility(payload().copy(formatVersion = 1, scheduleTimes = null), settings) }
        for (section in listOf(QrShareSection.TASKS, QrShareSection.PLANS, QrShareSection.NOTES, QrShareSection.EXAMS)) {
            qrCheckScheduleCompatibility(payload().copy(sections = setOf(section), scheduleTimes = null, secondTermStartDay = 2), settings)
        }
    }

    @Test fun ignoresNotificationDisplayAndCalendarSettingsAndDisabledTimeMinutes() {
        qrCheckScheduleCompatibility(payload(), settings.copy(lessonStartNotificationEnabled = true,
            addTasksToCalendar = true, showWeekdayOnDates = true, useDrawerNavigation = true))
        assertEquals(settings.qrScheduleTimes(), settings.copy(departureMinute = 0).qrScheduleTimes())
    }

    @Test fun resolvesPortableReferenceToReceiverIdAndIgnoresSenderId() {
        val source = lesson(999)
        val local = lesson(42)
        assertEquals(qrLessonReference(source), qrLessonReference(local))
        assertEquals(42L, qrResolveLessonId(qrLessonReference(source), mapOf(local.lessonKey() to local)))
        assertNull(qrResolveLessonId(null, emptyMap()))
    }

    @Test fun rejectsMissingOrChangedLinkedLessonAndWrongPartition() {
        val ref = qrLessonReference(lesson())
        failure(QrShareFailure.LESSON_REFERENCE) { qrResolveLessonId(ref, emptyMap()) }
        for (changed in listOf(lesson().copy(weeklySubject = "英語"), lesson().copy(weeklyTeacher = "担当B"),
            lesson().copy(weeklyLocation = "別教室"), lesson().copy(id = 0))) {
            failure(QrShareFailure.LESSON_REFERENCE) { qrResolveLessonId(ref, mapOf(changed.lessonKey() to changed)) }
        }
        val otherYear = lesson().copy(academicYear = 2025)
        failure(QrShareFailure.LESSON_REFERENCE) { qrResolveLessonId(ref, mapOf(otherYear.lessonKey() to otherYear)) }
        val otherTerm = lesson().copy(timetableTerm = TimetableTerm.FIRST)
        failure(QrShareFailure.LESSON_REFERENCE) { qrResolveLessonId(ref, mapOf(otherTerm.lessonKey() to otherTerm)) }
    }

    @Test fun handlesSeparatorCharactersAndNormalizesBlankLocationsInLessonSignature() {
        val source = lesson().copy(weeklySubject = "数学:実習", weeklyTeacher = "担当|A", weeklyLocation = null)
        assertEquals(qrLessonReference(source), qrLessonReference(source.copy(id = 7, weeklyLocation = " ")))
        assertNotEquals(qrLessonReference(source), qrLessonReference(source.copy(weeklySubject = "数学", weeklyTeacher = "実習:担当|A")))
    }

    @Test fun updatesOnlyExamLabelsInSelectedYearAndKeepsOtherDaysAndYears() {
        val oldExam = DayTypeEntity(date, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.MIDTERM)
        val regular = DayTypeEntity(date.plusDays(1), DayType.B, 2, DayType.A)
        val otherYear = oldExam.copy(date = date.minusYears(1))
        val next = oldExam.copy(date = date.plusDays(2), holidaySpecialLabel = HolidaySpecialLabel.FINAL)
        val updates = qrExamDayUpdates(listOf(oldExam, regular, otherYear), listOf(next), 2026)
        assertEquals(listOf(oldExam.copy(holidaySpecialLabel = null), next), updates)
        assertFalse(updates.any { it.date == regular.date || it.date == otherYear.date })
        assertEquals(listOf(next), qrExamDayUpdates(listOf(oldExam), listOf(next.copy(date = date)), 2026).map { it.copy(date = next.date) })
    }

    private fun failure(reason: QrShareFailure, action: () -> Unit) {
        try { action(); fail("Expected $reason") }
        catch (e: QrShareException) { assertEquals(reason, e.failure) }
    }
}
