package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test

class QrCameraTrackingTest {
    private val full=QrCameraRegion(0,0,800,600)
    private val detected=QrCameraDetection("qr",listOf(QrPoint(100f,100f),QrPoint(300f,100f),QrPoint(300f,300f),QrPoint(100f,300f)))

    @Test fun immediatelyFallsBackToFullViewportWhenTrackedQrMoves() {
        val tracker=QrCameraTracking()
        tracker.read(800,600){detected}
        val attempts=mutableListOf<QrCameraRegion>()
        val result=tracker.read(800,600){region -> attempts+=region; if(region==full) detected else null}
        assertEquals(detected,result)
        assertEquals(2,attempts.size)
        assertNotEquals(full,attempts.first())
        assertEquals(full,attempts.last())
    }

    @Test fun dropsTrackingAfterFiveMissingFrames() {
        val tracker=QrCameraTracking()
        tracker.read(800,600){detected}
        repeat(5){tracker.read(800,600){null}}
        val attempts=mutableListOf<QrCameraRegion>()
        tracker.read(800,600){region -> attempts+=region; null}
        assertEquals(listOf(full),attempts)
    }

    @Test fun discardsTrackedRegionOutsideNewViewport() {
        val tracker=QrCameraTracking()
        tracker.read(800,600){detected}
        val attempts=mutableListOf<QrCameraRegion>()
        tracker.read(120,100){region -> attempts+=region; null}
        assertEquals(listOf(QrCameraRegion(0,0,120,100)),attempts)
    }
}
