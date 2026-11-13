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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NittcProfileMigrationTest {
    @Test fun migrationPreservesExistingLessonsTasksAndRequiresModeChoice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sktjf-migration-${UUID.randomUUID()}.db"
        val date = LocalDate.of(2026, 4, 6)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_53_54).build()
        try {
            val fresh = open()
            assertNull(fresh.schedulerDao().getUsageMode())
            fresh.schedulerDao().upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            fresh.schedulerDao().upsertLesson(LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
                dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "数学", weeklyTeacher = "先生",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
            fresh.schedulerDao().upsertTask(TaskEntity(id = 93, lessonId = 71, subject = "数学", title = "課題", dueDate = date, createdDate = date))
            fresh.close()
            // v54 differs from v53 only by these two tables; reconstruct the v53 shape.
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sql ->
                sql.execSQL("DROP TABLE usage_mode"); sql.execSQL("DROP TABLE nittc_profiles"); sql.version = 53
            }
            val migrated = open()
            try {
                val mode = migrated.schedulerDao().getUsageMode()
                assertNotNull(mode); assertNull(mode!!.mode); assertTrue(mode.migratedFromLegacy)
                assertTrue(migrated.schedulerDao().getNittcProfilesOnce().isEmpty())
                assertEquals("数学", migrated.schedulerDao().getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
                assertEquals(71L, migrated.schedulerDao().getTaskById(93)!!.lessonId)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun backupRoundTripKeepsHistoryAndOlderBackupsPreserveLocalProfile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val date = LocalDate.of(2026, 4, 6)
            val repository = SchedulerRepository(db, UiDesignPreferences(context)); val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            repository.initialize(date)
            repository.setUsageMode(SchedulerUsageMode.NITTC)
            val first = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)
            val second = NittcProfileEntity(2027, "UNDERGRADUATE", 2, "2M", false, true)
            repository.saveNittcProfile(first); repository.saveNittcProfile(second)
            val backup = JSONObject(repository.exportAllData())
            assertEquals(19, backup.getInt("version"))
            assertFalse(repository.exportSyncPayload().toString().contains("nittcProfile"))
            repository.setUsageMode(SchedulerUsageMode.GENERIC)
            repository.importAllData(backup.toString(), requireSettings = true)
            assertEquals("NITTC", dao.getUsageMode()!!.mode)
            assertEquals(listOf(first, second), dao.getNittcProfilesOnce())
            val legacy = JSONObject(backup.toString()).put("version", 18).also { it.remove("nittcProfile") }
            repository.setUsageMode(SchedulerUsageMode.GENERIC)
            repository.importAllData(legacy.toString(), requireSettings = true)
            assertEquals("GENERIC", dao.getUsageMode()!!.mode)
            assertEquals(listOf(first, second), dao.getNittcProfilesOnce())
        } finally { db.close() }
    }
}
