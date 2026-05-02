package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SettingsEntity::class,
        DayTypeEntity::class,
        LongBreakEntity::class,
        LessonEntity::class,
        CancelledLessonEntity::class,
        TaskEntity::class,
        PlanEntity::class,
        SyncDatasetMetaEntity::class,
        SyncProfileEntity::class,
        SyncRegisteredDeviceEntity::class,
        SyncTrustedPeerEntity::class
    ],
    version = 25,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun schedulerDao(): SchedulerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun hasColumn(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            tableName: String,
            columnName: String
        ): Boolean {
            db.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                        return true
                    }
                }
            }
            return false
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN enableLocalAi INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN hfToken TEXT")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN useGpuAcceleration INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // LessonEntityに場所フィールドを追加
                db.execSQL("ALTER TABLE lessons ADD COLUMN weeklyLocation TEXT")
                db.execSQL("ALTER TABLE lessons ADD COLUMN aLocation TEXT")
                db.execSQL("ALTER TABLE lessons ADD COLUMN bLocation TEXT")
                
                // TaskEntityテーブルを作成
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        lessonId INTEGER,
                        subject TEXT NOT NULL,
                        teacher TEXT,
                        title TEXT NOT NULL,
                        description TEXT,
                        dueDate TEXT NOT NULL,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        completedDate TEXT,
                        createdDate TEXT NOT NULL,
                        priority INTEGER NOT NULL DEFAULT 0,
                        useTeacherMatching INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_dueDate ON tasks(dueDate)")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // useGpuAcceleration カラムを削除（テーブル再作成）
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS settings_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        termStart TEXT NOT NULL,
                        termEnd TEXT NOT NULL,
                        enableLocalAi INTEGER NOT NULL DEFAULT 0,
                        hfToken TEXT
                    )
                """)
                db.execSQL("""
                    INSERT INTO settings_new (id, termStart, termEnd, enableLocalAi, hfToken)
                    SELECT id, termStart, termEnd, enableLocalAi, hfToken FROM settings
                """)
                db.execSQL("DROP TABLE settings")
                db.execSQL("ALTER TABLE settings_new RENAME TO settings")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN periodsPerDay INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE settings ADD COLUMN periodDurationMin INTEGER NOT NULL DEFAULT 90")
                db.execSQL("ALTER TABLE settings ADD COLUMN breakBetweenPeriodsMin INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE settings ADD COLUMN lunchBreakMin INTEGER NOT NULL DEFAULT 60")
                db.execSQL("ALTER TABLE settings ADD COLUMN firstPeriodStartHour INTEGER NOT NULL DEFAULT 8")
                db.execSQL("ALTER TABLE settings ADD COLUMN firstPeriodStartMinute INTEGER NOT NULL DEFAULT 40")
                db.execSQL("ALTER TABLE settings ADD COLUMN useKosenMode INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN lunchAfterPeriod INTEGER NOT NULL DEFAULT 2")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN arrivalHour INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE settings ADD COLUMN arrivalMinute INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE settings ADD COLUMN departureHour INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE settings ADD COLUMN departureMinute INTEGER NOT NULL DEFAULT -1")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN dueHour INTEGER NOT NULL DEFAULT 23")
                db.execSQL("ALTER TABLE tasks ADD COLUMN dueMinute INTEGER NOT NULL DEFAULT 59")
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN useDrawerNavigation INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN addTasksToCalendar INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN calendarEventId INTEGER")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN showCurrentTimeMarker INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        lessonId INTEGER,
                        subject TEXT NOT NULL,
                        teacher TEXT,
                        title TEXT NOT NULL,
                        description TEXT,
                        dueDate TEXT NOT NULL,
                        dueHour INTEGER NOT NULL DEFAULT 23,
                        dueMinute INTEGER NOT NULL DEFAULT 59,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        completedDate TEXT,
                        createdDate TEXT NOT NULL,
                        priority INTEGER NOT NULL DEFAULT 0,
                        useTeacherMatching INTEGER NOT NULL DEFAULT 0,
                        calendarEventId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plans_dueDate ON plans(dueDate)")
            }
        }

        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                if (!hasColumn(db, "plans", "calendarEventId")) {
                    db.execSQL("ALTER TABLE plans ADD COLUMN calendarEventId INTEGER")
                }
            }
        }

        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN unifyTaskPlanView INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE day_types ADD COLUMN overrideLessonDayOfWeek INTEGER")
                db.execSQL("ALTER TABLE day_types ADD COLUMN overrideLessonDayType TEXT")
            }
        }

        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_profile (
                        id INTEGER NOT NULL PRIMARY KEY,
                        deviceId TEXT NOT NULL DEFAULT '',
                        userNickname TEXT NOT NULL DEFAULT '',
                        deviceName TEXT NOT NULL DEFAULT '',
                        passwordPlaintext TEXT NOT NULL DEFAULT '',
                        passwordHash TEXT NOT NULL DEFAULT '',
                        passwordLength INTEGER NOT NULL DEFAULT 0,
                        autoSyncEnabled INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_registered_devices (
                        deviceId TEXT NOT NULL PRIMARY KEY,
                        userNickname TEXT NOT NULL DEFAULT '',
                        deviceName TEXT NOT NULL DEFAULT '',
                        host TEXT NOT NULL DEFAULT '',
                        port INTEGER NOT NULL DEFAULT 0,
                        trustToken TEXT NOT NULL DEFAULT '',
                        addedAt INTEGER NOT NULL DEFAULT 0,
                        lastSeenAt INTEGER NOT NULL DEFAULT 0,
                        lastTasksSyncAt INTEGER NOT NULL DEFAULT 0,
                        lastPlansSyncAt INTEGER NOT NULL DEFAULT 0,
                        lastScheduleSettingsSyncAt INTEGER NOT NULL DEFAULT 0,
                        lastLessonsSyncAt INTEGER NOT NULL DEFAULT 0,
                        lastDayTypesSyncAt INTEGER NOT NULL DEFAULT 0,
                        lastLongBreaksSyncAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_trusted_peers (
                        peerDeviceId TEXT NOT NULL PRIMARY KEY,
                        peerUserNickname TEXT NOT NULL DEFAULT '',
                        peerDeviceName TEXT NOT NULL DEFAULT '',
                        trustToken TEXT NOT NULL DEFAULT '',
                        issuedAt INTEGER NOT NULL DEFAULT 0,
                        lastUsedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cancelled_lessons (
                        date TEXT NOT NULL,
                        slotIndex INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(date, slotIndex)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_profile ADD COLUMN conflictAutoNewerFirst INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_registered_devices ADD COLUMN lastCancelledLessonsSyncAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_dataset_meta (
                        datasetKey TEXT NOT NULL PRIMARY KEY,
                        lastUpdatedAt INTEGER NOT NULL DEFAULT 0,
                        lastUpdatedByDeviceId TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                val now = System.currentTimeMillis()
                val datasetKeys = listOf(
                    "tasks",
                    "plans",
                    "scheduleSettings",
                    "lessons",
                    "dayTypes",
                    "longBreaks",
                    "cancelledLessons"
                )
                datasetKeys.forEach { key ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO sync_dataset_meta(datasetKey, lastUpdatedAt, lastUpdatedByDeviceId) VALUES (?, ?, '')",
                        arrayOf<Any>(key, now)
                    )
                }
            }
        }

        val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_registered_devices ADD COLUMN serverCertFingerprint TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN enableTlsSync INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_24_25 = object : androidx.room.migration.Migration(24, 25) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plans ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nittc_scheduler.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25)
                 .build().also { INSTANCE = it }
            }
        }
    }
}
