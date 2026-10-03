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
    @Test fun smallerCameraQrUsesFewerModulesAndDecodesAtLowResolution() {
        val original = Base64.getEncoder().encodeToString(ByteArray(2100).also { Random(52).nextBytes(it) })
        val transfer = QrShareCodec.create(original).copy(id = "4".repeat(32))
        val frames = transfer.cameraFrames()
        val cameraSize = QrImageEncoding.matrix(frames.first()).width
        val gifSize = QrImageEncoding.matrix(transfer.frames(QrShareCodec.GIF_CHUNK_BYTES).first()).width
        assertTrue(cameraSize < gifSize)
        val collector = QrShareCollector()
        var result: String? = null
        for (frame in frames.reversed()) {
            val pixels = QrImageEncoding.pixels(frame, 240)
            val text = QrImageDecoding.read(RGBLuminanceSource(240, 240, pixels)).single()
            assertEquals(frame, text)
            collector.add(text)?.let { result = it }
        }
        assertEquals(original, result)
    }

    @Test fun cachedModuleImagesRoundTripAfterNearestNeighborScaling() {
        val original = Base64.getEncoder().encodeToString(ByteArray(2100).also { Random(12).nextBytes(it) })
        val frames = QrShareCodec.create(original).copy(id = "3".repeat(32)).frames()
        val cached = frames.map { QrImageEncoding.matrix(it) }
        for (size in listOf(360, 512, 768)) {
            val collector = QrShareCollector()
            var result: String? = null
            for (i in cached.indices.reversed()) {
                val matrix = cached[i]
                // 画面と同じ最近傍補間。整数倍でない拡大でも読み取れることを確認する。
                val pixels = IntArray(size * size) { pixel ->
                    val x = ((pixel % size + 0.5) * matrix.width / size).toInt()
                    val y = ((pixel / size + 0.5) * matrix.height / size).toInt()
                    if (matrix[x, y]) 0xff000000.toInt() else 0xffffffff.toInt()
                }
                // 保存画像の読み込み処理で確認する。カメラのC++版は実機テストで検証する。
                val texts = QrImageDecoding.read(RGBLuminanceSource(size, size, pixels))
                assertEquals("frame $i at ${size}px", listOf(frames[i]), texts)
                collector.add(texts.single())?.let { result = it }
            }
            assertEquals(original, result)
        }
    }

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
        // 白黒のグローバル色表とループ拡張の後に画像記述子がある。
        val marker = bytes.indices.first { index -> index > 13 && (bytes[index].toInt() and 255) == 0x2c }
        bytes[marker + 5] = 0xff.toByte()
        bytes[marker + 6] = 0xff.toByte()
        try { validateQrGif(bytes); fail("Expected invalid GIF frame") }
        catch (_: jp.linkserver.nittcsc.logic.QrShareException) { }
    }

    private fun read(pixels: IntArray, width: Int, height: Int): String =
        QrImageDecoding.read(RGBLuminanceSource(width, height, pixels), pure = true).single()
}
