package jp.linkserver.nittcsc.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.logic.QrDisplaySpeed
import jp.linkserver.nittcsc.ui.components.AppConnectedButtonGroup

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QrDisplaySpeedSelector(
    speed: QrDisplaySpeed, enabled: Boolean, onSpeedChange: (QrDisplaySpeed) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = listOf(stringResource(R.string.qr_speed_stable), stringResource(R.string.qr_speed_standard),
        stringResource(R.string.qr_speed_fast), stringResource(R.string.qr_speed_ultra_fast))
    val description = stringResource(R.string.qr_display_speed)
    val defaults = ToggleButtonDefaults.toggleButtonColors()
    val extreme = speed == QrDisplaySpeed.ULTRA_FAST
    // checkedの切り替えでも同じアニメーション色を使い、選択解除時も滑らかに戻す。
    val extremeContainer by animateColorAsState(
        if (extreme) MaterialTheme.colorScheme.errorContainer else defaults.containerColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "qrSpeedContainer")
    val extremeContent by animateColorAsState(
        if (extreme) MaterialTheme.colorScheme.onErrorContainer else defaults.contentColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "qrSpeedContent")
    val extremeColors = defaults.copy(containerColor = extremeContainer, checkedContainerColor = extremeContainer,
        contentColor = extremeContent, checkedContentColor = extremeContent)
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge
    val labelWidth = remember(labels, textStyle, measurer) { labels.maxOf { measurer.measure(it, textStyle).size.width } }
    val density = LocalDensity.current
    // チェック16dp + 間隔4dp + 左右余白4dp。文字倍率も含めて必要幅を求める。
    val buttonWidth = maxOf(48.dp, with(density) { labelWidth.toDp() } + 28.dp)
    val requiredWidth = buttonWidth * labels.size + ButtonGroupDefaults.ConnectedSpaceBetween * (labels.size - 1)
    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        val groupWidth = maxOf(maxWidth, requiredWidth)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            AppConnectedButtonGroup(labels, speed.ordinal, { onSpeedChange(QrDisplaySpeed.entries[it]) },
                modifier = Modifier.width(groupWidth), enabled = enabled,
                colorsForIndex = { if (it == QrDisplaySpeed.ULTRA_FAST.ordinal) extremeColors else defaults },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                iconSize = 16.dp, iconSpacing = 4.dp)
        }
    }
}
