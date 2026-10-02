package jp.linkserver.nittcsc.qr

import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import jp.linkserver.nittcsc.logic.QrShareCodec
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Base64
import java.util.Random

class QrCameraDecoderTest {
    private val frame = QrShareCodec.create(Base64.getEncoder().encodeToString(
        ByteArray(1500).also { Random(42).nextBytes(it) }
    )).copy(id = "a".repeat(32)).frames().first()

    @Test fun detectsDenseQrAtEveryRotationAndReturnsItsCorners() {
        val size = 512
        var pixels = QrImageEncoding.pixels(frame, size)
        repeat(4) {
            val result = QrCameraDecoder().read(RGBLuminanceSource(size, size, pixels))!!
            assertEquals(frame, result.text)
            assertEquals(4, result.corners.size)
            assertTrue(result.corners.all { point -> point.x in 0f..512f && point.y in 0f..512f })
            assertTrue(result.corners.maxOf { point -> point.x } - result.corners.minOf { point -> point.x } > 400f)
            pixels = IntArray(size * size) { i -> pixels[(size - 1 - i % size) * size + i / size] }
        }
    }

    @Test fun reacquiresQrOutsideTrackedRegionAndKeepsFullImageCoordinates() {
        val decoder = QrCameraDecoder()
        for ((left, top) in listOf(500 to 400, 40 to 40, 580 to 220)) {
            val qr = QrImageEncoding.pixels(frame, 512)
            val pixels = IntArray(1280 * 960) { -1 }
            for (y in 0 until 512) qr.copyInto(pixels, (y + top) * 1280 + left, y * 512, (y + 1) * 512)
            val result = decoder.read(RGBLuminanceSource(1280, 960, pixels))!!
            assertEquals(frame, result.text)
            assertTrue(result.corners.minOf { it.x } in left.toFloat()..(left + 50f))
            assertTrue(result.corners.minOf { it.y } in top.toFloat()..(top + 50f))
            assertTrue(result.corners.maxOf { it.x } in (left + 460f)..(left + 512f))
            assertTrue(result.corners.maxOf { it.y } in (top + 460f)..(top + 512f))
        }
    }

    @Test fun readsNoisyLuminanceAndInvertedQr() {
        val random = Random(18)
        val pixels = QrImageEncoding.pixels(frame, 512)
        val luminance = ByteArray(pixels.size) { i ->
            val base = if (pixels[i] == -1) 200 else 40
            (base + random.nextInt(21) - 10).toByte()
        }
        val source = PlanarYUVLuminanceSource(luminance, 512, 512, 0, 0, 512, 512, false)
        assertEquals(frame, QrCameraDecoder().read(source)?.text)
        val decoder = QrCameraDecoder()
        var result: QrCameraDetection? = null
        repeat(3) { decoder.read(source.invert())?.let { result = it } }
        assertEquals(frame, result?.text)
    }

    @Test fun readsQrOutsideCentralGuideWithoutWaitingForAnotherFrame() {
        val decoder = QrCameraDecoder()
        val width = 768
        val height = 1536
        val blank = IntArray(width * height) { -1 }
        val qr = QrImageEncoding.pixels(frame, 384)
        for ((left, top) in listOf(20 to 24, 350 to 1110)) {
            assertNull(decoder.read(RGBLuminanceSource(width, height, blank)))
            val pixels = blank.copyOf()
            for (y in 0 until 384) qr.copyInto(pixels, (top + y) * width + left, y * 384, (y + 1) * 384)
            val result = decoder.read(RGBLuminanceSource(width, height, pixels))!!
            assertEquals(frame, result.text)
            assertTrue(result.corners.minOf { it.x } in left.toFloat()..(left + 40f))
            assertTrue(result.corners.minOf { it.y } in top.toFloat()..(top + 40f))
        }
    }

    @Test fun copiesRowsWithPaddingAndNonzeroBufferPosition() {
        val bytes = ByteArray(24) { 99 }
        for (y in 0 until 3) for (x in 0 until 5) bytes[3 + y * 8 + x] = (y * 5 + x).toByte()
        val buffer = ByteBuffer.wrap(bytes).apply { position(3) }
        val target = ByteArray(15)
        copyQrLuminancePlane(buffer, 5, 3, 8, 1, target)
        assertArrayEquals(ByteArray(15) { it.toByte() }, target)
        assertEquals(3, buffer.position())
    }

    @Test fun copiesPixelStrideWithoutIncludingPadding() {
        val bytes = ByteArray(14) { 99 }
        for (y in 0 until 2) for (x in 0 until 3) bytes[1 + y * 8 + x * 2] = (y * 3 + x).toByte()
        val target = ByteArray(6)
        copyQrLuminancePlane(ByteBuffer.wrap(bytes).apply { position(1) }, 3, 2, 8, 2, target)
        assertArrayEquals(ByteArray(6) { it.toByte() }, target)
    }
}
