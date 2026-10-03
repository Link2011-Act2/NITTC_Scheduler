package jp.linkserver.nittcsc.ui

import android.util.Size
import android.os.SystemClock
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.linkserver.nittcsc.logic.QrShareCodec
import jp.linkserver.nittcsc.qr.QrCameraDecoder
import jp.linkserver.nittcsc.qr.QrCameraMetering
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** 読み取りのたびにカメラを閉じず、複数QRを続けて取得する。 */
@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
@Composable
internal fun QrCameraPreview(
    modifier: Modifier,
    analysisEnabled: Boolean,
    onFrame: (String) -> Unit,
    onDetection: (FloatArray) -> Unit,
    onPreviewReady: (PreviewView?) -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val frameCallback = rememberUpdatedState(onFrame)
    val errorCallback = rememberUpdatedState(onError)
    val detectionCallback = rememberUpdatedState(onDetection)
    val previewCallback = rememberUpdatedState(onPreviewReady)
    val analyzing = remember { AtomicBoolean(analysisEnabled) }
    SideEffect { analyzing.set(analysisEnabled) }
    val view = remember(context) { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    val metering = remember(view) { QrCameraMetering(view) }
    AndroidView(factory = { view }, modifier = modifier)
    DisposableEffect(owner, view) {
        previewCallback.value(view)
        val disposed = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
        val analysis = ImageAnalysis.Builder().setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val decoder = QrCameraDecoder()
        val transformFactory = ImageProxyTransformFactory()
        var lastMeteringFrame = 0L
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_STOP) metering.reset()
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        analysis.setAnalyzer(executor) { image ->
            try {
                if (!disposed.get() && analyzing.get()) {
                    // Previewと同じViewPortだけを読む。解析位置はバッファの元の座標へ戻す。
                    val crop = image.cropRect
                    val result = decoder.read(image)
                    val now = SystemClock.elapsedRealtime()
                    if (result != null || now - lastMeteringFrame >= 500L) {
                        lastMeteringFrame = now
                        val transform = result?.let { transformFactory.getOutputTransform(image) }
                        val corners = result?.corners?.flatMap { listOf(it.x + crop.left, it.y + crop.top) }?.toFloatArray()
                        mainExecutor.execute {
                            if (!disposed.get()) {
                                val target = view.outputTransform
                                if (corners != null && transform != null && target != null) {
                                    CoordinateTransform(transform, target).mapPoints(corners)
                                    if (analyzing.get()) detectionCallback.value(corners)
                                    metering.update(corners)
                                } else {
                                    metering.update(null)
                                }
                                if (analyzing.get() && result != null && QrShareCodec.isShareFrame(result.text)) frameCallback.value(result.text)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                mainExecutor.execute { if (!disposed.get()) errorCallback.value() }
            } finally { image.close() }
        }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var gestureCamera: Camera? = null
        var zoomRatio = 1f
        var multiTouch = false
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val bound = gestureCamera ?: return false
                if (!analyzing.get()) return false
                val zoom = bound.cameraInfo.zoomState.value ?: return false
                val factor = detector.scaleFactor
                if (!factor.isFinite() || factor <= 0f) return false
                zoomRatio = (zoomRatio * factor).coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
                bound.cameraControl.setZoomRatio(zoomRatio)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) { metering.reset() }
        }).apply {
            isQuickScaleEnabled = false
            isStylusScaleEnabled = false
        }
        val tapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true
            // ピンチの最後に指を離したイベントではAFを起動しない。
            override fun onSingleTapUp(event: MotionEvent): Boolean = !multiTouch && analyzing.get()
        })
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) multiTouch = false
            if (event.pointerCount > 1) multiTouch = true
            scaleDetector.onTouchEvent(event)
            val tapped = tapDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP && tapped) {
                metering.focus(event.x, event.y)
                view.performClick()
            }
            true
        }
        future.addListener({
            if (!disposed.get()) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    view.doOnLayout {
                        if (!disposed.get()) {
                            try {
                                val rotation = view.display?.rotation ?: Surface.ROTATION_0
                                preview.targetRotation = rotation
                                analysis.targetRotation = rotation
                                val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis)
                                view.viewPort?.let { group.setViewPort(it) }
                                val bound = cameraProvider.bindToLifecycle(owner, selector, group.build())
                                gestureCamera = bound
                                metering.bind(bound)
                                val zoom = bound.cameraInfo.zoomState.value
                                zoomRatio = if (zoom == null) 1f else 1f.coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
                                bound.cameraControl.setZoomRatio(zoomRatio)
                                metering.focus(view.width / 2f, view.height / 2f)
                            } catch (_: Exception) { errorCallback.value() }
                        }
                    }
                } catch (_: Exception) { errorCallback.value() }
            }
        }, mainExecutor)
        onDispose {
            disposed.set(true)
            analysis.clearAnalyzer()
            owner.lifecycle.removeObserver(lifecycleObserver)
            metering.bind(null)
            gestureCamera = null
            previewCallback.value(null)
            view.setOnTouchListener(null)
            provider?.unbind(preview, analysis)
            executor.shutdown()
        }
    }
}
