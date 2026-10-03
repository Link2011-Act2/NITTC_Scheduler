package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.TimetableTerm

/** 時間割の照合に使う時刻設定。受信側の設定へは書き込まない。 */
data class QrScheduleTimes(
    val periodsPerDay: Int, val periodDurationMin: Int, val breakBetweenPeriodsMin: Int,
    val lunchBreakMin: Int, val lunchAfterPeriod: Int,
    val firstPeriodStartHour: Int, val firstPeriodStartMinute: Int,
    val arrivalHour: Int, val arrivalMinute: Int, val departureHour: Int, val departureMinute: Int
)

internal fun SettingsEntity.qrScheduleTimes() = QrScheduleTimes(
    periodsPerDay, periodDurationMin, breakBetweenPeriodsMin, lunchBreakMin, lunchAfterPeriod,
    firstPeriodStartHour, firstPeriodStartMinute, arrivalHour, if (arrivalHour < 0) -1 else arrivalMinute,
    departureHour, if (departureHour < 0) -1 else departureMinute
)

/** 端末固有IDではなく、授業の位置と内容で紐付け先を照合する。 */
data class QrLessonReference(
    val academicYear: Int, val timetableTerm: TimetableTerm,
    val dayOfWeek: Int, val slotIndex: Int, val signature: String
)
