package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 日付を左右の操作部の中点に置き、狭い幅では前後移動を日付へ寄せる。 */
@Composable
internal fun TimetableDateNavigation(
    dateLabel: String,
    detailLabel: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val compact = maxWidth < 200.dp * LocalDensity.current.fontScale
        val arrowWidth = if (compact) 24.dp else 48.dp
        val textMeasurer = rememberTextMeasurer()
        val minimumDateStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val minimumDateWidthPx = remember(dateLabel, minimumDateStyle, textMeasurer) {
            textMeasurer.measure(dateLabel, style = minimumDateStyle, softWrap = false, maxLines = 1).size.width
        }
        val minimumDateWidth = with(LocalDensity.current) { minimumDateWidthPx.toDp() }
        val stackNavigation = maxWidth < minimumDateWidth + arrowWidth * 2
        val labels: @Composable (Modifier) -> Unit = { labelModifier ->
            Column(
                labelModifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    dateLabel,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MaterialTheme.typography.titleMedium.fontSize),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                detailLabel?.let {
                    Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (stackNavigation) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                labels(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = onPrevious) { Text("<") }
                    IconButton(onClick = onNext) { Text(">") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, modifier = Modifier.width(arrowWidth)) { Text("<") }
                labels(Modifier.weight(1f))
                IconButton(onClick = onNext, modifier = Modifier.width(arrowWidth)) { Text(">") }
            }
        }
    }
}
