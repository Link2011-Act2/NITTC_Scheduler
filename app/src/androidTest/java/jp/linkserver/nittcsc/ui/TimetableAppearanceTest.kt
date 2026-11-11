package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class TimetableAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dayWeekAndOtherDatesRemainUsableAcrossDesignsAndThemes() {
        val today = LocalDate.now()
        var date by mutableStateOf(today)
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3_EXPRESSIVE)
        var currentRangeToday by mutableStateOf(true)
        var expectedTextColor = Color.Unspecified
        var schemeInfo = ""
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "m3e-revision/toolbar").apply { mkdirs() }
        val colors = File(output, "colors.csv").apply {
            writeText("mode,dark,dynamic,case,text,expected,bar,toggle,oldAutomaticText,onSurfaceVariant\n")
        }
        compose.setContent {
            AppTheme(mode, darkTheme = dark, dynamicColor = dynamic) {
                val scheme = MaterialTheme.colorScheme
                val bar = timetableDateBarColor(currentRangeToday)
                val previousAutomaticText = contentColorFor(scheme.surfaceContainerHighest)
                SideEffect {
                    expectedTextColor = scheme.onSurface
                    schemeInfo = listOf(bar, scheme.surfaceContainerHighest, previousAutomaticText, scheme.onSurfaceVariant)
                        .joinToString(",") { "%08X".format(it.toArgb()) }
                }
                Surface {
                    Box(Modifier.width(360.dp).height(680.dp)) {
                        OutputScreen(
                            modifier = Modifier,
                            state = SchedulerUiState(
                                settings = SettingsEntity(termStart = today.minusMonths(1), termEnd = today.plusMonths(1)),
                                selectedResultDate = date
                            ),
                            dayTypeForDate = { DayType.A }, dayTypeEntityForDate = { null },
                            resolveLesson = { _, slot -> if (slot == 2) null else ResolvedLesson("数学", "担当教員", "教室") },
                            resolveOriginalLesson = { _, _ -> null }, changedLessonForDate = { _, _ -> null },
                            lessonNotes = emptyList(), onShiftDate = { date = date.plusDays(it) },
                            onPickDate = { date = it }, requestedDayViewEpochDay = null,
                            onRequestedDayViewHandled = {}, requestedSlotIndex = null, onRequestedSlotHandled = {},
                            onOpenLessonSearch = {}, onSaveLessonOverride = { _, _, _ -> },
                            onClearLessonOverride = {}, onUpdateHolidaySpecialLabel = { _, _ -> },
                            onOpenTask = {}, onOpenPlan = {}, onSetLessonCancelled = { _, _, _ -> },
                            onEditChangedLesson = { _, _ -> }, onSaveLessonNote = { _, _, _ -> },
                            onDeleteLessonNote = { _, _ -> }, onSaveExamMemo = { _, _, _ -> },
                            isLessonCancelled = { _, _ -> false }, onLessonDayVisibilityChanged = {}
                        )
                    }
                }
            }
        }
        fun capture(name: String) {
            File(output, "timetable-$mode-$dark-$dynamic-$name.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        fun assertLessonVisible() {
            assertTrue(compose.onAllNodesWithText("数学").fetchSemanticsNodes().any {
                it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0
            })
        }
        val dayFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        val weekFormatter = DateTimeFormatter.ofPattern("MM/dd")
        for (design in UiDesignMode.entries) for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            compose.runOnIdle { mode = design; dark = night; dynamic = wallpaper }
            for ((name, selected) in listOf("today-day" to today, "other-day" to today.plusDays(1),
                "this-week" to today, "other-week" to today.plusWeeks(2))) {
                compose.runOnIdle {
                    date = selected
                    currentRangeToday = name == "today-day" || name == "this-week"
                }
                val week = name.endsWith("week")
                compose.onNodeWithContentDescription(context.getString(if (week) R.string.display_mode_week else R.string.display_mode_day)).performClick()
                assertLessonVisible()
                capture(name)
                val reference = if (week && selected == today && today.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
                    today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else selected
                val monday = reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val label = if (week) "${monday.format(weekFormatter)}-${monday.plusDays(4).format(weekFormatter)}"
                    else selected.format(dayFormatter)
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val actual = layouts.single().layoutInput.style.color
                colors.appendText("$mode,$dark,$dynamic,$name,%08X,%08X,$schemeInfo\n".format(actual.toArgb(), expectedTextColor.toArgb()))
                if (mode == UiDesignMode.MATERIAL_3_EXPRESSIVE) assertEquals(expectedTextColor, actual)
            }
            compose.runOnIdle { date = today }
            compose.onNodeWithContentDescription(context.getString(R.string.display_mode_day)).performClick()
            compose.onRoot().performTouchInput {
                swipe(androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.6f),
                    androidx.compose.ui.geometry.Offset(width * 0.2f, height * 0.6f), 300)
            }
            compose.waitForIdle()
            compose.runOnIdle { assertNotEquals(today, date) }
        }
    }
}
