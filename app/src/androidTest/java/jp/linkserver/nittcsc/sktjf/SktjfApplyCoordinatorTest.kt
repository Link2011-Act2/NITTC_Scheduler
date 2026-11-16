package jp.linkserver.nittcsc.sktjf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.TimetableTerm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class SktjfApplyCoordinatorTest {
    private val date = LocalDate.of(2026, 4, 6)
    private fun document(): SktjfDocument {
        val source = SktjfReference("src", 1, null)
        return SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0",
            listOf(SktjfSource("src", "test.pdf", "a".repeat(64), null)),
            listOf(SktjfPeriod(1, LocalTime.of(8, 40), LocalTime.of(10, 10))),
            listOf(SktjfSlot(1, 1, SktjfSlotState.PRESENT, false, listOf(source))),
            listOf(SktjfLesson("lesson-1", 1, 1, SktjfCycle.WEEKLY, "配信授業", listOf("先生"), emptyList(),
                SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, listOf(source))),
            listOf(SktjfCalendarDay(date, SktjfDayType.B, null, null, null, listOf(source))))
    }

    @Test fun stalePreviewDoesNotWriteAndConfirmedApplyPreservesLessonId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val row = LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1,
                slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "手入力", weeklyTeacher = "先生",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
            dao.upsertLesson(row); dao.upsertDayType(DayTypeEntity(date, DayType.A))
            dao.upsertTask(TaskEntity(id = 93, lessonId = 71, subject = "手入力", title = "課題", dueDate = date, createdDate = date))
            val coordinator = SktjfApplyCoordinator(db, repository)
            val stale = (coordinator.preview(document()) as SktjfPreviewResult.Ready).preview
            dao.upsertLesson(row.copy(weeklySubject = "後から編集"))
            assertEquals(SktjfApplyResult.Stale, coordinator.apply(stale, emptyMap(), emptyMap()))
            assertTrue(dao.getSktjfSourceStates().isEmpty())
            assertEquals(DayType.A, dao.getDayType(date)!!.dayType)
            val preview = (coordinator.preview(document()) as SktjfPreviewResult.Ready).preview
            assertEquals(1, preview.lessonConflicts.size); assertEquals(1, preview.calendarConflicts.size)
            assertEquals(SktjfApplyResult.Blocked, coordinator.apply(preview, emptyMap(), mapOf(date to SktjfConflictChoice.APPLY_INCOMING)))
            assertTrue(dao.getSktjfSourceStates().isEmpty())
            assertEquals(DayType.A, dao.getDayType(date)!!.dayType)
            assertEquals("後から編集", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview,
                mapOf("lesson-1" to SktjfConflictChoice.KEEP_LOCAL), mapOf(date to SktjfConflictChoice.APPLY_INCOMING)))
            assertEquals("後から編集", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals(71L, dao.getTaskById(93)!!.lessonId)
            assertEquals(DayType.B, dao.getDayType(date)!!.dayType)
            assertFalse(dao.getSktjfSourceStates().single().requiresRelinking)
        } finally { db.close() }
    }

    @Test fun conditionalLessonReplacesConflictingRowAndRetainsItsIdentityAcrossUpdates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)))
            dao.upsertLesson(LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1,
                slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "手入力", weeklyTeacher = "先生",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
            dao.upsertDayType(DayTypeEntity(date, DayType.A))
            val normal = document().let { doc -> doc.copy(lessons = doc.lessons.map {
                it.copy(conditions = SktjfConditions(emptySet(), setOf("DD")))
            }) }; val dd = normal.lessons.single().copy(id = "dd-1", subject = "DD授業",
                conditions = SktjfConditions(setOf("DD"), emptySet()))
            val incoming = normal.copy(lessons = normal.lessons + dd)
            val coordinator = SktjfApplyCoordinator(db, repository)
            val preview = (coordinator.preview(incoming) as SktjfPreviewResult.Ready).preview
            assertEquals(1, preview.lessonConflicts.size)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview,
                mapOf("dd-1" to SktjfConflictChoice.APPLY_INCOMING),
                mapOf(date to SktjfConflictChoice.APPLY_INCOMING)))
            assertEquals("DD授業", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals(71L, dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.id)
            assertTrue(dao.getSktjfExtraLessons().isEmpty())
            val revised = incoming.copy(revision = "2026.S.1", lessons = listOf(incoming.lessons.first(), dd.copy(subject = "改訂DD授業")))
            val next = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            assertTrue(next.lessonConflicts.isEmpty()); assertTrue(next.calendarConflicts.isEmpty())
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(next, emptyMap(), emptyMap()))
            assertEquals(71L, dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.id)
            assertEquals("改訂DD授業", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertTrue(dao.getSktjfExtraLessons().isEmpty())
        } finally { db.close() }
    }

    @Test fun extraEditUpdatesOnlyThatLessonAndRejectsStaleEdit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC")); dao.upsertNittcProfiles(listOf(profile))
            val content = SktjfLessonContent(1, 1, SktjfCycle.A, "DD授業", listOf("先生"), emptyList(),
                SktjfConditions(setOf("DD"), emptySet()), SktjfLessonKind.NORMAL, null)
            val json = SktjfBaselineCodec.encode(SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf("extra-1" to content)),
                SktjfCalendarBaseline(2026, "S", "1-1", emptyMap())))
            val source = SktjfExtraLessonEntity("extra-1", 2026, "S", "1-1", "source-1", json)
            dao.upsertSktjfExtraLessons(listOf(source))
            val expected = SktjfExtraLessonResolver.create(SchedulerUsageMode.NITTC, listOf(profile), listOf(source))
                .lessonsOn(date, 1, DayTypeEntity(date, DayType.A), true, 10, 1).single()
            assertTrue(repository.editSktjfExtraLesson(expected, SktjfExtraLessonDraft("変更後", "新しい先生", "教室")))
            val saved = dao.getSktjfExtraLesson("extra-1")!!
            assertEquals("source-1", saved.sourceLessonId)
            assertEquals("変更後", SktjfBaselineCodec.decode(saved.contentJson).lessons.lessons.getValue("extra-1").subject)
            assertFalse(repository.editSktjfExtraLesson(expected, SktjfExtraLessonDraft("古い変更", "", "")))
            assertEquals(saved, dao.getSktjfExtraLesson("extra-1"))
            assertTrue(dao.getAllSyncDatasetMeta().any { it.datasetKey == LessonSyncPartition(2026, TimetableTerm.FIRST).datasetKey })
        } finally { db.close() }
    }

    @Test fun mixedEventIsSavedWithTheRegularDayAndReopenedWithoutConflict() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            dao.upsertDayType(DayTypeEntity(date, DayType.A))
            val initial = document()
            val incoming = initial.copy(calendar = listOf(initial.calendar.single().copy(
                event = SktjfEvent("清掃", SktjfEventKind.OTHER))))
            val coordinator = SktjfApplyCoordinator(db, repository)
            val preview = (coordinator.preview(incoming) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfCalendarConflictReason.BOTH_CHANGED, preview.calendarConflicts.single().reason)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview, emptyMap(),
                mapOf(date to SktjfConflictChoice.APPLY_INCOMING)))
            assertEquals(DayType.B, dao.getDayType(date)!!.dayType)
            assertEquals("清掃", dao.getDayType(date)!!.schoolEventName)
            assertEquals("OTHER", dao.getDayType(date)!!.schoolEventKind)
            assertEquals("清掃", SktjfBaselineCodec.decode(dao.getSktjfSourceStates().single().baselineJson)
                .calendar.days.getValue(date).event!!.name)
            val reopened = (coordinator.preview(incoming) as SktjfPreviewResult.Ready).preview
            assertTrue(reopened.calendarConflicts.isEmpty())
        } finally { db.close() }
    }

    @Test fun localFileCanBeFollowedByAnOfficialEarlierRevision() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val coordinator = SktjfApplyCoordinator(db, repository)
            val local = document().copy(revision = "2026.S.5")
            val preview = (coordinator.preview(local) as SktjfPreviewResult.Ready).preview.copy(localFile = true)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview, emptyMap(), emptyMap()))
            assertTrue(dao.getSktjfSourceStates().single().isLocalSktjfSource())
            val official = (coordinator.preview(document()) as SktjfPreviewResult.Ready).preview
            assertTrue(official.lessonConflicts.isEmpty()); assertTrue(official.calendarConflicts.isEmpty())
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(official, emptyMap(), emptyMap()))
            assertFalse(dao.getSktjfSourceStates().single().isLocalSktjfSource())
            assertEquals("2026.S.0", dao.getSktjfSourceStates().single().revision)
        } finally { db.close() }
    }
}
