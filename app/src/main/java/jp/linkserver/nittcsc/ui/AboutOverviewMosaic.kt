package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.linkserver.nittcsc.R

@Composable
internal fun AboutOverviewMosaic(
    channelName: String,
    channelLabel: String,
    versionName: String,
    simpleVersion: String,
    versionCode: Int,
    buildNumber: String,
    onVersionClick: () -> Unit,
    onChannelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val gutterColor = MaterialTheme.colorScheme.background
    val cardShape = RoundedCornerShape(20.dp)
    val gap = 3.dp
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth()
    ) {
        val squareSize = (maxWidth - gap) * 0.5f
        // アプリ名や完全なバージョン番号が収まらない幅では、アイコンを上へ移す。
        val stackedHeader = maxWidth / fontScale < 320.dp
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Surface(
                onClick = onVersionClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 176.dp),
                shape = cardShape,
                color = cardColor
            ) {
                if (stackedHeader) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AboutAppIcon(Modifier.size(64.dp))
                        AboutAppIdentity(channelLabel, versionName, versionCode)
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AboutAppIcon(Modifier.size(92.dp))
                        AboutAppIdentity(
                            channelLabel, versionName, versionCode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 通常は正方形。長い文字列や文字拡大時には、両カードを同じ高さで広げる。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .drawWithContent {
                        drawContent()
                        val gapPx = gap.toPx()
                        val overlap = 1.dp.toPx()
                        val bridgeLength = 48.dp.toPx()
                        val leftCardWidth = (size.width - gapPx) * 0.5f
                        val horizontalStart = (leftCardWidth - bridgeLength) * 0.5f
                        val verticalStart = (size.height - bridgeLength) * 0.5f

                        // 上段と左下、左右のカードをつなぎ、端に小さな丸い切り欠きを置く。
                        drawRect(
                            color = cardColor,
                            topLeft = Offset(horizontalStart, -gapPx - overlap),
                            size = Size(bridgeLength, gapPx + overlap * 2f)
                        )
                        drawCircle(gutterColor, gapPx * 0.5f, Offset(horizontalStart, -gapPx * 0.5f))
                        drawCircle(gutterColor, gapPx * 0.5f, Offset(horizontalStart + bridgeLength, -gapPx * 0.5f))
                        drawRect(
                            color = cardColor,
                            topLeft = Offset(leftCardWidth - overlap, verticalStart),
                            size = Size(gapPx + overlap * 2f, bridgeLength)
                        )
                        drawCircle(gutterColor, gapPx * 0.5f, Offset(leftCardWidth + gapPx * 0.5f, verticalStart))
                        drawCircle(gutterColor, gapPx * 0.5f, Offset(leftCardWidth + gapPx * 0.5f, verticalStart + bridgeLength))
                    },
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                Surface(
                    onClick = onChannelClick,
                    modifier = Modifier.weight(1f).heightIn(min = squareSize).fillMaxHeight(),
                    shape = cardShape,
                    color = cardColor
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(22.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.about_overview_channel_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = channelName,
                            modifier = Modifier.padding(top = 14.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 25.sp,
                            lineHeight = 29.sp
                        )
                    }
                }
                Surface(
                    onClick = onVersionClick,
                    modifier = Modifier.weight(1f).heightIn(min = squareSize).fillMaxHeight(),
                    shape = cardShape,
                    color = cardColor
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(22.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.about_overview_build_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = buildNumber,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 16.sp
                            )
                        }
                        Column(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.about_simple_version_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.about_simple_version_value, simpleVersion),
                                fontWeight = FontWeight.Bold,
                                fontSize = 25.sp,
                                lineHeight = 29.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutAppIcon(modifier: Modifier) {
    Surface(modifier = modifier, shape = CircleShape) {
        Image(
            painter = painterResource(R.drawable.icon_doc),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun AboutAppIdentity(
    channelLabel: String,
    versionName: String,
    versionCode: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.about_version_label, channelLabel, versionName, versionCode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
