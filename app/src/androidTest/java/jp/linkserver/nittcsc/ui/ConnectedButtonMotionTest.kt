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
        val labels = if (qr) listOf(R.string.qr_speed_stable, R.string.qr_speed_standard,
            R.string.qr_speed_fast, R.string.qr_speed_ultra_fast).map(context::getString)
            else listOf("月", "火", "水", "木", "金")
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
        fun capture(target: String, frame: String): Float {
            val left = compose.onNodeWithText(target, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value
            positions.appendText("$frame,$left\n")
            File(output, "$frame.png").outputStream().use {
                compose.onNodeWithTag("motion-group").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            return left
        }
        // A semantic click omits PressInteraction. Exercise both a quick tap and a held press.
        // Sample the target AND the previously selected button, so reverse Morph is measured too.
        for (index in listOf(0, labels.size / 2, labels.lastIndex)) for (hold in listOf(16L, 240L)) {
            val previous = if (index == 0) 1 else 0
            compose.runOnIdle { selected = previous; speed = QrDisplaySpeed.entries[previous] }
            compose.mainClock.advanceTimeBy(1000)
            val target = labels[index]
            val prefix = "$index-hold-$hold"
            val node = compose.onNodeWithText(target)
            val oldNode = compose.onNodeWithText(labels[previous])
            val corners = File(output, "corners/$prefix")
            val before = saveCornerContour(node, corners, "before", index == 0)
            val oldBefore = saveCornerContour(oldNode, corners, "old-before", previous == 0)
            val textBefore = capture(target, "$prefix-before")
            node.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(hold)
            val pressed = saveCornerContour(node, corners, "pressed-$hold", index == 0)
            capture(target, "$prefix-pressed")
            node.performTouchInput { up() }
            val release = saveCornerContour(node, corners, "release", index == 0)
            capture(target, "$prefix-release")
            val forward = mutableListOf<String>()
            val reverse = mutableListOf<String>()
            val textPositions = mutableListOf<Float>()
            var elapsed = 0L
            for (time in morphFrameTimes + 640L + 800L) {
                compose.mainClock.advanceTimeBy(time - elapsed)
                elapsed = time
                forward += saveCornerContour(node, corners, "select-$time", index == 0)
                reverse += saveCornerContour(oldNode, corners, "clear-$time", previous == 0)
                textPositions += capture(target, "$prefix-select-$time")
            }
            val after = forward.last()
            val oldAfter = reverse.last()
            assertTrue("$prefix must change the corner, independently of label/check", before != after)
            assertTrue("$prefix must not reach the selected shape while held", pressed != after)
            assertTrue("$prefix must not jump to the selected shape on release", release != after)
            assertTrue("$prefix needs multiple selection contours after release",
                forward.filter { it != release && it != after }.distinct().size >= 2)
            assertTrue("$prefix needs multiple deselection contours after release",
                reverse.filter { it != oldBefore && it != oldAfter }.distinct().size >= 2)
            val textAfter = textPositions.last()
            assertTrue("$prefix check must move the label", textAfter > textBefore + 1f)
            assertTrue("$prefix label must pass through an intermediate position",
                textPositions.any { it > textBefore && it < textAfter })
        }
        repeat(6) { index ->
            compose.onNodeWithText(if (index % 2 == 0) labels.first() else labels.last()).performTouchInput { click() }
            compose.mainClock.advanceTimeBy(32)
            capture(labels.last(), "interrupt-$index")
            saveCornerContour(compose.onNodeWithText(labels.last()), File(output, "corners/interrupt"), "$index", false)
        }
        compose.mainClock.advanceTimeBy(1000)
        capture(labels.last(), "settled")
        compose.onNodeWithText(labels.last()).assertIsOn()
    }
}
