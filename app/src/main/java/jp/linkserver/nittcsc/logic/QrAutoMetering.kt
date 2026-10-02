package jp.linkserver.nittcsc.logic

import kotlin.math.abs

/** プレビュー内の正規化した測光位置と、測光領域の大きさ。 */
internal data class QrMeteringRegion(val x: Float, val y: Float, val size: Float)

/** AEを継続しつつ、手動AFの中断やQRの微小な揺れによる要求の連発を防ぐ。 */
internal class QrAutoMetering {
    private var detected: QrMeteringRegion? = null
    private var detectedAt = 0L
    private var applied: QrMeteringRegion? = null
    private var appliedAt = 0L
    private var manualUntil = 0L

    fun recordQr(region: QrMeteringRegion, nowMs: Long) {
        detected = region
        detectedAt = nowMs
    }

    fun manualFocus(nowMs: Long) {
        manualUntil = nowMs + 3_000L
        applied = null
    }

    fun reset() {
        detected = null
        applied = null
        manualUntil = 0L
    }

    fun next(nowMs: Long): QrMeteringRegion? {
        if (nowMs < manualUntil) return null
        val previous = applied
        if (previous != null && nowMs - appliedAt < 750L) return null
        val target = detected?.takeIf { nowMs - detectedAt <= 1_500L }
            ?: QrMeteringRegion(0.5f, 0.5f, 0.35f)
        if (previous != null && abs(target.x - previous.x) < 0.08f &&
            abs(target.y - previous.y) < 0.08f && abs(target.size - previous.size) < 0.06f) return null
        applied = target
        appliedAt = nowMs
        return target
    }
}
