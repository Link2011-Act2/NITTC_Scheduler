package jp.linkserver.nittcsc.ui

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.logic.qrFragmentDotLayout
import jp.linkserver.nittcsc.logic.qrFragmentDotWindow
import jp.linkserver.nittcsc.logic.qrFragmentToRetire
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** 退場履歴だけがUI固有の状態。受信済みindexは常にCollectorのスナップショットを使う。 */
@Composable
internal fun QrFragmentDotIndicator(
    total: Int,
    receivedIndices: Set<Int>,
    receivedAtMillis: Map<Int, Long>,
    active: Boolean,
    completed: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val reducedMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val currentReceived by rememberUpdatedState(receivedIndices)
    val currentReceiptTimes by rememberUpdatedState(receivedAtMillis)
    var retired by remember { mutableStateOf(emptySet<Int>()) }
    var retiring by remember { mutableStateOf<Int?>(null) }
    var fixed by remember { mutableStateOf(false) }
    val description = stringResource(R.string.qr_fragment_progress, receivedIndices.size, total)
    BoxWithConstraints(modifier.semantics { contentDescription = description }) {
        val diameter = 5.dp
        val spacing = 4.dp
        val panelPadding = 8.dp
        val availableWidth = (constraints.maxWidth - with(density) { (panelPadding * 2).toPx() }).coerceAtLeast(0f)
        val diameterPx = with(density) { diameter.toPx() }
        val pitchPx = with(density) { (diameter + spacing).toPx() }
        val badgeSize = remember(total, labelStyle, textMeasurer, density) {
            textMeasurer.measure("+$total", labelStyle).size
        }
        val layout = remember(availableWidth, density, badgeSize, total) {
            qrFragmentDotLayout(availableWidth, diameterPx,
                with(density) { spacing.toPx() }, badgeSize.width.toFloat(), total)
        }
        val window = remember(total, layout.capacity, retired) { qrFragmentDotWindow(total, layout.capacity, retired) }
        // 一度穴埋めフェーズになったら、幅が変わっても退場を再開しない。
        SideEffect { if (window.overflow == 0) fixed = true }
        // 新しい受信でこのループをキャンセルしない。高速QRでもpulse→退場を最後まで実行する。
        LaunchedEffect(total, layout.capacity, active, completed, fixed) {
            if (!active || completed || fixed) return@LaunchedEffect
            try {
                while (isActive) {
                    val index = snapshotFlow { qrFragmentToRetire(total, layout.capacity, currentReceived, retired, fixed) }
                        .first { it != null } ?: continue
                    val wait = (currentReceiptTimes[index] ?: 0L) + 240L - SystemClock.elapsedRealtime()
                    if (!reducedMotion && wait > 0L) delay(wait)
                    // 幅変更・完了でキャンセルされる。未受信indexを退場対象にしない。
                    if (qrFragmentToRetire(total, layout.capacity, currentReceived, retired, fixed) == null) continue
                    val visible = index in qrFragmentDotWindow(total, layout.capacity, retired).indices
                    if (visible && !reducedMotion) {
                        retiring = index
                        delay(120L)
                    }
                    retired = retired + index
                    retiring = null
                    if (!reducedMotion) delay(60L)
                }
            } finally { retiring = null }
        }
        val rows = if (window.overflow > 0) 3 else ((window.indices.size + layout.columns - 1) / layout.columns).coerceAtLeast(1)
        val badgeY = 2 * pitchPx + with(density) { diameter.toPx() } / 2f - badgeSize.height / 2f
        val gridHeight = diameter + (diameter + spacing) * (rows - 1)
        val contentHeight = if (total > layout.columns * 3) maxOf(gridHeight, with(density) { (badgeY + badgeSize.height).toDp() }) else gridHeight
        val contentWidth = if (total > layout.columns * 3) availableWidth
            else minOf(availableWidth, diameterPx + (minOf(window.indices.size, layout.columns) - 1).coerceAtLeast(0) * pitchPx)
        val badgeWidth = minOf(badgeSize.width.toFloat(), contentWidth)
        val badgeMaxWidth = with(density) { badgeWidth.toDp() }
        Box(Modifier.align(Alignment.CenterEnd).width(with(density) { contentWidth.toDp() } + panelPadding * 2)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.60f), MaterialTheme.shapes.large)
            .padding(panelPadding).height(contentHeight)) {
            window.indices.forEachIndexed { position, index ->
                val row = position / layout.columns
                val rowCount = minOf(layout.columns, window.indices.size - row * layout.columns)
                val rowWidth = diameterPx + (rowCount - 1) * pitchPx
                val rowEnd = if (row == 2 && window.overflow > 0) contentWidth - badgeWidth - with(density) { spacing.toPx() }
                    else contentWidth
                val rowStart = (rowEnd - rowWidth).coerceAtLeast(0f)
                key(index) {
                    FragmentDot(index, index in receivedIndices, receivedAtMillis[index], index == retiring,
                        IntOffset((rowStart + (position % layout.columns) * pitchPx).roundToInt(), (row * pitchPx).roundToInt()),
                        active, reducedMotion, diameter, pitchPx)
                }
            }
            var shownOverflow by remember { mutableIntStateOf(window.overflow) }
            SideEffect { if (window.overflow > 0) shownOverflow = window.overflow }
            AnimatedVisibility(window.overflow > 0, enter = fadeIn(tween(100)), exit = fadeOut(tween(100)),
                modifier = Modifier.offset {
                    IntOffset((contentWidth - badgeWidth).roundToInt(), badgeY.roundToInt())
                }) {
                Text(stringResource(R.string.qr_fragment_overflow, shownOverflow), style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Clip,
                    modifier = Modifier.widthIn(max = badgeMaxWidth))
            }
        }
    }
}

