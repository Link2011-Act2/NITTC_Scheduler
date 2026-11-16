package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.NittcYearOutcome
import jp.linkserver.nittcsc.data.NittcYearTransitionEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NittcAffiliationReminderTest {
    private val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)

    @Test fun reminderTracksAcademicRatherThanCalendarYear() {
        assertNull(missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile), LocalDate.of(2027, 3, 31)))
        assertEquals(2027, missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile), LocalDate.of(2027, 4, 1)))
        val next = profile.copy(academicYear = 2027, grade = 2, classId = "2M")
        assertNull(missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile, next), LocalDate.of(2027, 4, 1)))
    }

    @Test fun genericAndUndecidedModesDoNotShowSchoolReminder() {
        assertNull(missingNittcAffiliation(SchedulerUsageMode.GENERIC, emptyList(), LocalDate.of(2027, 4, 1)))
        assertNull(missingNittcAffiliation(null, emptyList(), LocalDate.of(2027, 4, 1)))
    }

    @Test fun upcomingReminderRequiresAnnouncedReleaseAndNoSavedProfile() {
        val date = LocalDate.of(2027, 2, 1)
        assertNull(missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile), date))
        assertEquals(2027, missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile), date, true))
        val next = profile.copy(academicYear = 2027, grade = 2, classId = "2M")
        assertNull(missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile, next), date, true))
        assertEquals(2026, missingNittcAffiliation(SchedulerUsageMode.NITTC, emptyList(), date, true))
    }

    @Test fun deferralKeepsReminderGraduationSuppressesItAndLaterProfileRestoresIt() {
        val date = LocalDate.of(2027, 4, 1)
        val deferred = NittcYearTransitionEntity(2027, NittcYearOutcome.DEFERRED.name)
        val graduated = deferred.copy(outcome = NittcYearOutcome.GRADUATED.name)
        assertEquals(2027, missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile), LocalDate.of(2027, 2, 1), transitions = listOf(deferred)))
        assertEquals(2027, missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile), date, transitions = listOf(deferred)))
        assertNull(missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile), date, transitions = listOf(graduated)))
        assertNull(missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile), LocalDate.of(2028, 4, 1), transitions = listOf(graduated)))
        val advanced = NittcProfileEntity(2028, "ADVANCED", 1, "AC-A", false, false)
        assertEquals(2029, missingNittcAffiliation(SchedulerUsageMode.NITTC, listOf(profile, advanced), LocalDate.of(2029, 4, 1), transitions = listOf(graduated)))
    }
}
