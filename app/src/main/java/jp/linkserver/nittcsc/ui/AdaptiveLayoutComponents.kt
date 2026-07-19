package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.InternalFeatureFlags

internal val FormContentMaxWidth = 840.dp
internal val ListContentMaxWidth = 960.dp
internal val CalendarContentMaxWidth = 1120.dp
internal val CalendarCardMaxWidth = 720.dp

/**
 * スマートフォンでは親幅をそのまま使い、大画面では内容の横伸びを抑えて中央に配置する。
 */
@Composable
internal fun AdaptiveContentPane(
    modifier: Modifier = Modifier,
    maxWidth: Dp = ListContentMaxWidth,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = if (InternalFeatureFlags.ADAPTIVE_LARGE_SCREEN_LAYOUT) {
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            },
            content = content
        )
    }
}

@Composable
internal fun <T> EqualHeightEditorRow(
    items: List<T>,
    columnCount: Int,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T, Modifier) -> Unit
) {
    val matchItemHeights = items.size > 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (matchItemHeights) Modifier.height(IntrinsicSize.Min) else Modifier
            ),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            itemContent(
                item,
                Modifier
                    .weight(1f)
                    .then(if (matchItemHeights) Modifier.fillMaxHeight() else Modifier)
            )
        }
        repeat((columnCount - items.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}
