package jp.linkserver.nittcsc.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.nittcsc.logic.QrPoint
import jp.linkserver.nittcsc.logic.QrShareCollector
import jp.linkserver.nittcsc.logic.QrShareCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64
import java.util.Random

@RunWith(AndroidJUnit4::class)
class QrCameraDecoderTest {
    private val frame = QrShareCodec.create(Base64.getEncoder().encodeToString(
        ByteArray(1500).also { Random(42).nextBytes(it) }
    )).copy(id = "a".repeat(32)).frames().first()

    @Test fun detectsDenseQrAtEveryRotationAndReturnsItsCorners() {
        val size = 512
        var pixels = QrImageEncoding.pixels(frame, size)
        repeat(4) {
            val result = QrCameraDecoder().read(QrTestImage(size, size, pixels))!!
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
            val result = decoder.read(QrTestImage(1280, 960, pixels))!!
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
        val noisy = IntArray(pixels.size) { i ->
            val base = if (pixels[i] == -1) 200 else 40
            val value = base + random.nextInt(21) - 10
            Color.rgb(value, value, value)
        }
        val source = QrTestImage(512, 512, noisy)
        assertEquals(frame, QrCameraDecoder().read(source)?.text)
        val inverted = IntArray(noisy.size) { i -> val v = 255 - Color.red(noisy[i]); Color.rgb(v,v,v) }
        assertEquals(frame, QrCameraDecoder().read(QrTestImage(512,512,inverted))?.text)
    }

    @Test fun recoversSmallBlurredQrAtQuarterTurnsOnRectangularFrames() {
        val payload = ByteArray(200).also { Random(42).nextBytes(it) }
        val text = "SKTTP/QR:1:${"a".repeat(32)}:${"b".repeat(64)}:8:1:${Base64.getEncoder().encodeToString(payload)}"
        val size = 180
        var width = size + 73
        var height = size + 41
        val clean = IntArray(width * height) { 220 }
        val qr = QrImageEncoding.pixels(text, size)
        for (y in 0 until size) for (x in 0 until size) {
            clean[(y + 19) * width + x + 27] = if (qr[y * size + x] == -1) 220 else 40
        }
        val random = Random(18)
        var pixels = IntArray(clean.size) { i ->
            val x = i % width
            val y = i / width
            val value = ((-1..1).sumOf { dx -> clean[y * width + (x + dx).coerceIn(0, width - 1)] } / 3 +
                random.nextInt(21) - 10).coerceIn(0, 255)
            0xff000000.toInt() or (value shl 16) or (value shl 8) or value
        }
        val reference = QrCameraDecoder().read(QrTestImage(width, height, pixels))!!
        var expectedCorners = reference.corners
        val trackingDecoder = QrCameraDecoder()
        repeat(4) { turn ->
            val result = QrCameraDecoder().read(QrTestImage(width, height, pixels))
            assertEquals("rotation=$turn", text, result?.text)
            result!!.corners.zip(expectedCorners).forEach { (actual, expected) ->
                assertEquals(expected.x, actual.x, 6f)
                assertEquals(expected.y, actual.y, 6f)
            }
            val trackedResult = trackingDecoder.read(QrTestImage(width, height, pixels))
            assertEquals("tracking rotation=$turn", text, trackedResult?.text)
            val oldWidth = width
            pixels = IntArray(pixels.size) { i -> pixels[(i % height) * width + width - 1 - i / height] }
            expectedCorners = expectedCorners.map { QrPoint(it.y, oldWidth - 1f - it.x) }
            width = height
            height = oldWidth
        }
    }

    @Test fun readsQrOutsideCentralGuideWithoutWaitingForAnotherFrame() {
        val decoder = QrCameraDecoder()
        val width = 768
        val height = 1536
        val blank = IntArray(width * height) { -1 }
        val qr = QrImageEncoding.pixels(frame, 384)
        for ((left, top) in listOf(20 to 24, 350 to 1110)) {
            assertNull(decoder.read(QrTestImage(width, height, blank)))
            val pixels = blank.copyOf()
            for (y in 0 until 384) qr.copyInto(pixels, (top + y) * width + left, y * 384, (y + 1) * 384)
            val result = decoder.read(QrTestImage(width, height, pixels))!!
            assertEquals(frame, result.text)
            assertTrue(result.corners.minOf { it.x } in left.toFloat()..(left + 40f))
            assertTrue(result.corners.minOf { it.y } in top.toFloat()..(top + 40f))
        }
    }

    @Test fun readsSmallQrAtEveryFifteenDegreeAngleInOneFrame() {
        val text = QrShareCodec.create(Base64.getEncoder().encodeToString(
            ByteArray(2100).also { Random(52).nextBytes(it) }
        )).copy(id="a".repeat(32)).cameraFrames().first()
        val qr = Bitmap.createBitmap(QrImageEncoding.pixels(text,180),180,180,Bitmap.Config.ARGB_8888)
        try {
            for (angle in 0 until 360 step 15) {
                val bitmap = Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.rotate(angle.toFloat(),400f,300f)
                canvas.drawBitmap(qr,310f,210f,Paint(Paint.FILTER_BITMAP_FLAG))
                val pixels=IntArray(800*600)
                bitmap.getPixels(pixels,0,800,0,0,800,600)
                bitmap.recycle()
                assertEquals("angle=$angle",text,QrCameraDecoder().read(QrTestImage(800,600,pixels))?.text)
            }
        } finally { qr.recycle() }
    }

    @Test fun preservesViewportCoordinatesAndBufferPositionWithEveryCameraOrientation() {
        val width=768; val height=640
        val pixels=IntArray(width*height){Color.WHITE}
        val qr=QrImageEncoding.pixels(frame,384)
        for(y in 0 until 384) qr.copyInto(pixels,(y+120)*width+180,y*384,(y+1)*384)
        val viewport=Rect(100,70,700,600)
        var reference: List<QrPoint>?=null
        for(rotation in listOf(0,90,180,270)) for(pixelStride in listOf(1,2)) for(direct in listOf(true,false)) {
            val image=QrTestImage(width,height,pixels,viewport,rotation,pixelStride,direct,7,13)
            val start=image.planes[0].buffer.position()
            val result=QrCameraDecoder().read(image)!!
            assertEquals(frame,result.text)
            assertEquals(start,image.planes[0].buffer.position())
            assertFalse(image.closed)
            assertTrue(result.corners.minOf{it.x} in 80f..120f)
            assertTrue(result.corners.minOf{it.y} in 50f..90f)
            if(reference==null) reference=result.corners
            result.corners.zip(reference).forEach { (actual,expected) ->
                assertEquals(expected.x,actual.x,1f)
                assertEquals(expected.y,actual.y,1f)
            }
        }
    }

    @Test fun cameraFragmentsRoundTripAtLowResolution() {
        val original=Base64.getEncoder().encodeToString(ByteArray(2100).also{Random(52).nextBytes(it)})
        val frames=QrShareCodec.create(original).copy(id="4".repeat(32)).cameraFrames()
        val decoder=QrCameraDecoder()
        val collector=QrShareCollector()
        var decoded: String?=null
        for(text in frames.reversed()) {
            val result=decoder.read(QrTestImage(240,240,QrImageEncoding.pixels(text,240)))!!
            assertEquals(text,result.text)
            collector.add(result.text)?.let { decoded=it }
        }
        assertEquals(original,decoded)
    }
}
