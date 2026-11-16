package jp.linkserver.nittcsc.sktjf

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfBaselineCodecTest {
    private fun baseline(): SktjfStoredBaseline {
        val lesson = SktjfLessonContent(1, 2, SktjfCycle.A, "数学", listOf("教員"), listOf("教室"),
            SktjfConditions(setOf("DD"), setOf("INTERNATIONAL")), SktjfLessonKind.NORMAL, true)
        val day = SktjfCalendarContent(SktjfDayType.B, 1, SktjfDayType.A, SktjfEvent("行事", SktjfEventKind.OTHER))
        return SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf("lesson-1" to lesson)),
            SktjfCalendarBaseline(2026, "S", "1-1", mapOf(LocalDate.of(2026, 4, 6) to day)))
    }

    @Test fun baselineRoundTripKeepsConditionsEventsAndRemote() {
        val original = baseline()
        assertEquals(original, SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(original)))
        assertEquals(original.copy(localFile = true), SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(original.copy(localFile = true))))
        assertFalse(SktjfBaselineCodec.encode(original).contains("localFile"))
        assertTrue(runCatching { SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(original.copy(localFile = true)).replace("\"localFile\":true", "\"localFile\":1")) }.isFailure)
    }

    @Test fun rejectsCorruptOrIncompatibleBaselines() {
        val json = SktjfBaselineCodec.encode(baseline())
        for (bad in listOf(json.replace("\"version\":1", "\"version\":2"),
            json.replace("\"term\":\"S\"", "\"term\":\"X\""),
            json.replace("\"INTERNATIONAL\"", "\"DD\""),
            json.replace("2026-04-06", "2025-04-06"))) {
            assertTrue(runCatching { SktjfBaselineCodec.decode(bad) }.isFailure)
        }
        assertTrue(runCatching { SktjfBaselineCodec.encode(baseline().copy(calendar = baseline().calendar.copy(classId = "1-2"))) }.isFailure)
    }

    @Test fun missingOptionalInformationSurvivesRoundTrip() {
        val initial = baseline()
        val lesson = initial.lessons.lessons.getValue("lesson-1").copy(remote = null, teachers = emptyList(), rooms = emptyList())
        val day = SktjfCalendarContent(null, null, null, null)
        val updated = SktjfStoredBaseline(initial.lessons.copy(lessons = mapOf("lesson-1" to lesson)),
            initial.calendar.copy(days = mapOf(LocalDate.of(2026, 5, 1) to day)))
        assertEquals(updated, SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(updated)))
    }

    @Test fun rejectsDuplicateJsonKeys() {
        val json = SktjfBaselineCodec.encode(baseline()).replace("\"version\":1", "\"version\":1,\"version\":1")
        assertTrue(runCatching { SktjfBaselineCodec.decode(json) }.isFailure)
    }
}
