package jp.linkserver.nittcsc.logic

import kotlin.math.ceil
import kotlin.math.floor

internal data class QrPoint(val x: Float, val y: Float)
internal data class QrCameraDetection(val text: String, val corners: List<QrPoint>)
internal data class QrCameraRegion(val left: Int, val top: Int, val width: Int, val height: Int)

/** スキャン枠は目安だけ。追跡領域で失敗したら同じフレームのViewPort全体を読む。 */
internal class QrCameraTracking {
    private var tracked: QrCameraRegion? = null
    private var misses = 0

    fun read(width: Int, height: Int, decode: (QrCameraRegion) -> QrCameraDetection?): QrCameraDetection? {
        require(width > 0 && height > 0)
        val full = QrCameraRegion(0, 0, width, height)
        val preferred = tracked?.takeIf { it.left + it.width <= width && it.top + it.height <= height } ?: full
        val result = decode(preferred) ?: if (preferred != full) decode(full) else null
        if (result == null) {
            if (++misses >= 5) tracked = null
            return null
        }
        misses = 0
        val points = result.corners
        if (points.size == 4 && points.all { it.x.isFinite() && it.y.isFinite() }) {
            val left = points.minOf { it.x }
            val top = points.minOf { it.y }
            val right = points.maxOf { it.x }
            val bottom = points.maxOf { it.y }
            val margin = maxOf(right - left, bottom - top) * 0.15f
            val x0 = floor(left - margin).toInt().coerceIn(0, width - 1)
            val y0 = floor(top - margin).toInt().coerceIn(0, height - 1)
            val x1 = ceil(right + margin).toInt().coerceIn(x0 + 1, width)
            val y1 = ceil(bottom + margin).toInt().coerceIn(y0 + 1, height)
            tracked = QrCameraRegion(x0, y0, x1 - x0, y1 - y0)
        }
        return result
    }
}
