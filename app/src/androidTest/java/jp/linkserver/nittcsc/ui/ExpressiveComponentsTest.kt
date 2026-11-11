package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.ui.components.AppConnectedButtonGroup
import jp.linkserver.nittcsc.ui.components.AppBottomNavigation
import jp.linkserver.nittcsc.ui.components.AppBottomNavigationItem
import jp.linkserver.nittcsc.ui.components.AppFloatingToolbar
import jp.linkserver.nittcsc.logic.QrDisplaySpeed
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.logic.UpcomingLessonCandidate
import java.io.File

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
class ExpressiveComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectedButtonsMorphThroughIntermediateOutline() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, dynamicColor = false) {
                Surface(color = Color.White) {
                    Box(Modifier.width(360.dp)) {
                        val colors = ToggleButtonDefaults.colors(
                            containerColor = Color.Black, checkedContainerColor = Color.Black,
                            contentColor = Color.White, checkedContentColor = Color.White)
                        AppConnectedButtonGroup(listOf("月", "火", "水", "木", "金"), selected,
                            { selected = it }, colorsForIndex = { colors })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val textBefore = compose.onNodeWithText("水", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val before = outline()
        compose.onNodeWithText("水").performClick()
        compose.mainClock.advanceTimeBy(48)
        val during = outline()
        compose.mainClock.advanceTimeBy(1000)
        val after = outline()
        val textAfter = compose.onNodeWithText("水", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Selection must make room for the check", textAfter.left > textBefore.left)
        assertNotEquals("Selection must change the outline", before, after)
        assertNotEquals("The outline must start morphing", before, during)
        assertNotEquals("The outline must not snap to its destination", after, during)
        // Interrupt both the selection and deselection before either animation finishes.
        repeat(6) { index ->
            compose.onNodeWithText(if (index % 2 == 0) "金" else "火").performClick()
            compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.advanceTimeBy(1000)
    }

    @Test fun bothNavigationModesKeepEveryLabelAndSelection() {
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3)
        var selected by mutableIntStateOf(0)
        val labels = listOf("時間割", "課題", "編集", "授業日", "予定")
        compose.setContent {
            AppTheme(mode, dynamicColor = false) {
                Box(Modifier.width(360.dp)) {
                    AppBottomNavigation {
                        labels.forEachIndexed { index, label ->
                            AppBottomNavigationItem(selected == index, { selected = index },
                                icon = { Text("●") }, label = { Text(label) })
                        }
                    }
                }
            }
        }
        for (design in UiDesignMode.entries) {
            compose.runOnIdle { mode = design }
            labels.forEach { label ->
                compose.onNodeWithText(label).performClick().assertIsSelected()
            }
        }
    }

    @Test fun qrSpeedKeepsAllChoicesAndDisabledState() {
        var speed by mutableStateOf(QrDisplaySpeed.STANDARD)
        var enabled by mutableStateOf(true)
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, dynamicColor = false) {
                Box(Modifier.width(320.dp)) {
                    QrDisplaySpeedSelector(speed, enabled, { speed = it })
                }
            }
        }
        val resources = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources
        val labels = listOf(R.string.qr_speed_stable, R.string.qr_speed_standard,
            R.string.qr_speed_fast, R.string.qr_speed_ultra_fast)
        labels.forEachIndexed { index, label ->
            compose.onNodeWithText(resources.getString(label)).performClick()
            compose.runOnIdle { assertEquals(QrDisplaySpeed.entries[index], speed) }
        }
        compose.runOnIdle { enabled = false }
        compose.onNodeWithText(resources.getString(labels.first())).assertIsNotEnabled()
    }

    @Test fun countdownTicksDoNotRecomposeThePageOrTimetable() {
        val date = LocalDate.of(2026, 11, 11)
        var now by mutableStateOf(date.atTime(8, 0))
        var pageCompositions = 0
        var timetableCompositions = 0
        val candidates = listOf(UpcomingLessonCandidate(date, 0, "1/2校時",
            LocalTime.of(9, 0), LocalTime.of(10, 0), ResolvedLesson("数学", "先生")))
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, dynamicColor = false) {
                SideEffect { pageCompositions++ }
                TimetableDayPage(date, candidates, true, { now }) {
                    SideEffect { timetableCompositions++ }
                    Text("時間割")
                }
            }
        }
        compose.waitForIdle()
        val initialPage = pageCompositions
        val initialTable = timetableCompositions
        repeat(10) { tick ->
            compose.runOnIdle { now = date.atTime(8, tick + 1) }
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertEquals(initialPage, pageCompositions)
            assertEquals(initialTable, timetableCompositions)
        }
    }

    @Test fun timetableSurfacesStayDistinctAcrossAllThemeCombinations() {
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var background = Color.Unspecified
        var normal = Color.Unspecified
        var empty = Color.Unspecified
        var dateBar = Color.Unspecified
        var todayBar = Color.Unspecified
        var previous = Color.Unspecified
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "m3e-ui").apply { mkdirs() }
        val colors = File(output, "colors.csv").apply {
            writeText("dark,dynamic,background,previous,normal,empty,dateBar,todayBar\n")
        }
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = dark, dynamicColor = dynamic) {
                background = MaterialTheme.colorScheme.background
                previous = MaterialTheme.colorScheme.surfaceContainerLow
                normal = timetableLessonContainerColor()
                empty = timetableLessonContainerColor(empty = true)
                dateBar = timetableDateBarColor(today = false)
                todayBar = timetableDateBarColor(today = true)
                Surface(color = background) {
                    Column(Modifier.width(320.dp).padding(16.dp)) {
                        Surface(color = todayBar) { Text("今日", Modifier.padding(16.dp)) }
                        Surface(color = dateBar) { Text("11月12日", Modifier.padding(16.dp)) }
                        Surface(color = normal) { Text("数学", Modifier.padding(16.dp)) }
                        Surface(color = empty) { Text("空きコマ", Modifier.padding(16.dp)) }
                    }
                }
            }
        }
        for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            compose.runOnIdle { dark = night; dynamic = wallpaper }
            compose.runOnIdle {
                assertNotEquals(background, normal)
                assertNotEquals(background, empty)
                assertNotEquals(background, dateBar)
                assertNotEquals(normal, empty)
                assertEquals(todayBar, dateBar)
                assertTrue(normal.alpha == 1f)
                colors.appendText("$dark,$dynamic," + listOf(background, previous, normal, empty, dateBar, todayBar)
                    .joinToString(",") { "%08X".format(it.toArgb()) } + "\n")
            }
            File(output, "surfaces-$night-$wallpaper.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun toolbarExplicitForegroundSurvivesAliasedSurfaceRoles() {
        var inferred = Color.Unspecified
        var actual = Color.Unspecified
        var expected = Color.Unspecified
        var variant = Color.Unspecified
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, dynamicColor = false) {
                val scheme = MaterialTheme.colorScheme
                MaterialTheme(colorScheme = scheme.copy(surfaceVariant = scheme.surfaceContainerHighest)) {
                    val automatic = contentColorFor(scheme.surfaceContainerHighest)
                    AppFloatingToolbar(containerColor = scheme.surfaceContainerHighest, contentColor = scheme.onSurface) {
                        val foreground = LocalContentColor.current
                        SideEffect {
                            inferred = automatic
                            actual = foreground
                            expected = scheme.onSurface
                            variant = scheme.onSurfaceVariant
                        }
                        Text("日付")
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(variant, inferred)
            assertNotEquals(expected, inferred)
            assertEquals(expected, actual)
        }
    }

    private fun outline(): String {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        // Only inspect the top contour; text/icon animation cannot affect this region.
        return buildString {
            for (y in 0 until pixels.height / 8) for (x in 0 until pixels.width) {
                append(if (pixels[x, y].red < 0.5f) '1' else '0')
            }
        }
    }
}
