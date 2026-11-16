package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HolidaySpecialLabelRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: SchedulerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SchedulerRepository(database, UiDesignPreferences(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun selectingLabelOnSchoolDayMakesHolidayAndKeepsItAfterRebuild() = runBlocking {
        val date = LocalDate.of(2026, 4, 8)
        repository.initialize(date)
        repository.upsertLessonOverride(date, 1, DayType.B)

        repository.updateHolidaySpecialLabel(date, HolidaySpecialLabel.MIDTERM)

        val labeled = database.schedulerDao().getDayType(date)!!
        assertEquals(DayType.HOLIDAY, labeled.dayType)
        assertEquals(HolidaySpecialLabel.MIDTERM, labeled.holidaySpecialLabel)
        assertNull(labeled.overrideLessonDayOfWeek)
        assertNull(labeled.overrideLessonDayType)

        repository.syncDayTypes()
        assertEquals(HolidaySpecialLabel.MIDTERM, database.schedulerDao().getDayType(date)?.holidaySpecialLabel)

        repository.updateHolidaySpecialLabel(date, null)
        val cleared = database.schedulerDao().getDayType(date)!!
        assertEquals(DayType.HOLIDAY, cleared.dayType)
        assertNull(cleared.holidaySpecialLabel)
    }

    @Test
    fun schoolEventSurvivesDayEditsBackupAndSync() = runBlocking {
        val date = LocalDate.of(2026, 4, 8)
        repository.initialize(date)
        val original = DayTypeEntity(date, DayType.A, schoolEventName = "清掃", schoolEventKind = "OTHER")
        val dao = database.schedulerDao()
        dao.upsertDayType(original)
        repository.upsertDayType(date, DayType.B)
        assertEquals(original.copy(dayType = DayType.B), dao.getDayType(date))
        val backup = repository.exportAllData()
        val payload = repository.exportSyncPayload()
        dao.upsertDayType(DayTypeEntity(date, DayType.A))
        repository.importAllData(backup, requireSettings = true)
        assertEquals(original.copy(dayType = DayType.B), dao.getDayType(date))
        dao.upsertDayType(DayTypeEntity(date, DayType.A))
        repository.applySyncPayload(payload)
        assertEquals(original.copy(dayType = DayType.B), dao.getDayType(date))
    }
}
