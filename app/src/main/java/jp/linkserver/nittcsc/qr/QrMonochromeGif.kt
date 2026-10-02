package jp.linkserver.nittcsc.qr

import java.io.OutputStream

/** 白黒画像専用。画素ごとの色オブジェクトや可変長リストを作らずGIFを圧縮する。 */
internal class QrMonochromeGif(
    private val output: OutputStream,
    private val width: Int,
    private val height: Int,
    private val intervalMs: Long
) {
    private var finished = false

    init {
        require(width in 1..2048 && height in 1..2048 && intervalMs in 10..655350)
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))
        word(width); word(height)
        // グローバル色表は黒・白の2色。背景は白。
        output.write(byteArrayOf(0x80.toByte(), 1, 0, 0, 0, 0, -1, -1, -1))
        output.write(byteArrayOf(0x21, -1, 11))
        output.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        output.write(byteArrayOf(3, 1, 0, 0, 0)) // 無限ループ
    }

    fun add(pixels: IntArray) {
        check(!finished)
        require(pixels.size == width * height)
        output.write(byteArrayOf(0x21, 0xf9.toByte(), 4, 4)) // フレームを保持、透過なし
        word((intervalMs / 10).toInt())
        output.write(byteArrayOf(0, 0, 0x2c))
        word(0); word(0); word(width); word(height)
        output.write(0) // 共通色表、非インターレース
        output.write(2) // LZW最小コード幅
        compress(pixels)
    }

    fun finish() {
        check(!finished)
        output.write(0x3b)
        finished = true
    }

    private fun word(value: Int) {
        output.write(value and 255)
        output.write(value ushr 8 and 255)
    }

    private fun compress(pixels: IntArray) {
        // 接頭コード×次の色(0/1)で直接引ける辞書。最大12ビット、4096コード。
        val transitions = IntArray(4096 * 2) { -1 }
        val blocks = GifBlocks(output)
        var nextCode = 6
        var codeSize = 3
        fun color(index: Int): Int {
            val rgb = pixels[index] and 0xffffff
            // クラス名に色付き絵文字が入った場合も、白黒の文字画像に変換する。
            return when (rgb) {
                0 -> 0
                0xffffff -> 1
                else -> if (((rgb ushr 16) * 77 + ((rgb ushr 8) and 255) * 150 + (rgb and 255) * 29) >= 128 * 256) 1 else 0
            }
        }
        blocks.code(4, codeSize) // CLEAR
        var prefix = color(0)
        for (i in 1 until pixels.size) {
            val suffix = color(i)
            val key = prefix * 2 + suffix
            val existing = transitions[key]
            if (existing >= 0) {
                prefix = existing
            } else {
                blocks.code(prefix, codeSize)
                if (nextCode < 4096) {
                    // 復号側の辞書は1コード遅れて増えるため、出力後に幅を更新する。
                    if (nextCode == (1 shl codeSize)) codeSize++
                    transitions[key] = nextCode++
                } else {
                    blocks.code(4, codeSize)
                    transitions.fill(-1)
                    nextCode = 6
                    codeSize = 3
                }
                prefix = suffix
            }
        }
        blocks.code(prefix, codeSize)
        // 最終画素の復号でも辞書が増える。境界ではENDの幅も更新する。
        if (nextCode == (1 shl codeSize) && codeSize < 12) codeSize++
        blocks.code(5, codeSize) // END
        blocks.finish()
    }

    private class GifBlocks(private val output: OutputStream) {
        private val block = ByteArray(255)
        private var length = 0
        private var bits = 0
        private var bitCount = 0

        fun code(value: Int, width: Int) {
            bits = bits or (value shl bitCount)
            bitCount += width
            while (bitCount >= 8) {
                byte(bits and 255)
                bits = bits ushr 8
                bitCount -= 8
            }
        }

        private fun byte(value: Int) {
            block[length++] = value.toByte()
            if (length == block.size) flush()
        }

        private fun flush() {
            if (length == 0) return
            output.write(length)
            output.write(block, 0, length)
            length = 0
        }

        fun finish() {
            if (bitCount > 0) byte(bits and 255)
            flush()
            output.write(0)
        }
    }
}
