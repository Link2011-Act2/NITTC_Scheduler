package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Random
import java.util.zip.GZIPOutputStream

class QrShareCodecTest {
    @Test fun readsEveryPartInAnyOrderAndIgnoresDuplicates() {
        val json = buildString { repeat(4000) { append("授業メモ${Random(it.toLong()).nextLong()}") } }
        val frames = QrShareCodec.create(json).frames()
        assertTrue(frames.size > 3)
        assertTrue(frames.first().startsWith("SKTTP/QR:1:"))
        val collector = QrShareCollector()
        assertNull(collector.add(frames.last()))
        assertNull(collector.add(frames.last()))
        assertEquals(1, collector.received)
        assertEquals(frames.size - 1, collector.missing.size)
        var result: String? = null
        frames.dropLast(1).reversed().forEach { collector.add(it)?.let { value -> result = value } }
        assertEquals(json, result)
        assertTrue(collector.missing.isEmpty())
    }

    @Test fun supportsSingleQrAndLargeImageChunks() {
        val transfer = QrShareCodec.create("{\"日本語\":\"時間割\"}")
        val collector = QrShareCollector()
        assertEquals("{\"日本語\":\"時間割\"}", collector.add(transfer.frames(QrShareCodec.IMAGE_CHUNK_BYTES).single()))
    }

    @Test fun rejectsMixingTransfersWithoutDiscardingCollectedParts() {
        val a = QrShareCodec.create("a".repeat(10000)).frames(100)
        val b = QrShareCodec.create("b".repeat(10000)).frames(100)
        val collector = QrShareCollector()
        collector.add(a.first())
        failure(QrShareFailure.DIFFERENT_TRANSFER) { collector.add(b.first()) }
        assertEquals(1, collector.received)
    }

    @Test fun rejectsUnknownVersionInvalidIndexAndUnboundedPartCount() {
        val frame = QrShareCodec.create("hello").frames().single()
        failure(QrShareFailure.VERSION) { QrShareCollector().add(frame.replace("SKTTP/QR:1:", "SKTTP/QR:2:")) }
        val fields = frame.split(':').toMutableList()
        fields[5] = "-1"
        failure(QrShareFailure.INVALID) { QrShareCollector().add(fields.joinToString(":")) }
        fields[4] = "9999999"
        fields[5] = "0"
        failure(QrShareFailure.INVALID) { QrShareCollector().add(fields.joinToString(":")) }
    }

    @Test fun rejectsBrokenChecksum() {
        val fields = QrShareCodec.create("hello").frames().single().split(':').toMutableList()
        fields[3] = "0".repeat(64)
        failure(QrShareFailure.DAMAGED) { QrShareCollector().add(fields.joinToString(":")) }
    }

    @Test fun boundsInflatedDataEvenWhenCompressedDataIsSmall() {
        val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(ByteArray(QrShareCodec.MAX_JSON_BYTES + 1)) } }.toByteArray()
        val frame = "SKTTP/QR:1:${"1".repeat(32)}:${QrShareCodec.hash(bytes)}:1:0:${Base64.getEncoder().encodeToString(bytes)}"
        failure(QrShareFailure.TOO_LARGE) { QrShareCollector().add(frame) }
    }

    @Test fun rejectsOversizedExport() {
        failure(QrShareFailure.TOO_LARGE) { QrShareCodec.create("あ".repeat(QrShareCodec.MAX_JSON_BYTES / 3 + 1)) }
    }

    private fun failure(reason: QrShareFailure, action: () -> Unit) {
        try { action(); fail("Expected $reason") } catch (e: QrShareException) { assertEquals(reason, e.failure) }
    }
}
