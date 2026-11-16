package jp.linkserver.nittcsc.sktjf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.ExportRange
import jp.linkserver.nittcsc.logic.TimetableTerm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SktjfCalendarGenerationTest {
    @Test fun weekendTransferAndSameNamedExtraSurviveRepositoryGeneration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val date = LocalDate.of(2026, 4, 5)
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)))
            dao.upsertDayType(DayTypeEntity(date, DayType.A, overrideLessonDayOfWeek = 1, overrideLessonDayType = DayType.B))
            dao.upsertLesson(LessonEntity(id = 1, academicYear = 2026, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1,
                slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "数学", weeklyTeacher = "先生A",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
            val content = SktjfLessonContent(1, 1, SktjfCycle.B, "数学", listOf("先生B"), listOf("教室B"),
                SktjfConditions(setOf("DD"), emptySet()), SktjfLessonKind.NORMAL, null)
            val baseline = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf("extra" to content)),
                SktjfCalendarBaseline(2026, "S", "1-1", emptyMap()))
            dao.upsertSktjfExtraLessons(listOf(SktjfExtraLessonEntity("extra", 2026, "S", "1-1", "source", SktjfBaselineCodec.encode(baseline))))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val lessons = repository.generateLessons(ExportRange.Custom(date, date))
            assertEquals(2, lessons.size)
            assertEquals(listOf(null, "extra"), lessons.map { it.extraLessonId })
            assertEquals("教室B", lessons.last().location)
            assertEquals(1, dao.getDayType(date)?.overrideLessonDayOfWeek)
            assertEquals(DayType.B, dao.getDayType(date)?.overrideLessonDayType)
        } finally { db.close() }
    }
}
