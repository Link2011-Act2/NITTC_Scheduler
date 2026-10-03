package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Random
import java.util.zip.GZIPOutputStream

class QrShareCodecTest {
    @Test fun configurableCameraChunksRoundTripThroughExistingCollector() {
        val json = Base64.getEncoder().encodeToString(ByteArray(2100).also { Random(117).nextBytes(it) })
        val transfer = QrShareCodec.create(json)
        for (chunkBytes in listOf(100, 200, 600, 1600)) {
            val frames = transfer.cameraFrames(chunkBytes)
            assertEquals(chunkBytes, QrShareCodec.parse(frames.first()).bytes.size)
            assertTrue(frames.all { QrShareCodec.parse(it).bytes.size <= chunkBytes })
            val collector = QrShareCollector()
            var result: String? = null
            for (frame in frames.reversed()) {
                collector.add(frame)?.let { result = it }
                assertNull(collector.add(frame))
            }
            assertEquals("chunkBytes=$chunkBytes", json, result)
        }
        assertEquals(transfer.cameraFrames(), transfer.cameraFrames(QrShareCodec.SCREEN_CHUNK_BYTES))
    }

    @Test fun smallerRequestedChunksStillRespectTheExistingPartLimit() {
        val json = Base64.getEncoder().encodeToString(ByteArray(56000).also { Random(113).nextBytes(it) })
        val transfer = QrShareCodec.create(json)
        val frames = transfer.cameraFrames(QrShareCodec.MIN_CHUNK_BYTES)
        assertTrue(frames.size <= QrShareCodec.MAX_PARTS)
        assertEquals((transfer.compressed.size + QrShareCodec.MAX_PARTS - 1) / QrShareCodec.MAX_PARTS,
            QrShareCodec.parse(frames.first()).bytes.size)
        val collector = QrShareCollector()
        var result: String? = null
        frames.reversed().forEach { collector.add(it)?.let { value -> result = value } }
        assertEquals(json, result)
    }

