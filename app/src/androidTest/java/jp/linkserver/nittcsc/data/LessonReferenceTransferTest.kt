package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.nittcsc.logic.TimetableTerm
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LessonReferenceTransferTest {
    private val date = LocalDate.of(2026, 4, 6)
    private fun lesson(id: Long, day: Int, subject: String) = LessonEntity(id = id, academicYear = 2026,
        timetableTerm = TimetableTerm.FIRST, dayOfWeek = day, slotIndex = 0, mode = LessonMode.WEEKLY,
        weeklySubject = subject, weeklyTeacher = "先生", aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")

    @Test fun backupRestoresReferencesBySlotRatherThanOldDatabaseId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val from = source.schedulerDao(); val to = target.schedulerDao()
            val settings = SettingsEntity(termStart = date, termEnd = date.plusDays(30), activeAcademicYear = 2026)
            from.upsertSettings(settings); to.upsertSettings(settings)
            from.upsertLesson(lesson(71, 1, "英語II"))
            from.upsertTask(TaskEntity(lessonId = 71, subject = "英語II", title = "課題", dueDate = date, createdDate = date))
            from.upsertPlan(PlanEntity(lessonId = 71, subject = "英語Ⅱ", title = "予定", dueDate = date, createdDate = date))
            to.upsertLesson(lesson(500, 2, "別の授業"))
            val backup = SchedulerRepository(source, UiDesignPreferences(context)).exportAllData()
            val repository = SchedulerRepository(target, UiDesignPreferences(context))
            repository.importAllData(backup, requireSettings = true)
            val restored = to.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            assertNotEquals(71L, restored.id)
            assertEquals(restored.id, to.getTasksOnce().single().lessonId)
            assertEquals(restored.id, to.getPlansOnce().single().lessonId)

            val invalidReference = JSONObject(backup)
            invalidReference.getJSONArray("tasks").getJSONObject(0).getJSONObject("lessonReference").put("slotIndex", 1)
            repository.importAllData(invalidReference.toString(), requireSettings = true)
            assertNull(to.getTasksOnce().single().lessonId)
            assertNotNull(to.getPlansOnce().single().lessonId)

            val legacy = JSONObject(backup).put("version", 24)
            legacy.getJSONArray("tasks").getJSONObject(0).remove("lessonReference")
            legacy.getJSONArray("plans").getJSONObject(0).remove("lessonReference")
            repository.importAllData(legacy.toString(), requireSettings = true)
            assertNull(to.getTasksOnce().single().lessonId)
            assertNull(to.getPlansOnce().single().lessonId)
            assertEquals("課題", to.getTasksOnce().single().title)
        } finally { source.close(); target.close() }
    }

    @Test fun syncMapsRemoteReferencesAndKeepsLocalIdsWhenOnlyLessonsChange() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val from = source.schedulerDao(); val to = target.schedulerDao()
            val settings = SettingsEntity(termStart = date, termEnd = date.plusDays(30), activeAcademicYear = 2026)
            from.upsertSettings(settings); to.upsertSettings(settings)
            from.upsertLesson(lesson(71, 1, "英語II"))
            from.upsertTask(TaskEntity(lessonId = 71, subject = "英語II", title = "受信課題", dueDate = date, createdDate = date))
            from.upsertPlan(PlanEntity(lessonId = 71, subject = "英語II", title = "受信予定", dueDate = date, createdDate = date))
            to.upsertLesson(lesson(171, 1, "旧英語")); to.upsertLesson(lesson(71, 2, "別の授業"))
            to.upsertTask(TaskEntity(lessonId = 171, subject = "旧英語", title = "ローカル課題", dueDate = date, createdDate = date))
            to.upsertTask(TaskEntity(lessonId = 71, subject = "別の授業", title = "削除枠の課題", dueDate = date, createdDate = date))
            val sourceRepo = SchedulerRepository(source, UiDesignPreferences(context))
            val targetRepo = SchedulerRepository(target, UiDesignPreferences(context))
            val payload = sourceRepo.exportSyncPayload()
            val key = LessonSyncPartition(2026, TimetableTerm.FIRST).datasetKey
            val onlyLessons = JSONObject().putCurrentSyncProtocolVersion()
                .put(SECOND_TERM_START_SYNC_KEY, payload.getString(SECOND_TERM_START_SYNC_KEY))
                .put(key, payload.getJSONArray(key))
            targetRepo.applySyncPayload(onlyLessons)
            assertEquals(171L, to.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.id)
            assertEquals(171L, to.getTasksOnce().single { it.title == "ローカル課題" }.lessonId)
            assertEquals(71L, to.getTasksOnce().single { it.title == "削除枠の課題" }.lessonId)
            assertEquals("", to.getLesson(2026, TimetableTerm.FIRST, 2, 0)!!.weeklySubject)
            val onlyTasks = JSONObject().putCurrentSyncProtocolVersion()
                .put(SECOND_TERM_START_SYNC_KEY, payload.getString(SECOND_TERM_START_SYNC_KEY))
                .put(SchedulerRepository.DATASET_TASKS, payload.getJSONArray(SchedulerRepository.DATASET_TASKS))
            targetRepo.applySyncPayload(onlyTasks)
            assertEquals(171L, to.getTasksOnce().single().lessonId)
            targetRepo.applySyncPayload(payload)
            assertEquals(171L, to.getTasksOnce().single().lessonId)
            assertEquals(171L, to.getPlansOnce().single().lessonId)
            assertEquals("英語II", to.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
        } finally { source.close(); target.close() }
    }
}
