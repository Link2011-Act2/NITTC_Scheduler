package jp.linkserver.nittcsc.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/** Androidに依存しない描画・GIFエンコード部分。画素を整数倍で拡大し余白を保つ。 */
internal object QrImageEncoding {
    const val FRAME_INTERVAL_MS = 200L

    fun pixels(frame: String, size: Int): IntArray {
        val matrix = QRCodeWriter().encode(frame, BarcodeFormat.QR_CODE, size, size, mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4
        ))
        return IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xff000000.toInt() else 0xffffffff.toInt() }
    }

    fun animation(output: OutputStream, width: Int, height: Int) = Animation(output, width, height)

    class Animation(output: OutputStream, private val width: Int, private val height: Int) {
        private val encoder = GifEncoder(output, width, height, 0)
        fun add(pixels: IntArray) {
            require(pixels.size == width * height)
            val rgb = Array(height) { y -> IntArray(width) { x -> pixels[y * width + x] and 0xffffff } }
            encoder.addImage(rgb, ImageOptions().setDelay(FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS))
        }
        fun finish() { encoder.finishEncoding() }
    }
}
