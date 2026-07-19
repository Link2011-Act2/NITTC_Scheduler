package jp.linkserver.nittcsc.logic

import java.time.LocalTime

data class TimeRangeDraft(
    val startHour: String,
    val startMinute: String,
    val endHour: String,
    val endMinute: String
)

data class AdvancedTimeEditorDraft(
    val periodRanges: List<TimeRangeDraft>,
    val lunchRange: TimeRangeDraft
)

data class DerivedScheduleSettings(
    val periodsPerDay: Int,
    val periodDurationMin: Int,
    val breakBetweenPeriodsMin: Int,
    val lunchBreakMin: Int,
    val lunchAfterPeriod: Int,
    val firstPeriodStartHour: Int,
    val firstPeriodStartMinute: Int
)

enum class AdvancedTimeValidationError {
    INVALID_PERIOD_COUNT,
    INVALID_PERIOD_TIME,
    INVALID_LUNCH_TIME,
    PERIOD_END_BEFORE_START,
    LUNCH_END_BEFORE_START,
    PERIOD_DURATION_MISMATCH,
    PERIODS_OVERLAP,
    BREAK_DURATION_MISMATCH,
    LUNCH_FIRST_NOT_CONNECTED,
    LUNCH_LAST_NOT_CONNECTED,
    LUNCH_MIDDLE_NOT_CONNECTED
}

data class AdvancedTimeValidation(
    val error: AdvancedTimeValidationError? = null,
    val derivedSettings: DerivedScheduleSettings? = null
)

fun buildAdvancedTimeEditorDraft(
    periodsPerDay: Int,
    periodDurationMin: Int,
    breakBetweenPeriodsMin: Int,
    lunchBreakMin: Int,
    lunchAfterPeriod: Int,
    firstPeriodStartHour: Int,
    firstPeriodStartMinute: Int,
    periodLabelStyle: PeriodLabelStyle
): AdvancedTimeEditorDraft {
    val slots = generateClassSlots(
        periodsPerDay = periodsPerDay,
        periodDurationMin = periodDurationMin,
        breakBetweenPeriodsMin = breakBetweenPeriodsMin,
        lunchBreakMin = lunchBreakMin,
        firstPeriodStartHour = firstPeriodStartHour,
        firstPeriodStartMinute = firstPeriodStartMinute,
        periodLabelStyle = periodLabelStyle,
        lunchAfterPeriod = lunchAfterPeriod
    )
    val periodRanges = slots.map { slot ->
        TimeRangeDraft(
            startHour = slot.start.hour.toString(),
            startMinute = slot.start.minute.toString().padStart(2, '0'),
            endHour = slot.end.hour.toString(),
            endMinute = slot.end.minute.toString().padStart(2, '0')
        )
    }
    val lunchAfter = lunchAfterPeriod.coerceIn(0, periodsPerDay)
    val lunchStartMin = when {
        lunchAfter <= 0 -> {
            val firstStart = slots.firstOrNull()?.let { it.start.hour * 60 + it.start.minute }
                ?: (firstPeriodStartHour * 60 + firstPeriodStartMinute)
            (firstStart - lunchBreakMin).coerceAtLeast(0)
        }
        lunchAfter >= slots.size -> {
            val lastEnd = slots.lastOrNull()?.let { it.end.hour * 60 + it.end.minute }
                ?: (firstPeriodStartHour * 60 + firstPeriodStartMinute + periodDurationMin)
            lastEnd
        }
        else -> slots[lunchAfter - 1].end.hour * 60 + slots[lunchAfter - 1].end.minute
    }
    val lunchEndMin = when {
        lunchAfter <= 0 -> slots.firstOrNull()?.let { it.start.hour * 60 + it.start.minute }
            ?: (lunchStartMin + lunchBreakMin)
        lunchAfter >= slots.size -> lunchStartMin + lunchBreakMin
        else -> slots[lunchAfter].start.hour * 60 + slots[lunchAfter].start.minute
    }
    return AdvancedTimeEditorDraft(
        periodRanges = periodRanges,
        lunchRange = timeRangeDraftFromMinutes(lunchStartMin, lunchEndMin)
    )
}

fun resizeTimeRangeDrafts(
    current: List<TimeRangeDraft>,
    targetCount: Int,
    defaultPeriodDurationMin: Int,
    defaultBreakDurationMin: Int,
    fallbackStartHour: Int,
    fallbackStartMinute: Int
): List<TimeRangeDraft> {
    if (targetCount <= 0) return emptyList()
    if (current.size == targetCount) return current
    if (current.size > targetCount) return current.take(targetCount)

    val result = current.toMutableList()
    while (result.size < targetCount) {
        val previous = result.lastOrNull()
        val nextStartMin = previous?.let {
            parseTimeRangeDraft(it)?.endMin?.plus(defaultBreakDurationMin)
        } ?: (fallbackStartHour * 60 + fallbackStartMinute)
        result += timeRangeDraftFromMinutes(nextStartMin, nextStartMin + defaultPeriodDurationMin)
    }
    return result
}

