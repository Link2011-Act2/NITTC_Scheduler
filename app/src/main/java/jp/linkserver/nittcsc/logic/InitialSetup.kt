package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.SettingsEntity

/** 入力途中は文字列のまま保持し、不正な値を黙って補正しない。 */
data class SetupTimeDraft(
    val count: String = "4",
    val start: String = "08:40",
    val duration: String = "90",
    val breakMinutes: String = "10",
    val lunchMinutes: String = "60",
    val lunchAfter: String = "2"
) {
    fun values(): SetupTimeValues? {
        val countValue = count.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(start) ?: return null
        val hour = match.groupValues[1].toInt().takeIf { it in 0..23 } ?: return null
        val minute = match.groupValues[2].toInt().takeIf { it in 0..59 } ?: return null
        val durationValue = duration.toIntOrNull()?.takeIf { it in 10..180 } ?: return null
        val breakValue = breakMinutes.toIntOrNull()?.takeIf { it in 0..120 } ?: return null
        val lunchValue = lunchMinutes.toIntOrNull()?.takeIf { it in 0..180 } ?: return null
        val lunchPosition = lunchAfter.toIntOrNull()?.takeIf { it in 0..countValue } ?: return null
        val betweenCount = countValue - 1
        val hasLunch = lunchValue > 0 && lunchPosition in 1 until countValue
        val end = hour * 60 + minute + countValue * durationValue +
            (betweenCount - if (hasLunch) 1 else 0) * breakValue +
            if (hasLunch) lunchValue else 0
        if (end >= 24 * 60) return null
        return SetupTimeValues(countValue, hour, minute, durationValue, breakValue,
            lunchValue, if (hasLunch) lunchPosition else 0)
    }
}

data class SetupTimeValues(
    val count: Int, val hour: Int, val minute: Int, val duration: Int,
    val breakMinutes: Int, val lunchMinutes: Int, val lunchAfter: Int
) {
    fun slots(style: PeriodLabelStyle) = generateClassSlots(
        count, duration, breakMinutes, lunchMinutes, hour, minute, style, lunchAfter
    )
}

enum class SetupExamChoice { SEPARATE, SAME, LATER }

data class InitialSetupDraft(
    val semester: Boolean = false,
    val ab: Boolean = false,
    val regular: SetupTimeDraft = SetupTimeDraft(),
    val exam: SetupTimeDraft = SetupTimeDraft("4", "08:50", "50", "20", "50", "3"),
    val examChoice: SetupExamChoice = SetupExamChoice.LATER,
    val labelStyle: PeriodLabelStyle = PeriodLabelStyle.KOMA,
    val tsuruokaPreset: Boolean = false
) {
    fun applyTo(settings: SettingsEntity): SettingsEntity {
        val time = requireNotNull(regular.values())
        val testTime = if (examChoice == SetupExamChoice.SEPARATE) requireNotNull(exam.values()) else time
        return settings.copy(
            initialSetupCompleted = true,
            enableSemesterTimetables = semester, enableAbTimetable = ab,
            enableExamTimetable = examChoice == SetupExamChoice.SEPARATE,
            periodsPerDay = time.count, firstPeriodStartHour = time.hour,
            firstPeriodStartMinute = time.minute, periodDurationMin = time.duration,
            breakBetweenPeriodsMin = time.breakMinutes, lunchBreakMin = time.lunchMinutes,
            lunchAfterPeriod = time.lunchAfter, periodLabelStyle = labelStyle,
            useKosenMode = labelStyle == PeriodLabelStyle.PAIR_KOSHI,
            arrivalHour = if (tsuruokaPreset) 8 else -1,
            arrivalMinute = if (tsuruokaPreset) 30 else -1,
            departureHour = -1, departureMinute = -1,
            examPeriodsPerDay = testTime.count, examFirstPeriodStartHour = testTime.hour,
            examFirstPeriodStartMinute = testTime.minute, examPeriodDurationMin = testTime.duration,
            examBreakBetweenPeriodsMin = testTime.breakMinutes,
            examLunchBreakMin = testTime.lunchMinutes, examLunchAfterPeriod = testTime.lunchAfter,
            examArrivalHour = if (tsuruokaPreset) 8 else testTime.hour,
            examArrivalMinute = if (tsuruokaPreset) 30 else testTime.minute
        )
    }

    companion object {
        fun tsuruoka() = InitialSetupDraft(semester = true, ab = true,
            examChoice = SetupExamChoice.SEPARATE, labelStyle = PeriodLabelStyle.PAIR_KOSHI,
            tsuruokaPreset = true)
    }
}

/** 表示・計算にだけ適用する。保存済みB日やB授業は消さず、再有効化で復元する。 */
fun DayTypeEntity.forTimetable(abEnabled: Boolean): DayTypeEntity = if (abEnabled) this else copy(
    dayType = if (dayType == DayType.B) DayType.A else dayType,
    overrideLessonDayType = overrideLessonDayType?.let { if (it == DayType.B) DayType.A else it }
)

fun LessonEntity.forTimetable(abEnabled: Boolean): LessonEntity =
    if (abEnabled || mode != LessonMode.ALTERNATING) this else copy(
        mode = LessonMode.WEEKLY, weeklySubject = aSubject, weeklyTeacher = aTeacher,
        weeklyLocation = aLocation
    )

fun importedExamTimetableEnabled(exportVersion: Int, storedValue: Boolean): Boolean =
    exportVersion < 15 || storedValue
