package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class UpcomingLessonTest {
    private val date = LocalDate.of(2026, 8, 10)
    private val first = UpcomingLessonCandidate(
        date = date,
        slotIndex = 0,
        slotLabel = "1校時",
        start = LocalTime.of(8, 50),
        end = LocalTime.of(10, 20),
        content = "国語"
    )
    private val second = UpcomingLessonCandidate(
        date = date,
        slotIndex = 1,
        slotLabel = "2校時",
        start = LocalTime.of(10, 30),
        end = LocalTime.of(12, 0),
        content = "数学"
    )

    @Test
    fun beforeClass_selectsFirstUpcomingLesson() {
        val result = findUpcomingLesson(listOf(second, first), LocalDateTime.of(date, LocalTime.of(8, 30)))

        assertEquals(first, result?.candidate)
        assertEquals(UpcomingLessonPhase.UPCOMING, result?.phase)
    }

    @Test
    fun atClassStart_marksLessonOngoing() {
        val result = findUpcomingLesson(listOf(first, second), first.startsAt)

        assertEquals(first, result?.candidate)
        assertEquals(UpcomingLessonPhase.ONGOING, result?.phase)
    }

    @Test
    fun atClassEnd_selectsFollowingLesson() {
        val result = findUpcomingLesson(listOf(first, second), first.endsAt)

        assertEquals(second, result?.candidate)
        assertEquals(UpcomingLessonPhase.UPCOMING, result?.phase)
    }

    @Test
    fun afterLastClass_returnsNull() {
        val result = findUpcomingLesson(listOf(first, second), second.endsAt)

        assertNull(result)
    }

    @Test
    fun ongoingLesson_winsOverLaterUpcomingLesson() {
        val result = findUpcomingLesson(
            listOf(second, first),
            LocalDateTime.of(date, LocalTime.of(9, 30))
        )

        assertEquals(first, result?.candidate)
        assertEquals(UpcomingLessonPhase.ONGOING, result?.phase)
    }

    @Test
    fun unorderedCandidates_acrossDatesSelectNearestDate() {
        val nextDay = first.copy(date = date.plusDays(1), content = "英語")

        val result = findUpcomingLesson(
            listOf(nextDay, second),
            LocalDateTime.of(date, LocalTime.of(12, 1))
        )

        assertEquals(nextDay, result?.candidate)
        assertEquals(UpcomingLessonPhase.UPCOMING, result?.phase)
    }

    @Test
    fun emptyCandidates_returnsNull() {
        assertNull(findUpcomingLesson<String>(emptyList(), LocalDateTime.of(date, LocalTime.NOON)))
    }
}
