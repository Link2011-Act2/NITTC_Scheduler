package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
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

class TodayPillTest {
    @get:Rule val compose = createComposeRule()

    private fun save(image: ImageBitmap, name: String) {
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "today-indicator/layout").apply { mkdirs() }
        File(output, "$name.png").outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun colorBounds(image: ImageBitmap, color: Color): androidx.compose.ui.geometry.Rect? {
        val pixels = image.toPixelMap()
        var left = image.width; var top = image.height; var right = -1; var bottom = -1
        for (y in 0 until image.height) for (x in 0 until image.width) if (pixels[x, y] == color) {
            left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
        }
        return if (right < 0) null else androidx.compose.ui.geometry.Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
    }

    @Test fun datePillWrapsOnlyDateAndKeepsNavigationStillAcrossThemesAndFontScales() {
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var today by mutableStateOf(false)
        var width by mutableStateOf(240.dp)
        var fontScale by mutableStateOf(1f)
        var pillColor = Color.Unspecified
        var density = 1f
        var clicks = 0; var longClicks = 0; var next = 0
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = dark, dynamicColor = dynamic) {
                assertEquals(TodayIndicatorStyle.PILL, LocalTodayIndicatorStyle.current)
                density = LocalDensity.current.density
                pillColor = MaterialTheme.colorScheme.primaryContainer
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    Surface(color = timetableDateBarColor(today), contentColor = MaterialTheme.colorScheme.onSurface) {
                        Box(Modifier.padding(16.dp)) {
                            TimetableDateNavigation(
                                "2026/11/12", "木 / A", onPrevious = {}, onNext = { next++ },
                                onClick = { clicks++ }, onLongClick = { longClicks++ },
                                modifier = Modifier.width(width), todayIndicatorDescription = if (today) "today" else null
                            )
                        }
                    }
                }
            }
        }
        fun bounds() = listOf("2026/11/12", "木 / A", "<", ">").map {
            compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            for ((dpWidth, scale) in listOf(116 to 2f, 180 to 1.5f, 240 to 1f)) {
                compose.runOnIdle { dark = night; dynamic = wallpaper; today = false; width = dpWidth.dp; fontScale = scale }
                val original = compose.onRoot().captureToImage()
                val positions = bounds()
                assertNull(colorBounds(original, pillColor))
                compose.runOnIdle { today = true }
                val selected = compose.onRoot().captureToImage()
                assertEquals(positions, bounds())
                val pill = checkNotNull(colorBounds(selected, pillColor))
                assertTrue(pill.width > pill.height)
                assertEquals(positions[0].center.x, pill.center.x, 1.5f)
                assertTrue(pill.width <= positions[0].width + 16 * density + 3)
                assertTrue(pill.bottom <= positions[1].top + 1)
                assertTrue("Pill must leave the surrounding bar visible", pill.left > 0 && pill.right < selected.width)
                save(selected, "pill-date-$night-$wallpaper-$dpWidth-$scale")
                compose.runOnIdle { today = false }
                assertTrue(original.asAndroidBitmap().sameAs(compose.onRoot().captureToImage().asAndroidBitmap()))
            }
        }
        compose.onNodeWithText("2026/11/12").performClick()
        compose.onNodeWithText("2026/11/12").performTouchInput { longClick() }
        compose.onNodeWithText(">").performClick()
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, longClicks); assertEquals(1, next) }
    }

}
