package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class NittcModeGateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unsetModeRequiresChoiceAndPreservesExistingTask() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = SchedulerRepository(db, UiDesignPreferences(context))
        val store = ViewModelStore()
        val today = LocalDate.now()
        val visible = mutableStateOf(true)
        try {
            runBlocking {
                repository.initialize(today)
                db.schedulerDao().upsertTask(TaskEntity(id = 42, subject = "数学", title = "課題", dueDate = today, createdDate = today))
            }
            lateinit var model: SchedulerViewModel
            compose.runOnIdle { model = SchedulerViewModel(repository); store.put("mode-gate", model) }
            compose.setContent { MaterialTheme { if (visible.value) NittcSchedulerApp(model) } }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("学校と所属").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("保存").assertIsNotEnabled()
            compose.onNodeWithText("キャンセル").assertDoesNotExist()
            compose.onNodeWithText("選択").performClick()
            compose.onNodeWithText("汎用").performClick()
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(15_000) { runBlocking { db.schedulerDao().getUsageMode()?.mode == "GENERIC" } }
            runBlocking { assertEquals("課題", db.schedulerDao().getTaskById(42)?.title) }
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            compose.runOnIdle { store.clear() }
            runBlocking { db.withTransaction { } }
            db.close()
        }
    }
}
