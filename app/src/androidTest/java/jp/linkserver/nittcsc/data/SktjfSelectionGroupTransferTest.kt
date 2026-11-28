package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID
import jp.linkserver.nittcsc.sktjf.*

@RunWith(AndroidJUnit4::class)
class SktjfSelectionGroupTransferTest {
    private val date = LocalDate.of(2026,4,6)
    private fun group(selected: String?) = SktjfSelectionGroupEntity(2026,"S","2M","group",1,1,
        "[\"a\",\"b\"]","[{\"sourceId\":\"src\",\"page\":1}]",selected)
    @Test fun backupAndSyncPreserveChoiceAndRemovedId() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val source=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val target=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            source.schedulerDao().upsertSettings(SettingsEntity(termStart=date,termEnd=date.plusDays(30)))
            target.schedulerDao().upsertSettings(SettingsEntity(termStart=date,termEnd=date.plusDays(30)))
            val from=SchedulerRepository(source,UiDesignPreferences(context))
            val to=SchedulerRepository(target,UiDesignPreferences(context))
            val retiredGroup=group("removed").copy(retiredLessonIdsJson="[\"removed\"]", suspendedByTimetableReplacement=true)
            val content=SktjfLessonContent(1,1,SktjfCycle.WEEKLY,"旧授業",emptyList(),emptyList(),
                SktjfConditions(emptySet(),emptySet()),SktjfLessonKind.NORMAL,null)
            val json=SktjfBaselineCodec.encode(SktjfStoredBaseline(SktjfBaseline(2026,"S","2M",mapOf("retired-extra" to content)),
                SktjfCalendarBaseline(2026,"S","2M",emptyMap())))
            val retired=SktjfExtraLessonEntity("retired-extra",2026,"S","2M","removed",json)
            val task=TaskEntity(id=93,extraLessonId=retired.id,subject="旧授業",title="課題",dueDate=date,createdDate=date)
            source.schedulerDao().upsertSktjfSelectionGroups(listOf(retiredGroup))
            source.schedulerDao().upsertSktjfExtraLessons(listOf(retired))
            source.schedulerDao().upsertTask(task)
            to.importAllData(from.exportAllData(),requireSettings=true)
            assertEquals(listOf(retiredGroup),target.schedulerDao().getSktjfSelectionGroups())
            assertEquals(retired,target.schedulerDao().getSktjfExtraLesson(retired.id))
            assertEquals(task.copy(id=0,updatedAt=0),target.schedulerDao().getTasksOnce().single().copy(id=0,updatedAt=0))
            val unknownGroup=group(null).copy(retiredLessonIdsJson="[\"removed\"]")
            source.schedulerDao().upsertSktjfSelectionGroups(listOf(unknownGroup))
            to.applySyncPayload(from.exportSyncPayload())
            assertEquals(listOf(unknownGroup),target.schedulerDao().getSktjfSelectionGroups())
            assertEquals(retired,target.schedulerDao().getSktjfExtraLesson(retired.id))
            assertEquals(task.copy(id=0,updatedAt=0),target.schedulerDao().getTasksOnce().single().copy(id=0,updatedAt=0))
        } finally {source.close();target.close()}
    }
    @Test fun migration59To60PreservesExistingData() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="selection-migration-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_59_60, AppDatabase.MIGRATION_60_61).build()
        try {
            val original=open();original.schedulerDao().upsertSettings(SettingsEntity(termStart=date,termEnd=date.plusDays(30)));original.close()
            context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { sql ->
                sql.execSQL("DROP TABLE sktjf_selection_groups")
                sql.execSQL("ALTER TABLE sktjf_source_state DROP COLUMN replacedTimedSlotsJson")
                sql.version=59
            }
            val upgraded=open()
            try {
                assertEquals(date,upgraded.schedulerDao().getSettings()!!.termStart)
                assertTrue(upgraded.schedulerDao().getSktjfSelectionGroups().isEmpty())
                upgraded.schedulerDao().upsertSktjfSelectionGroups(listOf(group("a")))
                assertEquals(listOf(group("a")),upgraded.schedulerDao().getSktjfSelectionGroups())
            } finally {upgraded.close()}
        } finally {context.deleteDatabase(name)}
    }
    @Test fun choosingAndResettingUpdatesOnlyTheTargetGroup() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val dao=db.schedulerDao();dao.upsertUsageMode(UsageModeEntity(mode="NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026,"UNDERGRADUATE",2,"2M",false,false)))
            val original=group(null);dao.upsertSktjfSelectionGroups(listOf(original))
            val content=SktjfLessonContent(1,1,SktjfCycle.WEEKLY,"数学",emptyList(),emptyList(),
                SktjfConditions(emptySet(),emptySet()),SktjfLessonKind.NORMAL,null)
            val json=SktjfBaselineCodec.encode(SktjfStoredBaseline(SktjfBaseline(2026,"S","2M",mapOf("extra-a" to content)),
                SktjfCalendarBaseline(2026,"S","2M",emptyMap())))
            dao.upsertSktjfExtraLessons(listOf(SktjfExtraLessonEntity("extra-a",2026,"S","2M","a",json)))
            val repository=SchedulerRepository(db,UiDesignPreferences(context))
            assertFalse(repository.selectSktjfCourse(original,"invalid"))
            assertTrue(repository.selectSktjfCourse(original,"a"))
            val selected=dao.getSktjfSelectionGroups().single();assertEquals("a",selected.selectedLessonId)
            assertFalse(repository.selectSktjfCourse(original,null))
            assertTrue(repository.selectSktjfCourse(selected,null))
            assertNull(dao.getSktjfSelectionGroups().single().selectedLessonId)
            assertNotNull(dao.getSktjfExtraLesson("extra-a"))
        } finally {db.close()}
    }
}
