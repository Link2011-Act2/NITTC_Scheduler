package jp.linkserver.nittcsc.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class UpcomingLessonPhase {
    ONGOING,
    UPCOMING
}

/** UIやウィジェットに依存しない、次の授業を選ぶための候補。 */
data class UpcomingLessonCandidate<T>(
    val date: LocalDate,
    val slotIndex: Int,
    val slotLabel: String,
    val start: LocalTime,
    val end: LocalTime,
    val content: T
) {
    val startsAt: LocalDateTime get() = date.atTime(start)
    val endsAt: LocalDateTime get() = date.atTime(end)
}

data class UpcomingLessonSelection<T>(
    val candidate: UpcomingLessonCandidate<T>,
    val phase: UpcomingLessonPhase
)

/** 授業中ならその授業を、そうでなければ開始時刻が最も近い授業を返す。 */
fun <T> findUpcomingLesson(
    candidates: Iterable<UpcomingLessonCandidate<T>>,
    now: LocalDateTime
): UpcomingLessonSelection<T>? {
    val candidate = candidates
        .asSequence()
        .filter { it.endsAt.isAfter(now) }
        .minWithOrNull(compareBy<UpcomingLessonCandidate<T>> { it.startsAt }.thenBy { it.slotIndex })
        ?: return null

    val phase = if (!now.isBefore(candidate.startsAt) && now.isBefore(candidate.endsAt)) {
        UpcomingLessonPhase.ONGOING
    } else {
        UpcomingLessonPhase.UPCOMING
    }
    return UpcomingLessonSelection(candidate = candidate, phase = phase)
}
