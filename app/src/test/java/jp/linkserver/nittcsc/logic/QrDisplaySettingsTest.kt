package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.qr.QrImageEncoding
import org.junit.Assert.*
import org.junit.Test

class QrDisplaySettingsTest {
    @Test fun defaultsMatchExistingScreenCapacityAndPlaybackSpeed() {
        val defaults = QrDisplaySettings()
        assertEquals(QrShareCodec.SCREEN_CHUNK_BYTES, defaults.chunkBytes)
        assertEquals(QrImageEncoding.SCREEN_FRAME_INTERVAL_MS, defaults.frameIntervalMs)
        assertEquals(125L, defaults.frameIntervalMs)
        assertEquals(400L, QrImageEncoding.GIF_FRAME_INTERVAL_MS)
    }

    @Test fun acceptsBoundaryValuesAndRejectsInvalidSavedSettings() {
        QrDisplaySettings(100, 50L)
        QrDisplaySettings(1600, 2000L)
        for (chunkBytes in listOf(0, 99, 1601, Int.MAX_VALUE)) {
            try { QrDisplaySettings(chunkBytes); fail("chunkBytes=$chunkBytes") }
            catch (_: IllegalArgumentException) { }
        }
        for (interval in listOf(-1L, 0L, 49L, 2001L, Long.MAX_VALUE)) {
            try { QrDisplaySettings(frameIntervalMs = interval); fail("interval=$interval") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
