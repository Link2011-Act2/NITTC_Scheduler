package jp.linkserver.nittcsc.ui

import android.view.View
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.MainActivity
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.data.UiDesignPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real Activity/Room/DataStore integration, using only the app's settings controls. */
class PyriteActivityMigrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun ordinaryMaterialSettingsAndAboutRestore() = verify(UiDesignMode.MATERIAL_3)
    @Test fun expressiveSettingsAndAboutRestoreAndSwitchDesign() = verify(UiDesignMode.MATERIAL_3_EXPRESSIVE)

    private fun verify(mode: UiDesignMode) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun text(id: Int) = context.getString(id)
        val preferences = UiDesignPreferences(context)
        runBlocking { preferences.setUiDesignMode(mode); preferences.acknowledgeExpressiveWarning() }
        context.getSharedPreferences("tutorial_hints", 0).edit().apply {
            for (key in listOf("semester_switch_hint_shown", "exam_button_hint_shown", "exam_label_hint_shown",
                "lesson_long_press_hint_shown", "ab_multi_day_drag_hint_shown", "ab_custom_long_press_hint_shown")) putBoolean(key, true)
        }.commit()
        val dao = AppDatabase.getInstance(context).schedulerDao()
        fun settings() = runBlocking(Dispatchers.IO) { dao.getSettings() }
        val log = File(context.getExternalFilesDir(null), "pyrite-migration/after/geometry-real-${mode.name}.txt")
        log.parentFile?.mkdirs(); log.writeText("")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(timeoutMillis = 15_000) {
                settings() != null && (compose.onAllNodesWithContentDescription(text(R.string.cd_settings)).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithContentDescription(text(R.string.cd_more_actions)).fetchSemanticsNodes().isNotEmpty())
            }
            if (compose.onAllNodesWithContentDescription(text(R.string.cd_settings)).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithContentDescription(text(R.string.cd_settings)).performClick()
            } else {
                compose.onNodeWithContentDescription(text(R.string.cd_more_actions)).performClick()
                compose.onNodeWithText(text(R.string.settings_title)).performClick()
            }
            compose.onNodeWithText(text(R.string.label_show_current_time_marker)).performScrollTo()
            val title = compose.onNodeWithText(text(R.string.label_show_current_time_marker)).fetchSemanticsNode().boundsInRoot
            val toggles = compose.onAllNodes(isToggleable())
            val index = toggles.fetchSemanticsNodes().indices.minBy {
                kotlin.math.abs(toggles.fetchSemanticsNodes()[it].boundsInRoot.center.y - title.center.y)
            }
            val expectedMarker = !settings()!!.showCurrentTimeMarker
            toggles[index].performClick()
            compose.waitUntil(timeoutMillis = 5_000) { settings()!!.showCurrentTimeMarker == expectedMarker }
            val about = compose.onNode(hasText(text(R.string.about_section_title)) and hasClickAction())
            about.performScrollTo()
            fun geometry(label: String): String {
                compose.waitForIdle()
                val node = compose.onNode(hasScrollAction()).fetchSemanticsNode()
                val axis = node.config[SemanticsProperties.VerticalScrollAxisRange]
                var window = ""
                scenario.onActivity { activity ->
                    val view = activity.findViewById<View>(android.R.id.content)
                    val insets = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.systemBars())
                    window = "${view.width}x${view.height},insets=$insets"
                }
                return "scroll=${axis.value()},max=${axis.maxValue()},viewport=${node.boundsInRoot},$window"
                    .also { log.appendText("$label $it\n") }
            }
            val settingsBefore = geometry("settings-before-about")
            about.performClick()
            compose.onNodeWithText(text(R.string.about_oss_open)).performScrollTo().assertIsDisplayed()
            val aboutBefore = geometry("about-before")
            repeat(2) { index ->
                scenario.recreate()
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText(text(R.string.about_oss_open)).fetchSemanticsNodes().isNotEmpty()
                }
                val after = geometry("about-recreate-$index")
                assertEquals(aboutBefore, after)
                compose.onNodeWithText(text(R.string.about_oss_open)).assertIsDisplayed()
                assertEquals(expectedMarker, settings()!!.showCurrentTimeMarker)
                assertEquals(mode, runBlocking { preferences.uiDesignMode.first() })
            }
            compose.onNodeWithContentDescription(text(R.string.cd_back)).performClick()
            about.assertIsDisplayed()
            assertEquals(settingsBefore, geometry("settings-return"))
            if (mode == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
                fun switchTo(target: UiDesignMode) {
                    compose.onNodeWithText(text(R.string.label_ui_design)).performScrollTo().performClick()
                    if (target == UiDesignMode.MATERIAL_3) compose.onAllNodes(isSelectable()).onFirst().performClick()
                    else compose.onAllNodes(isSelectable()).onLast().performClick()
                    compose.waitUntil(timeoutMillis = 5_000) { runBlocking { preferences.uiDesignMode.first() } == target }
                    compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
                    assertEquals(expectedMarker, settings()!!.showCurrentTimeMarker)
                }
                switchTo(UiDesignMode.MATERIAL_3)
                switchTo(UiDesignMode.MATERIAL_3_EXPRESSIVE)
                // These are distinct legacy/expressive screen layouts; first choose a
                // nonzero position in the current layout, then check its restoration.
                compose.onNodeWithText(text(R.string.label_ui_design)).performScrollTo()
                val switched = geometry("settings-after-switch")
                scenario.recreate()
                compose.onNodeWithText(text(R.string.label_ui_design)).assertIsDisplayed()
                assertEquals(switched, geometry("settings-after-switch-recreate"))
            }
            // Reverse a top-level transition before its spring settles, then finish on settings.
            about.performScrollTo()
            compose.mainClock.autoAdvance = false
            about.performClick()
            compose.mainClock.advanceTimeBy(80)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.mainClock.advanceTimeBy(80)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
            assertEquals(expectedMarker, settings()!!.showCurrentTimeMarker)
        }
    }
}
