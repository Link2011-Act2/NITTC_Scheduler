package jp.linkserver.nittcsc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import jp.linkserver.nittcsc.data.UiDesignMode

internal val LocalAppDarkTheme = staticCompositionLocalOf { false }

/**
 * Pyrite SettingsContentCardと同じ主面。ライトはLowのキャンバスにLowest、ダークはHigh。
 * 完了・空きコマなどの控えめなカードにはContainerを使う。通常M3は呼び出し元の色を維持する。
 */
@Composable
internal fun appCardContainerColor(
    standardColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    subdued: Boolean = false
): Color = if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
    when {
        subdued -> MaterialTheme.colorScheme.surfaceContainer
        LocalAppDarkTheme.current -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
} else standardColor

/**
 * 主カードやダイアログ内の補助面。親がContainerの場合も同色に重ならないよう一段変える。
 * アクセント色の選択・警告・期限表示には使用しない。
 */
@Composable
internal fun appInsetContainerColor(
    standardColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    parentColor: Color = appCardContainerColor()
): Color = if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
    when {
        parentColor == MaterialTheme.colorScheme.surfaceContainer -> MaterialTheme.colorScheme.surfaceContainerHighest
        !LocalAppDarkTheme.current && parentColor == MaterialTheme.colorScheme.surfaceContainerHigh ->
            MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
} else standardColor

/** 標準AlertDialogのHighを親とする補助面。主要カードと同じ色を無条件に重ねない。 */
@Composable
internal fun appDialogSectionColor(standardColor: Color): Color = appInsetContainerColor(
    standardColor = standardColor,
    parentColor = MaterialTheme.colorScheme.surfaceContainerHigh
)
