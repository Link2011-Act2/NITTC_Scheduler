package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.logic.QrDisplaySpeed
import jp.linkserver.nittcsc.ui.components.AppConnectedButtonGroup
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ConnectedButtonMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun weekdayShapeCheckAndTextMoveTogether() = verify(qr = false)
    @Test fun qrSpeedShapeCheckAndTextMoveTogether() = verify(qr = true)

    private fun verify(qr: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val run = InstrumentationRegistry.getArguments().getString("motionRun", "current")
        val output = File(context.getExternalFilesDir(null), "m3e-revision/$run/${if (qr) "qr" else "weekday"}").apply { mkdirs() }
        var selected by mutableIntStateOf(0)
        var speed by mutableStateOf(QrDisplaySpeed.STANDARD)
        val target = if (qr) context.getString(R.string.qr_speed_fast) else "水"
        val alternate = if (qr) context.getString(R.string.qr_speed_stable) else "月"
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, dynamicColor = false) {
                Surface {
                    Box(Modifier.width(360.dp).testTag("motion-group")) {
                        if (qr) QrDisplaySpeedSelector(speed, true, { speed = it })
                        else AppConnectedButtonGroup(listOf("月", "火", "水", "木", "金"), selected, { selected = it })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val positions = File(output, "positions.csv").apply { writeText("frame,textLeftDp\n") }
        fun capture(frame: String): Float {
            val left = compose.onNodeWithText(target, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value
            positions.appendText("$frame,$left\n")
            File(output, "$frame.png").outputStream().use {
                compose.onNodeWithTag("motion-group").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            return left
        }
        val before = capture("00-before")
        compose.onNodeWithText(target).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(32)
        capture("01-press-32ms")
        compose.mainClock.advanceTimeBy(96)
        capture("02-press-held")
        compose.onNodeWithText(target).performTouchInput { up() }
        capture("03-release")
        var elapsed = 0L
        var during = before
        for (time in listOf(16L, 32L, 64L, 112L, 192L, 400L, 800L)) {
            compose.mainClock.advanceTimeBy(time - elapsed)
            elapsed = time
            val left = capture("04-select-${time}ms")
            if (time == 64L) during = left
        }
        val after = capture("05-selected")
        repeat(6) { index ->
            compose.onNodeWithText(if (index % 2 == 0) alternate else target).performTouchInput { click() }
            compose.mainClock.advanceTimeBy(32)
            capture("06-interrupt-$index")
        }
        compose.mainClock.advanceTimeBy(1000)
        capture("07-settled")
        assertTrue("The check must make room by moving the label", after > before + 1f)
        assertTrue("Label movement must pass through an intermediate position: $before, $during, $after",
            during > before && during < after)
    }
}
