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
        ChangedLessonEntity::class,
        LessonNoteEntity::class,
        ExamDayScheduleEntity::class,
        ExamLessonEntity::class,
        LessonNotificationExclusionEntity::class,
        TaskEntity::class,
        PlanEntity::class,
        SyncDatasetMetaEntity::class,
        SyncProfileEntity::class,
        SyncRegisteredDeviceEntity::class,
        SyncTrustedPeerEntity::class
    ],
    version = 48,
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

        val MIGRATION_25_26 = object : androidx.room.migration.Migration(25, 26) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderDate TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderHour INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderMinute INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderCalendarEventId INTEGER")
            }
        }

        val MIGRATION_26_27 = object : androidx.room.migration.Migration(26, 27) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plans ADD COLUMN reminderDate TEXT")
                db.execSQL("ALTER TABLE plans ADD COLUMN reminderHour INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE plans ADD COLUMN reminderMinute INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plans ADD COLUMN reminderCalendarEventId INTEGER")
            }
        }

        val MIGRATION_27_28 = object : androidx.room.migration.Migration(27, 28) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN showWeekdayOnDates INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_28_29 = object : androidx.room.migration.Migration(28, 29) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS changed_lessons (
                        date TEXT NOT NULL,
                        slotIndex INTEGER NOT NULL,
                        subject TEXT NOT NULL,
                        teacher TEXT NOT NULL,
                        location TEXT,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(date, slotIndex)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_29_30 = object : androidx.room.migration.Migration(29, 30) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE day_types ADD COLUMN holidaySpecialLabel TEXT")
            }
        }

        val MIGRATION_30_31 = object : androidx.room.migration.Migration(30, 31) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN useAdvancedTimeSettingsUi INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_31_32 = object : androidx.room.migration.Migration(31, 32) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN lessonStartNotificationEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN lessonStartNotificationMinutesBefore INTEGER NOT NULL DEFAULT 10")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lesson_notification_exclusions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        subject TEXT NOT NULL,
                        teacher TEXT,
                        matchTeacher INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_32_33 = object : androidx.room.migration.Migration(32, 33) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN lessonStartNotificationLiveUpdatesEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_33_34 = object : androidx.room.migration.Migration(33, 34) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN lessonStartNotificationProgressCountsDown INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_34_35 = object : androidx.room.migration.Migration(34, 35) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN syncLessonsToCalendar INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN lessonCalendarSyncStart TEXT")
                db.execSQL("ALTER TABLE settings ADD COLUMN lessonCalendarSyncEnd TEXT")
            }
        }

        val MIGRATION_35_36 = object : androidx.room.migration.Migration(35, 36) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_registered_devices ADD COLUMN lastChangedLessonsSyncAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_36_37 = object : androidx.room.migration.Migration(36, 37) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN lessonStartNotificationLiveUpdateEarlyMinutes INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_37_38 = object : androidx.room.migration.Migration(37, 38) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("UPDATE settings SET lessonStartNotificationLiveUpdateEarlyMinutes = 1 WHERE lessonStartNotificationLiveUpdateEarlyMinutes = 0")
            }
        }

        val MIGRATION_38_39 = object : androidx.room.migration.Migration(38, 39) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN enableNaturalLanguageTaskAdd INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_39_40 = object : androidx.room.migration.Migration(39, 40) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN lessonStartNotificationChipMode TEXT NOT NULL DEFAULT 'MINUTE_TEXT'")
            }
        }

        val MIGRATION_40_41 = object : androidx.room.migration.Migration(40, 41) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lesson_notes (
                        date TEXT NOT NULL,
                        slotIndex INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(date, slotIndex)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lesson_notes_date ON lesson_notes(date)")
                db.execSQL("ALTER TABLE sync_registered_devices ADD COLUMN lastLessonNotesSyncAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "INSERT OR IGNORE INTO sync_dataset_meta(datasetKey, lastUpdatedAt, lastUpdatedByDeviceId) VALUES ('lessonNotes', 0, '')"
                )
            }
        }

        val MIGRATION_41_42 = object : androidx.room.migration.Migration(41, 42) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE settings
                    SET arrivalHour = 8,
                        arrivalMinute = 30
                    WHERE arrivalHour < 0
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_42_43 = object : androidx.room.migration.Migration(42, 43) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN enableLessonNotes INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_43_44 = object : androidx.room.migration.Migration(43, 44) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN enableExamTimetable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN examPeriodsPerDay INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE settings ADD COLUMN examPeriodDurationMin INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE settings ADD COLUMN examBreakBetweenPeriodsMin INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE settings ADD COLUMN examLunchBreakMin INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE settings ADD COLUMN examLunchAfterPeriod INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE settings ADD COLUMN examFirstPeriodStartHour INTEGER NOT NULL DEFAULT 8")
                db.execSQL("ALTER TABLE settings ADD COLUMN examFirstPeriodStartMinute INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE settings ADD COLUMN examArrivalHour INTEGER NOT NULL DEFAULT 8")
                db.execSQL("ALTER TABLE settings ADD COLUMN examArrivalMinute INTEGER NOT NULL DEFAULT 30")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exam_day_schedules (
                        date TEXT NOT NULL PRIMARY KEY,
                        arrivalHour INTEGER NOT NULL,
                        arrivalMinute INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exam_lessons (
                        date TEXT NOT NULL,
                        slotIndex INTEGER NOT NULL,
                        startHour INTEGER NOT NULL,
                        startMinute INTEGER NOT NULL,
                        endHour INTEGER NOT NULL,
                        endMinute INTEGER NOT NULL,
                        subject TEXT NOT NULL,
                        teacher TEXT NOT NULL,
                        location TEXT NOT NULL,
                        memo TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(date, slotIndex)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exam_lessons_date ON exam_lessons(date)")
                db.execSQL("ALTER TABLE sync_registered_devices ADD COLUMN lastExamTimetablesSyncAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "INSERT OR IGNORE INTO sync_dataset_meta(datasetKey, lastUpdatedAt, lastUpdatedByDeviceId) VALUES ('examTimetables', 0, '')"
                )
            }
        }

        val MIGRATION_44_45 = object : androidx.room.migration.Migration(44, 45) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exam_day_schedules ADD COLUMN examName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_45_46 = object : androidx.room.migration.Migration(45, 46) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN periodLabelStyle TEXT NOT NULL DEFAULT 'PAIR_KOSHI'"
                )
                db.execSQL(
                    """
                    UPDATE settings
                    SET periodLabelStyle = CASE
                        WHEN useKosenMode = 1 THEN 'PAIR_KOSHI'
                        ELSE 'SINGLE_KOSHI'
                    END
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_46_47 = object : androidx.room.migration.Migration(46, 47) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN enableSemesterTimetables INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lessons_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timetableTerm TEXT NOT NULL,
                        dayOfWeek INTEGER NOT NULL,
                        slotIndex INTEGER NOT NULL,
                        mode TEXT NOT NULL,
                        weeklySubject TEXT NOT NULL,
                        weeklyTeacher TEXT NOT NULL,
                        weeklyLocation TEXT,
                        aSubject TEXT NOT NULL,
                        aTeacher TEXT NOT NULL,
                        aLocation TEXT,
                        bSubject TEXT NOT NULL,
                        bTeacher TEXT NOT NULL,
                        bLocation TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO lessons_new (
                        id, timetableTerm, dayOfWeek, slotIndex, mode,
                        weeklySubject, weeklyTeacher, weeklyLocation,
                        aSubject, aTeacher, aLocation,
                        bSubject, bTeacher, bLocation
                    )
                    SELECT
                        id, 'FIRST', dayOfWeek, slotIndex, mode,
                        weeklySubject, weeklyTeacher, weeklyLocation,
                        aSubject, aTeacher, aLocation,
                        bSubject, bTeacher, bLocation
                    FROM lessons
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE lessons")
                db.execSQL("ALTER TABLE lessons_new RENAME TO lessons")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_lessons_timetableTerm_dayOfWeek_slotIndex ON lessons(timetableTerm, dayOfWeek, slotIndex)"
                )
            }
        }

        val MIGRATION_47_48 = object : androidx.room.migration.Migration(47, 48) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN activeAcademicYear INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    UPDATE settings
                    SET activeAcademicYear = CASE
                        WHEN CAST(substr(termStart, 6, 2) AS INTEGER) >= 4
                            THEN CAST(substr(termStart, 1, 4) AS INTEGER)
                        ELSE CAST(substr(termStart, 1, 4) AS INTEGER) - 1
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lessons_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        academicYear INTEGER NOT NULL,
                        timetableTerm TEXT NOT NULL,
                        dayOfWeek INTEGER NOT NULL,
                        slotIndex INTEGER NOT NULL,
                        mode TEXT NOT NULL,
                        weeklySubject TEXT NOT NULL,
                        weeklyTeacher TEXT NOT NULL,
                        weeklyLocation TEXT,
                        aSubject TEXT NOT NULL,
                        aTeacher TEXT NOT NULL,
                        aLocation TEXT,
                        bSubject TEXT NOT NULL,
                        bTeacher TEXT NOT NULL,
                        bLocation TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO lessons_new (
                        id, academicYear, timetableTerm, dayOfWeek, slotIndex, mode,
                        weeklySubject, weeklyTeacher, weeklyLocation,
                        aSubject, aTeacher, aLocation,
                        bSubject, bTeacher, bLocation
                    )
                    SELECT
                        id,
                        COALESCE(
                            (SELECT NULLIF(activeAcademicYear, 0) FROM settings WHERE id = 1),
                            CASE
                                WHEN CAST(strftime('%m', 'now', 'localtime') AS INTEGER) >= 4
                                    THEN CAST(strftime('%Y', 'now', 'localtime') AS INTEGER)
                                ELSE CAST(strftime('%Y', 'now', 'localtime') AS INTEGER) - 1
                            END
                        ),
                        timetableTerm, dayOfWeek, slotIndex, mode,
                        weeklySubject, weeklyTeacher, weeklyLocation,
                        aSubject, aTeacher, aLocation,
                        bSubject, bTeacher, bLocation
                    FROM lessons
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE lessons")
                db.execSQL("ALTER TABLE lessons_new RENAME TO lessons")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_lessons_academicYear_timetableTerm_dayOfWeek_slotIndex ON lessons(academicYear, timetableTerm, dayOfWeek, slotIndex)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nittc_scheduler.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43, MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48)
                 .build().also { INSTANCE = it }
            }
        }
    }
}
