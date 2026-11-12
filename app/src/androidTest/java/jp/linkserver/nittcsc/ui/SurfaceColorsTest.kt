package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.*
import jp.linkserver.pyrite.settings.SettingsContentCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min

class SurfaceColorsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hierarchyMatchesPyriteCardsAndKeepsTextContrast() {
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var colors = emptyList<Color>()
        var warningContrast = 0f
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val run = InstrumentationRegistry.getArguments().getString("motionRun", "current")
        val folder = File(context.getExternalFilesDir(null), "surface-refresh/$run").apply { mkdirs() }
        val csv = File(folder, "roles.csv").apply {
            writeText("dark,dynamic,canvas,card,muted,inset,mutedInset,dialog,dialogInset,onSurface,onSurfaceVariant,primary\n")
        }
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = dark, dynamicColor = dynamic) {
                val scheme = MaterialTheme.colorScheme
                val card = appCardContainerColor()
                val muted = appCardContainerColor(subdued = true)
                val inset = appInsetContainerColor(parentColor = card)
                val mutedInset = appInsetContainerColor(parentColor = muted)
                val dialogInset = appDialogSectionColor(scheme.surfaceContainerLow)
                SideEffect {
                    warningContrast = contrast(scheme.onErrorContainer, scheme.errorContainer)
                    colors = listOf(scheme.background, card, muted, inset, mutedInset, scheme.surfaceContainerHigh,
                        dialogInset, scheme.onSurface, scheme.onSurfaceVariant, scheme.primary)
                }
                Surface(color = scheme.background) {
                    Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsContentCard(Modifier.testTag("pyrite-reference")) { Spacer(Modifier.height(48.dp)) }
                        Surface(Modifier.fillMaxWidth().height(72.dp).testTag("app-card"), color = card) {}
                    }
                }
            }
        }
        for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            compose.runOnIdle { dark = night; dynamic = wallpaper }
            compose.waitForIdle()
            val c = colors
            assertNotEquals("Canvas/card", c[0], c[1])
            assertNotEquals("Canvas/muted card", c[0], c[2])
            assertNotEquals("Active/muted card", c[1], c[2])
            assertNotEquals("Card/inset", c[1], c[3])
            assertNotEquals("Muted card/inset", c[2], c[4])
            assertNotEquals("Dialog/inset", c[5], c[6])
            for (background in c.take(7)) for (foreground in c.slice(7..8)) {
                assertTrue("Readable neutral text: dark=$dark dynamic=$dynamic contrast=${contrast(foreground, background)}",
                    contrast(foreground, background) >= 4.5f)
            }
            assertTrue("Section headings", contrast(c[9], c[0]) >= 4.5f)
            assertTrue("Warning container text", warningContrast >= 4.5f)
            fun pixel(tag: String): Color {
                val image = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                return image[image.width / 2, image.height / 2]
            }
            assertEquals("Match the actual Pyrite SettingsContentCard fill", pixel("pyrite-reference").toArgb(), pixel("app-card").toArgb())
            csv.appendText("$dark,$dynamic," + c.joinToString(",") { "%08X".format(it.toArgb()) } + "\n")
        }
    }

    @Test fun normalMaterial3KeepsEveryCallersOriginalColor() {
        var dark by mutableStateOf(false)
        val original = Color(0xFF826A93)
        var actual = emptyList<Color>()
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3, darkTheme = dark, dynamicColor = false) {
                val values = listOf(appCardContainerColor(original), appCardContainerColor(original, subdued = true),
                    appInsetContainerColor(original, parentColor = original), appDialogSectionColor(original))
                SideEffect { actual = values }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night }
            compose.runOnIdle { assertEquals(List(4) { original }, actual) }
        }
    }

    private fun contrast(a: Color, b: Color): Float =
        (max(a.luminance(), b.luminance()) + 0.05f) / (min(a.luminance(), b.luminance()) + 0.05f)
}
