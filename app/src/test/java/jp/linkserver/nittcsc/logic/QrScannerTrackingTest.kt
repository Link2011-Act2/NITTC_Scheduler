package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class QrScannerTrackingTest {
    private fun corners(x: Float, y: Float, angle: Float = 0f): FloatArray {
        val rad = Math.toRadians(angle.toDouble())
        val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
        return floatArrayOf(-50f, -40f, 50f, -40f, 50f, 40f, -50f, 40f).also { p ->
            for (i in p.indices step 2) {
                val px = p[i]; val py = p[i + 1]
                p[i] = x + px * c - py * s; p[i + 1] = y + px * s + py * c
            }
        }
    }

    @Test fun usesPreviewCoordinatesAndRotatedQrDimensions() {
        val target = QrScannerTracker().detect(corners(220f, 340f, 30f), 0)!!
        assertEquals(220f, target.x, 0.001f); assertEquals(340f, target.y, 0.001f)
        assertEquals(130f, target.width, 0.001f); assertEquals(104f, target.height, 0.001f)
        assertEquals(30f, target.angle, 0.001f)
    }

    @Test fun smoothsJitterAndKeepsTargetDuringGracePeriod() {
        val tracker = QrScannerTracker()
        tracker.detect(corners(100f, 100f), 0)
        val moved = tracker.detect(corners(110f, 120f), 100)!!
        assertEquals(104f, moved.x, 0.001f); assertEquals(108f, moved.y, 0.001f)
        assertEquals(moved, tracker.current(450)); assertNull(tracker.current(451))
        assertEquals(300f, tracker.detect(corners(300f, 300f), 500)!!.x, 0.001f)
    }

    @Test fun crossesRotationBoundaryWithoutTurningAround() {
        val tracker = QrScannerTracker()
        tracker.detect(corners(100f, 100f, 179f), 0)
        val target = tracker.detect(corners(100f, 100f, -179f), 100)!!
        assertEquals(179.8f, target.angle, 0.01f)
    }

    @Test fun rejectsMalformedOrDegenerateCorners() {
        val tracker = QrScannerTracker()
        assertNull(tracker.detect(FloatArray(6), 0))
        assertNull(tracker.detect(FloatArray(8), 0))
        assertNull(tracker.detect(corners(100f, 100f).apply { this[0] = Float.NaN }, 0))
    }

    @Test fun repeatedClockwiseTurnsKeepFollowingTheShortDirection() {
        val tracker = QrScannerTracker()
        var previous = tracker.detect(corners(100f, 100f), 0)!!
        for (degrees in 20..1080 step 20) {
            val next = tracker.detect(corners(100f, 100f, degrees.toFloat()), degrees.toLong())!!
            assertTrue(next.angle > previous.angle)
            assertTrue(next.angle - previous.angle < 30f)
            previous = next
        }
        assertTrue(previous.angle > 1000f)
    }
}
