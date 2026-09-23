package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode
import jp.linkserver.nittcsc.ui.theme.Shapes
import jp.linkserver.nittcsc.ui.theme.Typography

enum class TimetableSettingsPage {
    LESSON,
    EXAM,
    SPECIAL
}

/** M3E設定から開いた詳細設定にも、既存のM3設定画面の見た目を適用する。 */
@Composable
internal fun Material3SettingsPresentation(content: @Composable () -> Unit) {
    val expressiveColorScheme = MaterialTheme.colorScheme
    val colorScheme = expressiveColorScheme.copy(
        background = expressiveColorScheme.surface,
        surfaceContainer = expressiveColorScheme.surfaceContainerLow
    )
    CompositionLocalProvider(LocalUiDesignMode provides UiDesignMode.MATERIAL_3) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
