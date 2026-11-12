package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode
import jp.linkserver.nittcsc.ui.theme.appCardContainerColor

/** 授業も設定画面と共通の主面。空きコマの控えめな面は維持する。 */
@Composable
internal fun timetableLessonContainerColor(empty: Boolean = false): Color = appCardContainerColor(
    standardColor = if (empty) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    subdued = empty
)

@Composable
internal fun timetableDateBarColor(today: Boolean): Color =
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        appCardContainerColor()
    } else {
        if (today) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    }

@Composable
internal fun timetableDisplayModeContainerColor(): Color =
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        // 主面に近い中立色に留め、選択インジケーターだけを強調する。
        lerp(appCardContainerColor(), MaterialTheme.colorScheme.surfaceContainer, 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
