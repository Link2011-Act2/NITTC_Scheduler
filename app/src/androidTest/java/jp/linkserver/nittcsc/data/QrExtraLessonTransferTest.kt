package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.nittcsc.logic.QrShareCodec
import jp.linkserver.nittcsc.logic.QrShareCollector
import jp.linkserver.nittcsc.sktjf.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class QrExtraLessonTransferTest {
    private val date = LocalDate.of(2026, 4, 6)

    private fun extra(id: String): SktjfExtraLessonEntity {
        val content = SktjfLessonContent(1, 1, SktjfCycle.A, "数学", listOf("先生"), emptyList(),
            SktjfConditions(setOf("DD"), emptySet()), SktjfLessonKind.NORMAL, null)
        val json = SktjfBaselineCodec.encode(SktjfStoredBaseline(
            SktjfBaseline(2026, "S", "1-1", mapOf(id to content)), SktjfCalendarBaseline(2026, "S", "1-1", emptyMap())))
        return SktjfExtraLessonEntity(id, 2026, "S", "1-1", "source-1", json)
    }

    @Test fun transferResolvesReceiverIdentityAndKeepsMissingLinksUnmatched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databases = List(3) { Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build() }
        try {
            val transfers = databases.map { db ->
                db.schedulerDao().upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(100)))
                QrShareTransfer(SchedulerRepository(db, UiDesignPreferences(context)), db)
            }
            val sender = databases[0].schedulerDao()
            sender.upsertSktjfExtraLessons(listOf(extra("sender-id")))
            databases[1].schedulerDao().upsertSktjfExtraLessons(listOf(extra("receiver-id")))
            val id = sender.upsertTask(TaskEntity(subject = "数学", teacher = "先生", title = "課題", dueDate = date,
                createdDate = date, extraLessonId = "sender-id"))
            val payload = transfers[0].export(QrShareSelection(2026, setOf(QrShareSection.TASKS), taskIds = setOf(id)))
            assertEquals(4, payload.formatVersion)
            val collector = QrShareCollector()
            val text = QrShareCodec.create(QrShareJson.encode(payload), payload.formatVersion).frames().mapNotNull { collector.add(it) }.single()
            val decoded = QrShareJson.decode(text)
            assertEquals("receiver-id", transfers[1].import(decoded, emptySet()).importedTasks.single().extraLessonId)
            assertEquals(1, transfers[1].import(decoded, emptySet()).summary.duplicateTasks)
            val missing = transfers[2].import(decoded, emptySet())
            assertEquals(1, missing.summary.unlinkedItems)
            assertTrue(missing.importedTasks.single().extraLessonId!!.startsWith("qr-unresolved-"))
            assertEquals(1, transfers[2].import(decoded, emptySet()).summary.duplicateTasks)
            databases[2].schedulerDao().upsertSktjfExtraLessons(listOf(extra("later-id")))
            val relinked = transfers[2].import(decoded, emptySet())
            assertEquals(1, relinked.summary.duplicateTasks)
            assertTrue(relinked.importedTasks.isEmpty())
            assertEquals("later-id", databases[2].schedulerDao().getTasksOnce().single().extraLessonId)
        } finally {
            databases.forEach { it.close() }
        }
    }
}
