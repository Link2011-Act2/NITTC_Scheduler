package jp.linkserver.nittcsc.sktjf

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import jp.linkserver.nittcsc.logic.LessonWeekRange

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

    @Test fun weekRangesRoundTripAndLegacyBaselineRemainsUnchanged() {
        val initial=baseline()
        val oldJson=SktjfBaselineCodec.encode(initial)
        assertFalse(oldJson.contains("weekRange"))
        val lesson=initial.lessons.lessons.getValue("lesson-1").copy(weekRange=SktjfV2Range(LessonWeekRange(start=9),"（9週〜）"))
        val changed=initial.copy(lessons=initial.lessons.copy(lessons=mapOf("lesson-1" to lesson)))
        val json=SktjfBaselineCodec.encode(changed)
        assertTrue(json.contains("\"version\":2"))
        assertEquals(changed,SktjfBaselineCodec.decode(json))
        assertEquals(initial,SktjfBaselineCodec.decode(oldJson))
        assertTrue(runCatching { SktjfBaselineCodec.decode(json.replace("\"start\":9", "\"start\":0")) }.isFailure)
    }

    @Test fun confirmedWeekDatesAndSourceReferencesRoundTrip() {
        val initial=baseline()
        val date=LocalDate.of(2026,4,6)
        val updated=initial.copy(weeks=mapOf(date to SktjfV2Week(9,listOf(SktjfReference("source-1",2,"記載箇所")))))
        val json=SktjfBaselineCodec.encode(updated)
        assertTrue(json.contains("\"version\":2"))
        assertEquals(updated,SktjfBaselineCodec.decode(json))
        assertTrue(runCatching { SktjfBaselineCodec.decode(json.replace("\"week\":9", "\"week\":0")) }.isFailure)
        assertTrue(runCatching { SktjfBaselineCodec.decode(json.replace("2026-04-06", "2028-04-06")) }.isFailure)
        assertTrue(runCatching { SktjfBaselineCodec.decode(json.replace("\"version\":2", "\"version\":1")) }.isFailure)
    }

    @Test fun opportunitySeriesRoundTripDoesNotAlterOlderFormats() {
        val initial = baseline()
        val item = SktjfV2WeekSeries("shared", 1, setOf(2), setOf("lesson-1"), SktjfCycle.WEEKLY,
            listOf(SktjfReference("source-1", 2, "出典")))
        val updated = initial.copy(series = listOf(item))
        val json = SktjfBaselineCodec.encode(updated)
        assertTrue(json.contains("\"version\":3"))
        assertEquals(updated, SktjfBaselineCodec.decode(json))
        assertEquals(initial, SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(initial)))
        assertTrue(runCatching { SktjfBaselineCodec.decode(json.replace("\"version\":3", "\"version\":2")) }.isFailure)
        assertTrue(runCatching { SktjfBaselineCodec.encode(updated.copy(series = listOf(item.copy(lessonIds = setOf("missing"))))) }.isFailure)
        assertTrue(runCatching { SktjfBaselineCodec.encode(updated.copy(series = listOf(item.copy(periods = setOf(2, 3))))) }.isFailure)
    }
}
