package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ArrivalTimeSettingsTest {
    @Test fun notificationSettingsSurviveBothDesignModesAndMasterToggle() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = UiDesignPreferences(context)
        val previousMode = preferences.uiDesignMode.first()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val date = LocalDate.of(2026, 11, 9)
            val repository = SchedulerRepository(db, preferences)
            val original = SettingsEntity(termStart = date, termEnd = date.plusDays(30),
                lessonStartNotificationEnabled = true, lessonStartNotificationMinutesBefore = 7,
                lessonStartNotificationLiveUpdatesEnabled = true, lessonStartNotificationProgressCountsDown = true)
            db.schedulerDao().upsertSettings(original)
            for (mode in listOf(UiDesignMode.MATERIAL_3, UiDesignMode.MATERIAL_3_EXPRESSIVE, UiDesignMode.MATERIAL_3)) {
                repository.updateUiDesignMode(mode)
                assertEquals(original, db.schedulerDao().getSettings())
            }
            repository.toggleLessonStartNotifications(false)
            assertEquals(original.copy(lessonStartNotificationEnabled = false), db.schedulerDao().getSettings())
            repository.toggleLessonStartNotifications(true)
            assertEquals(original, db.schedulerDao().getSettings())
        } finally {
            preferences.setUiDesignMode(previousMode)
            db.close()
        }
    }

    @Test fun migrationPreservesTimesAndDistinguishesLegacyDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .callback(object : SupportSQLiteOpenHelper.Callback(52) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE settings (id INTEGER PRIMARY KEY NOT NULL, arrivalHour INTEGER NOT NULL, arrivalMinute INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO settings VALUES (1, 8, 30), (2, 9, 10), (3, -1, -1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_52_53.migrate(db)
            db.query("SELECT arrivalHour, arrivalMinute, arrivalTimeAuto FROM settings ORDER BY id").use {
                assertTrue(it.moveToNext()); assertEquals(8, it.getInt(0)); assertEquals(30, it.getInt(1)); assertEquals(1, it.getInt(2))
                assertTrue(it.moveToNext()); assertEquals(9, it.getInt(0)); assertEquals(10, it.getInt(1)); assertEquals(0, it.getInt(2))
                assertTrue(it.moveToNext()); assertEquals(-1, it.getInt(0)); assertEquals(1, it.getInt(2))
            }
        } finally { helper.close() }
    }

    @Test fun manualEightThirtySurvivesBackupAndLegacyBackupsUseAutomatic() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val date = LocalDate.of(2026, 11, 9)
            val dao = db.schedulerDao()
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30), arrivalTimeAuto = false))
            repository.initialize(date)
            val backup = JSONObject(repository.exportAllData())
            assertFalse(backup.getJSONObject("settings").getBoolean("arrivalTimeAuto"))
            repository.importAllData(backup.toString(), requireSettings = true)
            assertFalse(dao.getSettings()!!.arrivalTimeAuto)
            assertEquals(8, dao.getSettings()!!.arrivalHour)
            assertFalse(repository.exportSyncPayload().toString().contains("arrivalTimeAuto"))
            backup.put("version", 17)
            backup.getJSONObject("settings").remove("arrivalTimeAuto")
            repository.importAllData(backup.toString(), requireSettings = true)
            assertTrue(dao.getSettings()!!.arrivalTimeAuto)
            backup.getJSONObject("settings").put("arrivalHour", 9)
            repository.importAllData(backup.toString(), requireSettings = true)
            assertFalse(dao.getSettings()!!.arrivalTimeAuto)
        } finally { db.close() }
    }
}
