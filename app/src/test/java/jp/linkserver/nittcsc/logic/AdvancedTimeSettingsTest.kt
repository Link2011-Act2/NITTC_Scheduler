package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvancedTimeSettingsTest {
    @Test
    fun generatedDraft_roundTripsToDerivedSettings() {
        val draft = buildAdvancedTimeEditorDraft(
            periodsPerDay = 4,
            periodDurationMin = 50,
            breakBetweenPeriodsMin = 20,
            lunchBreakMin = 50,
            lunchAfterPeriod = 3,
            firstPeriodStartHour = 8,
            firstPeriodStartMinute = 50,
            useKosenMode = false
        )

        val result = validateAdvancedTimeEditor(
            periodCountText = "4",
            periodRanges = draft.periodRanges,
            lunchRange = draft.lunchRange,
            lunchAfterPeriod = 3,
            fallbackBreakDurationMin = 20
        )

        assertNull(result.error)
        assertEquals(
            DerivedScheduleSettings(
                periodsPerDay = 4,
                periodDurationMin = 50,
                breakBetweenPeriodsMin = 20,
                lunchBreakMin = 50,
                lunchAfterPeriod = 3,
                firstPeriodStartHour = 8,
                firstPeriodStartMinute = 50
            ),
            result.derivedSettings
        )
    }

    @Test
    fun differentPeriodDurations_areRejected() {
        val draft = buildAdvancedTimeEditorDraft(
            periodsPerDay = 2,
            periodDurationMin = 50,
            breakBetweenPeriodsMin = 10,
            lunchBreakMin = 40,
            lunchAfterPeriod = 2,
            firstPeriodStartHour = 9,
            firstPeriodStartMinute = 0,
            useKosenMode = false
        )
        val changedPeriods = draft.periodRanges.toMutableList().also { periods ->
            periods[1] = periods[1].copy(endMinute = "55")
        }

        val result = validateAdvancedTimeEditor(
            periodCountText = "2",
            periodRanges = changedPeriods,
            lunchRange = draft.lunchRange,
            lunchAfterPeriod = 2,
            fallbackBreakDurationMin = 10
        )

        assertEquals(AdvancedTimeValidationError.PERIOD_DURATION_MISMATCH, result.error)
        assertNull(result.derivedSettings)
    }

    @Test
    fun disconnectedLunchRange_isRejected() {
        val draft = buildAdvancedTimeEditorDraft(
            periodsPerDay = 4,
            periodDurationMin = 50,
            breakBetweenPeriodsMin = 20,
            lunchBreakMin = 50,
            lunchAfterPeriod = 3,
            firstPeriodStartHour = 8,
            firstPeriodStartMinute = 50,
            useKosenMode = false
        )

        val result = validateAdvancedTimeEditor(
            periodCountText = "4",
            periodRanges = draft.periodRanges,
            lunchRange = draft.lunchRange.copy(startMinute = "05"),
            lunchAfterPeriod = 3,
            fallbackBreakDurationMin = 20
        )

        assertEquals(AdvancedTimeValidationError.LUNCH_MIDDLE_NOT_CONNECTED, result.error)
    }

    @Test
    fun resizeAppendsPeriodsFromPreviousEnd() {
        val original = listOf(
            TimeRangeDraft("8", "50", "9", "40")
        )

        val resized = resizeTimeRangeDrafts(
            current = original,
            targetCount = 3,
            defaultPeriodDurationMin = 50,
            defaultBreakDurationMin = 20,
            fallbackStartHour = 8,
            fallbackStartMinute = 50
        )

        assertEquals(3, resized.size)
        assertEquals(TimeRangeDraft("10", "00", "10", "50"), resized[1])
        assertEquals(TimeRangeDraft("11", "10", "12", "00"), resized[2])
    }

    @Test
    fun invalidPeriodCount_hasSpecificError() {
        val result = validateAdvancedTimeEditor(
            periodCountText = "",
            periodRanges = emptyList(),
            lunchRange = TimeRangeDraft("12", "00", "13", "00"),
            lunchAfterPeriod = 0,
            fallbackBreakDurationMin = 10
        )

        assertEquals(AdvancedTimeValidationError.INVALID_PERIOD_COUNT, result.error)
    }
}
