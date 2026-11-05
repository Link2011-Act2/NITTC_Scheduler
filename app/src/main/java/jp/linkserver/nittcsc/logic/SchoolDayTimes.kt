package jp.linkserver.nittcsc.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 下校は基本時刻表の最終終了時刻を正時に切り上げる。休日にも同じ基準を使う。 */
fun schoolDepartureTime(manual: LocalTime?, basicSlots: List<ClassSlot>): LocalTime {
    if (manual != null) return manual
    val end = basicSlots.maxOfOrNull { it.end } ?: LocalTime.of(15, 30)
    val minute = end.hour * 60 + end.minute
    return LocalTime.ofSecondOfDay((((minute + 59) / 60 * 60).coerceAtMost(1439) * 60).toLong())
}

fun schoolArrivalTime(
    manual: LocalTime?,
    effectiveSlots: List<ClassSlot>,
    basicSlots: List<ClassSlot>
): LocalTime = manual ?: (effectiveSlots.minOfOrNull { it.start }
    ?: basicSlots.minOfOrNull { it.start } ?: LocalTime.of(8, 40)).minusMinutes(10)

/** 旧版の8:30は初期値と手動入力を区別できないため、自動として引き継ぐ。値自体は保持する。 */
fun legacyArrivalTimeIsAutomatic(hour: Int, minute: Int): Boolean =
    hour !in 0..23 || minute !in 0..59 || (hour == 8 && minute == 30)

enum class TaskReminderKind { MANUAL, DAY_BEFORE, DUE_MORNING }

fun automaticTaskReminderTimes(
    dueDate: LocalDate,
    arrival: LocalTime,
    previousDayDeparture: LocalTime
): Map<TaskReminderKind, LocalDateTime> = mapOf(
    TaskReminderKind.DAY_BEFORE to dueDate.minusDays(1).atTime(previousDayDeparture),
    TaskReminderKind.DUE_MORNING to dueDate.atTime(arrival).minusMinutes(20)
)

/** 古い予約・早過ぎる実行・大量の遅延送信を防ぐ。AlarmとWorkerで同じ判定を使う。 */
fun reminderIsDue(expected: LocalDateTime, current: LocalDateTime, now: LocalDateTime): Boolean =
    expected == current && !now.isBefore(current) && now.isBefore(current.plusMinutes(15))
