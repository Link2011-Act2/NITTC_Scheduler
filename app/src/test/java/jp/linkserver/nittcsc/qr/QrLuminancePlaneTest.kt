package jp.linkserver.nittcsc.qr

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class QrLuminancePlaneTest {
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

    @Test fun validatesDirectPlaneWithNoPaddingAfterTheFinalPixel() {
        validateQrLuminancePlane(ByteBuffer.allocateDirect(21),5,3,8,1)
    }

    @Test(expected=IllegalArgumentException::class) fun rejectsTruncatedPlanesBeforeCallingNativeCode() {
        validateQrLuminancePlane(ByteBuffer.allocateDirect(20),5,3,8,1)
    }

    @Test(expected=IllegalArgumentException::class) fun rejectsPixelStrideLargerThanRowStride() {
        validateQrLuminancePlane(ByteBuffer.allocateDirect(20),5,2,8,2)
    }
}
