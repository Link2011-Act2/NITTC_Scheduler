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
}
