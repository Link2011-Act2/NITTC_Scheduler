package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.pyrite.theme.PyriteTheme
import jp.linkserver.pyrite.theme.PyriteStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class PyriteThemeIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expressiveUsesPublishedColorsTypographyShapesAndMotionWithoutInnerOverride() {
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var expected = ""
        var actual = ""
        var colors = ""
        compose.setContent {
            PyriteTheme(style = PyriteStyle.Material3Expressive, darkTheme = dark, dynamicColor = dynamic) {
                val theme = "${MaterialTheme.colorScheme}|${MaterialTheme.typography}|${MaterialTheme.shapes}|${MaterialTheme.motionScheme::class}"
                SideEffect { expected = theme }
            }
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = dark, dynamicColor = dynamic) {
                val theme = "${MaterialTheme.colorScheme}|${MaterialTheme.typography}|${MaterialTheme.shapes}|${MaterialTheme.motionScheme::class}"
                val scheme = MaterialTheme.colorScheme
                SideEffect {
                    actual = theme
                    colors = "surface=%08X,background=%08X,card=%08X".format(
                        scheme.surface.toArgb(), scheme.background.toArgb(),
                        (if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest).toArgb())
                }
            }
        }
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "pyrite-migration/after/colors.txt")
        file.parentFile?.mkdirs(); file.writeText("")
        for (night in listOf(false, true)) for (wallpaper in listOf(false, true)) {
            compose.runOnIdle { dark = night; dynamic = wallpaper }
            compose.runOnIdle {
                assertEquals("dark=$dark dynamic=$dynamic", expected, actual)
                file.appendText("dark=$dark dynamic=$dynamic $colors\n")
            }
        }
    }
}
