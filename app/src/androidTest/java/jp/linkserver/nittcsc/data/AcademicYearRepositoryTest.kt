package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import jp.linkserver.nittcsc.logic.TimetableTerm
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcademicYearRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: SchedulerDao
    private lateinit var repository: SchedulerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.schedulerDao()
        repository = SchedulerRepository(database, UiDesignPreferences(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun preparingNextAcademicYearTwicePreservesEnteredLessons() = runBlocking {
        val preparationDate = LocalDate.of(2027, 2, 1)
        repository.initialize(preparationDate)

        assertTrue(repository.prepareNextAcademicYear(preparationDate))
        assertEquals(20, dao.countLessons(2027, TimetableTerm.FIRST))
        assertEquals(0, dao.countLessons(2027, TimetableTerm.SECOND))

        repository.upsertLesson(
            academicYear = 2027,
            timetableTerm = TimetableTerm.FIRST,
            dayOfWeek = 1,
            slotIndex = 0,
            draft = LessonDraft(weeklySubject = "数学", weeklyTeacher = "山田")
        )

        assertFalse(repository.prepareNextAcademicYear(preparationDate))
        assertEquals(
            "数学",
            dao.getLesson(2027, TimetableTerm.FIRST, 1, 0)?.weeklySubject
        )
        assertNotNull(dao.getDayType(LocalDate.of(2027, 4, 1)))
        assertNotNull(dao.getDayType(LocalDate.of(2027, 9, 30)))
        assertNull(dao.getDayType(LocalDate.of(2027, 10, 1)))
    }

    @Test
    fun AprilRolloverPromotesPreparedYearAndRetainsPreviousYearData() = runBlocking {
        val preparationDate = LocalDate.of(2027, 2, 1)
        repository.initialize(preparationDate)
        repository.upsertLesson(
            academicYear = 2026,
            timetableTerm = TimetableTerm.FIRST,
            dayOfWeek = 1,
            slotIndex = 0,
            draft = LessonDraft(weeklySubject = "旧年度", weeklyTeacher = "旧担当")
        )
        assertTrue(repository.prepareNextAcademicYear(preparationDate))
        repository.upsertLesson(
            academicYear = 2027,
            timetableTerm = TimetableTerm.FIRST,
            dayOfWeek = 1,
            slotIndex = 0,
            draft = LessonDraft(weeklySubject = "新年度", weeklyTeacher = "新担当")
        )

        assertTrue(repository.refreshAcademicYear(LocalDate.of(2027, 4, 1)))

        val settings = checkNotNull(dao.getSettings())
        assertEquals(2027, settings.activeAcademicYear)
        assertEquals(LocalDate.of(2027, 4, 1), settings.termStart)
        assertEquals(LocalDate.of(2028, 3, 31), settings.termEnd)
        assertEquals(
            "新年度",
            dao.getLesson(2027, TimetableTerm.FIRST, 1, 0)?.weeklySubject
        )
        assertEquals(
            "旧年度",
            dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)?.weeklySubject
        )
        assertNotNull(dao.getLesson(2027, TimetableTerm.SECOND, 1, 0))
        assertNotNull(dao.getDayType(LocalDate.of(2026, 4, 1)))
        assertNotNull(dao.getDayType(LocalDate.of(2028, 3, 31)))

        assertFalse(repository.refreshAcademicYear(LocalDate.of(2027, 4, 1)))
        assertEquals(
            "新年度",
            dao.getLesson(2027, TimetableTerm.FIRST, 1, 0)?.weeklySubject
        )
    }

    @Test
    fun preparationAdvancesAStaleActiveYearBeforeCreatingNextYearRows() = runBlocking {
        repository.initialize(LocalDate.of(2026, 2, 1))
        assertEquals(2025, dao.getSettings()?.activeAcademicYear)

        assertTrue(repository.prepareNextAcademicYear(LocalDate.of(2027, 2, 1)))

        assertEquals(2026, dao.getSettings()?.activeAcademicYear)
        assertEquals(20, dao.countLessons(2027, TimetableTerm.FIRST))
        assertEquals(0, dao.countLessons(2027, TimetableTerm.SECOND))
    }

    @Test
    fun nextAcademicYearCanBePreparedOutsideFebruaryAndMarch() = runBlocking {
        val preparationDate = LocalDate.of(2027, 8, 1)
        repository.initialize(preparationDate)

        assertTrue(repository.prepareNextAcademicYear(preparationDate))

        assertEquals(20, dao.countLessons(2028, TimetableTerm.FIRST))
        assertEquals(0, dao.countLessons(2028, TimetableTerm.SECOND))
    }

    @Test
    fun termRangeCannotSwitchTheActiveAcademicYear() = runBlocking {
        repository.initialize(LocalDate.of(2027, 2, 1))

        assertFalse(
            repository.updateTerm(
                LocalDate.of(2027, 4, 1),
                LocalDate.of(2028, 3, 31)
            )
        )
        assertEquals(2026, dao.getSettings()?.activeAcademicYear)

        assertTrue(
            repository.updateTerm(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2027, 2, 28)
            )
        )
        assertEquals(LocalDate.of(2026, 5, 1), dao.getSettings()?.termStart)
        assertEquals(LocalDate.of(2027, 2, 28), dao.getSettings()?.termEnd)
    }
}
