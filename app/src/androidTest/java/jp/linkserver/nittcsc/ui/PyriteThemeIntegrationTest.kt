package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import android.os.Build
import androidx.compose.ui.graphics.Color
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

    @Test fun expressiveSharesOnlyFourErrorRolesAndPreservesPublishedTheme() {
        var dark by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var expected = ""
        var actual = ""
        var colors = ""
        var published: ColorScheme? = null
        var standard: ColorScheme? = null
        var expressive: ColorScheme? = null
        compose.setContent {
            PyriteTheme(style = PyriteStyle.Material3Expressive, darkTheme = dark, dynamicColor = dynamic) {
                val theme = "${MaterialTheme.colorScheme}|${MaterialTheme.typography}|${MaterialTheme.shapes}|${MaterialTheme.motionScheme::class}"
                val scheme = MaterialTheme.colorScheme
                SideEffect { expected = theme.substringAfter("|"); published = scheme }
            }
            AppTheme(UiDesignMode.MATERIAL_3, darkTheme = dark, dynamicColor = dynamic) {
                val scheme = MaterialTheme.colorScheme
                SideEffect { standard = scheme }
            }
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = dark, dynamicColor = dynamic) {
                val theme = "${MaterialTheme.colorScheme}|${MaterialTheme.typography}|${MaterialTheme.shapes}|${MaterialTheme.motionScheme::class}"
                val scheme = MaterialTheme.colorScheme
                SideEffect {
                    actual = theme.substringAfter("|")
                    expressive = scheme
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
                val m3 = checkNotNull(standard)
                val m3e = checkNotNull(expressive)
                val pyrite = checkNotNull(published)
                assertEquals(m3.error, m3e.error)
                assertEquals(m3.onError, m3e.onError)
                assertEquals(m3.errorContainer, m3e.errorContainer)
                assertEquals(m3.onErrorContainer, m3e.onErrorContainer)
                assertEquals(pyrite.toString(), m3e.copy(error = pyrite.error, onError = pyrite.onError,
                    errorContainer = pyrite.errorContainer, onErrorContainer = pyrite.onErrorContainer).toString())
                if (dynamic && Build.VERSION.SDK_INT >= 31) {
                    assertEquals(Color(if (dark) 0xFFFF3B30 else 0xFFFF0000), m3e.error)
                    assertEquals(Color.White, m3e.onError)
                    assertEquals(Color(0xFFFFE8E6), m3e.errorContainer)
                    assertEquals(Color(0xFFD50000), m3e.onErrorContainer)
                }
                file.appendText("dark=$dark dynamic=$dynamic $colors\n")
            }
        }
    }
}
