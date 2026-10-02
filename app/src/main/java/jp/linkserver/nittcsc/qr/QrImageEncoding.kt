package jp.linkserver.nittcsc.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.OutputStream

/** Androidに依存しない描画・GIFエンコード部分。画素を整数倍で拡大し余白を保つ。 */
internal object QrImageEncoding {
    const val SCREEN_FRAME_INTERVAL_MS = 200L
    const val GIF_FRAME_INTERVAL_MS = 400L

    /** size=0は余白を含むマス数のまま返す。画面用画像を小さく保持できる。 */
    fun matrix(frame: String, size: Int = 0): BitMatrix =
        QRCodeWriter().encode(frame, BarcodeFormat.QR_CODE, size, size, mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4
        ))

    fun pixels(frame: String, size: Int): IntArray {
        val matrix = matrix(frame, size)
        return IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xff000000.toInt() else 0xffffffff.toInt() }
    }

    fun animation(output: OutputStream, width: Int, height: Int) = Animation(output, width, height)

    class Animation(output: OutputStream, private val width: Int, private val height: Int) {
        private val encoder = QrMonochromeGif(output, width, height, GIF_FRAME_INTERVAL_MS)
        fun add(pixels: IntArray) {
            require(pixels.size == width * height)
            encoder.add(pixels)
        }
        fun finish() { encoder.finish() }
    }
}
