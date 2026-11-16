package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfLocalCalendarProjectionTest {
    private val date = LocalDate.of(2026, 6, 10)
    private fun baseline(content: SktjfCalendarContent) = SktjfCalendarBaseline(2026, "S", "1-1", mapOf(date to content))

    @Test fun unchangedProjectedDayKeepsOriginalSourceEventName() {
        val original = SktjfCalendarContent(null, null, null, SktjfEvent("前期中間試験", SktjfEventKind.MIDTERM))
        val row = DayTypeEntity(date, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.MIDTERM)
        val result = projectSktjfLocalCalendar(2026, "S", "1-1", listOf(row), baseline(original))
        assertEquals(original, result.days[date])
    }

    @Test fun locallyChangedDayIsDistinguishedFromSourceAndNotWrittenBack() {
        val old = SktjfCalendarContent(SktjfDayType.A, null, null, null)
        val row = DayTypeEntity(date, DayType.B, overrideLessonDayOfWeek = 1, overrideLessonDayType = DayType.A)
        val local = projectSktjfLocalCalendar(2026, "S", "1-1", listOf(row), baseline(old))
        assertEquals(SktjfCalendarContent(SktjfDayType.B, 1, SktjfDayType.A, null), local.days[date])
        assertNotEquals(old, local.days[date]); assertEquals(DayType.B, row.dayType)
    }

    @Test fun retainsLegacyClosedDayAsLocalDifferenceAndRejectsOtherPartitions() {
        val row = DayTypeEntity(date, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.SCHOOL_CLOSED)
        val result = projectSktjfLocalCalendar(2026, "S", "1-1", listOf(row), null)
        assertEquals(SktjfEventKind.OTHER, result.days.getValue(date).event!!.kind)
        assertTrue(runCatching { projectSktjfLocalCalendar(2026, "F", "1-1", listOf(row), baseline(SktjfCalendarContent(null, null, null, null))) }.isFailure)
        assertTrue(runCatching { projectSktjfLocalCalendar(2026, "S", "1-1", listOf(row, row), null) }.isFailure)
    }

    @Test fun savedBaselineDetectsCalendarConflictAndRequiresRelinkingAfterRestore() {
        fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/sktjf/$name")).use { it.readBytes() }
        val manifest = SktjfJson.manifest(resource("synthetic-manifest-v1.json"), "example/SKTJF")
        val doc = SktjfJson.verifiedDocument(resource("synthetic-v1.json"), manifest, manifest.assets.single())
        val first = doc.calendar.first()
        val old = SktjfStoredBaseline(SktjfBaseline(doc.academicYear, doc.term, doc.classId, emptyMap()),
            SktjfCalendarBaseline(doc.academicYear, doc.term, doc.classId, mapOf(first.date to first.content())))
        val state = SktjfSourceStateEntity(doc.academicYear, doc.term, doc.classId, doc.revision, SktjfBaselineCodec.encode(old), false)
        val local = DayTypeEntity(first.date, DayType.B)
        val incoming = doc.copy(calendar = listOf(first.copy(dayType = SktjfDayType.HOLIDAY)))
        val result = prepareSktjfCalendarMerge(incoming, listOf(local), state) as SktjfCalendarPreparation.Ready
        assertEquals(SktjfCalendarConflictReason.BOTH_CHANGED, result.plan.conflicts.single().reason)
        val kept = result.plan.resolve(mapOf(first.date to SktjfConflictChoice.KEEP_LOCAL))
        assertEquals(SktjfDayType.B, kept.days.getValue(first.date).dayType)
        assertEquals(SktjfLocalProjectionIssue.RELINK_REQUIRED,
            (prepareSktjfCalendarMerge(incoming, listOf(local), state.copy(requiresRelinking = true)) as SktjfCalendarPreparation.Blocked).issue)
    }
}
