package jp.linkserver.nittcsc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.components.AppConnectedButtonGroup
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.min

internal val morphFrameTimes = listOf(0L, 16L, 32L, 64L, 96L, 160L, 240L, 400L)

/** Compare the contour only. Label position and check pixels are outside this corner region. */
internal fun saveCornerContour(node: SemanticsNodeInteraction, folder: File, name: String, right: Boolean): String {
    folder.mkdirs()
    val image = node.captureToImage()
    File(folder, "$name.png").outputStream().use {
        image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    val pixels = image.toPixelMap()
    val background = pixels[0, 0]
    fun distance(a: Color, b: Color) = (a.red-b.red)*(a.red-b.red) +
        (a.green-b.green)*(a.green-b.green) + (a.blue-b.blue)*(a.blue-b.blue)
    val fill = pixels[pixels.width/2, pixels.height*3/4]
    val threshold = distance(background, fill) * 0.5f
    fun inside(x: Int, y: Int) = distance(pixels[x, y], background) > threshold
    val top = (0 until pixels.height).first { y -> (0 until pixels.width).any { x -> inside(x,y) } }
    val bottom = (pixels.height-1 downTo top).first { y -> (0 until pixels.width).any { x -> inside(x,y) } }
    val contour = (0 until min(32, (bottom-top+1)/4)).map { row ->
        (0 until pixels.width/2).firstOrNull { dx ->
            inside(if (right) pixels.width-1-dx else dx, top+row)
        } ?: pixels.width/2
    }.joinToString(";")
    File(folder, "contours.csv").appendText("$name,${image.width},${bottom-top+1},$right,$contour\n")
    return contour
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ConnectedShapeMorphTest {
    @get:Rule val compose = createComposeRule()
    @Test fun officialMinimalGroupHasIntermediateCorners() = verify(false)
    @Test fun appGroupHasIntermediateCornersInBothDirections() = verify(true)
    @Test fun officialGroupMorphsAfterHeldPressIsReleased() = verify(false, pointer = true)
    @Test fun appGroupMorphsAfterHeldPressIsReleased() = verify(true, pointer = true)

    private fun verify(app: Boolean, pointer: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val run = InstrumentationRegistry.getArguments().getString("motionRun", "current")
        val output = File(context.getExternalFilesDir(null), "m3e-shape/$run/${if (app) "app" else "official"}${if (pointer) "-pointer" else ""}").apply { mkdirs() }
        File(output, "contours.csv").delete()
        var selected by mutableIntStateOf(-1)
        val labels = listOf("月", "火", "水", "木", "金")
        var previous: List<ToggleButtonShapes>? = null
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, dynamicColor = false) {
                val density = LocalDensity.current
                val shapes = listOf(ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    ButtonGroupDefaults.connectedMiddleButtonShapes(), ButtonGroupDefaults.connectedTrailingButtonShapes())
                val checkedForLog = selected
                SideEffect {
                    File(output, "shapes.txt").appendText("checked=$checkedForLog equalPrevious=${previous == shapes}\n" +
                        shapes.joinToString("\n") { s -> listOf(s.shape,s.pressedShape,s.checkedShape).joinToString(" | ") {
                            val corner = it as CornerBasedShape
                            "${it.javaClass.name} $it -> " + listOf(corner.topStart,corner.topEnd,corner.bottomEnd,corner.bottomStart)
                                .joinToString { c -> c.toPx(Size(72f*density.density,40f*density.density),density).toString() }
                        } } + "\n")
                    previous = shapes
                }
                val colors = ToggleButtonDefaults.colors(containerColor = Color.Black, checkedContainerColor = Color.Black,
                    contentColor = Color.White, checkedContentColor = Color.White)
                Surface(color = Color.White) {
                    Box(Modifier.width(360.dp)) {
                        if (app) AppConnectedButtonGroup(labels, selected, { selected = it }, colorsForIndex = { colors })
                        else Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            labels.forEachIndexed { index, label ->
                                ToggleButton(checked = selected == index, onCheckedChange = { selected = index },
                                    shapes = shapes[if (index==0) 0 else if (index==4) 2 else 1], colors = colors,
                                    modifier = Modifier.weight(1f)) { Text(label) }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val failures = mutableListOf<String>()
        for (index in listOf(0,2,4)) {
            compose.runOnIdle { selected = -1 }
            compose.mainClock.advanceTimeBy(1000)
            val node = compose.onNodeWithText(labels[index])
            val before = saveCornerContour(node, output, "$index-before", index==0)
            if (pointer) {
                node.performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(32)
                saveCornerContour(node, output, "$index-down-32", index==0)
                compose.mainClock.advanceTimeBy(208)
                saveCornerContour(node, output, "$index-held-240", index==0)
                node.performTouchInput { up() }
                saveCornerContour(node, output, "$index-release", index==0)
            } else node.performClick()
            val forward = mutableListOf<String>()
            var elapsed = 0L
            for (time in morphFrameTimes) {
                compose.mainClock.advanceTimeBy(time-elapsed); elapsed=time
                forward += saveCornerContour(node, output, "$index-select-$time", index==0)
            }
            val after = forward.last()
            if (pointer) compose.onNodeWithText(labels[if (index == 0) 1 else 0]).performTouchInput { click() }
            else compose.runOnIdle { selected = -1 }
            val reverse = mutableListOf<String>()
            elapsed=0L
            for (time in morphFrameTimes) {
                compose.mainClock.advanceTimeBy(time-elapsed); elapsed=time
                reverse += saveCornerContour(node, output, "$index-clear-$time", index==0)
            }
            if (before == after) failures += "$index endpoints identical"
            if (forward.filter { it != before && it != after }.distinct().size < 2) failures += "$index selection has fewer than two intermediate contours"
            if (reverse.filter { it != before && it != after }.distinct().size < 2) failures += "$index deselection has fewer than two intermediate contours"
        }
        assertTrue(failures.joinToString(), failures.isEmpty())
    }
}
