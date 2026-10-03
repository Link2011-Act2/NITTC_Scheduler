package jp.linkserver.nittcsc.qr

import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import java.nio.ByteBuffer

/** 実機上でJNIへ渡すY平面。stride・position・ViewPortを独立に変えられる。 */
internal class QrTestImage(
    private val width: Int, private val height: Int, pixels: IntArray,
    private var crop: Rect = Rect(0,0,width,height), rotation: Int = 0,
    pixelStep: Int = 1, direct: Boolean = true, prefix: Int = 0, padding: Int = 0
) : ImageProxy {
    var closed = false
        private set
    private val stride = width * pixelStep + padding
    private val buffer = (if(direct) ByteBuffer.allocateDirect(prefix+stride*height) else ByteBuffer.allocate(prefix+stride*height)).apply {
        for(y in 0 until height) for(x in 0 until width) put(prefix+y*stride+x*pixelStep,(pixels[y*width+x] and 255).toByte())
        position(prefix)
        limit(prefix+(height-1)*stride+(width-1)*pixelStep+1)
    }
    private val planes = arrayOf<ImageProxy.PlaneProxy>(object : ImageProxy.PlaneProxy {
        override fun getBuffer(): ByteBuffer = this@QrTestImage.buffer
        override fun getPixelStride(): Int = pixelStep
        override fun getRowStride(): Int = stride
    })
    private val info = object : ImageInfo {
        override fun getRotationDegrees(): Int = rotation
        override fun getTimestamp(): Long = 0L
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
        override fun getSensorToBufferTransformMatrix(): Matrix = Matrix()
        override fun populateExifData(exifData: ExifData.Builder) = Unit
    }
    override fun getWidth(): Int = width
    override fun getHeight(): Int = height
    override fun getFormat(): Int = ImageFormat.YUV_420_888
    override fun getCropRect(): Rect = crop
    override fun setCropRect(rect: Rect?) { crop = rect ?: Rect(0,0,width,height) }
    override fun getImageInfo(): ImageInfo = info
    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = planes
    @ExperimentalGetImage
    override fun getImage(): Image? = null
    override fun close() { closed=true }
}
