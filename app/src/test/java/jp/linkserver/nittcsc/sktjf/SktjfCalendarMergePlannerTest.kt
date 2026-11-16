package jp.linkserver.nittcsc.sktjf

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfCalendarMergePlannerTest {
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/sktjf/$name")).use { it.readBytes() }
    private fun document(): SktjfDocument {
        val manifest = SktjfJson.manifest(resource("synthetic-manifest-v1.json"), "example/SKTJF")
        return SktjfJson.verifiedDocument(resource("synthetic-v1.json"), manifest, manifest.assets.single())
    }
    private fun baseline(doc: SktjfDocument) = SktjfCalendarBaseline(doc.academicYear, doc.term, doc.classId, doc.calendar.associate { it.date to it.content() })
    private fun local(doc: SktjfDocument, days: Map<LocalDate, SktjfCalendarContent>) = SktjfLocalCalendar(doc.academicYear, doc.term, doc.classId, days)
    private inline fun rejects(action: () -> Unit) { assertTrue(runCatching(action).isFailure) }

    @Test fun localChangeSurvivesUnchangedDistributionAndMissingDates() {
        val doc = document(); val first = doc.calendar.first(); val edited = first.content().copy(dayType = SktjfDayType.B)
        val manualDate = first.date.minusDays(1); val manual = edited.copy(event = SktjfEvent("個人の変更", null))
        val plan = SktjfCalendarMergePlan.create(baseline(doc), local(doc, mapOf(first.date to edited, manualDate to manual)), doc)
        assertTrue(plan.conflicts.isEmpty())
        val result = plan.resolve(emptyMap()).days; assertEquals(edited, result[first.date]); assertEquals(manual, result[manualDate])
    }

    @Test fun bothChangesNeedAChoiceAndTheOtherDatesRemain() {
        val doc = document(); val first = doc.calendar.first(); val current = first.content().copy(dayType = SktjfDayType.B)
        val incoming = doc.copy(calendar = doc.calendar.map { if (it.date == first.date) it.copy(dayType = SktjfDayType.HOLIDAY) else it })
        val plan = SktjfCalendarMergePlan.create(baseline(doc), local(doc, mapOf(first.date to current)), incoming)
        assertEquals(SktjfCalendarConflictReason.BOTH_CHANGED, plan.conflicts.single().reason)
        rejects { plan.resolve(emptyMap()) }
        assertEquals(current, plan.resolve(mapOf(first.date to SktjfConflictChoice.KEEP_LOCAL)).days[first.date])
        assertEquals(SktjfDayType.HOLIDAY, plan.resolve(mapOf(first.date to SktjfConflictChoice.APPLY_INCOMING)).days[first.date]!!.dayType)
    }

    @Test fun unsupportedEventCannotBeAppliedWithoutExtendedStorage() {
        val doc = document(); val first = doc.calendar.first()
        val incoming = doc.copy(calendar = listOf(first.copy(dayType = null, event = SktjfEvent("未対応の行事", SktjfEventKind.OTHER))))
        val plan = SktjfCalendarMergePlan.create(null, local(doc, emptyMap()), incoming)
        assertEquals(SktjfCalendarConflictReason.NEEDS_EXTENDED_HANDLING, plan.conflicts.single().reason)
        rejects { plan.resolve(mapOf(first.date to SktjfConflictChoice.APPLY_INCOMING)) }
        assertTrue(plan.resolve(mapOf(first.date to SktjfConflictChoice.KEEP_LOCAL)).days.isEmpty())
    }

    @Test fun skippedUndeterminedEventRemainsVisibleWhenTheSameRevisionIsCheckedAgain() {
        val doc = document(); val first = doc.calendar.first()
        val source = first.copy(dayType = null, event = SktjfEvent("清掃", SktjfEventKind.OTHER))
        val incoming = doc.copy(calendar = listOf(source))
        val current = first.content().copy(dayType = SktjfDayType.B, event = null)
        val initial = SktjfCalendarMergePlan.create(null, local(doc, mapOf(first.date to current)), incoming)
        val kept = initial.resolve(mapOf(first.date to SktjfConflictChoice.KEEP_LOCAL))
        val repeated = SktjfCalendarMergePlan.create(kept.baseline, local(doc, kept.days), incoming)
        assertEquals(listOf(first.date), repeated.conflicts.map { it.date })
        assertEquals(SktjfCalendarConflictReason.NEEDS_EXTENDED_HANDLING, repeated.conflicts.single().reason)
        assertEquals(current, repeated.resolve(mapOf(first.date to SktjfConflictChoice.KEEP_LOCAL)).days[first.date])
    }

    @Test fun rejectsDifferentProfilesAndDuplicateDates() {
        val doc = document()
        rejects { SktjfCalendarMergePlan.create(baseline(doc).copy(classId = "1-2"), local(doc, emptyMap()), doc) }
        rejects { SktjfCalendarMergePlan.create(null, local(doc, emptyMap()).copy(academicYear = 2025), doc) }
        rejects { SktjfCalendarMergePlan.create(null, local(doc, emptyMap()), doc.copy(calendar = listOf(doc.calendar.first(), doc.calendar.first()))) }
    }
}
