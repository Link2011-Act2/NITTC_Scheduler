package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.SettingsEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SktjfSettingsCompatibilityTest {
    private fun doc(): SktjfDocument {
        fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/sktjf/$name")).use { it.readBytes() }
        val manifest = SktjfJson.manifest(resource("synthetic-manifest-v1.json"), "example/SKTJF")
        return SktjfJson.verifiedDocument(resource("synthetic-v1.json"), manifest, manifest.assets.single())
    }
    private fun settings() = SettingsEntity(termStart = LocalDate.of(2026, 4, 1), termEnd = LocalDate.of(2027, 3, 31))

    @Test fun mismatchedPeriodTimesAndMissingPeriodsAreBlocked() {
        val doc = doc(); val settings = settings()
        assertTrue(settingsSupportSktjf(settings, doc))
        assertFalse(settingsSupportSktjf(settings.copy(firstPeriodStartMinute = 45), doc))
        assertFalse(settingsSupportSktjf(settings, doc.copy(periods = listOf(SktjfPeriod(5, LocalTime.of(8, 40), LocalTime.of(10, 10))))))
    }

    @Test fun disabledAlternationAndDifferentTermBoundariesAreBlocked() {
        val doc = doc(); val settings = settings()
        assertFalse(settingsSupportSktjf(settings.copy(enableAbTimetable = false), doc))
        assertFalse(settingsSupportSktjf(settings.copy(secondTermStartMonth = 4, secondTermStartDay = 5), doc))
        val fall = doc.copy(term = "F", start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 2))
        assertTrue(settingsSupportSktjf(settings, fall))
        assertFalse(settingsSupportSktjf(settings.copy(enableSemesterTimetables = false), fall))
    }
}
