package jp.linkserver.nittcsc.ui

import android.animation.ValueAnimator
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
    val successMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val path = remember { Path() }
    val progressPath = remember { Path() }
    val measure = remember { PathMeasure() }
    val matrix = remember { Matrix() }
    val position = remember { FloatArray(2) }
    val stroke = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND } }
    val fill = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val primary = MaterialTheme.colorScheme.primary
    val scrim = MaterialTheme.colorScheme.scrim
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val surface = MaterialTheme.colorScheme.surface
    val labelBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val stage = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val check = remember { Animatable(0f) }
    val ripple = remember { Animatable(-1f) }
    val reducedMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val completed = state.phase == QrScannerPhase.Completed
    val tracking = state.target != null
    var frozen by remember { mutableStateOf<QrScanTarget?>(null) }
    var successStarted by remember { mutableStateOf(false) }
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
        val angle by animateFloatAsState(target.angle, motion, label = "qrAngle")
        val progress by animateFloatAsState(state.progress, tween(160), label = "qrProgress")
        LaunchedEffect(completed, tracking, active) {
            if (!active) { ripple.snapTo(-1f); return@LaunchedEffect }
            if (completed) {
                if (!successStarted) frozen = QrScanTarget(x, y, w, h, angle)
                if (reducedMotion || successStarted) { stage.snapTo(2f); check.snapTo(1f); pulse.snapTo(1f) }
                else {
                    successStarted = true
                    stage.animateTo(1f, tween(100))
                    coroutineScope {
                        launch { stage.animateTo(2f, tween(300)); pulse.animateTo(1.08f, tween(100)); pulse.animateTo(1f, spring(0.75f, 600f)) }
                        launch { delay(130); check.animateTo(1.15f, tween(140)); check.animateTo(1f, spring(0.65f, 700f)) }
                        launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(850)) }
                    }
                }
            } else {
                successStarted = false
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
            else successMorph.toPath((stage.value - 1f).coerceIn(0f, 1f), path)
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
            effects.updateWindow(path, geometryKey, if (active) ripple.value else -1f, x, y)
            val canvas = drawContext.canvas.nativeCanvas
            val successAmount = (stage.value - 1f).coerceIn(0f, 1f)
            if (successAmount > 0f) {
                fill.color = primary.copy(alpha = successAmount * 0.96f).toArgb()
                canvas.drawPath(path, fill)
            }
            stroke.strokeWidth = 6.dp.toPx()
            stroke.color = surface.copy(alpha = 0.5f).toArgb()
            canvas.drawPath(path, stroke)
            stroke.strokeWidth = 3.dp.toPx()
            stroke.color = primary.copy(alpha = 0.3f).toArgb()
            canvas.drawPath(path, stroke)
            if (progress > 0f && stage.value > 0.05f) {
                measure.setPath(path, true)
                val length = measure.length
                var start = 0f
                var top = Float.MAX_VALUE
                // 画面上の12時方向に近い外周位置から始める。回転中も上側を保つ。
                repeat(64) { i ->
                    val distance = i * length / 64f
                    measure.getPosTan(distance, position, null)
                    if (position[1] < top) { top = position[1]; start = distance }
                }
                progressPath.rewind()
                val end = start + length * progress
                measure.getSegment(start, minOf(end, length), progressPath, true)
                if (end > length) measure.getSegment(0f, end - length, progressPath, true)
                stroke.color = primary.toArgb()
                stroke.strokeWidth = 4.dp.toPx()
                canvas.drawPath(progressPath, stroke)
            }
        }
        AnimatedVisibility(visible = !completed && (tracking || state.received > 0), enter = fadeIn(), exit = fadeOut(),
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
                        if (state.total > 0) Text(stringResource(R.string.qr_part_number, state.received, state.total), style = MaterialTheme.typography.labelMedium, color = primary)
                    }
                }
            }
        }
        if (completed) {
            val iconSize = 48.dp
            val iconPx = with(density) { iconSize.toPx() }
            Box(Modifier.offset { IntOffset((x - iconPx / 2f).roundToInt(), (y - iconPx / 2f).roundToInt()) }.size(iconSize)) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.qr_scan_complete), tint = onPrimary,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = check.value.coerceIn(0f, 1f); scaleX = check.value; scaleY = check.value
                        rotationZ = -8f * (1f - check.value.coerceIn(0f, 1f))
                    })
            }
        }
    }
}
