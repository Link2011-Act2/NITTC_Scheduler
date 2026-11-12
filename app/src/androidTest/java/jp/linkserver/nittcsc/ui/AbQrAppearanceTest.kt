package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.*
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.ui.theme.appCardContainerColor
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class AbQrAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val output get() = File(context.getExternalFilesDir(null), "ab-qr").apply { mkdirs() }
    private fun label(id: Int) = context.getString(id)
    private fun capture(name: String) = compose.onRoot().captureToImage().also { image ->
        File(output, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun scopeCardsKeepTagsBodyAndRangesInsideTheirSurfaces() {
        val cases = listOf(
            "second" to QrSharePayload(2026, setOf(QrShareSection.SECOND, QrShareSection.DAY_TYPES), 9, 23, dayTypesScope = QrDayTypesScope.SECOND),
            "first" to QrSharePayload(2026, setOf(QrShareSection.FIRST, QrShareSection.DAY_TYPES), 9, 23, dayTypesScope = QrDayTypesScope.FIRST),
            "year" to QrSharePayload(2026, setOf(QrShareSection.DAY_TYPES), 9, 23),
            "both" to QrSharePayload(2026, setOf(QrShareSection.FIRST, QrShareSection.SECOND, QrShareSection.DAY_TYPES), 9, 23, dayTypesScope = QrDayTypesScope.SECOND),
            "legacy" to QrSharePayload(2026, setOf(QrShareSection.SECOND, QrShareSection.DAY_TYPES), 9, 23, formatVersion = 2),
            "annual" to QrSharePayload(2026, setOf(QrShareSection.SECOND, QrShareSection.DAY_TYPES,
                QrShareSection.CANCELLATIONS, QrShareSection.CHANGES, QrShareSection.EXAMS), 9, 23, dayTypesScope = QrDayTypesScope.SECOND),
            "opposite" to QrSharePayload(2026, setOf(QrShareSection.FIRST, QrShareSection.DAY_TYPES), 9, 23, dayTypesScope = QrDayTypesScope.SECOND)
        )
        var data by mutableStateOf(cases.first().second)
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3_EXPRESSIVE)
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var scale by mutableStateOf(1f)
        var density = 1f
        var cardColor = Color.Unspecified
        var warningColor = Color.Unspecified
        compose.setContent {
            AppTheme(mode, darkTheme = dark, dynamicColor = dynamic) {
                density = LocalDensity.current.density
                cardColor = appCardContainerColor()
                warningColor = MaterialTheme.colorScheme.errorContainer
                if (mode == UiDesignMode.MATERIAL_3_EXPRESSIVE) assertNotEquals(MaterialTheme.colorScheme.surfaceContainerLow, cardColor)
                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Box(Modifier.width(320.dp).height(680.dp)) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                qrImportScopeGroups(data).forEach { group ->
                                    Box(Modifier.testTag("scope-${group.period}")) { QrImportScope(group) }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (design in UiDesignMode.entries) for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            val scales = if (design == UiDesignMode.MATERIAL_3_EXPRESSIVE) listOf(1f, 1.3f, 1.5f, 2f) else listOf(1f)
            for (font in scales) for ((name, payload) in cases) {
                compose.runOnIdle { mode = design; dark = night; dynamic = wallpaper; scale = font; data = payload }
                for (group in qrImportScopeGroups(payload)) {
                    val tag = "scope-${group.period}"
                    compose.onNodeWithTag(tag).performScrollTo()
                    val card = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                    val firstImage = capture("scope-$design-$night-$wallpaper-$font-$name-${group.period}")
                    val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and
                        hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                    val nodes = texts.fetchSemanticsNodes()
                    for (index in nodes.indices) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        val layout = layouts.single()
                        assertFalse("Clipped scope text height at $name/$design/$font: ${layout.layoutInput.text}", layout.didOverflowHeight)
                        // Textの整数幅と段落の小数幅の丸め差は1pxまで許容する。
                        for (line in 0 until layout.lineCount) {
                            assertFalse(layout.isLineEllipsized(line))
                            assertTrue("Clipped scope text width: ${layout.layoutInput.text}", layout.getLineRight(line) <= layout.size.width + 1f)
                        }
                        val text = nodes[index].boundsInRoot
                        assertTrue(text.left >= card.left + 15 * density && text.right <= card.right - 15 * density)
                        assertTrue(text.top >= card.top + 15 * density && text.bottom <= card.bottom - 15 * density)
                        if (group.period == QrImportPeriod.ALL_YEARS) {
                            val offset = if (layout.layoutInput.text.text == label(R.string.qr_import_all_years)) 6 else 4
                            val background = firstImage.toPixelMap()[(text.left - offset * density).toInt(), text.center.y.toInt()]
                            assertTrue("Warning text needs readable contrast", androidx.core.graphics.ColorUtils.calculateContrast(
                                layout.layoutInput.style.color.toArgb(), background.toArgb()) >= 4.5)
                        }
                    }
                    val image = capture("scope-$design-$night-$wallpaper-$font-$name-${group.period}")
                    if (design == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
                        val expected = if (group.period == QrImportPeriod.ALL_YEARS) warningColor else cardColor
                        assertEquals(expected.toArgb(), image.toPixelMap()[(card.left + 4 * density).toInt(), card.center.y.toInt()].toArgb())
                    }
                    if (group.period == QrImportPeriod.ALL_YEARS) {
                        compose.onNodeWithText(label(R.string.qr_import_all_years_warning)).assertIsDisplayed()
                    }
                }
            }
        }
    }

    @Test fun pastWeeksAreMutedButRemainEditableAcrossThemes() {
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3_EXPRESSIVE)
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val boundaryWeek = LocalDate.of(2026, 9, 23).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val rows = listOf(monday.minusWeeks(1), monday, monday.plusWeeks(1), boundaryWeek).map { start ->
            WeekRow(start, start.plusDays(4), (0L..4L).map { start.plusDays(it) })
        }
        var density = 1f
        var expectedPast = Color.Unspecified
        var expectedCurrent = Color.Unspecified
        var saved: Pair<List<LocalDate>, DayType>? = null
        var opened: LocalDate? = null
        var dragStarts = 0; var dragEnds = 0
        val metrics = File(output, "past-colors.csv").apply { writeText("mode,dark,dynamic,alpha,A,B,holiday\n") }
        compose.setContent {
            AppTheme(mode, darkTheme = dark, dynamicColor = dynamic) {
                density = LocalDensity.current.density
                val colors = MaterialTheme.colorScheme
                val alpha = 0.38f
                val canvas = colors.background
                val pastFill = appCardContainerColor(standardColor = Color.Transparent, subdued = true)
                expectedPast = pastFill.copy(alpha = pastFill.alpha * alpha).compositeOver(canvas)
                expectedCurrent = appCardContainerColor(standardColor = Color.Transparent).compositeOver(canvas)
                SideEffect {
                    val pairs = listOf(colors.onTertiaryContainer to colors.tertiaryContainer,
                        colors.onSecondaryContainer to colors.secondaryContainer,
                        colors.onSurfaceVariant to colors.surfaceVariant)
                    val contrasts = pairs.map { (fg, bg) -> androidx.core.graphics.ColorUtils.calculateContrast(
                        fg.copy(alpha = alpha).compositeOver(canvas).toArgb(), bg.copy(alpha = alpha).compositeOver(canvas).toArgb()) }
                    metrics.appendText("$mode,$dark,$dynamic,$alpha,${contrasts.joinToString(",")}\n")
                }
                Surface(color = canvas) {
                    key(mode, dark, dynamic) {
                    Column(Modifier.width(360.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEachIndexed { index, row ->
                            Box(Modifier.testTag("week-$index")) {
                                WeekRow(true, row, rows.minOf { it.weekStart }, rows.maxOf { it.weekEnd },
                                    dayTypeForDate = { when (it.dayOfWeek) { DayOfWeek.MONDAY, DayOfWeek.THURSDAY -> DayType.A
                                        DayOfWeek.TUESDAY, DayOfWeek.FRIDAY -> DayType.B; else -> DayType.HOLIDAY } },
                                    dayTypeEntityForDate = { null }, onSaveDayTypes = { dates, type -> saved = dates to type },
                                    onOpenLessonOverride = { opened = it }, isPast = index == 0 || index == 3,
                                    onDragStartRoot = { dragStarts++ }, onDragEnd = { dragEnds++ })
                            }
                        }
                    }
                    }
                }
            }
        }
        for (design in UiDesignMode.entries) for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            compose.runOnIdle { mode = design; dark = night; dynamic = wallpaper }
            val image = capture("ab-$design-$night-$wallpaper")
            val pixels = image.toPixelMap()
            for (index in rows.indices) {
                val bounds = compose.onNodeWithTag("week-$index").fetchSemanticsNode().boundsInRoot
                val actual = pixels[(bounds.left + 4 * density).toInt(), bounds.center.y.toInt()].toArgb()
                val expected = (if (index == 0 || index == 3) expectedPast else expectedCurrent).toArgb()
                for (shift in listOf(0, 8, 16)) assertTrue(kotlin.math.abs((actual shr shift and 255) - (expected shr shift and 255)) <= 2)
            }
            val a = compose.onAllNodes(hasText("A") and hasAnyAncestor(hasTestTag("week-0"))).onFirst()
            a.performClick()
            compose.runOnIdle { assertEquals(listOf(rows.first().weekStart) to DayType.B, saved) }
            a.performTouchInput { longClick() }
            compose.runOnIdle { assertEquals(rows.first().weekStart, opened) }
            val start = a.fetchSemanticsNode().boundsInRoot.center
            val end = compose.onAllNodes(hasText("B") and hasAnyAncestor(hasTestTag("week-0"))).onLast().fetchSemanticsNode().boundsInRoot.center
            val parent = compose.onNodeWithTag("week-0").fetchSemanticsNode().boundsInRoot
            val previous = dragEnds
            compose.onNodeWithTag("week-0").performTouchInput { swipe(start - parent.topLeft, end - parent.topLeft, 400) }
            compose.runOnIdle { assertTrue(dragStarts > 0); assertEquals(previous + 1, dragEnds) }
        }
    }

    @Test fun selectionRowsAndCheckboxesRetainSemesterYearAndDisabledState() {
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3_EXPRESSIVE)
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var busy by mutableStateOf(false)
        var scale by mutableStateOf(1f)
        var generated: QrShareSelection? = null
        val settings = SettingsEntity(termStart = LocalDate.of(2026, 4, 1), termEnd = LocalDate.of(2027, 3, 31),
            activeAcademicYear = 2026, enableSemesterTimetables = false)
        val oldDate = LocalDate.of(2025, 5, 1)
        compose.setContent {
            AppTheme(mode, darkTheme = dark, dynamicColor = dynamic) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    Surface {
                        Box(Modifier.width(320.dp).height(680.dp)) {
                            key(mode, dark, dynamic, scale) {
                                QrShareSelectionScreen(SchedulerUiState(initialized = true, settings = settings,
                                    dayTypeEntities = mapOf(oldDate to DayTypeEntity(oldDate, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.MIDTERM))), busy, null, {}, {},
                                    onGenerate = { generated = it })
                            }
                        }
                    }
                }
            }
        }
        fun row(id: Int) = compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(id))).let {
            compose.onNodeWithText(label(id))
        }
        fun generate() { row(R.string.qr_generate).performClick() }
        for (design in UiDesignMode.entries) for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            compose.runOnIdle { mode = design; dark = night; dynamic = wallpaper; busy = false; scale = 1f }
            row(R.string.qr_first_days).assertIsOn().performTouchInput { click(Offset(36.dp.toPx(), height / 2f)) }
            row(R.string.qr_first_days).assertIsOff()
            row(R.string.qr_second_days).assertIsOff().performClick().assertIsOn()
            row(R.string.qr_first).performClick().assertIsOff()
            row(R.string.qr_second).performClick().assertIsOn()
            generate()
            compose.runOnIdle {
                assertEquals(QrDayTypesScope.SECOND, generated?.dayTypesScope)
                assertTrue(QrShareSection.SECOND in requireNotNull(generated).sections)
                assertFalse(QrShareSection.FIRST in requireNotNull(generated).sections)
            }
            row(R.string.qr_first_days).performClick().assertIsOn()
            generate()
            compose.runOnIdle { assertEquals(QrDayTypesScope.YEAR, generated?.dayTypesScope) }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            compose.onNodeWithText(context.getString(R.string.qr_image_year, 2025)).performClick()
            generate()
            compose.runOnIdle { assertEquals(2025, generated?.academicYear) }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            capture("selection-$design-$night-$wallpaper")
            compose.runOnIdle { busy = true }
            row(R.string.qr_first_days).assertIsNotEnabled()
            capture("selection-disabled-$design-$night-$wallpaper")
            compose.runOnIdle { busy = false; scale = 1.5f }
            row(R.string.qr_first_days).assertIsDisplayed()
            capture("selection-large-$design-$night-$wallpaper")
        }
    }
}