fun validateAdvancedTimeEditor(
    periodCountText: String,
    periodRanges: List<TimeRangeDraft>,
    lunchRange: TimeRangeDraft,
    lunchAfterPeriod: Int,
    fallbackBreakDurationMin: Int
): AdvancedTimeValidation {
    val periodCount = periodCountText.toIntOrNull()?.coerceIn(1, 12)
        ?: return AdvancedTimeValidation(error = AdvancedTimeValidationError.INVALID_PERIOD_COUNT)
    if (periodRanges.size != periodCount) return AdvancedTimeValidation()

    val parsedPeriods = periodRanges.map {
        parseTimeRangeDraft(it)
            ?: return AdvancedTimeValidation(error = AdvancedTimeValidationError.INVALID_PERIOD_TIME)
    }
    val parsedLunch = parseTimeRangeDraft(lunchRange)
        ?: return AdvancedTimeValidation(error = AdvancedTimeValidationError.INVALID_LUNCH_TIME)

    if (parsedPeriods.any { it.endMin <= it.startMin }) {
        return AdvancedTimeValidation(error = AdvancedTimeValidationError.PERIOD_END_BEFORE_START)
    }
    if (parsedLunch.endMin <= parsedLunch.startMin) {
        return AdvancedTimeValidation(error = AdvancedTimeValidationError.LUNCH_END_BEFORE_START)
    }

    val lunchAfter = lunchAfterPeriod.coerceIn(0, periodCount)
    val periodDurations = parsedPeriods.map { it.endMin - it.startMin }
    if (periodDurations.distinct().size > 1) {
        return AdvancedTimeValidation(error = AdvancedTimeValidationError.PERIOD_DURATION_MISMATCH)
    }

    val regularBreaks = mutableListOf<Int>()
    for (index in 0 until parsedPeriods.lastIndex) {
        if (lunchAfter == index + 1) continue
        val gap = parsedPeriods[index + 1].startMin - parsedPeriods[index].endMin
        if (gap < 0) {
            return AdvancedTimeValidation(error = AdvancedTimeValidationError.PERIODS_OVERLAP)
        }
        regularBreaks += gap
    }
    if (regularBreaks.distinct().size > 1) {
        return AdvancedTimeValidation(error = AdvancedTimeValidationError.BREAK_DURATION_MISMATCH)
    }

    when {
        lunchAfter == 0 && parsedLunch.endMin != parsedPeriods.first().startMin -> {
            return AdvancedTimeValidation(error = AdvancedTimeValidationError.LUNCH_FIRST_NOT_CONNECTED)
        }
        lunchAfter == periodCount && parsedLunch.startMin != parsedPeriods.last().endMin -> {
            return AdvancedTimeValidation(error = AdvancedTimeValidationError.LUNCH_LAST_NOT_CONNECTED)
        }
        lunchAfter in 1 until periodCount -> {
            val before = parsedPeriods[lunchAfter - 1]
            val after = parsedPeriods[lunchAfter]
            if (before.endMin != parsedLunch.startMin || parsedLunch.endMin != after.startMin) {
                return AdvancedTimeValidation(error = AdvancedTimeValidationError.LUNCH_MIDDLE_NOT_CONNECTED)
            }
        }
    }

    val firstPeriodStart = parsedPeriods.first().startMin
    return AdvancedTimeValidation(
        derivedSettings = DerivedScheduleSettings(
            periodsPerDay = periodCount,
            periodDurationMin = periodDurations.first(),
            breakBetweenPeriodsMin = regularBreaks.firstOrNull() ?: fallbackBreakDurationMin,
            lunchBreakMin = parsedLunch.endMin - parsedLunch.startMin,
            lunchAfterPeriod = lunchAfter,
            firstPeriodStartHour = firstPeriodStart / 60,
            firstPeriodStartMinute = firstPeriodStart % 60
        )
    )
}

private data class TimeRangeValue(val startMin: Int, val endMin: Int)

private fun parseTimeRangeDraft(draft: TimeRangeDraft): TimeRangeValue? {
    val start = parseRequiredTime(draft.startHour, draft.startMinute) ?: return null
    val end = parseRequiredTime(draft.endHour, draft.endMinute) ?: return null
    return TimeRangeValue(startMin = start, endMin = end)
}

private fun parseRequiredTime(hour: String, minute: String): Int? {
    val parsedHour = hour.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val parsedMinute = minute.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return parsedHour * 60 + parsedMinute
}

private fun timeRangeDraftFromMinutes(startMin: Int, endMin: Int): TimeRangeDraft {
    val safeStart = startMin.coerceIn(0, 23 * 60 + 59)
    val safeEnd = endMin.coerceIn(0, 23 * 60 + 59)
    val start = LocalTime.of(safeStart / 60, safeStart % 60)
    val end = LocalTime.of(safeEnd / 60, safeEnd % 60)
    return TimeRangeDraft(
        startHour = start.hour.toString(),
        startMinute = start.minute.toString().padStart(2, '0'),
        endHour = end.hour.toString(),
        endMinute = end.minute.toString().padStart(2, '0')
    )
}
