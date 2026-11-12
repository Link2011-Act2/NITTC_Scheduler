package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
        if (today) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainer
    } else {
        if (today) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    }

@Composable
internal fun timetableDisplayModeContainerColor(): Color =
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
