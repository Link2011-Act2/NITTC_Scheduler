package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.sktjf.SktjfBaseline
import jp.linkserver.nittcsc.sktjf.SktjfBaselineCodec
import jp.linkserver.nittcsc.sktjf.SktjfCalendarBaseline
import jp.linkserver.nittcsc.sktjf.SktjfStoredBaseline
import jp.linkserver.nittcsc.sktjf.SktjfLessonContent
import jp.linkserver.nittcsc.sktjf.SktjfCycle
import jp.linkserver.nittcsc.sktjf.SktjfConditions
import jp.linkserver.nittcsc.sktjf.SktjfLessonKind
import jp.linkserver.nittcsc.sktjf.SktjfVacation
import jp.linkserver.nittcsc.sktjf.SktjfVacationSource
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NittcProfileMigrationTest {
    private fun removeSchoolEventColumns(sql: android.database.sqlite.SQLiteDatabase) {
        val create = sql.rawQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'day_types'", null).use { cursor ->
            check(cursor.moveToFirst()); cursor.getString(0)
        }
        val oldCreate = create.replace(", `schoolEventName` TEXT", "").replace(", `schoolEventKind` TEXT", "")
        check(oldCreate != create)
        sql.execSQL("ALTER TABLE day_types RENAME TO day_types_v58_fixture")
        sql.execSQL(oldCreate)
        val columns = sql.rawQuery("PRAGMA table_info(day_types)", null).use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names += "`${cursor.getString(1)}`"
            names.joinToString(", ")
        }
        sql.execSQL("INSERT INTO day_types ($columns) SELECT $columns FROM day_types_v58_fixture")
        sql.execSQL("DROP TABLE day_types_v58_fixture")
    }
    // SQLite before 3.35 cannot DROP COLUMN. Rebuild the old table shape using APIs available on API 26.
    private fun removeExtraLessonColumn(sql: android.database.sqlite.SQLiteDatabase, table: String) {
        require(table == "tasks" || table == "plans")
        val create = sql.rawQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { cursor ->
            check(cursor.moveToFirst()); cursor.getString(0)
        }
        val oldCreate = create.replace(", `extraLessonId` TEXT", "")
        check(oldCreate != create)
        val oldTable = "${table}_v57_fixture"
        sql.execSQL("ALTER TABLE `$table` RENAME TO `$oldTable`")
        sql.execSQL(oldCreate)
        val columns = sql.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names += "`${cursor.getString(1)}`"
            names.joinToString(", ")
        }
        sql.execSQL("INSERT INTO `$table` ($columns) SELECT $columns FROM `$oldTable`")
        sql.execSQL("DROP TABLE `$oldTable`")
        sql.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_dueDate` ON `$table` (`dueDate`)")
    }

    @Test fun migrationPreservesExistingLessonsTasksAndRequiresModeChoice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sktjf-migration-${UUID.randomUUID()}.db"
        val date = LocalDate.of(2026, 4, 6)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_53_54, AppDatabase.MIGRATION_54_55, AppDatabase.MIGRATION_55_56, AppDatabase.MIGRATION_56_57, AppDatabase.MIGRATION_57_58, AppDatabase.MIGRATION_58_59, AppDatabase.MIGRATION_59_60, AppDatabase.MIGRATION_60_61).build()
        try {
            val fresh = open()
            assertNull(fresh.schedulerDao().getUsageMode())
            fresh.schedulerDao().upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            fresh.schedulerDao().upsertLesson(LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
                dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "数学", weeklyTeacher = "先生",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
            fresh.schedulerDao().upsertTask(TaskEntity(id = 93, lessonId = 71, subject = "数学", title = "課題", dueDate = date, createdDate = date))
            fresh.close()
            // Reconstruct the v53 shape before the source and profile tables were introduced.
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sql ->
                sql.execSQL("DROP TABLE sktjf_selection_groups"); sql.execSQL("DROP TABLE school_vacations")
                sql.execSQL("DROP TABLE usage_mode"); sql.execSQL("DROP TABLE nittc_profiles"); sql.execSQL("DROP TABLE sktjf_source_state"); sql.execSQL("DROP TABLE sktjf_extra_lessons"); sql.execSQL("DROP TABLE nittc_year_transitions"); removeExtraLessonColumn(sql, "tasks"); removeExtraLessonColumn(sql, "plans"); removeSchoolEventColumns(sql); sql.version = 53
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
            val baseline = SktjfBaselineCodec.encode(SktjfStoredBaseline(
                SktjfBaseline(2026, "S", "1-1", emptyMap()), SktjfCalendarBaseline(2026, "S", "1-1", emptyMap())))
            val source = SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.0", baseline, false)
            dao.upsertSktjfSourceStates(listOf(source))
            val transition = NittcYearTransitionEntity(2028, NittcYearOutcome.GRADUATED.name)
            dao.upsertNittcYearTransitions(listOf(transition))
            val backup = JSONObject(repository.exportAllData())
            assertEquals(31, backup.getInt("version"))
            assertEquals(0, backup.getJSONArray("schoolVacations").length())
            assertFalse(repository.exportSyncPayload().toString().contains("nittcProfile"))
            assertFalse(repository.exportSyncPayload().toString().contains("nittcYearTransitions"))
            val syncPayload = repository.exportSyncPayload()
            assertFalse(syncPayload.toString().contains("sktjfSourceState"))
            repository.applySyncPayload(syncPayload)
            assertTrue(dao.getSktjfSourceStates().single().requiresRelinking)
            repository.setUsageMode(SchedulerUsageMode.GENERIC)
            dao.deleteAllNittcYearTransitions()
            repository.importAllData(backup.toString(), requireSettings = true)
            assertEquals("NITTC", dao.getUsageMode()!!.mode)
            assertEquals(listOf(first, second), dao.getNittcProfilesOnce())
            assertEquals(source, dao.getSktjfSourceStates().single())
            assertEquals(listOf(transition), dao.getNittcYearTransitions())
            val invalid = JSONObject(backup.toString())
            invalid.getJSONArray("nittcYearTransitions").getJSONObject(0).put("outcome", "INVALID")
            assertTrue(runCatching { repository.importAllData(invalid.toString(), requireSettings = true) }.isFailure)
            assertEquals(listOf(first, second), dao.getNittcProfilesOnce())
            assertEquals(listOf(transition), dao.getNittcYearTransitions())
            val legacy = JSONObject(backup.toString()).put("version", 18).also { it.remove("nittcProfile"); it.remove("sktjfSourceState"); it.remove("sktjfExtraLessons"); it.remove("nittcYearTransitions") }
            repository.setUsageMode(SchedulerUsageMode.GENERIC)
            repository.importAllData(legacy.toString(), requireSettings = true)
            assertEquals("GENERIC", dao.getUsageMode()!!.mode)
            assertEquals(listOf(first, second), dao.getNittcProfilesOnce())
            assertEquals(source.copy(requiresRelinking = true), dao.getSktjfSourceStates().single())
            assertEquals(listOf(transition), dao.getNittcYearTransitions())
        } finally { db.close() }
    }

    @Test fun migrationFrom55PreservesExistingRowsAndCreatesEmptyTransitionHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "nittc-year-${UUID.randomUUID()}.db"
        val date = LocalDate.of(2026, 4, 6)
        try {
            val fresh = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            fresh.schedulerDao().upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)
            fresh.schedulerDao().upsertNittcProfiles(listOf(profile))
            fresh.schedulerDao().upsertTask(TaskEntity(id = 93, subject = "数学", title = "課題", dueDate = date, createdDate = date))
            fresh.close()
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sql ->
                sql.execSQL("DROP TABLE sktjf_selection_groups"); sql.execSQL("DROP TABLE school_vacations")
                sql.execSQL("ALTER TABLE sktjf_source_state DROP COLUMN replacedTimedSlotsJson")
                sql.execSQL("DROP TABLE nittc_year_transitions"); removeExtraLessonColumn(sql, "tasks"); removeExtraLessonColumn(sql, "plans"); removeSchoolEventColumns(sql); sql.version = 55
            }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_55_56, AppDatabase.MIGRATION_56_57, AppDatabase.MIGRATION_57_58, AppDatabase.MIGRATION_58_59, AppDatabase.MIGRATION_59_60, AppDatabase.MIGRATION_60_61).build()
            try {
                assertEquals(listOf(profile), migrated.schedulerDao().getNittcProfilesOnce())
                assertEquals("課題", migrated.schedulerDao().getTaskById(93)?.title)
                assertTrue(migrated.schedulerDao().getNittcYearTransitions().isEmpty())
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun migrationFrom58KeepsManualBreaksAndOfficialDaysStayReversible() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "school-vacations-${UUID.randomUUID()}.db"
        val march = LocalDate.of(2027, 3, 31)
        val april = march.plusDays(1)
        val manual = LongBreakEntity(12, "手動の休み", march.minusMonths(2), march.minusMonths(2))
        try {
            val fresh = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                val dao = fresh.schedulerDao()
                dao.upsertLongBreak(manual)
                dao.upsertDayTypes(listOf(DayTypeEntity(march, DayType.A), DayTypeEntity(april, DayType.B)))
                dao.upsertUsageMode(UsageModeEntity(mode = SchedulerUsageMode.NITTC.name))
            } finally { fresh.close() }
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sql ->
                sql.execSQL("DROP TABLE sktjf_selection_groups"); sql.execSQL("DROP TABLE school_vacations")
                sql.execSQL("ALTER TABLE sktjf_source_state DROP COLUMN replacedTimedSlotsJson")
                sql.version = 58
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_58_59, AppDatabase.MIGRATION_59_60, AppDatabase.MIGRATION_60_61).build()
            try {
                val dao = db.schedulerDao()
                assertEquals(listOf(manual), dao.getLongBreaksOnce())
                assertTrue(dao.getSchoolVacations().isEmpty())
                val repository = SchedulerRepository(db, UiDesignPreferences(context))
                val period = SktjfVacation("2026-year_end", "year_end", "学年末休業",
                    LocalDate.of(2027, 2, 20), null, march, 4,
                    listOf(SktjfVacationSource("calendar.pdf", null, "a".repeat(64), 1)))
                val preview = repository.previewSchoolVacations("2026.F.1", listOf(period))
                assertTrue(dao.getSchoolVacations().isEmpty())
                assertTrue(repository.applySchoolVacations(preview, setOf(period.id)))
                val vacation = dao.getSchoolVacations().single()
                assertEquals(period.id, vacation.id)
                assertNull(vacation.local?.end)
                assertEquals(DayType.HOLIDAY, dao.getEffectiveDayType(march)?.dayType)
                assertEquals(DayType.A, dao.getDayType(march)?.dayType)
                assertEquals(DayType.B, dao.getEffectiveDayType(april)?.dayType)
                assertTrue(repository.editSchoolVacation(vacation, null))
                assertEquals(DayType.A, dao.getEffectiveDayType(march)?.dayType)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun extraLessonsSurviveBackupAndSyncAndInvalidSyncIsAtomic() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val date = LocalDate.of(2026, 4, 6); val dao = db.schedulerDao()
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            repository.initialize(date)
            val content = SktjfLessonContent(1, 1, SktjfCycle.A, "数学", emptyList(), emptyList(),
                SktjfConditions(setOf("DD"), emptySet()), SktjfLessonKind.NORMAL, null)
            val json = SktjfBaselineCodec.encode(SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf("extra-1" to content)),
                SktjfCalendarBaseline(2026, "S", "1-1", emptyMap())))
            val extra = SktjfExtraLessonEntity("extra-1", 2026, "S", "1-1", "source-1", json)
            dao.upsertSktjfExtraLessons(listOf(extra))
            dao.upsertTask(TaskEntity(subject = "数学", title = "課題", dueDate = date, createdDate = date, extraLessonId = extra.id))
            dao.upsertPlan(PlanEntity(subject = "数学", title = "予定", dueDate = date, createdDate = date, extraLessonId = extra.id))
            val backup = JSONObject(repository.exportAllData())
            dao.deleteAllSktjfExtraLessons()
            repository.importAllData(backup.toString(), requireSettings = true)
            assertEquals(listOf(extra), dao.getSktjfExtraLessons())
            assertEquals(extra.id, dao.getTasksOnce().single().extraLessonId)
            assertEquals(extra.id, dao.getPlansOnce().single().extraLessonId)
            val payload = repository.exportSyncPayload()
            val bad = JSONObject(payload.toString())
            val partition = bad.getJSONArray("lessons/2026/FIRST")
            val extraIndex = (0 until partition.length()).first { partition.getJSONObject(it).optString("recordType") == "sktjf-extra" }
            partition.getJSONObject(extraIndex).remove("content")
            val before = dao.getLessonsOnce()
            assertTrue(runCatching { repository.applySyncPayload(bad) }.isFailure)
            assertEquals(before, dao.getLessonsOnce())
            assertEquals(listOf(extra), dao.getSktjfExtraLessons())
            dao.deleteAllSktjfExtraLessons()
            repository.applySyncPayload(payload)
            assertEquals(listOf(extra), dao.getSktjfExtraLessons())
            assertEquals(extra.id, dao.getTasksOnce().single().extraLessonId)
            assertEquals(extra.id, dao.getPlansOnce().single().extraLessonId)
            val old = JSONObject(backup.toString()).put("version", 19).also { it.remove("sktjfExtraLessons"); it.remove("sktjfSourceState") }
            repository.importAllData(old.toString(), requireSettings = true)
            assertEquals(listOf(extra), dao.getSktjfExtraLessons())
        } finally { db.close() }
    }
}
