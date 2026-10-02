package jp.linkserver.nittcsc.qr

import jp.linkserver.nittcsc.logic.QrShareCodec
import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.QrShareFailure

/** ヘッダーだけでなく各画像の寸法・枚数も検証し、大きなフレームの展開を防ぐ。 */
internal fun validateQrGif(bytes: ByteArray) {
    try {
        require(bytes.size >= 14 && bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) in setOf("GIF87a", "GIF89a"))
        fun word(offset: Int): Int {
            require(offset + 1 < bytes.size)
            return (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
        }
        val width = word(6)
        val height = word(8)
        require(width in 1..2048 && height in 1..2048)
        var position = 13
        val packed = bytes[10].toInt() and 255
        if (packed and 128 != 0) position += 3 * (1 shl ((packed and 7) + 1))
        var count = 0
        fun skipBlocks() {
            while (true) {
                require(position < bytes.size)
                val length = bytes[position++].toInt() and 255
                if (length == 0) return
                position += length
                require(position <= bytes.size)
            }
        }
        while (position < bytes.size) {
            when (bytes[position++].toInt() and 255) {
                0x21 -> { require(position < bytes.size); position++; skipBlocks() }
                0x2c -> {
                    require(position + 9 <= bytes.size)
                    val left = word(position)
                    val top = word(position + 2)
                    val frameWidth = word(position + 4)
                    val frameHeight = word(position + 6)
                    require(frameWidth > 0 && frameHeight > 0 && left + frameWidth <= width && top + frameHeight <= height)
                    val flags = bytes[position + 8].toInt() and 255
                    position += 9
                    if (flags and 128 != 0) position += 3 * (1 shl ((flags and 7) + 1))
                    require(position < bytes.size && (bytes[position++].toInt() and 255) in 2..8)
                    skipBlocks()
                    count++
                    if (count > QrShareCodec.MAX_PARTS) throw QrShareException(QrShareFailure.TOO_LARGE)
                }
                0x3b -> { require(count > 0); return }
                else -> throw QrShareException(QrShareFailure.INVALID)
            }
        }
        throw QrShareException(QrShareFailure.DAMAGED)
    } catch (e: QrShareException) { throw e }
    catch (_: Exception) { throw QrShareException(QrShareFailure.INVALID) }
}
