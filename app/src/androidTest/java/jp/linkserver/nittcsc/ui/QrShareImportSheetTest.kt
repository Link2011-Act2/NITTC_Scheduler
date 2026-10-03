package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.LessonNoteEntity
import jp.linkserver.nittcsc.data.QrShareJson
import jp.linkserver.nittcsc.data.QrShareSection
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.data.QrShareSelection
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.LongBreakEntity
import jp.linkserver.nittcsc.logic.QrDayTypesScope
import java.time.LocalDate
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 実DBには書き込まず、確認・選択・取り込み中の操作を検証する。 */
class QrShareImportSheetTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(id: Int) = context.getString(id)
    private fun fixture() = QrShareJson.decode(InstrumentationRegistry.getInstrumentation().context.assets
        .open("qr-import-mixed.json").bufferedReader().use { it.readText() })

    @Test
    fun scopedAbShowsSemesterRangeWithoutGlobalWarningAndDetailsUseScopedCounts() {
        val day = LocalDate.of(2026, 9, 23)
        val data = QrSharePayload(2026, setOf(QrShareSection.DAY_TYPES), 9, 23,
            dayTypesScope = QrDayTypesScope.SECOND, dayTypes = listOf(DayTypeEntity(day, DayType.A)))
        val local = listOf(day.minusDays(1), day, day.plusYears(1)).associateWith { DayTypeEntity(it, DayType.B) }
        composeRule.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = false, dynamicColor = true) {
                QrShareImportDialog(data, SchedulerUiState(initialized = true, dayTypeEntities = local), false, null, {}, {})
            }
        }
        composeRule.onNodeWithText(label(R.string.qr_import_all_years_warning)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.qr_days_second)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.qr_days_range, "2026-09-23", "2027-03-31"))
            .performScrollTo().assertIsDisplayed()
        captureQrSheet("scoped-ab")
        composeRule.onNodeWithText(label(R.string.qr_import_details)).performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.qr_import_count, label(R.string.qr_days), 1, 1))
            .assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.qr_import_days_scoped)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.qr_import_back_to_summary)).assertIsDisplayed()
    }

    @Test
    fun selectionCarriesChosenAbSemesterIntoGeneratedQr() {
        var generated: QrShareSelection? = null
        val day = LocalDate.of(2027, 4, 1)
        val state = SchedulerUiState(initialized = true,
            settings = SettingsEntity(termStart = LocalDate.of(2026, 4, 1), termEnd = LocalDate.of(2027, 3, 31),
                activeAcademicYear = 2026, enableSemesterTimetables = false, secondTermStartMonth = 9, secondTermStartDay = 23),
            dayTypeEntities = mapOf(day to DayTypeEntity(day, DayType.HOLIDAY)),
            longBreaks = listOf(LongBreakEntity(name = "春休み", startDate = day.minusDays(12), endDate = day.plusDays(9))))
        composeRule.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = false, dynamicColor = true) {
                QrShareSelectionScreen(state, false, null, {}, {}, onGenerate = { generated = it })
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.qr_image_year, 2027)).assertDoesNotExist()
        fun row(id: Int) {
            composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(id)))
        }
        fun generate() {
            row(R.string.qr_generate)
            composeRule.onNodeWithText(label(R.string.qr_generate)).assertIsDisplayed().performClick()
        }
        row(R.string.qr_first_days)
        composeRule.onNodeWithText(label(R.string.qr_first_days)).assertIsOn().performClick().assertIsOff()
        row(R.string.qr_second_days)
        composeRule.onNodeWithText(label(R.string.qr_second_days)).assertIsOff().performClick().assertIsOn()
        composeRule.waitForIdle()
        captureQrSheet("separate-ab-selection")
        generate()
        composeRule.runOnIdle {
            assertEquals(QrDayTypesScope.SECOND, generated?.dayTypesScope)
            assertTrue(QrShareSection.DAY_TYPES in generated!!.sections)
            assertTrue(QrShareSection.FIRST in generated!!.sections)
            assertFalse(QrShareSection.SECOND in generated!!.sections)
        }
        row(R.string.qr_first_days)
        composeRule.onNodeWithText(label(R.string.qr_first_days)).performClick().assertIsOn()
        generate()
        composeRule.runOnIdle { assertEquals(QrDayTypesScope.YEAR, generated?.dayTypesScope) }
        row(R.string.qr_second_days)
        composeRule.onNodeWithText(label(R.string.qr_second_days)).performClick().assertIsOff()
        generate()
        composeRule.runOnIdle { assertEquals(QrDayTypesScope.FIRST, generated?.dayTypesScope) }
        row(R.string.qr_first_days)
        composeRule.onNodeWithText(label(R.string.qr_first_days)).performClick().assertIsOff()
        generate()
        composeRule.runOnIdle { assertFalse(QrShareSection.DAY_TYPES in generated!!.sections) }
    }

    @Test
    fun detailsReturnToSummaryAndConfirmWithoutMemoReplacements() {
        var confirmed: Set<LessonNoteEntity>? = null
        val data = fixture()
        composeRule.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = false, dynamicColor = true) {
                QrShareImportDialog(data, SchedulerUiState(initialized = true), false, null, {}, { confirmed = it })
            }
        }
        captureQrSheet("mixed-summary")
        composeRule.onNodeWithText(label(R.string.qr_import_all_years_warning)).performScrollTo().assertIsDisplayed()
        // 大きい文字では末尾のカードまでスクロールしてから、実際のタップを行う。
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        composeRule.onNodeWithText(label(R.string.qr_import_details)).assertIsDisplayed().performClick()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(R.string.qr_import_times_notice)))
        composeRule.onNodeWithText(label(R.string.qr_import_times_notice)).assertIsDisplayed()
        captureQrSheet("details")
        composeRule.onNodeWithText(label(R.string.qr_import_back_to_summary)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(label(R.string.qr_import_confirm)).assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(emptySet<LessonNoteEntity>(), confirmed) }
    }

    @Test
    fun onlyExplicitlyChosenMemoIsPassedToImportAndBusyBlocksDismissal() {
        val full = fixture()
        val data = full.copy(sections = setOf(QrShareSection.NOTES), notes = full.notes.take(2))
        val current = data.notes.map { it.copy(text = "保存済みのメモ", updatedAt = 1) }
        var confirmed: Set<LessonNoteEntity>? = null
        var dismissals = 0
        val busy = mutableStateOf(false)
        composeRule.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = false, dynamicColor = true) {
                QrShareImportDialog(data, SchedulerUiState(initialized = true, lessonNotes = current), busy.value, null,
                    { dismissals++ }, { confirmed = it; busy.value = true })
            }
        }
        composeRule.onNodeWithText(label(R.string.qr_import_notes_review)).performScrollTo().performClick()
        composeRule.onAllNodesWithText(label(R.string.qr_import_note_replace))[0]
            .performScrollTo().assertIsOff().performClick().assertIsOn()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        composeRule.onAllNodesWithText(label(R.string.qr_import_note_replace)).onLast().assertIsOff()
        captureQrSheet("memo-comparison")
        composeRule.onNodeWithText(label(R.string.qr_import_back_to_summary)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.qr_import_note_choices, 1, 1)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.qr_import_notes_review)).performClick()
        composeRule.onAllNodesWithText(label(R.string.qr_import_note_replace))[0].performScrollTo().assertIsOn()
        composeRule.onNodeWithContentDescription(label(R.string.cd_back)).performClick()
        composeRule.onNodeWithText(label(R.string.qr_import_confirm)).performClick()
        composeRule.onNodeWithText(label(R.string.qr_import_busy)).assertIsNotEnabled()
        composeRule.onNodeWithText(label(R.string.qr_cancel)).assertIsNotEnabled()
        Espresso.pressBack()
        composeRule.onNodeWithText(label(R.string.qr_import_busy)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(setOf(current.first()), confirmed)
            assertEquals(0, dismissals)
        }
        composeRule.runOnIdle { busy.value = false }
        composeRule.onNodeWithText(label(R.string.qr_cancel)).performClick()
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun personalOnlyDuplicatesDoNotSuggestTimetableReplacementAndErrorsStayVisible() {
        val full = fixture()
        val data = full.copy(sections = setOf(QrShareSection.TASKS), tasks = full.tasks.take(1))
        val error = label(R.string.qr_import_times_notice)
        composeRule.setContent {
            AppTheme(UiDesignMode.MATERIAL_3, darkTheme = false, dynamicColor = false) {
                QrShareImportDialog(data, SchedulerUiState(initialized = true, tasks = data.tasks.map { it.asTask() }),
                    false, error, {}, {})
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.qr_import_duplicates_skipped, 1)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.qr_import_replace)).assertDoesNotExist()
        composeRule.onNodeWithText(error).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.qr_import_confirm)).assertIsDisplayed()
        captureQrSheet("personal-duplicate-error")
    }
}

