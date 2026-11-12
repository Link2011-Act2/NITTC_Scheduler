package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.ui.components.AppIconButton
import jp.linkserver.nittcsc.ui.components.AppTopAppBar
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ExamTimetableSetupTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val output get() = File(context.getExternalFilesDir(null), "exam-unset/font-${context.resources.configuration.fontScale}").apply { mkdirs() }
    private fun label(id: Int) = context.getString(id)
    private fun capture(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
        File(output, "$name.png").outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun visibleTag(tag: String): SemanticsNodeInteraction {
        val nodes = compose.onAllNodesWithTag(tag)
        return nodes[nodes.fetchSemanticsNodes().indices.first { nodes[it].isDisplayed() }]
    }
    private fun assertTextFits(text: String, node: SemanticsNodeInteraction = compose.onNodeWithText(text, useUnmergedTree = true)) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        if (layout.didOverflowHeight) capture("clipped-$text")
        assertFalse("Clipped text: $text; size=${layout.size}; paragraphHeight=${layout.multiParagraph.height}; lines=${layout.lineCount}", layout.didOverflowHeight)
        for (line in 0 until layout.lineCount) {
            assertFalse("Ellipsized text: $text", layout.isLineEllipsized(line))
            assertTrue(layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1)
        }
    }

    @Test fun dayAndWeekUnsetCardsGuideTheCorrectDateAndRefreshAfterSaving() {
        val monday = LocalDate.of(2026, 11, 9)
        val wednesday = monday.plusDays(2)
        var selected by mutableStateOf(monday)
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3_EXPRESSIVE)
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var saved by mutableStateOf(false)
        var request: LocalDate? = null
        val examDates = setOf(monday, wednesday)
        compose.setContent {
            AppTheme(mode, darkTheme = dark, dynamicColor = dynamic) {
                Surface {
                    Box(Modifier.fillMaxWidth().height(680.dp)) {
                        val state = SchedulerUiState(
                            initialized = true,
                            settings = SettingsEntity(termStart = monday.minusMonths(1), termEnd = monday.plusMonths(1), enableExamTimetable = enabled),
                            selectedResultDate = selected,
                            dayTypeEntities = (0L..4L).associate { offset ->
                                val date = monday.plusDays(offset)
                                date to DayTypeEntity(date, DayType.HOLIDAY,
                                    holidaySpecialLabel = HolidaySpecialLabel.MIDTERM.takeIf { date in examDates })
                            },
                            // 科目だけでなく、日付設定レコードすらない新規試験日を検証する。
                            examLessons = if (saved) mapOf((monday to 1) to ExamLessonEntity(monday, 1, 9, 50, 10, 40, "試験数学"),
                                (monday to 0) to ExamLessonEntity(monday, 0, 8, 40, 9, 30)) else emptyMap()
                        )
                        OutputScreen(
                            modifier = Modifier, state = state,
                            dayTypeForDate = { state.dayTypeEntities[it]?.dayType ?: DayType.A },
                            dayTypeEntityForDate = { state.dayTypeEntities[it] },
                            resolveLesson = { _, _ -> null }, resolveOriginalLesson = { _, _ -> null }, changedLessonForDate = { _, _ -> null },
                            lessonNotes = emptyList(), onShiftDate = { selected = selected.plusDays(it) }, onPickDate = { selected = it },
                            requestedDayViewEpochDay = null, onRequestedDayViewHandled = {}, requestedSlotIndex = null, onRequestedSlotHandled = {},
                            onOpenLessonSearch = {}, onSaveLessonOverride = { _, _, _ -> }, onClearLessonOverride = {}, onUpdateHolidaySpecialLabel = { _, _ -> },
                            onOpenTask = {}, onOpenPlan = {}, onSetLessonCancelled = { _, _, _ -> }, onEditChangedLesson = { _, _ -> },
                            onSaveLessonNote = { _, _, _ -> }, onDeleteLessonNote = { _, _ -> }, onSaveExamMemo = { _, _, _ -> },
                            isLessonCancelled = { _, _ -> false }, onLessonDayVisibilityChanged = {},
                            examEntryAppearance = ExamTimetableEntryAppearance(label(R.string.app_name), label(R.string.label_second_term)),
                            onOpenExamTimetable = { request = it }
                        )
                    }
                }
            }
        }
        for (design in UiDesignMode.entries) for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) for (week in listOf(false, true)) {
            compose.runOnIdle { mode = design; dark = night; dynamic = wallpaper; selected = monday; enabled = true; saved = false }
            compose.onNodeWithContentDescription(label(if (week) R.string.display_mode_week else R.string.display_mode_day)).performClick()
            for (date in if (week) listOf(monday, wednesday) else listOf(monday)) {
                val card = visibleTag("timetable-slot-$date-0")
                // 未マージの輪郭も調べ、アイコンが1限右上にあり、本文と重ならないことを確認する。
                val iconNodes = compose.onAllNodes(hasContentDescription(label(R.string.btn_create_exam_timetable)), useUnmergedTree = true)
                val rect = card.fetchSemanticsNode().boundsInRoot
                val marker = iconNodes.fetchSemanticsNodes().first { it.boundsInRoot.center.x in rect.left..rect.right }
                assertTrue(marker.boundsInRoot.right <= rect.right)
                assertTrue(marker.boundsInRoot.top >= rect.top)
                assertTrue(marker.boundsInRoot.center.x > rect.center.x)
                assertTrue(marker.boundsInRoot.center.y < rect.center.y)
                val unsetTexts = compose.onAllNodesWithText(label(R.string.label_exam_unset), useUnmergedTree = true)
                val texts = unsetTexts.fetchSemanticsNodes()
                val unsetIndex = texts.indices.first { texts[it].boundsInRoot.center.x in rect.left..rect.right && texts[it].boundsInRoot.center.y in rect.top..rect.bottom }
                val unsetText = texts[unsetIndex]
                assertTextFits(label(R.string.label_exam_unset), unsetTexts[unsetIndex])
                assertFalse("Quiz overlaps unset label", marker.boundsInRoot.overlaps(unsetText.boundsInRoot))
                if (!week) {
                    val headerTexts = compose.onAllNodes(
                        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
                            hasAnyAncestor(hasTestTag("timetable-slot-$date-0")) and hasText(label(R.string.label_exam_unset)).not(),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes()
                    headerTexts.forEach {
                        val overlaps = it.boundsInRoot.overlaps(unsetText.boundsInRoot)
                        if (overlaps) capture("overlap-$design-$night-$wallpaper")
                        assertFalse("Period label ${it.boundsInRoot} overlaps unset label ${unsetText.boundsInRoot}; card=$rect; mode=$design", overlaps)
                    }
                }
                assertTrue(rect.width >= 40f * context.resources.displayMetrics.density)
                visibleTag("timetable-slot-$date-1").assert(hasContentDescription(label(R.string.btn_create_exam_timetable)).not())
                capture("screen-$design-$night-$wallpaper-$week-$date")
                card.performTouchInput { click(Offset(marker.boundsInRoot.center.x - rect.left, marker.boundsInRoot.center.y - rect.top)) }
                compose.onNodeWithText(label(R.string.dialog_exam_unset_title)).assertIsDisplayed()
                // プレビューは偽の操作を公開しない。ダイアログの操作は2ボタンのみ。
                compose.onAllNodes(hasClickAction() and hasAnyAncestor(isDialog())).assertCountEquals(2)
                assertTextFits(label(R.string.btn_cancel))
                assertTextFits(label(R.string.btn_configure_exam_timetable))
                capture("dialog-$design-$night-$wallpaper-$week-$date", compose.onNode(isDialog()))
                compose.onNodeWithText(label(R.string.btn_cancel)).performClick()
                compose.onNode(isDialog()).assertDoesNotExist()
                compose.runOnIdle { assertNull(request) }
                card.performClick()
                compose.onNodeWithText(label(R.string.btn_configure_exam_timetable)).performClick()
                compose.runOnIdle { assertEquals(date, request); request = null }
            }
            compose.runOnIdle { saved = true; selected = monday }
            val first = visibleTag("timetable-slot-$monday-0")
            first.assert(hasContentDescription(label(R.string.btn_create_exam_timetable)).not())
            first.assertTextContains(label(R.string.label_exam_no_test))
            visibleTag("timetable-slot-$monday-1").assertTextContains("試験数学")
            compose.runOnIdle { enabled = false; saved = false }
            first.assert(hasContentDescription(label(R.string.btn_create_exam_timetable)).not())
        }
        compose.onNodeWithContentDescription(label(R.string.display_mode_day)).performClick()
        compose.runOnIdle { selected = monday.plusWeeks(1); enabled = true; saved = false }
        visibleTag("timetable-slot-${monday.plusWeeks(1)}-0").assertTextContains(label(R.string.label_no_class_short))
        compose.runOnIdle { selected = monday.plusDays(1) }
        val markers = compose.onAllNodesWithContentDescription(label(R.string.btn_create_exam_timetable))
        assertTrue(markers.fetchSemanticsNodes().indices.none { markers[it].isDisplayed() })
    }

    @Test fun entryPreviewMatchesEditorTopBarPartsWithoutInteractiveSemantics() {
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3_EXPRESSIVE)
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var target by mutableStateOf(true)
        compose.setContent {
            AppTheme(mode, darkTheme = dark, dynamicColor = dynamic) {
                Surface {
                    Column {
                        // 実際の編集タブと同じAppTopAppBar・タイトル・Dropdown・AppIconButton。
                        AppTopAppBar(
                            modifier = Modifier.testTag("editor-bar"),
                            title = { ExamTimetableEntryTitle(label(R.string.app_name)) },
                            actions = {
                                if (target) TimetableTargetDropdown(listOf(label(R.string.label_second_term)), 0) { }
                                AppIconButton({}) { ExamTimetableEntryIcon() }
                            }
                        )
                        Box(Modifier.padding(24.dp).testTag("preview")) {
                            ExamTimetableEntryPreview(ExamTimetableEntryAppearance(label(R.string.app_name), label(R.string.label_second_term).takeIf { target }))
                        }
                    }
                }
            }
        }
        for (design in UiDesignMode.entries) for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) for (hasTarget in listOf(false, true)) {
            compose.runOnIdle { mode = design; dark = night; dynamic = wallpaper; target = hasTarget }
            compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("preview"))).assertCountEquals(0)
            capture("comparison-$design-$night-$wallpaper-$hasTarget")
        }
    }
}
