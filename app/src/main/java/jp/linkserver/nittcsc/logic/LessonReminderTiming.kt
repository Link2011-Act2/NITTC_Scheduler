package jp.linkserver.nittcsc.logic

import java.time.LocalDateTime

data class LessonReminderTiming(val standardAt: LocalDateTime, val liveAt: LocalDateTime?)

/** 通常通知はOS・UIモード・promoted権限の状態に影響されない。 */
fun lessonReminderTiming(
    startsAt: LocalDateTime,
    minutesBefore: Int,
    liveUpdatesEnabled: Boolean,
    liveUpdatesSupported: Boolean,
    liveEarlyMinutes: Int
): LessonReminderTiming {
    val minutes = minutesBefore.coerceIn(0, 360)
    val standard = startsAt.minusMinutes(minutes.toLong())
    val live = if (liveUpdatesEnabled && liveUpdatesSupported && minutes > 0)
        standard.minusMinutes(liveEarlyMinutes.coerceIn(0, 5).toLong()) else null
    return LessonReminderTiming(standard, live)
}