    @Test fun rejectsUnsupportedCameraChunkSizesInsteadOfSilentlyChangingThem() {
        val transfer = QrShareCodec.create("hello")
        for (chunkBytes in listOf(-1, 0, 99, 1601, Int.MAX_VALUE)) {
            try { transfer.cameraFrames(chunkBytes); fail("chunkBytes=$chunkBytes") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun exposesActualIndicesAndUniqueReceiptWithoutChangingDuplicateState() {
        val json = Base64.getEncoder().encodeToString(ByteArray(2100).also { Random(91).nextBytes(it) })
        val frames = QrShareCodec.create(json).cameraFrames()
        assertTrue(frames.size >= 8)
        val collector = QrShareCollector()
        assertTrue(collector.receivedFragmentIndices.isEmpty())
        assertNull(collector.lastReceivedIndex)
        collector.add(frames[5])
        val firstSnapshot = collector.receivedFragmentIndices
        assertEquals(setOf(5), firstSnapshot)
        assertEquals(5, collector.lastReceivedIndex)
        collector.add(frames[6]); collector.add(frames[7]); collector.add(frames[0])
        assertEquals(setOf(0, 5, 6, 7), collector.receivedFragmentIndices)
        assertEquals(setOf(5), firstSnapshot) // 過去のUI snapshotが内部Mapの更新で変わらない。
        assertEquals(0, collector.lastReceivedIndex)
        collector.add(frames[5])
        assertEquals(setOf(0, 5, 6, 7), collector.receivedFragmentIndices)
        assertEquals(0, collector.lastReceivedIndex)
        assertEquals(4, collector.received)
        var result: String? = null
        frames.forEach { collector.add(it)?.let { data -> result = data } }
        assertEquals(json, result)
        assertEquals((frames.indices).toSet(), collector.receivedFragmentIndices)
    }

    @Test fun rejectedDifferentTransferDoesNotChangeDotSnapshot() {
        val a = QrShareCodec.create("a").cameraFrames().single()
        val b = QrShareCodec.create("b").cameraFrames().single()
        val collector = QrShareCollector()
        collector.add(a)
        failure(QrShareFailure.DIFFERENT_TRANSFER) { collector.add(b) }
        assertEquals(setOf(0), collector.receivedFragmentIndices)
        assertEquals(0, collector.lastReceivedIndex)
    }

    @Test fun cameraFramesUseSmallChunksAndRoundTripOutOfOrder() {
        val json = Base64.getEncoder().encodeToString(ByteArray(2100).also { Random(27).nextBytes(it) })
        val transfer = QrShareCodec.create(json)
        val frames = transfer.cameraFrames()
        assertTrue(frames.size > transfer.frames(QrShareCodec.GIF_CHUNK_BYTES).size)
        assertTrue(frames.all { QrShareCodec.parse(it).bytes.size <= QrShareCodec.SCREEN_CHUNK_BYTES })
        assertEquals(600, QrShareCodec.parse(transfer.frames().first()).bytes.size)
        val collector = QrShareCollector()
        var result: String? = null
        frames.reversed().forEach { collector.add(it)?.let { data -> result = data } }
        assertEquals(json, result)
    }

    @Test fun largeCameraTransfersKeepExistingCapacityAndPartLimit() {
        val json = Base64.getEncoder().encodeToString(ByteArray(56000).also { Random(31).nextBytes(it) })
        val transfer = QrShareCodec.create(json)
        assertTrue(transfer.compressed.size > QrShareCodec.SCREEN_CHUNK_BYTES * QrShareCodec.MAX_PARTS)
        val frames = transfer.cameraFrames()
        assertTrue(frames.size <= QrShareCodec.MAX_PARTS)
        assertTrue(frames.all { QrShareCodec.parse(it).bytes.size <= 512 })
        val collector = QrShareCollector()
        var result: String? = null
        frames.shuffled(kotlin.random.Random(38)).forEach { collector.add(it)?.let { data -> result = data } }
        assertEquals(json, result)
    }

    @Test fun readsEveryPartInAnyOrderAndIgnoresDuplicates() {
        val json = buildString { repeat(4000) { append("授業メモ${Random(it.toLong()).nextLong()}") } }
        val frames = QrShareCodec.create(json).frames()
        assertTrue(frames.size > 3)
        assertTrue(frames.first().startsWith("SKTTP/QR:${QrShareCodec.VERSION}:"))
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
        failure(QrShareFailure.VERSION) { QrShareCollector().add(frame.replace("SKTTP/QR:${QrShareCodec.VERSION}:", "SKTTP/QR:99:")) }
        val fields = frame.split(':').toMutableList()
        fields[5] = "-1"
        failure(QrShareFailure.INVALID) { QrShareCollector().add(fields.joinToString(":")) }
        fields[4] = "9999999"
        fields[5] = "0"
        failure(QrShareFailure.INVALID) { QrShareCollector().add(fields.joinToString(":")) }
    }

    @Test fun acceptsLegacyVersionOneFramesButDoesNotMixVersions() {
        val frames = QrShareCodec.create("legacy ".repeat(1000)).frames(100)
        val legacy = frames.map { it.replace("SKTTP/QR:${QrShareCodec.VERSION}:", "SKTTP/QR:1:") }
        val collector = QrShareCollector()
        var result: String? = null
        legacy.forEach { collector.add(it)?.let { value -> result = value } }
        assertEquals("legacy ".repeat(1000), result)
        // 圧縮されにくいデータで複数枚に分割し、同一IDでも混在を拒否する。
        val multiple = QrShareCodec.create(Base64.getEncoder().encodeToString(ByteArray(300).also { Random(4).nextBytes(it) })).frames(100)
        val mixed = QrShareCollector()
        mixed.add(multiple.first())
        failure(QrShareFailure.DIFFERENT_TRANSFER) {
            mixed.add(multiple[1].replace("SKTTP/QR:${QrShareCodec.VERSION}:", "SKTTP/QR:1:"))
        }
    }

    @Test fun acceptsVersionTwoFramesAfterScopedVersionThreeUpgrade() {
        val text = "version two ".repeat(1000)
        val collector = QrShareCollector()
        var received: String? = null
        QrShareCodec.create(text).frames(100).map {
            it.replace("SKTTP/QR:${QrShareCodec.VERSION}:", "SKTTP/QR:2:")
        }.forEach { collector.add(it)?.let { json -> received = json } }
        assertEquals(text, received)
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
