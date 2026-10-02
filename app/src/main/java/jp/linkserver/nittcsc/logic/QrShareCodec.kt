package jp.linkserver.nittcsc.logic

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class QrShareFailure { INVALID, VERSION, TOO_LARGE, DIFFERENT_TRANSFER, DAMAGED, SETTINGS }

class QrShareException(val failure: QrShareFailure) : IllegalArgumentException(failure.name)

/** 同期プロトコルとは独立した、一方向のQR転送形式。 */
object QrShareCodec {
    const val VERSION = 1
    const val SCREEN_CHUNK_BYTES = 200
    const val GIF_CHUNK_BYTES = 600
    const val IMAGE_CHUNK_BYTES = 1600
    const val MAX_PARTS = 128
    const val MAX_COMPRESSED_BYTES = 64 * 1024
    const val MAX_JSON_BYTES = 512 * 1024
    private const val PREFIX = "SKTTP/QR"

    data class Transfer(val compressed: ByteArray, val id: String, val digest: String) {
        /** 画像・GIF用。カメラに見せる画面はcameraFrames()を使う。 */
        fun frames(chunkBytes: Int = GIF_CHUNK_BYTES): List<String> {
            require(chunkBytes in 100..IMAGE_CHUNK_BYTES)
            val count = (compressed.size + chunkBytes - 1) / chunkBytes
            if (count !in 1..MAX_PARTS) throw QrShareException(QrShareFailure.TOO_LARGE)
            return (0 until count).map { index ->
                val bytes = compressed.copyOfRange(index * chunkBytes, minOf((index + 1) * chunkBytes, compressed.size))
                "$PREFIX:$VERSION:$id:$digest:$count:$index:${Base64.getEncoder().encodeToString(bytes)}"
            }
        }

        /** 小さいQRを優先する。既存の受信側も読める128枚以内に収める。 */
        fun cameraFrames(): List<String> {
            val minimumChunkBytes = (compressed.size + MAX_PARTS - 1) / MAX_PARTS
            return frames(maxOf(SCREEN_CHUNK_BYTES, minimumChunkBytes))
        }
    }

    fun create(json: String): Transfer {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_JSON_BYTES) throw QrShareException(QrShareFailure.TOO_LARGE)
        val compressed = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(bytes) }
        }.toByteArray()
        if (compressed.size > MAX_COMPRESSED_BYTES) {
            throw QrShareException(QrShareFailure.TOO_LARGE)
        }
        return Transfer(compressed, UUID.randomUUID().toString().replace("-", ""), hash(compressed))
    }

    internal fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    internal data class Part(val id: String, val digest: String, val count: Int, val index: Int, val bytes: ByteArray)

    internal fun parse(text: String): Part {
        try {
            if (text.length > 2400) throw QrShareException(QrShareFailure.TOO_LARGE)
            val fields = text.split(':', limit = 7)
            require(fields.size == 7 && fields[0] == PREFIX)
            if (fields[1] != VERSION.toString()) throw QrShareException(QrShareFailure.VERSION)
            require(fields[2].matches(Regex("[0-9a-f]{32}")) && fields[3].matches(Regex("[0-9a-f]{64}")))
            val count = fields[4].toInt()
            val index = fields[5].toInt()
            require(count in 1..MAX_PARTS && index in 0 until count)
            val bytes = Base64.getDecoder().decode(fields[6])
            require(bytes.size in 1..IMAGE_CHUNK_BYTES)
            return Part(fields[2], fields[3], count, index, bytes)
        } catch (e: QrShareException) {
            throw e
        } catch (_: Exception) {
            throw QrShareException(QrShareFailure.INVALID)
        }
    }

    fun isShareFrame(text: String): Boolean = text.startsWith("$PREFIX:")
}

/** 順不同・重複を許容する。表示用と画像用で分割数が異なるQRは混在させない。 */
class QrShareCollector {
    private var transferId: String? = null
    private var digest: String? = null
    var total: Int = 0
        private set
    private val parts = mutableMapOf<Int, ByteArray>()
    val received: Int get() = parts.size
    val missing: List<Int> get() = (0 until total).filter { it !in parts }.map { it + 1 }

    fun add(text: String): String? {
        val part = QrShareCodec.parse(text)
        if (transferId != null && (transferId != part.id || total != part.count || digest != part.digest)) {
            throw QrShareException(QrShareFailure.DIFFERENT_TRANSFER)
        }
        parts[part.index]?.let {
            if (!it.contentEquals(part.bytes)) throw QrShareException(QrShareFailure.DAMAGED)
            return null
        }
        if (parts.values.sumOf { it.size } + part.bytes.size > QrShareCodec.MAX_COMPRESSED_BYTES) {
            throw QrShareException(QrShareFailure.TOO_LARGE)
        }
        transferId = part.id
        digest = part.digest
        total = part.count
        parts[part.index] = part.bytes
        if (received != total) return null
        try {
            val bytes = ByteArrayOutputStream().also { out -> (0 until total).forEach { out.write(parts.getValue(it)) } }.toByteArray()
            if (QrShareCodec.hash(bytes) != digest) throw QrShareException(QrShareFailure.DAMAGED)
            val json = GZIPInputStream(bytes.inputStream()).use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    if (out.size() + size > QrShareCodec.MAX_JSON_BYTES) throw QrShareException(QrShareFailure.TOO_LARGE)
                    out.write(buffer, 0, size)
                }
                out.toByteArray()
            }
            return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(json)).toString()
        } catch (e: QrShareException) {
            throw e
        } catch (_: Exception) {
            throw QrShareException(QrShareFailure.DAMAGED)
        }
    }
}
