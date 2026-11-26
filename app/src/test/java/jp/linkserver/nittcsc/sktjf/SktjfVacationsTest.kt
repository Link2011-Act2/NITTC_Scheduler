package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.SchoolVacationBackup
import jp.linkserver.nittcsc.data.withSchoolVacations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.time.LocalDate

class SktjfVacationsTest {
    private val repository = "example/SKTJF"
    private val revision = "2026.F.1"
    private val url = "https://github.com/$repository/releases/download/$revision/vacations.json"
    private val document = """
        {"format":"SKTJF-VACATIONS","schemaVersion":1,"schoolId":"nittc","revision":"2026.F.1","periods":[{
          "id":"2026-year_end","type":"year_end","name":"学年末休業","start":"2027-02-20",
          "end":null,"confirmedThrough":"2027-03-31","estimatedEndMonth":4,
          "sources":[{"name":"calendar.pdf","url":null,"sha256":"${"a".repeat(64)}","page":1}]
        }]}
    """.trimIndent()

    private fun asset(json: String): SktjfVacationAsset {
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray()).joinToString("") { "%02x".format(it) }
        val manifest = """
            {"format":"SKTJF-VACATIONS-MANIFEST","manifestVersion":1,"schoolId":"nittc","revision":"$revision",
             "asset":{"name":"vacations.json","url":"$url","sha256":"$digest","dataFormatVersion":1}}
        """.trimIndent().toByteArray()
        return SktjfVacationJson.manifest(manifest, repository, revision)
    }

    @Test fun unfinishedYearEndOnlyCoversTheConfirmedPeriod() {
        val period = SktjfVacationJson.periods(document.toByteArray(), asset(document)).single()
        assertEquals("2026-year_end", period.id)
        assertNull(period.end)
        assertTrue(period.covers(LocalDate.parse("2027-02-20")))
        assertTrue(period.covers(LocalDate.parse("2027-03-31")))
        assertFalse(period.covers(LocalDate.parse("2027-04-01")))
    }

    @Test fun explicitEndCanExtendTheSameEventIntoNextYear() {
        val resolved = document.replace("\"end\":null", "\"end\":\"2027-04-05\"")
            .replace("2027-03-31", "2027-04-05").replace("\"estimatedEndMonth\":4", "\"estimatedEndMonth\":null")
        val period = SktjfVacationJson.periods(resolved.toByteArray(), asset(resolved)).single()
        assertEquals("2026-year_end", period.id)
        assertTrue(period.covers(LocalDate.parse("2027-04-05")))
        assertFalse(period.covers(LocalDate.parse("2027-04-06")))
    }

    @Test fun rejectsTamperedDataAndInvalidFormatVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            SktjfVacationJson.periods((document + " ").toByteArray(), asset(document))
        }
        val invalid = document.replace("\"schemaVersion\":1", "\"schemaVersion\":2")
        assertThrows(IllegalArgumentException::class.java) {
            SktjfVacationJson.periods(invalid.toByteArray(), asset(invalid))
        }
    }

    @Test fun removesOnlyTheOverlayWhenAnOpenEndedVacationIsDeleted() {
        val period = SktjfVacationJson.periods(document.toByteArray(), asset(document)).single()
        val vacation = planSchoolVacations(revision, listOf(period), emptyList(), emptyList()).changes.single().candidate
        val march = LocalDate.parse("2027-03-31")
        val april = march.plusDays(1)
        val base = listOf(DayTypeEntity(march, DayType.A), DayTypeEntity(april, DayType.B))
        val applied = base.withSchoolVacations(listOf(vacation)).associateBy { it.date }
        assertEquals(DayType.HOLIDAY, applied[march]?.dayType)
        assertEquals(DayType.B, applied[april]?.dayType)
        assertEquals(base, base.withSchoolVacations(listOf(vacation.copy(local = null))))
    }

    @Test fun nextYearCorrectionKeepsTheIdAndProtectsLocalChanges() {
        val period = SktjfVacationJson.periods(document.toByteArray(), asset(document)).single()
        val initial = planSchoolVacations(revision, listOf(period), emptyList(), emptyList()).changes.single().candidate
        val edited = initial.copy(local = initial.base.copy(name = "春休み"))
        val end = LocalDate.parse("2027-04-05")
        val remote = period.copy(end = end, confirmedThrough = end, estimatedEndMonth = null)
        val change = planSchoolVacations("2027.S.1", listOf(remote), listOf(edited), emptyList()).changes.single()
        assertEquals(initial.id, change.candidate.id)
        assertEquals(end, change.candidate.base.end)
        assertFalse(change.defaultSelected(manual = false))
        assertTrue(change.defaultSelected(manual = true))
        assertTrue(planSchoolVacations(revision, listOf(period), listOf(change.candidate), emptyList()).changes.isEmpty())
        assertEquals(listOf(edited), SchoolVacationBackup.decode(SchoolVacationBackup.encode(listOf(edited))))
    }

    @Test fun highlightsAConflictAndKeepsTransferredSchoolDays() {
        val period = SktjfVacationJson.periods(document.toByteArray(), asset(document)).single()
        val transferred = DayTypeEntity(period.start, DayType.A, overrideLessonDayOfWeek = 1)
        val change = planSchoolVacations(revision, listOf(period), emptyList(), listOf(transferred)).changes.single()
        assertEquals(listOf(transferred.date), change.teachingDates)
        assertFalse(change.defaultSelected(manual = true))
        assertEquals(transferred, listOf(transferred).withSchoolVacations(listOf(change.candidate)).first())
    }

    @Test fun preservesExplicitCycleOverridesWithinVacation() {
        val period = SktjfVacationJson.periods(document.toByteArray(), asset(document)).single()
        val override = DayTypeEntity(period.start, DayType.A, overrideLessonDayType = DayType.B)
        val change = planSchoolVacations(revision, listOf(period), emptyList(), listOf(override)).changes.single()
        assertEquals(listOf(override.date), change.teachingDates)
        assertFalse(change.defaultSelected(manual = true))
        assertEquals(override, listOf(override).withSchoolVacations(listOf(change.candidate)).first())
        assertEquals(listOf(override), listOf(override).withSchoolVacations(listOf(change.candidate.copy(local = null))))
    }
}
