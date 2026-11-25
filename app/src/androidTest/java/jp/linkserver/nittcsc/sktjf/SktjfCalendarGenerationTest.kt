package jp.linkserver.nittcsc.sktjf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.ExportRange
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.LessonWeekRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SktjfCalendarGenerationTest {
    @Test fun timedSourceUsesOneCalendarLessonAndPreservesItsIdentity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val start = LocalDate.of(2026, 4, 6); val end = start.plusDays(62)
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = start, termEnd = end, enableSemesterTimetables = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)))
            val before = SktjfLessonContent(1, 1, SktjfCycle.WEEKLY, "製図", listOf("先生"), emptyList(),
                SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, SktjfV2Range(LessonWeekRange(end = 8), "8週まで"))
            val after = before.copy(subject = "卒研", weekRange = SktjfV2Range(LessonWeekRange(start = 9), "9週から"))
            val policy = SktjfCountingPolicy(true, false, emptyMap(), true, sources = listOf(SktjfReference("src", 1, null)))
            val group = SktjfV2WeekSeries("shared", 1, setOf(1), setOf("before", "after"), SktjfCycle.WEEKLY,
                listOf(SktjfReference("src", 1, null)), policy)
            val calendar = (0L..62L).associate { offset -> start.plusDays(offset) to SktjfCalendarContent(SktjfDayType.A, null, null, null) }
            val source = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf("before" to before, "after" to after)),
                SktjfCalendarBaseline(2026, "S", "1-1", calendar), series = listOf(group), termBounds = SktjfTermBounds(start, end))
            val links = SktjfSourceLinks.encode(listOf(SktjfSourceLink("before", 1, 1, SktjfCycle.WEEKLY)))
            dao.upsertSktjfSourceStates(listOf(SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.0.Beta", SktjfBaselineCodec.encode(source), false, links)))
            dao.upsertLesson(LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1,
                slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "編集済み製図", weeklyTeacher = "先生",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
            val extraSource = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf("alt" to after)),
                SktjfCalendarBaseline(2026, "S", "1-1", emptyMap()))
            dao.upsertSktjfExtraLessons(listOf(SktjfExtraLessonEntity("alt", 2026, "S", "1-1", "after", SktjfBaselineCodec.encode(extraSource))))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val eighth = repository.generateLessons(ExportRange.Custom(start.plusWeeks(7), start.plusWeeks(7)))
            val ninth = repository.generateLessons(ExportRange.Custom(start.plusWeeks(8), start.plusWeeks(8)))
            assertEquals(listOf("編集済み製図"), eighth.map { it.subject })
            assertEquals(listOf("卒研"), ninth.map { it.subject })
            assertEquals("alt", ninth.single().extraLessonId)
        } finally { db.close() }
    }

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
