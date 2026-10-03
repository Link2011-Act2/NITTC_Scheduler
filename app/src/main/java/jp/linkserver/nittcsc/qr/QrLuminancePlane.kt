package jp.linkserver.nittcsc.qr

import java.nio.ByteBuffer

/** JNIへ渡す前に、最終行の画素までバッファ内にあることを確認する。 */
internal fun validateQrLuminancePlane(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int) {
    require(width > 0 && height > 0 && pixelStride > 0)
    val rowBytes = (width - 1L) * pixelStride + 1L
    require(rowStride >= rowBytes && buffer.remaining().toLong() >= (height - 1L) * rowStride + rowBytes)
}

/** Y平面の余白とpixelStrideを尊重し、通常の連続画素は行単位でコピーする。 */
internal fun copyQrLuminancePlane(
    buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, target: ByteArray
) {
    require(target.size.toLong() == width.toLong() * height)
    validateQrLuminancePlane(buffer, width, height, rowStride, pixelStride)
    val input = buffer.duplicate()
    val start = input.position()
    for (y in 0 until height) {
        if (pixelStride == 1) {
            input.position(start + y * rowStride)
            input.get(target, y * width, width)
        } else {
            for (x in 0 until width) target[y * width + x] = input.get(start + y * rowStride + x * pixelStride)
        }
    }
}
