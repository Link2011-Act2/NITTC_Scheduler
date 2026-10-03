package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SyncButtonSettingsTest {
    @Test fun upgradeEnablesOnlyConfiguredProfilesAndNewRowsRemainHidden() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .callback(object : SupportSQLiteOpenHelper.Callback(51) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE settings (id INTEGER PRIMARY KEY NOT NULL)")
                        db.execSQL("CREATE TABLE sync_profile (id INTEGER PRIMARY KEY NOT NULL, userNickname TEXT NOT NULL, passwordLength INTEGER NOT NULL)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        try {
            val db = helper.writableDatabase
            for ((nickname, length) in listOf(null to 0, "" to 0, "名前" to 0, "   " to 8, "名前" to 8)) {
                db.execSQL("DROP TABLE settings")
                db.execSQL("CREATE TABLE settings (id INTEGER PRIMARY KEY NOT NULL)")
                db.execSQL("INSERT INTO settings VALUES (1)")
                db.execSQL("DELETE FROM sync_profile")
                if (nickname != null) db.execSQL("INSERT INTO sync_profile VALUES (1, ?, ?)", arrayOf<Any>(nickname, length))
                AppDatabase.MIGRATION_51_52.migrate(db)
                db.query("SELECT showSyncButton FROM settings WHERE id = 1").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(if (nickname == "名前" && length > 0) 1 else 0, it.getInt(0))
                }
                db.execSQL("INSERT INTO settings (id) VALUES (2)")
                db.query("SELECT showSyncButton FROM settings WHERE id = 2").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
            }
        } finally { helper.close() }
    }

    @Test fun visibilityPersistsAcrossInitializationAndBackupWithLegacyDefault() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val today = LocalDate.of(2026, 10, 3)
            dao.upsertSettings(SettingsEntity(termStart = today, termEnd = today.plusMonths(6)))
            assertFalse(dao.getSettings()!!.showSyncButton)
            dao.upsertSyncProfile(SyncProfileEntity(deviceId = "test", userNickname = "名前", passwordLength = 8))
            repository.initialize(today)
            assertFalse(dao.getSettings()!!.showSyncButton)
            repository.toggleShowSyncButton(true)
            val backup = JSONObject(repository.exportAllData())
            assertEquals(17, backup.getInt("version"))
            assertTrue(backup.getJSONObject("settings").getBoolean("showSyncButton"))
            assertFalse(repository.exportSyncPayload().toString().contains("showSyncButton"))
            repository.toggleShowSyncButton(false)
            repository.initialize(today)
            assertFalse(dao.getSettings()!!.showSyncButton)
            repository.importAllData(backup.toString(), requireSettings = true)
            assertTrue(dao.getSettings()!!.showSyncButton)
            backup.put("version", 16)
            backup.getJSONObject("settings").remove("showSyncButton")
            repository.importAllData(backup.toString(), requireSettings = true)
            assertFalse(dao.getSettings()!!.showSyncButton)
        } finally { db.close() }
    }
}
