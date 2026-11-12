package jp.linkserver.nittcsc.ui

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

class EditorShapeMorphTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun actualEditorHasIntermediateCornersAndKeepsItsButtonNodes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val run = InstrumentationRegistry.getArguments().getString("motionRun", "current")
        val folder = File(context.getExternalFilesDir(null), "m3e-shape/$run/editor").apply { mkdirs() }
        File(folder, "contours.csv").delete()
        val preferences = UiDesignPreferences(context)
        val dao = AppDatabase.getInstance(context).schedulerDao()
        val originalMode = runBlocking { preferences.uiDesignMode.first() }
        val originalSettings = runBlocking(Dispatchers.IO) { dao.getSettings() }
        runBlocking(Dispatchers.IO) {
            preferences.setUiDesignMode(UiDesignMode.MATERIAL_3_EXPRESSIVE)
            preferences.acknowledgeExpressiveWarning()
            dao.upsertSettings((originalSettings ?: SettingsEntity(termStart=LocalDate.now(), termEnd=LocalDate.now().plusMonths(1)))
                .copy(initialSetupCompleted=true,pendingLegacyTimetableYear=null,useDrawerNavigation=false))
        }
        context.getSharedPreferences("tutorial_hints",0).edit().apply {
            listOf("semester_switch_hint_shown","exam_button_hint_shown","exam_label_hint_shown",
                "lesson_long_press_hint_shown","ab_multi_day_drag_hint_shown","ab_custom_long_press_hint_shown")
                .forEach { putBoolean(it,true) }
        }.commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                val edit = context.getString(R.string.tab_timetable_m3e)
                compose.waitUntil(30_000) { compose.onAllNodes(isSelectable() and hasText(edit)).fetchSemanticsNodes().isNotEmpty() }
                if (compose.onAllNodesWithText("スキップ").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("スキップ").performClick()
                compose.onNode(isSelectable() and hasText(edit)).performClick()
                compose.waitForIdle()
                compose.mainClock.autoAdvance=false
                val failures=mutableListOf<String>()
                for ((index,label) in listOf(0 to "月",2 to "水",4 to "金")) {
                    val other=if(index==0) "火" else "月"
                    compose.onNodeWithText(other).performClick()
                    compose.mainClock.advanceTimeBy(1000)
                    val node=compose.onNodeWithText(label)
                    val id=node.fetchSemanticsNode().id
                    val before=saveCornerContour(node,folder,"$index-before",index==0)
                    node.performClick()
                    var elapsed=0L
                    val forward=mutableListOf<String>()
                    for(time in morphFrameTimes) {
                        compose.mainClock.advanceTimeBy(time-elapsed);elapsed=time
                        forward+=saveCornerContour(node,folder,"$index-select-$time",index==0)
                    }
                    val after=forward.last()
                    val sameNode=id==node.fetchSemanticsNode().id
                    File(folder,"nodes.txt").appendText("$index beforeId=$id afterId=${node.fetchSemanticsNode().id} same=$sameNode\n")
                    compose.onNodeWithText(other).performClick()
                    val reverse=mutableListOf<String>()
                    elapsed=0L
                    for(time in morphFrameTimes) {
                        compose.mainClock.advanceTimeBy(time-elapsed);elapsed=time
                        reverse+=saveCornerContour(node,folder,"$index-clear-$time",index==0)
                    }
                    if(!sameNode) failures+="$index button node was recreated"
                    if(before==after) failures+="$index endpoints identical"
                    if(forward.filter{it!=before&&it!=after}.distinct().size<2) failures+="$index selection lacks intermediate contours"
                    if(reverse.filter{it!=before&&it!=after}.distinct().size<2) failures+="$index deselection lacks intermediate contours"
                }
                repeat(8) { index ->
                    compose.onNodeWithText(if(index%2==0) "金" else "月").performTouchInput { click() }
                    compose.mainClock.advanceTimeBy(32)
                    saveCornerContour(compose.onNodeWithText("月"),folder,"rapid-$index",true)
                }
                compose.mainClock.advanceTimeBy(1000)
                assertTrue(failures.joinToString(),failures.isEmpty())
            }
        } finally {
            runBlocking(Dispatchers.IO) {
                preferences.setUiDesignMode(originalMode)
                originalSettings?.let { dao.upsertSettings(it) }
            }
        }
    }
}