@Composable
private fun FragmentDot(
    index: Int, received: Boolean, receivedAt: Long?, retiring: Boolean, position: IntOffset,
    active: Boolean, reducedMotion: Boolean, diameter: androidx.compose.ui.unit.Dp, pitchPx: Float
) {
    val colors = MaterialTheme.colorScheme
    val pulse = remember { Animatable(1f) }
    val entry = remember { Animatable(if (reducedMotion) 1f else 0f) }
    var receiptAnimated by remember { mutableStateOf(false) }
    LaunchedEffect(received, active) {
        if (received && !receiptAnimated) {
            receiptAnimated = true
            // 画面外で以前受信済みになったdotは、後から入っても再pulseしない。
            if (active && !reducedMotion && receivedAt != null && SystemClock.elapsedRealtime() - receivedAt < 240L) {
                pulse.animateTo(1.3f, tween(75))
                pulse.animateTo(1f, tween(125))
            }
        }
        pulse.snapTo(1f)
    }
    LaunchedEffect(active) {
        if (active && !reducedMotion) entry.animateTo(1f, tween(100)) else entry.snapTo(1f)
    }
    val animatedPosition by animateIntOffsetAsState(position,
        if (reducedMotion) tween(0) else spring(dampingRatio = 1f, stiffness = 700f), label = "fragmentPosition")
    val exit by animateFloatAsState(if (retiring) 0f else 1f,
        tween(if (reducedMotion) 0 else 120), label = "fragmentExit")
    val description = stringResource(if (received) R.string.qr_fragment_received else R.string.qr_fragment_missing, index + 1)
    val outlineWidth = with(LocalDensity.current) { 0.7.dp.toPx() }
    val outline = remember(outlineWidth) { Stroke(width = outlineWidth) }
    Canvas(Modifier.offset { animatedPosition }.size(diameter).graphicsLayer {
        scaleX = pulse.value; scaleY = pulse.value
        alpha = entry.value * exit
        translationX = (1f - exit) * pitchPx * 0.6f
    }.semantics { contentDescription = description }) {
        if (received) drawCircle(colors.primary)
        else drawCircle(colors.onSurfaceVariant.copy(alpha = 0.65f), radius = size.minDimension / 2f - outlineWidth / 2f,
            style = outline)
    }
}
