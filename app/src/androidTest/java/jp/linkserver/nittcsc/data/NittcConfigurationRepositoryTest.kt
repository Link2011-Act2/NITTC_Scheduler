package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NittcConfigurationRepositoryTest {
    @Test fun savesModeAndYearProfileTogetherAndRejectsStaleChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = SchedulerRepository(db, UiDesignPreferences(context)); val dao = db.schedulerDao()
            val old = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false, "[\"extra\"]")
            dao.upsertNittcProfiles(listOf(old))
            val next = old.copy(classId = "1-2", isDd = true)
            assertTrue(repository.saveNittcConfiguration(null, old, SchedulerUsageMode.NITTC, next))
            assertEquals("NITTC", dao.getUsageMode()?.mode)
            assertEquals(next, dao.getNittcProfilesOnce().single())
            val stale = next.copy(classId = "1-3")
            assertFalse(repository.saveNittcConfiguration(null, next, SchedulerUsageMode.NITTC, stale))
            assertFalse(repository.saveNittcConfiguration(dao.getUsageMode(), old, SchedulerUsageMode.NITTC, stale))
            assertEquals(next, dao.getNittcProfilesOnce().single())
            assertTrue(repository.saveNittcConfiguration(dao.getUsageMode(), next, SchedulerUsageMode.GENERIC, null))
            assertEquals(next, dao.getNittcProfilesOnce().single())
        } finally { db.close() }
    }

    @Test fun yearChoicesAreAtomicAndDoNotErasePriorData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = SchedulerRepository(db, UiDesignPreferences(context)); val dao = db.schedulerDao()
            val mode = UsageModeEntity(mode = "NITTC")
            val old = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)
            dao.upsertUsageMode(mode); dao.upsertNittcProfiles(listOf(old))
            val date = java.time.LocalDate.of(2026, 4, 6)
            dao.upsertTask(TaskEntity(id = 41, subject = "数学", title = "課題", dueDate = date, createdDate = date))
            assertTrue(repository.recordNittcYearOutcome(2027, mode, null, null, NittcYearOutcome.DEFERRED))
            val deferred = NittcYearTransitionEntity(2027, "DEFERRED")
            val next = NittcProfileEntity(2027, "UNDERGRADUATE", 2, "2M", false, false)
            assertFalse(repository.completeNittcYearOutcome(2027, mode, null, null, NittcYearOutcome.PROMOTED, next))
            assertEquals(deferred, dao.getNittcYearTransition(2027))
            assertTrue(repository.completeNittcYearOutcome(2027, mode, null, deferred, NittcYearOutcome.PROMOTED, next))
            assertEquals(listOf(old, next), dao.getNittcProfilesOnce())
            assertEquals("PROMOTED", dao.getNittcYearTransition(2027)?.outcome)
            assertTrue(repository.recordNittcYearOutcome(2028, mode, null, null, NittcYearOutcome.GRADUATED))
            assertEquals("GRADUATED", dao.getNittcYearTransition(2028)?.outcome)
            assertEquals("課題", dao.getTaskById(41)?.title)
            assertEquals(listOf(old, next), dao.getNittcProfilesOnce())
            assertTrue(repository.recordNittcYearOutcome(2029, mode, null, null, NittcYearOutcome.DEFERRED))
            val resumed = NittcProfileEntity(2029, "ADVANCED", 1, "AC-A", false, false)
            assertTrue(repository.saveNittcConfiguration(mode, null, SchedulerUsageMode.NITTC, resumed))
            assertNull(dao.getNittcYearTransition(2029))
            assertEquals("GRADUATED", dao.getNittcYearTransition(2028)?.outcome)
        } finally { db.close() }
    }
}
