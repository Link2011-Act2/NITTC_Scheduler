package jp.linkserver.nittcsc.logic

import java.time.LocalDate
import java.time.LocalTime

data class ClassSlot(
    val index: Int,
    val label: String,
    val start: LocalTime,
    val end: LocalTime
)

val CLASS_SLOTS = listOf(
    ClassSlot(0, "1/2校時", LocalTime.of(8, 40), LocalTime.of(10, 10)),
    ClassSlot(1, "3/4校時", LocalTime.of(10, 20), LocalTime.of(11, 50)),
    ClassSlot(2, "5/6校時", LocalTime.of(12, 40), LocalTime.of(14, 10)),
    ClassSlot(3, "7/8校時", LocalTime.of(14, 20), LocalTime.of(15, 50))
)

fun generateClassSlots(
    periodsPerDay: Int,
    periodDurationMin: Int,
    breakBetweenPeriodsMin: Int,
    lunchBreakMin: Int,
    firstPeriodStartHour: Int,
    firstPeriodStartMinute: Int,
    useKosenMode: Boolean,
    lunchAfterPeriod: Int = periodsPerDay / 2
): List<ClassSlot> {
    val lunchAfter = lunchAfterPeriod.coerceIn(0, periodsPerDay)
    val slots = mutableListOf<ClassSlot>()
    var currentMin = firstPeriodStartHour * 60 + firstPeriodStartMinute
    fun safeLocalTime(totalMin: Int): LocalTime {
        val clampedMin = totalMin.coerceIn(0, 23 * 60 + 59)
        return LocalTime.of(clampedMin / 60, clampedMin % 60)
    }
    for (i in 0 until periodsPerDay) {
        val startH = currentMin / 60
        val startM = currentMin % 60
        currentMin += periodDurationMin
        val endH = currentMin / 60
        val endM = currentMin % 60
        val label = if (useKosenMode) "${i * 2 + 1}/${i * 2 + 2}校時" else "${i + 1}校時"
        slots.add(
            ClassSlot(
                i,
                label,
                safeLocalTime(startH * 60 + startM),
                safeLocalTime(endH * 60 + endM)
            )
        )
        if (i < periodsPerDay - 1) {
            currentMin += if (i == lunchAfter - 1) lunchBreakMin else breakBetweenPeriodsMin
        }
    }
    return slots
}

data class GeneratedLesson(
    val date: LocalDate,
    val slot: ClassSlot,
    val subject: String,
    val teacher: String
)

sealed class ExportRange {
    object ThisWeek : ExportRange()
    data class Custom(val start: LocalDate, val end: LocalDate) : ExportRange()
}

data class ExportResult(
    val addedCount: Int,
    val skippedCount: Int,
    val message: String
)
