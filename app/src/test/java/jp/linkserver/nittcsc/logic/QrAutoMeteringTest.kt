package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test

class QrAutoMeteringTest {
    private val center = QrMeteringRegion(0.5f, 0.5f, 0.35f)
    private val qr = QrMeteringRegion(0.2f, 0.7f, 0.2f)

    @Test fun startsAtCenterAndKeepsContinuousMeteringWithoutRepeatedRequests() {
        val auto = QrAutoMetering()
        assertEquals(center, auto.next(0))
        assertNull(auto.next(750))
        assertNull(auto.next(10_000))
    }

    @Test fun followsQrWithRateLimitAndIgnoresSmallMovement() {
        val auto = QrAutoMetering()
        auto.next(0)
        auto.recordQr(qr, 500)
        assertNull(auto.next(500))
        assertEquals(qr, auto.next(750))
        auto.recordQr(qr.copy(x = 0.22f, size = 0.21f), 1_500)
        assertNull(auto.next(1_500))
        val moved = qr.copy(x = 0.7f)
        auto.recordQr(moved, 2_000)
        assertEquals(moved, auto.next(2_000))
    }

    @Test fun keepsTargetAcrossBriefDecodeMissesThenReturnsToCenter() {
        val auto = QrAutoMetering()
        auto.recordQr(qr, 0)
        assertEquals(qr, auto.next(0))
        assertNull(auto.next(1_500))
        assertEquals(center, auto.next(1_501))
    }

    @Test fun manualFocusHasPriorityThenResumesAtLatestQrPosition() {
        val auto = QrAutoMetering()
        auto.next(0)
        auto.manualFocus(1_000)
        auto.recordQr(qr, 3_000)
        assertNull(auto.next(3_999))
        assertEquals(qr, auto.next(4_000))
        auto.manualFocus(4_001)
        assertNull(auto.next(7_000))
        assertEquals(center, auto.next(7_001))
    }

    @Test fun lifecycleResetRemovesOldTargetAndReissuesMetering() {
        val auto = QrAutoMetering()
        auto.recordQr(qr, 0)
        auto.next(0)
        auto.manualFocus(100)
        auto.reset()
        assertEquals(center, auto.next(200))
    }
}
