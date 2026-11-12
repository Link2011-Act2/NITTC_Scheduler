package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ExamTimetableNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualAppOpensTheMatchingPeriodSavesAndReturnsToTheTimetable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = SchedulerRepository(db, UiDesignPreferences(context))
        val store = ViewModelStore()
        val prefs = context.getSharedPreferences("tutorial_hints", 0)
        val keys = listOf("exam_button_hint_shown", "semester_switch_hint_shown", "lesson_long_press_hint_shown")
        val old = keys.associateWith { key -> if (prefs.contains(key)) prefs.getBoolean(key, false) else null }
        val today = LocalDate.now()
        var model: SchedulerViewModel? = null
        fun label(id: Int) = context.getString(id)
        fun capture(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
            val file = File(context.getExternalFilesDir(null), "exam-unset/navigation/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        try {
            runBlocking {
                repo.initialize(today)
                // 近接しない別期間も作り、常に最初の期間を開く誤りを検出する。
                repo.updateHolidaySpecialLabel(today.minusWeeks(3), HolidaySpecialLabel.MIDTERM)
                repo.updateHolidaySpecialLabel(today, HolidaySpecialLabel.FINAL)
            }
            prefs.edit().also { editor -> keys.forEach { editor.putBoolean(it, true) } }.commit()
            compose.runOnIdle {
                val viewModel = SchedulerViewModel(repo)
                model = viewModel
                store.put("exam-test", viewModel)
            }
            compose.setContent {
                AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, dynamicColor = false) { NittcSchedulerApp(model!!) }
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("timetable-slot-$today-0").fetchSemanticsNodes().isNotEmpty() }
            val first = compose.onNodeWithTag("timetable-slot-$today-0")
            first.assertTextContains(label(R.string.label_exam_unset)).performClick()
            compose.onNodeWithText(label(R.string.btn_cancel)).performClick()
            first.assertExists()
            first.performClick()
            capture("guide", compose.onNode(isDialog()))
            compose.onNodeWithText(label(R.string.btn_configure_exam_timetable)).performClick()
            compose.onNodeWithText(label(R.string.btn_save)).assertIsDisplayed()
            capture("matching-period-editor")
            compose.onNodeWithContentDescription(label(R.string.cd_back)).performClick()
            // 直接遷移の戻る先は、試験期間一覧ではなく元の日表示。
            first.assertExists()
            val week = today.dayOfWeek.value <= 5
            if (week) compose.onNodeWithContentDescription(label(R.string.display_mode_week)).performClick()
            first.performClick()
            compose.onNodeWithText(label(R.string.btn_configure_exam_timetable)).performClick()
            val fields = compose.onAllNodes(hasSetTextAction())
            fields[0].performTextInput("保存後の試験科目")
            compose.onNodeWithText(label(R.string.btn_save)).performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("保存後の試験科目").fetchSemanticsNodes().isNotEmpty() }
            first.assertTextContains("保存後の試験科目")
            first.assert(hasContentDescription(label(R.string.btn_create_exam_timetable)).not())
            if (week) {
                val monday = today.minusDays(today.dayOfWeek.value - 1L)
                val otherDay = if (today.dayOfWeek.value <= 2) monday.plusDays(4) else monday
                compose.onNodeWithTag("timetable-slot-$otherDay-0").assertIsDisplayed()
            }
            runBlocking {
                assertEquals("保存後の試験科目", db.schedulerDao().getExamLessonsOnce().first { it.date == today && it.slotIndex == 0 }.subject)
            }
            capture("saved-timetable")
            compose.onNodeWithText(label(R.string.tab_timetable_m3e)).performClick()
            compose.onNodeWithContentDescription(label(R.string.btn_create_exam_timetable)).assertIsDisplayed()
            capture("actual-editor-tab-top-bar")
        } finally {
            compose.runOnIdle { store.clear() }
            db.close()
            prefs.edit().also { editor -> old.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putBoolean(key, value) } }.commit()
        }
    }
}