/** 幅・文字倍率は端末の実設定で変える。Dialogは独立したWindowのDensityを使う。 */
class QrShareImportSheetLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun lightReplacementScopeAndFooterRemainReachable() = verifyLayout(dark = false)

    @Test
    fun darkReplacementScopeAndFooterRemainReachable() = verifyLayout(dark = true)

    private fun verifyLayout(dark: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = QrShareJson.decode(InstrumentationRegistry.getInstrumentation().context.assets
            .open("qr-import-mixed.json").bufferedReader().use { it.readText() })
        composeRule.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = dark, dynamicColor = true) {
                QrShareImportDialog(data, SchedulerUiState(initialized = true), false, null, {}, {})
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.qr_import_confirm)).assertIsDisplayed().assertIsEnabled()
        captureQrSheet("layout-$dark-top")
        composeRule.onNodeWithText(context.getString(R.string.qr_import_all_years_warning)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.qr_cancel)).assertIsDisplayed()
        captureQrSheet("layout-$dark-scope")
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        composeRule.onNodeWithText(context.getString(R.string.qr_import_details)).assertIsDisplayed().performClick()
        captureQrSheet("layout-$dark-details-open")
        composeRule.waitUntil(5_000) {
            composeRule.onNodeWithText(context.getString(R.string.qr_import_back_to_summary)).isDisplayed()
        }
        captureQrSheet("layout-$dark-details")
        composeRule.onNodeWithText(context.getString(R.string.qr_import_back_to_summary)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(context.getString(R.string.qr_import_confirm)).assertIsDisplayed()
    }

}

private fun captureQrSheet(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val configuration = instrumentation.targetContext.resources.configuration
    val viewport = "${configuration.screenWidthDp}-${configuration.fontScale}"
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
    instrumentation.targetContext.cacheDir.resolve("qr-import-$name-$viewport.png").outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    bitmap.recycle()
}
