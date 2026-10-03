package jp.linkserver.nittcsc.ui

import android.animation.ValueAnimator
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.logic.QrScanTarget
import jp.linkserver.nittcsc.logic.QrScannerPhase
import jp.linkserver.nittcsc.logic.QrScannerRotation
import jp.linkserver.nittcsc.logic.QrScannerVisualState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QrScannerVisuals(state: QrScannerVisualState, preview: PreviewView?, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val effects = remember(context) { QrScannerCameraEffects(context) }
    val morph = remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Cookie9Sided) }
    val progressStartFraction = remember(morph) {
        val reference = PathMeasure(morph.toPath(1f), true)
        val point = FloatArray(2)
        var start = 0f
        var top = Float.MAX_VALUE
        repeat(128) { i ->
            val fraction = i / 128f
            reference.getPosTan(reference.length * fraction, point, null)
            if (point[1] < top) { top = point[1]; start = fraction }
        }
        start
    }
    val rotation = remember { QrScannerRotation() }
    val successMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val failureMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val path = remember { Path() }
    val progressPath = remember { Path() }
    val measure = remember { PathMeasure() }
    val matrix = remember { Matrix() }
    val stroke = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND } }
    val fill = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val primary = MaterialTheme.colorScheme.primary
    val scrim = MaterialTheme.colorScheme.scrim
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onError = MaterialTheme.colorScheme.onError
    val labelBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val stage = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val check = remember { Animatable(0f) }
    val ripple = remember { Animatable(-1f) }
    val completionBlur = remember { Animatable(0f) }
    val reducedMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val completed = state.phase == QrScannerPhase.Completed
    val failed = state.phase == QrScannerPhase.Failed
    val terminal = completed || failed
    val outlineColor by animateColorAsState(if (failed) MaterialTheme.colorScheme.error else primary,
        tween(200), label = "qrOutcomeColor")
    LaunchedEffect(completed, active) {
        if (!completed || !active) completionBlur.snapTo(0f)
        else if (reducedMotion) completionBlur.snapTo(1f)
        else completionBlur.animateTo(1f, tween(400))
    }
    val tracking = state.target != null
    val hasReceived = state.received > 0
    var showReceivingHint by remember { mutableStateOf(false) }
    // 受信済み枚数の増加や重複検出では延長せず、最初の1枚から一度だけ表示する。
    LaunchedEffect(hasReceived, state.total) {
        showReceivingHint = hasReceived && state.total >= 3
        if (showReceivingHint) {
            delay(1_500L)
            showReceivingHint = false
        }
    }
    var frozen by remember { mutableStateOf<QrScanTarget?>(null) }
    var terminalStarted by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val idleSize = minOf(screenWidth, screenHeight) * 0.85f
        val target = frozen ?: state.target ?: QrScanTarget(screenWidth / 2f, screenHeight / 2f, idleSize, idleSize, 0f)
        val motion = spring<Float>(dampingRatio = 0.88f, stiffness = 480f)
        val x by animateFloatAsState(target.x, motion, label = "qrX")
        val y by animateFloatAsState(target.y, motion, label = "qrY")
        val w by animateFloatAsState(target.width, motion, label = "qrWidth")
        val h by animateFloatAsState(target.height, motion, label = "qrHeight")
        val angle by animateFloatAsState(rotation.follow(target.angle), motion, label = "qrAngle")
        val progress by animateFloatAsState(state.progress, tween(160), label = "qrProgress")
        // 終了演出中は検出の猶予切れで演出を再起動しない。
        LaunchedEffect(completed, failed, if (terminal) false else tracking, active) {
            if (!active) { ripple.snapTo(-1f); return@LaunchedEffect }
            if (terminal) {
                if (!terminalStarted) frozen = QrScanTarget(x, y, w, h, angle)
                if (reducedMotion || terminalStarted) { stage.snapTo(2f); check.snapTo(1f); pulse.snapTo(1f) }
                else {
                    terminalStarted = true
                    ripple.snapTo(-1f)
                    stage.animateTo(1f, tween(100))
                    coroutineScope {
                        launch { stage.animateTo(2f, tween(300)); pulse.animateTo(1.08f, tween(100)); pulse.animateTo(1f, spring(0.75f, 600f)) }
                        launch { delay(130); check.animateTo(1.15f, tween(140)); check.animateTo(1f, spring(0.65f, 700f)) }
                        if (completed) launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(1_000)) }
                    }
                }
            } else {
                terminalStarted = false
                frozen = null
                check.snapTo(0f); pulse.snapTo(1f); ripple.snapTo(-1f)
                if (reducedMotion) stage.snapTo(if (tracking) 1f else 0f)
                else stage.animateTo(if (tracking) 1f else 0f, spring(0.85f, 500f))
            }
        }
        AndroidView(factory = { effects }, modifier = Modifier.fillMaxSize(), update = {
            it.configure(preview, scrim.toArgb(), primary.toArgb(), active)
        })
        Canvas(Modifier.fillMaxSize().semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f)
        }) {
            if (stage.value <= 1f) morph.toPath(stage.value.coerceIn(0f, 1f), path)
            else (if (failed) failureMorph else successMorph).toPath((stage.value - 1f).coerceIn(0f, 1f), path)
            val width = w * pulse.value
            val height = h * pulse.value
            matrix.setScale(width, height)
            matrix.postTranslate(-width / 2f, -height / 2f)
            matrix.postRotate(angle)
            matrix.postTranslate(x, y)
            path.transform(matrix)
            var geometryKey = stage.value.toBits().toLong()
            geometryKey = 31 * geometryKey + width.toBits()
            geometryKey = 31 * geometryKey + height.toBits()
            geometryKey = 31 * geometryKey + angle.toBits()
            geometryKey = 31 * geometryKey + x.toBits()
            geometryKey = 31 * geometryKey + y.toBits()
            geometryKey = 31 * geometryKey + if (failed) 1 else 0
            effects.updateWindow(path, geometryKey, if (active) ripple.value else -1f,
                if (completed && active) completionBlur.value else 0f, x, y)
            val canvas = drawContext.canvas.nativeCanvas
            val successAmount = (stage.value - 1f).coerceIn(0f, 1f)
            if (successAmount > 0f) {
                fill.color = outlineColor.copy(alpha = successAmount * 0.96f).toArgb()
                canvas.drawPath(path, fill)
            }
            // 外周は1本だけ描き、進捗も同じ太さで上塗りする。
            stroke.strokeWidth = 4.dp.toPx()
            stroke.color = outlineColor.copy(alpha = if (failed) 1f else 0.3f).toArgb()
            canvas.drawPath(path, stroke)
            if (progress > 0f) {
                measure.setPath(path, true)
                val length = measure.length
                // Shape上の開始位置を固定し、回転・Morph中も同じ外周部分に追従させる。
                val start = length * progressStartFraction
                progressPath.rewind()
                val end = start + length * progress
                measure.getSegment(start, minOf(end, length), progressPath, true)
                if (end > length) measure.getSegment(0f, end - length, progressPath, true)
                stroke.color = outlineColor.toArgb()
                canvas.drawPath(progressPath, stroke)
            }
        }
        AnimatedVisibility(visible = !terminal && tracking, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxWidth().offset {
                IntOffset((x - screenWidth / 2f).roundToInt(),
                    (y + maxOf(w, h) / 2f + with(density) { 12.dp.toPx() }).coerceIn(0f, (screenHeight - with(density) { 150.dp.toPx() }).coerceAtLeast(0f)).roundToInt())
            }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 表示だけのラベルは、背後のカメラへのピンチ操作を遮らない。
                Box(Modifier.background(labelBackground.copy(alpha = 0.9f), MaterialTheme.shapes.large)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        AnimatedContent(targetState = (state.progress * 100).roundToInt(), transitionSpec = {
                            (fadeIn(tween(120)) + slideInVertically(tween(120)) { it / 5 }) togetherWith fadeOut(tween(100))
                        }, label = "qrPercent") { percent -> Text(stringResource(R.string.qr_scan_percent, percent), style = MaterialTheme.typography.headlineMedium, color = primary) }
                        if (state.total > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.qr_part_number, state.received, state.total),
                                    style = MaterialTheme.typography.labelMedium, color = primary)
                                AnimatedVisibility(visible = showReceivingHint, modifier = Modifier.weight(1f, fill = false),
                                    enter = fadeIn(tween(100)), exit = fadeOut(tween(100))) {
                                    Text(stringResource(R.string.qr_scan_keep_aimed), style = MaterialTheme.typography.labelMedium,
                                        color = primary, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (terminal) {
            val iconSize = 48.dp
            val iconPx = with(density) { iconSize.toPx() }
            Box(Modifier.offset { IntOffset((x - iconPx / 2f).roundToInt(), (y - iconPx / 2f).roundToInt()) }.size(iconSize)) {
                Icon(if (failed) Icons.Rounded.Close else Icons.Rounded.Check,
                    contentDescription = stringResource(if (failed) R.string.qr_scan_failed else R.string.qr_scan_complete),
                    tint = if (failed) onError else onPrimary,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = check.value.coerceIn(0f, 1f); scaleX = check.value; scaleY = check.value
                        rotationZ = -8f * (1f - check.value.coerceIn(0f, 1f))
                    })
            }
        }
    }
}
