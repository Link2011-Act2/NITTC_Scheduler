package jp.linkserver.nittcsc.logic

import kotlin.math.atan2
import kotlin.math.hypot

internal data class QrScanTarget(val x: Float, val y: Float, val width: Float, val height: Float, val angle: Float)

/** 中央へ戻る0度も、現在の角度から最も近い同じ向きとして扱う。 */
internal class QrScannerRotation {
    private var target = 0f

    fun follow(angle: Float): Float {
        if (!angle.isFinite()) return target
        val delta = (((angle - target) % 360f + 540f) % 360f) - 180f
        target += delta
        return target
    }
}

/** CameraXでプレビュー座標へ変換済みの四隅を扱う。角度は±180度の境界で飛ばさない。 */
internal class QrScannerTracker {
    private var filtered: QrScanTarget? = null
    private var seenAt = 0L

    fun detect(corners: FloatArray, nowMs: Long): QrScanTarget? {
        if (corners.size != 8 || corners.any { !it.isFinite() }) return null
        val width = hypot(corners[2] - corners[0], corners[3] - corners[1])
        val height = hypot(corners[6] - corners[0], corners[7] - corners[1])
        if (width < 1f || height < 1f) return null
        val raw = QrScanTarget((corners[0] + corners[2] + corners[4] + corners[6]) / 4f,
            (corners[1] + corners[3] + corners[5] + corners[7]) / 4f,
            width * 1.3f, height * 1.3f,
            Math.toDegrees(atan2((corners[3] - corners[1]).toDouble(), (corners[2] - corners[0]).toDouble())).toFloat())
        val previous = filtered?.takeIf { nowMs - seenAt <= GRACE_MS }
        filtered = if (previous == null) raw else {
            val alpha = 0.4f
            val angleDelta = (((raw.angle - previous.angle) % 360f + 540f) % 360f) - 180f
            QrScanTarget(previous.x + (raw.x - previous.x) * alpha, previous.y + (raw.y - previous.y) * alpha,
                previous.width + (raw.width - previous.width) * alpha, previous.height + (raw.height - previous.height) * alpha,
                previous.angle + angleDelta * alpha)
        }
        seenAt = nowMs
        return filtered
    }

    fun current(nowMs: Long): QrScanTarget? = filtered?.takeIf { nowMs - seenAt <= GRACE_MS }

    companion object { const val GRACE_MS = 350L }
}

internal enum class QrScannerPhase { Idle, Tracking, Receiving, Completed, Error }

internal data class QrScannerVisualState(
    val phase: QrScannerPhase,
    val target: QrScanTarget?,
    val received: Int,
    val total: Int
) {
    val progress: Float get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
}
