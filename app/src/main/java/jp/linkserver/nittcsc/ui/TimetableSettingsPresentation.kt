package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode
import jp.linkserver.nittcsc.ui.theme.Shapes
import jp.linkserver.nittcsc.ui.theme.Typography
import jp.linkserver.nittcsc.ui.theme.appCardContainerColor

enum class TimetableSettingsPage {
    LESSON,
    EXAM,
    SPECIAL
}

/** 詳細設定の既存M3レイアウトを保ち、M3Eから開く場合の面の色はPyriteに揃える。 */
@Composable
internal fun Material3SettingsPresentation(content: @Composable () -> Unit) {
    val expressiveColorScheme = MaterialTheme.colorScheme
    val cardColor = appCardContainerColor(standardColor = expressiveColorScheme.surfaceContainerLow)
    val colorScheme = expressiveColorScheme.copy(
        background = expressiveColorScheme.surface,
        surfaceContainer = cardColor,
        surfaceContainerLow = cardColor
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
