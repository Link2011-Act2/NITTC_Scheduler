package jp.linkserver.nittcsc.qr

import android.graphics.Rect
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import jp.linkserver.nittcsc.logic.QrCameraDetection
import jp.linkserver.nittcsc.logic.QrCameraTracking
import jp.linkserver.nittcsc.logic.QrPoint
import java.nio.ByteBuffer
import zxingcpp.BarcodeReader

/** 回転・反転の検出はZXing-C++へ任せ、Y平面をBitmapに変換せず読み取る。 */
internal class QrCameraDecoder {
    private val reader = BarcodeReader(BarcodeReader.Options(
        formats = setOf(BarcodeReader.Format.QR_CODE),
        tryHarder = true,
        tryRotate = true,
        tryInvert = true,
        tryDownscale = true,
        maxNumberOfSymbols = 1
    ))
    private val tracking = QrCameraTracking()
    private var copyBuffer = ByteBuffer.allocateDirect(0)
    private var copyPixels = ByteArray(0)

    /** 四隅は回転前のViewPort内座標。CameraXの既存変換へ渡す。 */
    fun read(image: ImageProxy): QrCameraDetection? {
        val viewport = image.cropRect
        require(viewport.left >= 0 && viewport.top >= 0 && viewport.right <= image.width &&
            viewport.bottom <= image.height && viewport.width() > 0 && viewport.height() > 0)
        val plane = image.planes[0]
        val input = plane.buffer
        validateQrLuminancePlane(input, image.width, image.height, plane.rowStride, plane.pixelStride)
        val buffer: ByteBuffer
        val rowStride: Int
        if (input.isDirect && plane.pixelStride == 1) {
            // JNIはpositionを参照しないため、sliceで現在位置をアドレスの先頭にする。
            buffer = input.slice()
            rowStride = plane.rowStride
        } else {
            val size = image.width * image.height
            if (copyPixels.size != size) {
                copyPixels = ByteArray(size)
                copyBuffer = ByteBuffer.allocateDirect(size)
            }
            copyQrLuminancePlane(input, image.width, image.height, plane.rowStride, plane.pixelStride, copyPixels)
            copyBuffer.clear()
            copyBuffer.put(copyPixels).flip()
            buffer = copyBuffer
            rowStride = image.width
        }
        return tracking.read(viewport.width(), viewport.height()) decode@{ region ->
            val crop = Rect(viewport.left + region.left, viewport.top + region.top,
                viewport.left + region.left + region.width, viewport.top + region.top + region.height)
            val nativeImage = QrNativeImage(image, buffer, rowStride, crop)
            val result = reader.read(nativeImage).firstOrNull { it.error == null && it.text != null }
                ?: return@decode null
            val position = result.position
            val corners = listOf(position.topLeft, position.topRight, position.bottomRight, position.bottomLeft)
                .map { QrPoint(it.x + region.left.toFloat(), it.y + region.top.toFloat()) }
            QrCameraDetection(result.text!!, corners)
        }
    }
}

/** エンジンに渡すcropとY平面だけを差し替える。元ImageProxyのcloseは呼び出し元が行う。 */
private class QrNativeImage(
    image: ImageProxy, yBuffer: ByteBuffer, yRowStride: Int, private val crop: Rect
) : ImageProxy by image {
    private val info = object : ImageInfo by image.imageInfo {
        // 回転後の座標を返すAPIではなく、元バッファ座標からCameraXに変換する。
        override fun getRotationDegrees(): Int = 0
    }
    private val luminance = arrayOf<ImageProxy.PlaneProxy>(object : ImageProxy.PlaneProxy {
        override fun getBuffer(): ByteBuffer = yBuffer
        override fun getRowStride(): Int = yRowStride
        override fun getPixelStride(): Int = 1
    })
    override fun getCropRect(): Rect = crop
    override fun getImageInfo(): ImageInfo = info
    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = luminance
}
