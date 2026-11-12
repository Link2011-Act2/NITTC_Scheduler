package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.ui.components.AppSplitButton
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.ceil

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
class LessonActionSplitButtonTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val output get() = File(context.getExternalFilesDir(null),
        "lesson-split/font-${context.resources.configuration.fontScale}").apply { mkdirs() }
    private fun label(id: Int) = context.getString(id)

    private fun assertTextFits(text: String, centered: Boolean) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertFalse("Clipped button text height: $text", layout.didOverflowHeight)
        for (line in 0 until layout.lineCount) {
            assertFalse(layout.isLineEllipsized(line))
            // 中央揃えの行は左端が0とは限らないため、右端の座標ではなく行の幅を比較する。
            val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
            assertTrue("Clipped button text width: $text ($lineWidth > ${layout.size.width})", lineWidth <= layout.size.width + 1f)
            File(output, "text-layout.csv").appendText("$text,${layout.size.width},${layout.multiParagraph.width},${layout.getLineLeft(line)},${layout.getLineRight(line)}\n")
        }
        if (centered) assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
    }

    @Test fun dayAndWeekLessonAndExamDialogsUseFullWidthActionsAcrossThemes() {
        val day = LocalDate.of(2026, 11, 12)
        var selected by mutableStateOf(day)
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3_EXPRESSIVE)
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var exam by mutableStateOf(false)
        var primary = Color.Unspecified
        var dialogColor = Color.Unspecified
        var density = 1f
        var spacing = 0f
        val calls = mutableListOf<Triple<String, Boolean, Boolean>>()
        val metrics = File(output, "bounds.csv").apply {
            writeText("mode,dark,dynamic,exam,week,referenceLeft,referenceRight,taskLeft,taskRight,planLeft,planRight,taskHeight,planHeight\n")
        }
        compose.setContent {
            AppTheme(mode, darkTheme = dark, dynamicColor = dynamic) {
                primary = MaterialTheme.colorScheme.primary
                dialogColor = MaterialTheme.colorScheme.surfaceContainerHigh
                density = LocalDensity.current.density
                spacing = with(LocalDensity.current) { SplitButtonDefaults.Spacing.toPx() }
                Surface {
                    Box(Modifier.width(320.dp).height(680.dp)) {
                        key(mode, dark, dynamic, exam) {
                            val examLesson = ExamLessonEntity(day, 0, 8, 40, 10, 10, "試験数学", "試験担当")
                            val state = SchedulerUiState(
                                initialized = true,
                                settings = SettingsEntity(termStart = day.minusMonths(1), termEnd = day.plusMonths(1)),
                                selectedResultDate = selected,
                                dayTypeEntities = if (exam) mapOf(day to DayTypeEntity(day, DayType.HOLIDAY,
                                    holidaySpecialLabel = HolidaySpecialLabel.MIDTERM)) else emptyMap(),
                                examDaySchedules = if (exam) mapOf(day to ExamDayScheduleEntity(day, examName = "中間試験")) else emptyMap(),
                                examLessons = if (exam) mapOf((day to 0) to examLesson) else emptyMap()
                            )
                            OutputScreen(
                                modifier = Modifier, state = state,
                                dayTypeForDate = { if (exam && it == day) DayType.HOLIDAY else DayType.A },
                                dayTypeEntityForDate = { state.dayTypeEntities[it] },
                                resolveLesson = { date, slot -> if (!exam && date == day && slot == 0) ResolvedLesson("数学", "担当教員", "教室") else null },
                                resolveOriginalLesson = { _, _ -> null }, changedLessonForDate = { _, _ -> null },
                                lessonNotes = emptyList(), onShiftDate = { selected = selected.plusDays(it) },
                                onPickDate = { selected = it }, requestedDayViewEpochDay = null, onRequestedDayViewHandled = {},
                                requestedSlotIndex = null, onRequestedSlotHandled = {}, onOpenLessonSearch = {},
                                onSaveLessonOverride = { _, _, _ -> }, onClearLessonOverride = {}, onUpdateHolidaySpecialLabel = { _, _ -> },
                                onOpenTask = {}, onOpenPlan = {},
                                onAddFromLesson = { subject, _, plan, date, time, examSlot ->
                                    assertEquals(day, date); assertEquals(LocalTime.of(8, 40), time)
                                    calls += Triple(subject, plan, examSlot)
                                },
                                onSetLessonCancelled = { _, _, _ -> }, onEditChangedLesson = { _, _ -> },
                                onSaveLessonNote = { _, _, _ -> }, onDeleteLessonNote = { _, _ -> }, onSaveExamMemo = { _, _, _ -> },
                                isLessonCancelled = { _, _ -> false }, onLessonDayVisibilityChanged = {}
                            )
                        }
                    }
                }
            }
        }
        val taskLabel = label(R.string.dialog_add_task_from_lesson)
        val planLabel = label(R.string.dialog_add_plan_from_lesson)
        for (design in UiDesignMode.entries) for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            for (isExam in listOf(false, true)) for (week in listOf(false, true)) {
                compose.runOnIdle { mode = design; dark = night; dynamic = wallpaper; exam = isExam; selected = day }
                compose.onNodeWithContentDescription(label(if (week) R.string.display_mode_week else R.string.display_mode_day)).performClick()
                val subject = if (isExam) "試験数学" else "数学"
                val subjects = compose.onAllNodesWithText(subject)
                val visible = (subjects.fetchSemanticsNodes().indices).first { subjects[it].isDisplayed() }
                subjects[visible].performTouchInput { longClick() }
                compose.onNode(isDialog()).assertExists()
                val task = compose.onNodeWithText(taskLabel).assertIsEnabled().assertHasClickAction().fetchSemanticsNode().boundsInRoot
                val plan = compose.onNodeWithText(planLabel).assertIsEnabled().assertHasClickAction().fetchSemanticsNode().boundsInRoot
                val reference = compose.onNodeWithText(label(if (isExam) R.string.dialog_edit_exam_memo else R.string.dialog_edit_lesson_note))
                    .fetchSemanticsNode().boundsInRoot
                metrics.appendText("$design,$night,$wallpaper,$isExam,$week," + listOf(reference.left, reference.right,
                    task.left, task.right, plan.left, plan.right, task.height, plan.height).joinToString(",") + "\n")
                File(output, "dialog-$design-$night-$wallpaper-$isExam-$week.png").outputStream().use {
                    compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                assertEquals(reference.left, task.left, 1f)
                assertEquals(reference.right, plan.right, 1f)
                if (design == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
                    assertEquals(task.width, plan.width, 1f)
                    assertEquals(spacing, plan.left - task.right, 1f)
                    assertEquals(task.top, plan.top, 1f); assertEquals(task.bottom, plan.bottom, 1f)
                    // ボタン自身の描画も端まで伸びていることを、中央高さの塗り・右枠線で確認する。
                    val leftImage = compose.onNodeWithText(taskLabel).captureToImage().toPixelMap()
                    assertTrue((0 until ceil(2 * density).toInt()).any { x -> leftImage[x, leftImage.height / 2].toArgb() == primary.toArgb() })
                    val rightImage = compose.onNodeWithText(planLabel).captureToImage().toPixelMap()
                    assertTrue((0 until ceil(2 * density).toInt()).any { x ->
                        rightImage[rightImage.width - 1 - x, rightImage.height / 2].toArgb() != dialogColor.toArgb()
                    })
                } else {
                    assertEquals(reference.right, task.right, 1f)
                    assertEquals(reference.left, plan.left, 1f)
                    assertTrue(plan.top >= task.bottom)
                }
                assertTextFits(taskLabel, design == UiDesignMode.MATERIAL_3_EXPRESSIVE)
                assertTextFits(planLabel, design == UiDesignMode.MATERIAL_3_EXPRESSIVE)
                val before = calls.size
                compose.onNodeWithText(if (week) planLabel else taskLabel).performClick()
                compose.onNode(isDialog()).assertDoesNotExist()
                compose.runOnIdle {
                    assertEquals(before + 1, calls.size)
                    assertEquals(Triple(subject, week, isExam), calls.last())
                }
            }
        }
    }

    @Test fun splitButtonsKeepIndependentClicksDisabledStateAndEqualWrappedHeights() {
        var primaryEnabled by mutableStateOf(true)
        var secondaryEnabled by mutableStateOf(true)
        var primaryClicks = 0; var secondaryClicks = 0
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, dynamicColor = false) {
                Surface {
                    Column(Modifier.width(232.dp)) {
                        AppSplitButton(
                            primaryLabel = { Text("主操作", textAlign = TextAlign.Center) }, onPrimaryClick = { primaryClicks++ },
                            secondaryLabel = { Text("文字の長い副操作", textAlign = TextAlign.Center) }, onSecondaryClick = { secondaryClicks++ },
                            modifier = Modifier.fillMaxWidth(), primaryEnabled = primaryEnabled, secondaryEnabled = secondaryEnabled
                        )
                        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("参照") }
                    }
                }
            }
        }
        val left = compose.onNodeWithText("主操作").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithText("文字の長い副操作").fetchSemanticsNode().boundsInRoot
        assertEquals(left.width, right.width, 1f); assertEquals(left.height, right.height, 1f)
        assertTextFits("文字の長い副操作", centered = true)
        compose.onNodeWithText("主操作").performClick()
        compose.onNodeWithText("文字の長い副操作").performClick()
        compose.runOnIdle { assertEquals(1, primaryClicks); assertEquals(1, secondaryClicks); primaryEnabled = false }
        compose.onNodeWithText("主操作").assertIsNotEnabled()
        compose.onNodeWithText("文字の長い副操作").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, primaryClicks); assertEquals(2, secondaryClicks); primaryEnabled = true; secondaryEnabled = false }
        compose.onNodeWithText("文字の長い副操作").assertIsNotEnabled()
        compose.onNodeWithText("主操作").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, primaryClicks); assertEquals(2, secondaryClicks) }
    }
}
