package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyTimetableMigrationTest {
    @Test
    fun singleTimetableUpgradeRemembersItsYearAndPreservesLessonId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "legacy-timetable-migration-test.db"
        context.deleteDatabase(databaseName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(46) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE settings (id INTEGER PRIMARY KEY NOT NULL, termStart TEXT NOT NULL)")
                        db.execSQL("INSERT INTO settings (id, termStart) VALUES (1, '2026-04-01')")
                        db.execSQL(
                            """
                            CREATE TABLE lessons (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                dayOfWeek INTEGER NOT NULL, slotIndex INTEGER NOT NULL,
                                mode TEXT NOT NULL, weeklySubject TEXT NOT NULL,
                                weeklyTeacher TEXT NOT NULL, weeklyLocation TEXT,
                                aSubject TEXT NOT NULL, aTeacher TEXT NOT NULL, aLocation TEXT,
                                bSubject TEXT NOT NULL, bTeacher TEXT NOT NULL, bLocation TEXT
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO lessons (
                                id, dayOfWeek, slotIndex, mode, weeklySubject, weeklyTeacher,
                                aSubject, aTeacher, bSubject, bTeacher
                            ) VALUES (42, 1, 0, 'WEEKLY', '数学', '山田', '', '', '', '')
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_46_47.migrate(db)
            AppDatabase.MIGRATION_47_48.migrate(db)

            db.query("SELECT pendingLegacyTimetableYear, activeAcademicYear FROM settings WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(2026, it.getInt(0))
                assertEquals(2026, it.getInt(1))
            }
            db.query("SELECT id, academicYear, timetableTerm, weeklySubject FROM lessons").use {
                it.moveToFirst()
                assertEquals(42, it.getInt(0))
                assertEquals(2026, it.getInt(1))
                assertEquals("FIRST", it.getString(2))
                assertEquals("数学", it.getString(3))
            }
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }
}
