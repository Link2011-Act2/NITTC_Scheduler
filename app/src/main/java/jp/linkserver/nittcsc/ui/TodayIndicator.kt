package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode

internal enum class TodayIndicatorStyle { DOT, PILL }

// 実機比較時の切り替えはここだけで行う。公開設定・保存データには追加しない。
internal val LocalTodayIndicatorStyle = staticCompositionLocalOf { TodayIndicatorStyle.PILL }

@Composable
private fun todayIndicatorStyle(): TodayIndicatorStyle? =
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) LocalTodayIndicatorStyle.current else null

@Composable
internal fun todayDateLabelPadding(isWeekRange: Boolean): Dp =
    if (todayIndicatorStyle() == TodayIndicatorStyle.PILL && !isWeekRange) 8.dp else 0.dp

/** 日付の中央配置・文字の自動縮小を保ち、文字列だけを強調する。 */
@Composable
internal fun TimetableTodayDateLabel(
    text: String,
    isToday: Boolean,
    isWeekRange: Boolean,
    modifier: Modifier = Modifier
) {
    val style = todayIndicatorStyle()
    val pillLayout = style == TodayIndicatorStyle.PILL && !isWeekRange
    val showPill = pillLayout && isToday
    val showDot = style == TodayIndicatorStyle.DOT && isToday
    val dateLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val dotColor = MaterialTheme.colorScheme.primary
    val label: @Composable (Modifier) -> Unit = { textModifier ->
        Text(
            text,
            modifier = textModifier.then(if (showDot) Modifier.drawWithContent {
                drawContent()
                dateLayout.value?.let { layout ->
                    val radius = 2.5.dp.toPx()
                    drawCircle(
                        color = dotColor,
                        radius = radius,
                        center = Offset(
                            (layout.getLineRight(0) + 4.dp.toPx()).coerceIn(radius, size.width - radius),
                            -1.dp.toPx()
                        )
                    )
                }
            } else Modifier),
            onTextLayout = { if (showDot) dateLayout.value = it },
            style = MaterialTheme.typography.titleMedium,
            color = if (showPill) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MaterialTheme.typography.titleMedium.fontSize),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (pillLayout) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // 透明時も余白を保つ。背景はfillMaxWidthのTextではなく文字幅のBoxへ付ける。
            Box(
                Modifier
                    .background(if (showPill) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) { label(Modifier) }
        }
    } else {
        label(modifier.fillMaxWidth())
    }
}

