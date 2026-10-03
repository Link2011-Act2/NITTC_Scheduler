package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.nittcsc.logic.QrDayTypesScope
import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.QrShareFailure
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** 端末のユーザーDBには触れず、Roomの一時DBでQRの取引全体を検証する。 */
@RunWith(AndroidJUnit4::class)
class QrDayTypesTransferTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: SchedulerDao
    private lateinit var transfer: QrShareTransfer
    private fun date(value: String) = LocalDate.parse(value)
    private val sections = setOf(QrShareSection.DAY_TYPES)
    private fun payload(scope: QrDayTypesScope = QrDayTypesScope.YEAR) =
        QrSharePayload(2026, sections, 9, 23, dayTypesScope = scope)

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.schedulerDao()
        val repository = SchedulerRepository(db, UiDesignPreferences(context))
        transfer = QrShareTransfer(repository, db)
        dao.upsertSettings(SettingsEntity(termStart = date("2026-04-01"), termEnd = date("2027-03-31"),
            secondTermStartMonth = 9, secondTermStartDay = 23))
    }

    @After fun teardown() { db.close() }

    @Test fun exportFiltersYearAndSemesterButKeepsFullOverlappingSpringBreak() = runBlocking {
        val previous = DayTypeEntity(date("2026-03-31"), DayType.B)
        val first = DayTypeEntity(date("2026-09-22"), DayType.A, 1, DayType.B)
        val second = DayTypeEntity(date("2026-09-23"), DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.EVENT)
        val next = DayTypeEntity(date("2027-04-01"), DayType.A)
        dao.upsertDayTypes(listOf(previous, first, second, next))
        val spring = LongBreakEntity(name = "春休み", startDate = date("2027-03-20"), endDate = date("2027-04-10"))
        dao.upsertLongBreak(spring)
        dao.upsertLongBreak(LongBreakEntity(name = "別年度", startDate = date("2028-08-01"), endDate = date("2028-08-31")))
        val sent = transfer.export(QrShareSelection(2026, sections, dayTypesScope = QrDayTypesScope.SECOND))
        assertEquals(listOf(second), sent.dayTypes)
        assertEquals(listOf(spring), sent.longBreaks.map { it.copy(id = 0) })
        val nextYear = transfer.export(QrShareSelection(2027, sections, dayTypesScope = QrDayTypesScope.FIRST))
        assertEquals(listOf(next), nextYear.dayTypes)
        assertEquals(listOf(spring), nextYear.longBreaks.map { it.copy(id = 0) })
        assertTrue(transfer.export(QrShareSelection(2026, sections, dayTypesScope = QrDayTypesScope.FIRST)).longBreaks.isEmpty())
    }

    @Test fun importReplacesOnlyChosenSemesterAndRepeatedSpringImportDoesNotDuplicate() = runBlocking {
        val old = DayTypeEntity(date("2026-03-31"), DayType.B)
        val first = DayTypeEntity(date("2026-09-22"), DayType.B, 1, DayType.A)
        val second = DayTypeEntity(date("2026-09-23"), DayType.A)
        val next = DayTypeEntity(date("2027-04-01"), DayType.B)
        dao.upsertDayTypes(listOf(old, first, second, next))
        val spring = LongBreakEntity(name = "春休み", startDate = date("2027-03-20"), endDate = date("2027-04-10"))
        val unrelated = LongBreakEntity(name = "来年度", startDate = date("2027-08-01"), endDate = date("2027-08-31"))
        dao.upsertLongBreak(spring)
        dao.upsertLongBreak(unrelated)
        val original = dao.getLongBreaksOnce().sortedBy { it.startDate }
        val data = payload(QrDayTypesScope.SECOND).copy(dayTypes = listOf(second.copy(dayType = DayType.HOLIDAY)), longBreaks = listOf(spring))
        repeat(2) { transfer.import(data, emptySet()) }
        assertEquals(listOf(old, first, second.copy(dayType = DayType.HOLIDAY), next), dao.getDayTypesOnce().sortedBy { it.date })
        assertEquals(original, dao.getLongBreaksOnce().sortedBy { it.startDate })
        assertEquals(23, dao.getSettings()!!.secondTermStartDay)
    }

    @Test fun emptyScopedDatasetClearsOnlyTargetRangeAndRetainsOutsideBreakPortion() = runBlocking {
        dao.upsertDayTypes(listOf(DayTypeEntity(date("2027-03-31"), DayType.B), DayTypeEntity(date("2027-04-01"), DayType.A)))
        dao.upsertLongBreak(LongBreakEntity(name = "春休み", startDate = date("2027-03-20"), endDate = date("2027-04-10")))
        transfer.import(payload(), emptySet())
        assertNull(dao.getDayType(date("2027-03-31")))
        assertNotNull(dao.getDayType(date("2027-04-01")))
        val retained = dao.getLongBreaksOnce().single()
        assertEquals(date("2027-04-01"), retained.startDate)
        assertEquals(date("2027-04-10"), retained.endDate)
    }

    @Test fun incompatibleStartOrOutOfRangeRowsLeaveAllTablesUntouched() = runBlocking {
        val original = DayTypeEntity(date("2026-10-01"), DayType.B)
        dao.upsertDayType(original)
        val originalSettings = dao.getSettings()
        val variants = listOf(payload().copy(secondTermStartDay = 24),
            payload().copy(dayTypes = listOf(DayTypeEntity(date("2027-04-01"), DayType.A))))
        for ((index, data) in variants.withIndex()) {
            try { transfer.import(data, emptySet()); fail("Expected rejection") }
            catch (error: QrShareException) { assertEquals(if (index == 0) QrShareFailure.SETTINGS else QrShareFailure.INVALID, error.failure) }
            assertEquals(listOf(original), dao.getDayTypesOnce())
            assertEquals(originalSettings, dao.getSettings())
        }
    }

    @Test fun legacyVersionTwoRetainsOriginalGlobalReplacementBehavior() = runBlocking {
        dao.upsertDayType(DayTypeEntity(date("2027-04-01"), DayType.B))
        val row = DayTypeEntity(date("2025-10-01"), DayType.A)
        transfer.import(payload().copy(formatVersion = 2, dayTypes = listOf(row)), emptySet())
        assertEquals(listOf(row), dao.getDayTypesOnce())
    }
}
