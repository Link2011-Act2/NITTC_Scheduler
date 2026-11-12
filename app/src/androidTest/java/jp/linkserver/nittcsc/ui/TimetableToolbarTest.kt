package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TimetableToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun todayOverlayKeepsLabelsAndArrowsStillAtNarrowWidthsAndLargeFonts() {
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var width by mutableStateOf(240.dp)
        var fontScale by mutableStateOf(1f)
        var label by mutableStateOf("2026/11/12")
        var today by mutableStateOf(false)
        var bar = Color.Unspecified
        var density = 1f
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "toolbar-layout").apply { mkdirs() }
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = dark, dynamicColor = dynamic) {
                val actualDensity = LocalDensity.current
                density = actualDensity.density
                bar = timetableDateBarColor(today)
                assertEquals(timetableLessonContainerColor(), bar)
                assertEquals(lerp(bar, MaterialTheme.colorScheme.surfaceContainer, 0.4f), timetableDisplayModeContainerColor())
                CompositionLocalProvider(
                    LocalDensity provides Density(actualDensity.density, fontScale),
                    LocalTodayIndicatorStyle provides TodayIndicatorStyle.DOT
                ) {
                    Surface(color = bar, contentColor = MaterialTheme.colorScheme.onSurface) {
                        Box(Modifier.padding(16.dp)) {
                            TimetableDateNavigation(
                                dateLabel = label, detailLabel = if (label.startsWith("2026")) "木曜日 / A日" else null,
                                onPrevious = {}, onNext = {}, onClick = {}, onLongClick = {},
                                modifier = Modifier.width(width),
                                todayIndicatorDescription = if (today) "today" else null
                            )
                        }
                    }
                }
            }
        }
        fun bounds() = listOf(label, "<", ">").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            for ((dpWidth, scale) in listOf(116 to 2f, 180 to 1.5f, 240 to 1f)) {
                for (dateLabel in listOf("2026/11/12", "11/09-11/13")) {
                    compose.runOnIdle {
                        dark = night; dynamic = wallpaper; width = dpWidth.dp; fontScale = scale
                        label = dateLabel; today = false
                    }
                    val beforeBounds = bounds()
                    val before = compose.onRoot().captureToImage()
                    compose.runOnIdle { today = true }
                    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "today")).assertExists()
                    assertEquals(beforeBounds, bounds())
                    val after = compose.onRoot().captureToImage()
                    assertEquals(before.width, after.width)
                    assertEquals(before.height, after.height)
                    val a = before.toPixelMap()
                    val b = after.toPixelMap()
                    var count = 0
                    var left = after.width; var right = 0; var top = after.height; var bottom = 0
                    for (y in 0 until after.height) for (x in 0 until after.width) {
                        if (a[x, y] != b[x, y]) {
                            count++
                            left = minOf(left, x); right = maxOf(right, x)
                            top = minOf(top, y); bottom = maxOf(bottom, y)
                            // 差分は背景上のドットだけ。文字の画素を覆っていないことも確認する。
                            assertEquals(bar, a[x, y])
                        }
                    }
                    assertTrue("No visible today dot", count > 5)
                    assertTrue("Changed more than the dot", right - left <= 6 * density && bottom - top <= 6 * density)
                    val name = "$night-$wallpaper-$dpWidth-$scale-${if (dateLabel.startsWith("2026")) "day" else "week"}"
                    File(output, "$name.png").outputStream().use {
                        after.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    compose.runOnIdle { today = false }
                    assertEquals(beforeBounds, bounds())
                    val restored = compose.onRoot().captureToImage()
                    assertTrue(before.asAndroidBitmap().sameAs(restored.asAndroidBitmap()))
                }
            }
        }
    }
}
