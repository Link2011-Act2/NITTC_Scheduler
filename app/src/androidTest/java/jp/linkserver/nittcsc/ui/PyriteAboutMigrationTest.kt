package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Offline About rendering and host callbacks; never requests GitHub or installs an update. */
@RunWith(Parameterized::class)
class PyriteAboutMigrationTest(
    private val mode: UiDesignMode, private val dark: Boolean,
    private val dynamic: Boolean, private val fontScale: Float
) {
    @get:Rule val compose = createComposeRule()

    @Test fun metadataDialogsAndUpdateActionsRemainReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun text(id: Int) = context.getString(id)
        var backs = 0
        var oss = 0
        var checks = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme(mode, darkTheme = dark, dynamicColor = dynamic) {
                    AboutScreen(onBack = { backs++ }, onCheckForUpdates = {
                        checks++
                        jp.linkserver.goldy.CheckOutcome.UpToDate
                    }, onOssLicenses = { oss++ })
                }
            }
        }
        fun capture(part: String) {
            compose.mainClock.advanceTimeBy(1_200)
            compose.waitForIdle()
            val end = SystemClock.uptimeMillis() + 400
            compose.waitUntil(timeoutMillis = 2_000) { SystemClock.uptimeMillis() >= end }
            val phase = InstrumentationRegistry.getArguments().getString("migrationPhase", "after")
            val dir = File(context.getExternalFilesDir(null), "pyrite-migration/$phase").apply { mkdirs() }
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "about-${mode.name}-$dark-$dynamic-$fontScale-$part.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        capture("top")
        compose.onNodeWithText(text(R.string.about_overview_channel_title)).performScrollTo().performClick()
        compose.onNodeWithText(text(android.R.string.ok)).performClick()
        compose.onNodeWithText(text(R.string.about_simple_version_title)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about_version_details_title)).assertIsDisplayed()
        compose.onNodeWithText(text(android.R.string.ok)).performClick()
        compose.onNodeWithText(text(R.string.about_update_check_button)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about_update_latest)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.about_oss_open)).performScrollTo().performClick()
        capture("actions")
        compose.onNodeWithContentDescription(text(R.string.cd_back)).performClick()
        compose.runOnIdle { assertEquals(1, checks); assertEquals(1, oss); assertEquals(1, backs) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0},dark={1},dynamic={2},font={3}")
        fun cases(): List<Array<Any>> = buildList {
            for (mode in UiDesignMode.entries) for (dark in listOf(false, true)) for (dynamic in listOf(false, true)) {
                add(arrayOf(mode, dark, dynamic, 1f))
            }
            add(arrayOf(UiDesignMode.MATERIAL_3_EXPRESSIVE, false, false, 1.8f))
            add(arrayOf(UiDesignMode.MATERIAL_3, true, false, 1.8f))
        }
    }
}
