package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode

/** Pyriteのライト背景はsurfaceContainerLow。時間割の面だけ一段以上離す。 */
@Composable
internal fun timetableLessonContainerColor(empty: Boolean = false): Color = when {
    empty -> MaterialTheme.colorScheme.surfaceContainer
    LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE ->
        MaterialTheme.colorScheme.surfaceContainerHigh
    else -> MaterialTheme.colorScheme.surfaceContainerLow
}

@Composable
internal fun timetableDateBarColor(today: Boolean): Color =
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        if (today) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        if (today) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    }
