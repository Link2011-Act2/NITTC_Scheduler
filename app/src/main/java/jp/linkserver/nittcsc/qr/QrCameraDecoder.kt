package jp.linkserver.nittcsc.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.detector.FinderPattern
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

internal data class QrPoint(val x: Float, val y: Float)
internal data class QrCameraDetection(val text: String, val corners: List<QrPoint>)
private data class QrRegion(val left: Int, val top: Int, val width: Int, val height: Int)

/** スキャンShapeは目安だけ。未検出時は映像全体、追跡中は前回の位置を優先する。 */
internal class QrCameraDecoder {
    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)
    private var tracked: QrRegion? = null
    private var misses = 0
    private var recoveryRotation = 1
    private var rotationBuffer = ByteArray(0)

    fun read(source: LuminanceSource): QrCameraDetection? {
        val full = QrRegion(0, 0, source.width, source.height)
        val preferred = tracked?.takeIf { it.left + it.width <= source.width && it.top + it.height <= source.height } ?: full
        var result = decode(source, preferred)
        if (result == null) {
            // 大きい映像全体は1フレーム1方向に制限。小さい追跡領域だけ同じフレームで全方向を試す。
            val attempts = if (preferred != full && preferred.width.toLong() * preferred.height <= 512 * 512) 3 else 1
            result = decodeRotated(source, preferred, attempts)
        }
        if (result == null && preferred != full) {
            result = decode(source, full) ?: decodeRotated(source, full, 1)
        }
        if (result == null && misses % 3 == 2) result = decode(source.invert(), full)
        if (result == null) {
            if (++misses >= 5) tracked = null
            return null
        }
        misses = 0
        val points = result.corners
        if (points.isNotEmpty()) {
            val left = points.minOf { it.x }
            val top = points.minOf { it.y }
            val right = points.maxOf { it.x }
            val bottom = points.maxOf { it.y }
            val margin = maxOf(right - left, bottom - top) * 0.15f
            val x0 = floor(left - margin).toInt().coerceIn(0, source.width - 1)
            val y0 = floor(top - margin).toInt().coerceIn(0, source.height - 1)
            val x1 = ceil(right + margin).toInt().coerceIn(x0 + 1, source.width)
            val y1 = ceil(bottom + margin).toInt().coerceIn(y0 + 1, source.height)
            tracked = QrRegion(x0, y0, x1 - x0, y1 - y0)
        }
        return result
    }

    private fun decode(source: LuminanceSource, region: QrRegion): QrCameraDetection? =
        decodeRegion(source.crop(region.left, region.top, region.width, region.height), region, 0)

    private fun decodeRotated(source: LuminanceSource, region: QrRegion, attempts: Int): QrCameraDetection? {
        val pixels = source.crop(region.left, region.top, region.width, region.height).matrix
        val start = recoveryRotation
        repeat(attempts) { index ->
            val turns = (start - 1 + index) % 3 + 1
            val rotated = rotateLuminance(pixels, region.width, region.height, turns)
            decodeRegion(rotated, region, turns)?.let {
                recoveryRotation = turns
                return it
            }
        }
        recoveryRotation = start % 3 + 1
        return null
    }

    private fun rotateLuminance(pixels: ByteArray, width: Int, height: Int, turns: Int): LuminanceSource {
        val size = width * height
        if (rotationBuffer.size < size) rotationBuffer = ByteArray(size)
        when (turns) {
            1 -> for (y in 0 until height) for (x in 0 until width) {
                rotationBuffer[(width - 1 - x) * height + y] = pixels[y * width + x]
            }
            2 -> for (i in 0 until size) rotationBuffer[size - 1 - i] = pixels[i]
            3 -> for (y in 0 until height) for (x in 0 until width) {
                rotationBuffer[x * height + height - 1 - y] = pixels[y * width + x]
            }
        }
        val rotatedWidth = if (turns == 2) width else height
        val rotatedHeight = if (turns == 2) height else width
        return PlanarYUVLuminanceSource(rotationBuffer, rotatedWidth, rotatedHeight,
            0, 0, rotatedWidth, rotatedHeight, false)
    }

    private fun decodeRegion(crop: LuminanceSource, region: QrRegion, turns: Int): QrCameraDetection? = try {
        val result = reader.decode(BinaryBitmap(HybridBinarizer(crop)), hints)
        // 90/270度では幅と高さも入れ替わる。回転とcropを戻してからCameraXへ渡す。
        QrCameraDetection(result.text, corners(result).map {
            val point = when (turns) {
                1 -> QrPoint(region.width - 1f - it.y, it.x)
                2 -> QrPoint(region.width - 1f - it.x, region.height - 1f - it.y)
                3 -> QrPoint(it.y, region.height - 1f - it.x)
                else -> it
            }
            QrPoint(point.x + region.left, point.y + region.top)
        })
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }

    private fun corners(result: Result): List<QrPoint> {
        val points = result.resultPoints ?: return emptyList()
        if (points.size < 3) return emptyList()
        // ZXingの順序は左下・左上・右上。検出模様の中心からQRの外側へ広げる。
        val bottomLeft = points[0]
        val topLeft = points[1]
        val topRight = points[2]
        val dx = topRight.x - topLeft.x
        val dy = topRight.y - topLeft.y
        val ex = bottomLeft.x - topLeft.x
        val ey = bottomLeft.y - topLeft.y
        val horizontal = hypot(dx, dy).coerceAtLeast(1f)
        val vertical = hypot(ex, ey).coerceAtLeast(1f)
        val moduleSize = points.take(3).filterIsInstance<FinderPattern>().map { it.estimatedModuleSize }
            .takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val padding = moduleSize?.times(4f) ?: minOf(horizontal, vertical) * 0.08f
        val px = dx * padding / horizontal
        val py = dy * padding / horizontal
        val qx = ex * padding / vertical
        val qy = ey * padding / vertical
        return listOf(
            QrPoint(topLeft.x - px - qx, topLeft.y - py - qy),
            QrPoint(topRight.x + px - qx, topRight.y + py - qy),
            QrPoint(topRight.x + ex + px + qx, topRight.y + ey + py + qy),
            QrPoint(bottomLeft.x - px + qx, bottomLeft.y - py + qy)
        )
    }
}

/** Y平面の余白とpixelStrideを尊重し、通常の連続画素は行単位でコピーする。 */
internal fun copyQrLuminancePlane(
    buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, target: ByteArray
) {
    require(width > 0 && height > 0 && pixelStride > 0 && target.size == width * height)
    val rowBytes = (width - 1) * pixelStride + 1
    require(rowStride >= rowBytes && buffer.remaining() >= (height - 1) * rowStride + rowBytes)
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
