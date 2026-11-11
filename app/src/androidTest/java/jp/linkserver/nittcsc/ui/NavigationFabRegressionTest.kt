package jp.linkserver.nittcsc.ui

import android.os.Handler
import android.os.HandlerThread
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.MainActivity
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.graphics.asAndroidBitmap

/** 実Activityでナビゲーションと作成フローを通し、同じ操作のフレーム時間を保存する。 */
class NavigationFabRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun standardSeparateTabs() = verify(UiDesignMode.MATERIAL_3, false)
    @Test fun expressiveSeparateTabs() = verify(UiDesignMode.MATERIAL_3_EXPRESSIVE, false)
    @Test fun standardUnifiedTodo() = verify(UiDesignMode.MATERIAL_3, true)
    @Test fun expressiveUnifiedTodo() = verify(UiDesignMode.MATERIAL_3_EXPRESSIVE, true)
    @Test fun expressiveRailAndDrawerKeepCreateActions() = verify(UiDesignMode.MATERIAL_3_EXPRESSIVE, false, true)

    private fun verify(mode: UiDesignMode, unified: Boolean, adaptive: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun text(id: Int) = context.getString(id)
        val dao = AppDatabase.getInstance(context).schedulerDao()
        val preferences = UiDesignPreferences(context)
        val originalMode = runBlocking { preferences.uiDesignMode.first() }
        val originalSettings = runBlocking(Dispatchers.IO) { dao.getSettings() }
        runBlocking(Dispatchers.IO) {
            preferences.setUiDesignMode(mode)
            preferences.acknowledgeExpressiveWarning()
            dao.upsertSettings((originalSettings ?: SettingsEntity(
                termStart = LocalDate.now().minusMonths(1), termEnd = LocalDate.now().plusMonths(1)
            )).copy(initialSetupCompleted = true, pendingLegacyTimetableYear = null,
                useDrawerNavigation = false, unifyTaskPlanView = unified))
        }
        context.getSharedPreferences("tutorial_hints", 0).edit().apply {
            listOf("semester_switch_hint_shown", "exam_button_hint_shown", "exam_label_hint_shown",
                "lesson_long_press_hint_shown", "ab_multi_day_drag_hint_shown",
                "ab_custom_long_press_hint_shown").forEach { putBoolean(it, true) }
        }.commit()
        val phase = AtomicReference("initial")
        val frames = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())
        val thread = HandlerThread("navigation-frame-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames.add(phase.get() to metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasText(text(R.string.tab_output)) or
                        hasContentDescription(text(R.string.tab_output))).fetchSemanticsNodes().isNotEmpty()
                }
                // 同期を使わない検証では、初回の権限説明を閉じてから操作する。
                compose.onAllNodesWithText("スキップ").fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
                    compose.onNodeWithText("スキップ").performClick()
                }
                if (adaptive) {
                    val screenshot = File(context.getExternalFilesDir(null), "m3e-ui/short-navigation.png")
                    screenshot.parentFile?.mkdirs()
                    screenshot.outputStream().use {
                        compose.onRoot().captureToImage().asAndroidBitmap()
                            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                fun select(label: String) {
                    compose.onNode(isSelectable() and (hasText(label) or hasContentDescription(label))).performClick()
                    compose.waitForIdle()
                }
                phase.set("tabs")
                repeat(3) {
                    select(if (unified) "ToDo" else text(R.string.tab_tasks))
                    if (!unified) select(text(R.string.tab_plans))
                    select(text(if (mode == UiDesignMode.MATERIAL_3_EXPRESSIVE)
                        R.string.tab_timetable_m3e else R.string.tab_timetable))
                    select(text(R.string.tab_output))
                }
                select(if (unified) "ToDo" else text(R.string.tab_tasks))
                fun add(isPlan: Boolean) {
                    if (mode == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
                        compose.onAllNodesWithContentDescription(text(R.string.cd_expand_create_menu)).assertCountEquals(1)
                        compose.onNodeWithContentDescription(text(R.string.cd_expand_create_menu)).performClick()
                        compose.onNodeWithText(text(if (isPlan) R.string.cd_add_plan else R.string.cd_add_task)).performClick()
                    } else {
                        compose.onNodeWithContentDescription(text(if (isPlan) R.string.cd_add_plan else R.string.cd_add_task)).performClick()
                    }
                    compose.onNodeWithText(text(if (isPlan) R.string.title_add_plan else R.string.title_add_task)).assertIsDisplayed()
                    compose.onAllNodesWithContentDescription(text(R.string.cd_expand_create_menu)).assertCountEquals(0)
                    compose.onNodeWithContentDescription(text(R.string.cd_back)).performClick()
                }
                phase.set("create")
                add(false)
                if (unified) compose.onNodeWithText(text(R.string.tab_plans)).performClick()
                else select(text(R.string.tab_plans))
                add(true)
                select(text(if (mode == UiDesignMode.MATERIAL_3_EXPRESSIVE)
                    R.string.tab_timetable_m3e else R.string.tab_timetable))
                compose.onAllNodesWithContentDescription(text(R.string.cd_expand_create_menu)).assertCountEquals(0)
                phase.set("weekdays")
                repeat(2) {
                    listOf(R.string.weekday_monday, R.string.weekday_friday, R.string.weekday_wednesday).forEach {
                        compose.onNodeWithText(text(it)).performClick()
                    }
                }
                phase.set("idle")
                // 回転でWindowが作り直される前に、計測を終了する。
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                if (adaptive) {
                    scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    compose.waitUntil(15_000) { context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                    select(text(R.string.tab_tasks))
                    add(false)
                    scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    compose.waitUntil(15_000) { context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
                    runBlocking(Dispatchers.IO) {
                        dao.upsertSettings(checkNotNull(dao.getSettings()).copy(useDrawerNavigation = true))
                    }
                    compose.waitUntil(15_000) {
                        compose.onAllNodesWithContentDescription(text(R.string.cd_open_navigation_menu)).fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.onNodeWithContentDescription(text(R.string.cd_open_navigation_menu)).performClick()
                    select(text(R.string.tab_plans))
                    add(true)
                }
            }
            val run = InstrumentationRegistry.getArguments().getString("metricsRun", "current")
            val output = File(context.getExternalFilesDir(null), "m3e-ui/$run-${mode.name}-$unified.csv")
            output.parentFile?.mkdirs()
            val snapshot = synchronized(frames) { frames.toList() }
            output.writeText("phase,totalDurationNs\n" + snapshot.joinToString("\n") { "${it.first},${it.second}" })
            assertTrue("Frame metrics must be captured", snapshot.isNotEmpty())
        } finally {
            thread.quitSafely()
            runBlocking(Dispatchers.IO) {
                preferences.setUiDesignMode(originalMode)
                originalSettings?.let { dao.upsertSettings(it) }
            }
        }
    }
}
