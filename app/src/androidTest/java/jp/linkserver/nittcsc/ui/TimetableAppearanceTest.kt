package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class TimetableAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dayWeekAndOtherDatesRemainUsableInEveryExpressiveTheme() {
        val today = LocalDate.now()
        var date by mutableStateOf(today)
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "m3e-ui").apply { mkdirs() }
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = dark, dynamicColor = dynamic) {
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
            File(output, "timetable-$dark-$dynamic-$name.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        fun assertLessonVisible() {
            assertTrue(compose.onAllNodesWithText("数学").fetchSemanticsNodes().any {
                it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0
            })
        }
        for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            compose.runOnIdle { dark = night; dynamic = wallpaper; date = today }
            compose.onNodeWithContentDescription(context.getString(R.string.display_mode_day)).performClick()
            assertLessonVisible()
            capture("day")
            compose.onRoot().performTouchInput {
                swipe(androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.6f),
                    androidx.compose.ui.geometry.Offset(width * 0.2f, height * 0.6f), 300)
            }
            compose.waitForIdle()
            compose.runOnIdle { assertNotEquals(today, date) }
            capture("other-day")
            compose.onNodeWithContentDescription(context.getString(R.string.display_mode_week)).performClick()
            assertLessonVisible()
            capture("week")
        }
    }
}
