package jp.linkserver.nittcsc.qr

import com.google.zxing.RGBLuminanceSource
import jp.linkserver.nittcsc.logic.QrShareCodec
import jp.linkserver.nittcsc.logic.QrShareCollector
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Random
import javax.imageio.ImageIO

class QrImageEncodingTest {
    @Test fun denseSingleImageQrDecodesWithErrorCorrectionAndMargin() {
        val bytes = ByteArray(1500).also { Random(42).nextBytes(it) }
        val original = Base64.getEncoder().encodeToString(bytes)
        val transfer = QrShareCodec.create(original).copy(id = "1".repeat(32))
        val frame = transfer.frames(QrShareCodec.IMAGE_CHUNK_BYTES).single()
        val pixels = QrImageEncoding.pixels(frame, 1024)
        val text = read(pixels, 1024, 1024)
        assertEquals(frame, text)
        assertEquals(original, QrShareCollector().add(text))
        assertEquals(0xffffffff.toInt(), pixels.first())
    }

    @Test fun gifPreservesEveryQrAndRoundTripsTransfer() {
        val original = Base64.getEncoder().encodeToString(ByteArray(2100).also { Random(99).nextBytes(it) })
        val frames = QrShareCodec.create(original).copy(id = "2".repeat(32)).frames()
        assertTrue(frames.size > 1)
        val output = ByteArrayOutputStream()
        val animation = QrImageEncoding.animation(output, 512, 512)
        frames.forEach { animation.add(QrImageEncoding.pixels(it, 512)) }
        animation.finish()
        validateQrGif(output.toByteArray())
        val stream = ImageIO.createImageInputStream(output.toByteArray().inputStream())
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        try {
            reader.input = stream
            assertEquals(frames.size, reader.getNumImages(true))
            val collector = QrShareCollector()
            var result: String? = null
            for (i in frames.indices.reversed()) {
                val image = reader.read(i)
                val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
                assertArrayEquals("GIF frame $i must preserve the QR pixels", QrImageEncoding.pixels(frames[i], 512), pixels)
                val text = read(pixels, image.width, image.height)
                assertEquals(frames[i], text)
                collector.add(text)?.let { result = it }
            }
            assertEquals(original, result)
        } finally { reader.dispose(); stream.close() }
    }

    @Test fun rejectsOversizedGifFramesBeforeDecoding() {
        val output = ByteArrayOutputStream()
        val animation = QrImageEncoding.animation(output, 64, 64)
        animation.add(IntArray(64 * 64) { 0xffffffff.toInt() })
        animation.finish()
        val bytes = output.toByteArray()
        // このエンコーダーはグローバル色表なし。拡張ブロックの後に画像記述子がある。
        val marker = bytes.indices.first { index -> index > 13 && (bytes[index].toInt() and 255) == 0x2c }
        bytes[marker + 5] = 0xff.toByte()
        bytes[marker + 6] = 0xff.toByte()
        try { validateQrGif(bytes); fail("Expected invalid GIF frame") }
        catch (_: jp.linkserver.nittcsc.logic.QrShareException) { }
    }

    private fun read(pixels: IntArray, width: Int, height: Int): String =
        QrImageDecoding.read(RGBLuminanceSource(width, height, pixels), pure = true).single()
}
