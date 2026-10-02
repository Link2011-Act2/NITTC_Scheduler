package jp.linkserver.nittcsc.ui

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import jp.linkserver.nittcsc.logic.QrShareCodec
import jp.linkserver.nittcsc.qr.QrImageDecoding
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** 読み取りのたびにカメラを閉じず、複数QRを続けて取得する。 */
@Suppress("DEPRECATION")
@Composable
internal fun QrCameraPreview(modifier: Modifier, onFrame: (String) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val frameCallback = rememberUpdatedState(onFrame)
    val errorCallback = rememberUpdatedState(onError)
    val view = remember(context) { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    AndroidView(factory = { view }, modifier = modifier)
    DisposableEffect(owner, view) {
        val disposed = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val analysis = ImageAnalysis.Builder().setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis.setAnalyzer(executor) { image ->
            try {
                if (!disposed.get()) {
                    val plane = image.planes[0]
                    val buffer = plane.buffer.duplicate()
                    val data = ByteArray(image.width * image.height)
                    for (y in 0 until image.height) {
                        for (x in 0 until image.width) data[y * image.width + x] = buffer.get(y * plane.rowStride + x * plane.pixelStride)
                    }
                    val source = PlanarYUVLuminanceSource(data, image.width, image.height, 0, 0, image.width, image.height, false)
                    val result = QrImageDecoding.read(source).firstOrNull(QrShareCodec::isShareFrame)
                    if (result != null) mainExecutor.execute {
                        if (!disposed.get()) frameCallback.value(result)
                    }
                }
            } catch (_: ReaderException) {
                // QRが見つからないフレームは通常動作。
            } catch (_: Exception) {
                mainExecutor.execute { if (!disposed.get()) errorCallback.value() }
            } finally { image.close() }
        }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            if (!disposed.get()) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.bindToLifecycle(owner, selector, preview, analysis)
                } catch (_: Exception) { errorCallback.value() }
            }
        }, mainExecutor)
        onDispose {
            disposed.set(true)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            executor.shutdown()
        }
    }
}
